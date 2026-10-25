import { z } from 'zod';
import { cameraAccess, cameraAuthorized, cameraRequest, cameraId, cameraLifetime } from './camera-session';
import { code, commandKey, equal, hash, random, sign } from './crypto';
import { DeliveryError, production, type Subscription, type Transport } from './transport';

type Device = { id: string; role: 'android' | 'web' | 'native'; nativePeer?: string; tokenHash: string; name: string; peer?: string; pending?: string; fcm?: string; push?: Subscription; lastSeen: number; autoDismiss: boolean; claimExpires?: number };
type Pair = { androidId: string; expires: number };
class ApiError extends Error { constructor(public status: number, public code: string) { super(code); } }
const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}';
const eventId = z.string().regex(new RegExp('^evt_' + uuid + '$'));
const cmdId = z.string().regex(new RegExp('^cmd_' + uuid + '$'));
const fresh = z.number().int().refine(v => Math.abs(Date.now() - v) < 300000, 'STALE_TIMESTAMP');
const envelope = z.object({ timestamp: fresh, nonce: z.string().regex(new RegExp('^'+uuid+'$')) });
const replyAction = z.object({ actionId: z.string().regex(/^[0-9a-f]{64}$/), label: z.string().max(80) }).strict();
const replySchema = z.discriminatedUnion('mode', [
  z.object({ actionId: replyAction.shape.actionId, mode: z.literal('plain'), text: z.string().min(1).max(500).refine(v => !!v.trim() && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(v)) }).strict(),
  z.object({ actionId: replyAction.shape.actionId, mode: z.literal('encrypted'), version: z.literal(1), keyId: z.string().regex(/^[0-9a-f]{32}$/), iv: z.string().regex(/^[A-Za-z0-9_-]{16}$/), ciphertext: z.string().regex(/^[A-Za-z0-9_-]+$/).min(23).max(2022) }).strict()
]);
const eventSchema = z.object({
  id: eventId, type: z.enum(['notification', 'sms', 'call', 'removed', 'command_result']), timestamp: z.number().int(),
  packageName: z.string().max(200).optional(), app: z.string().max(80).optional(), title: z.string().max(160).optional(), body: z.string().max(500).optional(),
  notificationKey: z.string().max(300).optional(), sender: z.string().max(80).optional(), number: z.string().max(80).optional(), contact: z.string().max(100).optional(),
  notificationId:z.number().int().optional(),hasContentIntent:z.boolean().optional(),actionCount:z.number().int().min(0).max(64).optional(),
  postTime:z.number().int().nonnegative().optional(),replacesEventId:eventId.optional(),callId:eventId.optional(),
  reply: replyAction.optional(),
  state: z.enum(['ringing', 'ended', 'offhook']).optional(), targetEventId: eventId.optional(), commandId: cmdId.optional(),
  result: z.enum(['OPEN_SENT', 'USER_ACTION_REQUIRED', 'REMOVED', 'NOT_FOUND', 'NO_CONTENT_INTENT', 'PERMISSION_REQUIRED', 'PONG', 'SETTINGS_APPLIED', 'FAILED', 'REPLY_SENT', 'REPLY_UNAVAILABLE', 'REPLY_ACTION_CHANGED', 'REPLY_REQUIRES_UNLOCK', 'E2EE_KEY_REQUIRED', 'INVALID_REPLY']).optional()
}).strict().superRefine((v, c) => {
  if (v.timestamp > Date.now() + 300000 || v.timestamp < Date.now() - 86400000) c.addIssue({ code: 'custom', message: 'EVENT_TOO_OLD' });
  if (v.type === 'removed' && !v.targetEventId) c.addIssue({ code: 'custom', message: 'targetEventId required' });
  if (v.type === 'command_result' && (!v.commandId || !v.result)) c.addIssue({ code: 'custom', message: 'command result required' });
  if (v.reply && !['notification', 'sms'].includes(v.type)) c.addIssue({ code: 'custom', message: 'reply requires notification' });
});
// Temporary state only: never store titles, bodies, numbers, contacts or encrypted content.
const stateSchema=z.object({id:eventId,type:z.enum(['removed','command_result','call']),timestamp:z.number().int(),targetEventId:eventId.optional(),commandId:cmdId.optional(),callId:eventId.optional(),state:z.enum(['offhook','ended']).optional(),result:eventSchema.shape.result}).strict().superRefine((v,c)=>{
  if(v.timestamp>Date.now()+300000||v.timestamp<Date.now()-86400000)c.addIssue({code:'custom',message:'STALE_STATE'});
  if(v.type==='removed'&&!v.targetEventId)c.addIssue({code:'custom',message:'target required'});
  if(v.type==='command_result'&&(!v.commandId||!v.result))c.addIssue({code:'custom',message:'command required'});
  if(v.type==='call'&&(!v.callId||!v.state))c.addIssue({code:'custom',message:'call state required'});
});
type SyncState=z.infer<typeof stateSchema>;
const stateKey=(webId:string,kind:string,id:string)=>`status:${webId}:${kind}:${id}`;
async function storeState(env:Env,webId:string,state:SyncState){
  const kind=state.type==='command_result'?'command':state.type==='call'?'call':'event';
  const id=state.commandId??state.callId??state.targetEventId!;
  await env.STATE.put(stateKey(webId,kind,id),JSON.stringify(state),{expirationTtl:state.type==='call'?86400:3600});
}
const control=(e:z.infer<typeof eventSchema>)=>e.type==='removed'||e.type==='command_result'||(e.type==='call'&&e.state!=='ringing');
const subscription = z.object({ endpoint: z.string().url().refine(s => {
  const u = new URL(s); return u.protocol === 'https:' && !u.username && !u.password && !u.port &&
    (u.hostname === 'web.push.apple.com' || u.hostname.endsWith('.push.apple.com') || u.hostname === 'fcm.googleapis.com' || u.hostname === 'jmt17.google.com' || u.hostname === 'updates.push.services.mozilla.com');
}, 'UNSUPPORTED_PUSH_ENDPOINT'), keys: z.object({ p256dh: z.string().regex(/^[A-Za-z0-9_-]{87}$/), auth: z.string().regex(/^[A-Za-z0-9_-]{22}$/) }).strict() }).strip();
const encryptedSchema = z.object({id:eventId,type:z.literal('encrypted'),version:z.literal(1),timestamp:z.number().int(),keyId:z.string().regex(/^[0-9a-f]{32}$/),iv:z.string().regex(/^[A-Za-z0-9_-]{16}$/),ciphertext:z.string().regex(/^[A-Za-z0-9_-]+$/).min(24).max(3500)}).strict().refine(v=>v.timestamp<=Date.now()+300000&&v.timestamp>=Date.now()-86400000);
const json = (data: object, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Strict-Transport-Security': 'max-age=31536000' } });
const load = <T>(env: Env, key: string) => env.STATE.get<T>(key, 'json');
const save = (env: Env, d: Device) => env.STATE.put('device:' + d.id, JSON.stringify(d));
async function pushFor(env:Env,d:Device){const record=await load<{subscription:Subscription|null}>(env,'push:'+d.id);return record?record.subscription:d.push;}
async function fcmFor(env:Env,d:Device){return (await load<{fcmToken:string}>(env,'android:'+d.id))?.fcmToken??d.fcm;}
async function settingsFor(env:Env,d:Device){return (await load<{autoDismiss:boolean}>(env,'settings:'+d.id))?.autoDismiss??d.autoDismiss;}
async function lastSeen(env:Env,d:Device){return (await load<{timestamp:number}>(env,'heartbeat:'+d.id))?.timestamp??d.lastSeen;}
async function issue(env: Env, role: Device['role'], name: string, fields: Partial<Device> = {}) {
  const token = random(), tokenHash = await hash(token), id = crypto.randomUUID();
  const device: Device = { id, role, name, tokenHash, lastSeen: Date.now(), autoDismiss: false, ...fields };
  const options=role==='web'?{expirationTtl:600}:undefined;
  await env.STATE.put('device:'+device.id,JSON.stringify(device),options); await env.STATE.put('token:' + tokenHash, id,options);
  return { device, token };
}
async function authenticate(req: Request, env: Env) {
  const token = req.headers.get('Authorization')?.replace(/^Bearer /, '') ?? '';
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new ApiError(401, 'UNAUTHORIZED');
  const digest = await hash(token), id = await env.STATE.get('token:' + digest);
  const device = id ? await load<Device>(env, 'device:' + id) : null;
  if (!device || !equal(device.tokenHash, digest)) throw new ApiError(401, 'UNAUTHORIZED');
  return device;
}
async function read(req: Request,maximum=8192) {
  if (!req.headers.get('content-type')?.includes('application/json')) throw new ApiError(415, 'JSON_REQUIRED');
  if (Number(req.headers.get('content-length')) > maximum) throw new ApiError(413, 'REQUEST_TOO_LARGE');
  const reader = req.body?.getReader(); if (!reader) throw new ApiError(400, 'INVALID_BODY');
  let n = 0; const chunks: Uint8Array[] = [];
  while (true) { const { done, value } = await reader.read(); if (done) break; n += value.byteLength; if (n > maximum) { await reader.cancel(); throw new ApiError(413, 'REQUEST_TOO_LARGE'); } chunks.push(value); }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new ApiError(400, 'INVALID_JSON'); }
}
function requireRole(d: Device, role: Device['role']) { if (d.role !== role) throw new ApiError(403, 'WRONG_ROLE'); }
async function peer(env: Env, d: Device) {
  const id=d.peer??d.nativePeer;const p=id?await load<Device>(env,'device:'+id):null;
  if (!p || (d.role==='native'?p.nativePeer!==d.id:p.peer!==d.id)) throw new ApiError(409, 'NOT_PAIRED'); return p;
}
async function deliver(env: Env, d: Device, payload: object, transport: Transport) {
  const subscription=await pushFor(env,d);if (!subscription) throw new ApiError(409, 'PUSH_NOT_REGISTERED');
  try { await transport.push(env, subscription, payload); } catch (e) {
    if (e instanceof DeliveryError && e.code === 'PUSH_EXPIRED') {const current=await pushFor(env,d);if(current?.endpoint===subscription.endpoint)await env.STATE.put('push:'+d.id,JSON.stringify({subscription:null}));}
    throw e;
  }
}
export function createApp(transport: Transport = production) {
  return { async fetch(req: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(req.url), path = url.pathname;
      if (url.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(url.hostname)) throw new ApiError(400, 'HTTPS_REQUIRED');
      const origin = req.headers.get('Origin'); if (origin && origin !== url.origin) throw new ApiError(403, 'ORIGIN_REJECTED');
      if (req.method === 'GET' && path === '/api/status' && !req.headers.has('Authorization')) return json({ ok: true, service: 'Galaxy Relay', version: 1 });
      if (req.method === 'GET' && path === '/api/camera/client') {
        const asset=await env.ASSETS.fetch(new Request(new URL('/camera',url),req));
        return new Response(asset.body,{status:asset.status,headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Permissions-Policy':'microphone=(), camera=(self)','Content-Security-Policy':"default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; media-src blob:; img-src blob: data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"}});
      }
      if (req.method === 'GET' && path === '/api/config') return json({ vapidPublicKey: env.VAPID_PUBLIC_KEY });
      if (req.method !== 'POST' && !(req.method === 'GET' && path === '/api/status')) throw new ApiError(404, 'NOT_FOUND');
      const pairRequest = ['/api/android/register', '/api/pair/claim', '/api/pair/create'].includes(path);
      if (pairRequest && !(await env.PAIR_LIMIT.limit({ key: req.headers.get('CF-Connecting-IP') ?? 'local' })).success) throw new ApiError(429, 'RATE_LIMITED');
      const input = req.method === 'POST' ? await read(req,['/api/native/pull/upload','/api/camera/session'].includes(path)?65536:8192) : {};
      if (path === '/api/android/register') {
        const b = envelope.extend({ installSecret: z.string().min(32).max(200), name: z.string().min(1).max(80), fcmToken: z.string().min(10).max(4096).optional() }).strict().parse(input);
        if (!env.INSTALL_SECRET || !equal(b.installSecret, env.INSTALL_SECRET)) throw new ApiError(401, 'INSTALL_SECRET_REJECTED');
        const { device, token } = await issue(env, 'android', b.name);
        if(b.fcmToken)await env.STATE.put('android:'+device.id,JSON.stringify({fcmToken:b.fcmToken}));
        return json({ deviceId: device.id, token, commandKey: commandKey(env.COMMAND_SECRET, device.id) }, 201);
      }
      if (path === '/api/pair/claim') {
        const b = envelope.extend({ code: z.string().regex(/^\d{12}$/), name: z.string().min(1).max(80) }).strict().parse(input);
        const key = 'pair:' + await hash(b.code), pair = await load<Pair>(env, key);
        if (!pair || pair.expires < Date.now()) throw new ApiError(410, 'PAIR_EXPIRED_OR_USED');
        const android = await load<Device>(env, 'device:' + pair.androidId);
        if (!android || android.peer || android.pending) throw new ApiError(409, 'PAIR_UNAVAILABLE');
        const { device, token } = await issue(env, 'web', b.name, { pending: android.id, claimExpires: Date.now() + 300000 }); android.pending = device.id;
        await save(env, android); await env.STATE.delete(key);
        return json({ deviceId: device.id, token, approvalCode: device.id.slice(0, 8), awaitingApproval: true }, 201);
      }
      if(path==='/api/camera/session'){
        const b=cameraRequest.parse(input);
        if(!cameraAuthorized(env.COMMAND_SECRET,b))throw new ApiError(403,'CAMERA_GRANT_REJECTED');
        return json(await env.CAMERA.getByName(b.androidId).exchange(b.sessionId,b.role,b.op,b.after,b.packet));
      }
      const d = await authenticate(req, env);
      if (!(await env.API_LIMIT.limit({ key: d.id })).success) throw new ApiError(429, 'RATE_LIMITED');

      if (path.startsWith('/api/native/')) {
        const nativePeer = async () => {
          if(d.role==='native'){const a=await load<Device>(env,'device:'+d.peer);if(!a||a.nativePeer!==d.id)throw new ApiError(409,'NOT_PAIRED');return a;}
          requireRole(d,'android');const n=d.nativePeer?await load<Device>(env,'device:'+d.nativePeer):null;
          if(!n||n.role!=='native'||n.peer!==d.id)throw new ApiError(409,'NOT_PAIRED');return n;
        };
        const send = async (a:Device,type:string,target:string,fields?:{commandId:string;timestamp:number;nonce:string;reply?:z.infer<typeof replySchema>},autoDismiss='') => {
          const token=await fcmFor(env,a);if(!token)throw new ApiError(409,'FCM_NOT_REGISTERED');
          const values={commandId:fields?.commandId??'cmd_'+crypto.randomUUID(),type,eventId:target,timestamp:String(fields?.timestamp??Date.now()),nonce:fields?.nonce??crypto.randomUUID(),autoDismiss};
          const reply=fields?.reply?JSON.stringify(fields.reply):undefined;
          const canonical=[values.commandId,type,target,values.timestamp,values.nonce,autoDismiss,...(reply?[reply]:[])].join('\n');
          const data={...values,...(reply?{reply}:{}),signature:sign(commandKey(env.COMMAND_SECRET,a.id),canonical)};
          if(Buffer.byteLength(JSON.stringify(data))>3500)throw new ApiError(413,'REPLY_TOO_LARGE');
          await transport.fcm(env,token,data);return values.commandId;
        };
        if(path==='/api/native/provision'){
          requireRole(d,'android');const b=envelope.extend({name:z.string().min(1).max(80)}).strict().parse(input);
          if(d.nativePeer)throw new ApiError(409,'NATIVE_ALREADY_PAIRED');
          const created=await issue(env,'native',b.name,{peer:d.id});d.nativePeer=created.device.id;await save(env,d);
          return json({deviceId:created.device.id,token:created.token},201);
        }
        if(path==='/api/native/revoke'){
          const other=await nativePeer(),a=d.role==='android'?d:other,n=d.role==='native'?d:other;
          if(env.CAMERA)await env.CAMERA.getByName(a.id).revoke(n.id);
          let androidNotified=false;try{await send(a,'NATIVE_REVOKE','',undefined,n.id);androidNotified=true;}catch{/* Token revocation still proceeds if FCM is unavailable. */}
          delete a.nativePeer;await save(env,a);await env.STATE.delete('token:'+n.tokenHash);await env.STATE.delete('device:'+n.id);
          let cursor:string|undefined;do{const page=await env.STATE.list({prefix:'native:'+n.id+':',limit:100,cursor});await Promise.all(page.keys.map(k=>env.STATE.delete(k.name)));cursor=page.list_complete?undefined:page.cursor;}while(cursor);
          return json({revoked:true,androidNotified});
        }
        if(path==='/api/native/camera/start'){
          requireRole(d,'native');envelope.strict().parse(input);const a=await nativePeer(),sessionId='evt_'+crypto.randomUUID(),expires=Date.now()+cameraLifetime,room=env.CAMERA.getByName(a.id);
          if(!await room.start(sessionId,d.id,expires))throw new ApiError(409,'CAMERA_BUSY');
          try{await send(a,'NATIVE_CAMERA',sessionId,undefined,JSON.stringify({deviceId:d.id,expires:Date.now()+35000}));}
          catch(error){await room.exchange(sessionId,'viewer','end',0);throw error;}
          return json(cameraAccess(env.COMMAND_SECRET,sessionId,a.id,d.id,expires,'viewer'),202);
        }
        if(path==='/api/native/camera/attach'){
          requireRole(d,'android');const b=envelope.extend({sessionId:cameraId}).strict().parse(input),n=await nativePeer();
          const expires=await env.CAMERA.getByName(d.id).attach(b.sessionId,n.id);if(!expires)throw new ApiError(410,'CAMERA_ENDED');
          return json(cameraAccess(env.COMMAND_SECRET,b.sessionId,d.id,n.id,expires,'sender'));
        }
        if(path==='/api/native/pull/start'){
          requireRole(d,'native');envelope.strict().parse(input);const a=await nativePeer(),requestId='evt_'+crypto.randomUUID(),expires=Date.now()+120000;
          const grant=sign(env.COMMAND_SECRET,['native-pull/v2',requestId,d.id,a.id,expires].join('\n'));
          await send(a,'NATIVE_PULL',requestId,undefined,JSON.stringify({grant,expires}));
          return json({requestId,expires,grant},202);
        }
        // User-triggered, end-to-end encrypted one-shot location. Domain separated
        // grants cannot be used by the notification pull routes or another pair.
        if(path==='/api/native/locate/start'){
          requireRole(d,'native');envelope.strict().parse(input);const a=await nativePeer(),requestId='evt_'+crypto.randomUUID(),expires=Date.now()+120000;
          const grant=sign(env.COMMAND_SECRET,['native-locate/v1',requestId,d.id,a.id,expires].join('\n'));
          await send(a,'NATIVE_LOCATE',requestId,undefined,JSON.stringify({grant,expires,deviceId:d.id}));
          return json({requestId,expires,grant},202);
        }
        if(['/api/native/locate/upload','/api/native/locate/result','/api/native/locate/ack'].includes(path)){
          const locating=envelope.extend({requestId:eventId,expires:z.number().int(),grant:z.string().regex(/^[A-Za-z0-9_-]{43}$/)});
          const upload=path.endsWith('/upload');requireRole(d,upload?'android':'native');
          const packetSchema=z.object({version:z.literal(2),iv:z.string().regex(/^[A-Za-z0-9_-]{16}$/),ciphertext:z.string().regex(/^[A-Za-z0-9_-]{23,4096}$/)}).strict();
          const b=(upload?locating.extend({packet:packetSchema}):locating).strict().parse(input),other=await nativePeer(),n=upload?other:d,a=upload?d:other;
          if(b.expires<Date.now()||b.expires>Date.now()+120000)throw new ApiError(410,'REQUEST_EXPIRED');
          if(!equal(b.grant,sign(env.COMMAND_SECRET,['native-locate/v1',b.requestId,n.id,a.id,b.expires].join('\n'))))throw new ApiError(403,'REQUEST_REJECTED');
          const key='native:'+n.id+':location:'+b.requestId;
          if(upload){const packet=packetSchema.parse('packet' in b?b.packet:undefined);await env.STATE.put(key,JSON.stringify(packet),{expirationTtl:120});return json({accepted:true});}
          if(path.endsWith('/ack')){await env.STATE.delete(key);return json({accepted:true});}
          const packet=await load<object>(env,key);return json(packet?{ready:true,packet}:{ready:false});
        }
        const requestSchema=envelope.extend({requestId:eventId,expires:z.number().int(),grant:z.string().regex(/^[A-Za-z0-9_-]{43}$/)});
        const authorizedRequest=(b:{requestId:string;expires:number;grant:string},native:Device,android:Device)=>{
          if(b.expires<Date.now()||b.expires>Date.now()+120000)throw new ApiError(410,'REQUEST_EXPIRED');
          if(!equal(b.grant,sign(env.COMMAND_SECRET,['native-pull/v2',b.requestId,native.id,android.id,b.expires].join('\n'))))throw new ApiError(403,'REQUEST_REJECTED');
        };
        if(path==='/api/native/pull/upload'){
          requireRole(d,'android');const b=requestSchema.extend({packet:z.object({version:z.literal(2),iv:z.string().regex(/^[A-Za-z0-9_-]{16}$/),ciphertext:z.string().regex(/^[A-Za-z0-9_-]{23,44000}$/)}).strict()}).strict().parse(input),n=await nativePeer();
          authorizedRequest(b,n,d);
          // Only a user-requested E2EE transit packet, never searchable notification history.
          await env.STATE.put('native:'+n.id+':packet:'+b.requestId,JSON.stringify(b.packet),{expirationTtl:120});return json({accepted:true});
        }
        if(path==='/api/native/pull/result'){
          requireRole(d,'native');const b=requestSchema.strict().parse(input),a=await nativePeer();authorizedRequest(b,d,a);
          const packet=await load<object>(env,'native:'+d.id+':packet:'+b.requestId);return json(packet?{ready:true,packet}:{ready:false});
        }
        if(path==='/api/native/pull/ack'){
          requireRole(d,'native');const b=requestSchema.strict().parse(input),a=await nativePeer();
          authorizedRequest(b,d,a);
          await send(a,'NATIVE_ACK',b.requestId);await env.STATE.delete('native:'+d.id+':packet:'+b.requestId);
          return json({accepted:true});
        }
        if(path==='/api/native/command'){
          requireRole(d,'native');const b=envelope.extend({type:z.enum(['OPEN','DISMISS','PING','REPLY']),eventId:eventId.optional(),commandId:cmdId,reply:replySchema.optional()}).strict().parse(input),a=await nativePeer();
          if(b.type!=='PING'&&!b.eventId)throw new ApiError(400,'EVENT_ID_REQUIRED');
          if((b.type==='REPLY')!==!!b.reply||(b.reply&&b.reply.mode!=='encrypted'))throw new ApiError(400,'ENCRYPTED_REPLY_REQUIRED');
          return json({accepted:true,commandId:await send(a,b.type,b.eventId??'',b)});
        }
        throw new ApiError(404,'NOT_FOUND');
      }


      if (path === '/api/status') {
          let connected = false, peerLastSeen: number | null = null, pairedDevice:Device|undefined;
          try { const p = await peer(env, d); connected = true; pairedDevice=p;peerLastSeen = await lastSeen(env,p); } catch { /* unpaired status */ }
          const recipient=d.role==='web'?d:pairedDevice,recipientPush=recipient?.role==='web'?await pushFor(env,recipient):null;
        const pending = d.pending ? await load<Device>(env, 'device:' + d.pending) : null;
        return json({ ok: true, deviceId: d.id, paired: connected, peerLastSeen, lastSeen: await lastSeen(env,d), pushRegistered: !!await pushFor(env,d), fcmRegistered: !!await fcmFor(env,d),
            pushFingerprint:recipientPush?await hash(recipientPush.endpoint):null,autoDismiss: await settingsFor(env,d), pendingId: d.role === 'android' && pending?.claimExpires && pending.claimExpires > Date.now() ? pending.id : null,
          approvalCode: d.role === 'android' && pending ? pending.id.slice(0, 8) : null });
      }
      envelope.parse(input);
      if(path==='/api/state'){
        requireRole(d,'android');const b=envelope.extend({event:stateSchema}).strict().parse(input),w=await peer(env,d);
        await storeState(env,w.id,b.event);return json({accepted:true,id:b.event.id,delivery:'state'});
      }
      if(path==='/api/sync'){
        requireRole(d,'web');await peer(env,d);
        const b=envelope.extend({eventIds:z.array(eventId).max(20).default([]),commandIds:z.array(cmdId).max(8).default([]),callIds:z.array(eventId).max(10).default([])}).strict().parse(input);
        const keys=[...b.eventIds.map(id=>stateKey(d.id,'event',id)),...b.commandIds.map(id=>stateKey(d.id,'command',id)),...b.callIds.map(id=>stateKey(d.id,'call',id))];
        const states=(await Promise.all([...new Set(keys)].map(key=>load<SyncState>(env,key)))).filter(Boolean);
        return json({states});
      }
      if (path === '/api/pair/create') {
        requireRole(d, 'android'); if (d.peer) throw new ApiError(409, 'ALREADY_PAIRED');
        if (d.pending) { const old=await load<Device>(env,'device:'+d.pending);if(old?.pending===d.id&&!old.peer){await env.STATE.delete('token:'+old.tokenHash);await env.STATE.delete('device:'+old.id);}delete d.pending; await save(env, d); } const c = code(), expires = Date.now() + 300000;
        await env.STATE.put('pair:' + await hash(c), JSON.stringify({ androidId: d.id, expires }), { expirationTtl: 300 });
        return json({ code: c, expires });
      }
      if (path === '/api/pair/approve') {
        requireRole(d, 'android'); const b = envelope.extend({ webId: z.string().uuid() }).strict().parse(input);
        if (d.pending !== b.webId || d.peer) throw new ApiError(409, 'CLAIM_CHANGED');
        const web = await load<Device>(env, 'device:' + b.webId);
        if (!web || web.pending !== d.id || !web.claimExpires || web.claimExpires < Date.now()) throw new ApiError(410, 'CLAIM_EXPIRED');
        d.peer = web.id; web.peer = d.id; delete d.pending; delete web.pending; delete web.claimExpires;
        await save(env, d); await save(env, web);await env.STATE.put('token:'+web.tokenHash,web.id); return json({ paired: true });
      }
      if (path === '/api/android/update') {
        requireRole(d, 'android'); const b = envelope.extend({ fcmToken: z.string().min(10).max(4096) }).strict().parse(input);
        await env.STATE.put('android:'+d.id,JSON.stringify({fcmToken:b.fcmToken})); return json({ ok: true });
      }
      if (path === '/api/web/register') {
        requireRole(d, 'web'); const b = envelope.extend({ subscription }).strict().parse(input);
        await env.STATE.put('push:'+d.id,JSON.stringify({subscription:b.subscription})); return json({ ok: true });
      }
      if (path === '/api/ping') {
        if (Date.now() - await lastSeen(env,d) > 600000) await env.STATE.put('heartbeat:'+d.id,JSON.stringify({timestamp:Date.now()})); return json({ ok: true });
      }
      if (path === '/api/event') {
        requireRole(d, 'android'); const b = envelope.extend({ event: z.union([eventSchema,encryptedSchema]) }).strict().parse(input); const w = await peer(env, d);
        if(b.event.type!=='encrypted'&&control(b.event)){
          const e=b.event;
          const minimal=Object.fromEntries(['id','type','timestamp','targetEventId','commandId','callId','state','result'].filter(k=>k in e).map(k=>[k,e[k as keyof typeof e]]));
          // Legacy call updates without a session id cannot target an existing alert; do not emit a status banner.
          if(e.type!=='call'||e.callId)await storeState(env,w.id,stateSchema.parse(minimal));
          return json({accepted:true,id:e.id,delivery:'state'});
        }
          // Notification content/history is never persisted to KV. Receivers deduplicate durably.
        const payload = { ...b.event, autoDismiss: await settingsFor(env,w) };
        if (Buffer.byteLength(JSON.stringify(payload)) > (b.event.type==='encrypted'?3800:2800)) throw new ApiError(413, 'PUSH_TOO_LARGE');
        await deliver(env, w, payload, transport); return json({ accepted: true, id: b.event.id });
      }
      if (['/api/command/open', '/api/command/dismiss', '/api/command/ack', '/api/command/ping', '/api/command/reply', '/api/settings'].includes(path)) {
        requireRole(d, 'web');
        const settings = path === '/api/settings';
        const b = envelope.extend({ eventId: eventId.optional(), commandId: cmdId, autoDismiss: z.boolean().optional(), reply: replySchema.optional() }).strict().parse(input);
        if ((path === '/api/command/reply') !== !!b.reply) throw new ApiError(400, 'REPLY_REQUIRED');
        if (!settings && path !== '/api/command/ping' && !b.eventId) throw new ApiError(400, 'EVENT_ID_REQUIRED');
        if (settings && b.autoDismiss === undefined) throw new ApiError(400, 'SETTING_REQUIRED');
        const a = await peer(env, d), fcm=await fcmFor(env,a); if (!fcm) throw new ApiError(409, 'FCM_NOT_REGISTERED');
        const type = settings ? 'SETTINGS_SYNC' : path.split('/').at(-1)!.toUpperCase();
        const setting=await settingsFor(env,d);if (type === 'ACK' && !setting) return json({ accepted: true, skipped: true, commandId: b.commandId });
        const autoDismiss = settings ? String(b.autoDismiss) : type === 'ACK' ? String(setting) : '';
        const reply = b.reply ? JSON.stringify(b.reply) : undefined;
        const fields = [b.commandId, type, b.eventId ?? '', b.timestamp, b.nonce, autoDismiss];
        if (reply) fields.push(reply); // Bind the exact JSON string, including action and encrypted/plain text.
        const data = { commandId: b.commandId, type, eventId: b.eventId ?? '', timestamp: String(b.timestamp), nonce: b.nonce,
          autoDismiss, ...(reply ? {reply} : {}), signature: sign(commandKey(env.COMMAND_SECRET, a.id), fields.join('\n')) };
        if (Buffer.byteLength(JSON.stringify(data)) > 3500) throw new ApiError(413, 'REPLY_TOO_LARGE');
        await transport.fcm(env, fcm, data);
        if (settings) await env.STATE.put('settings:'+d.id,JSON.stringify({autoDismiss:b.autoDismiss}));
        return json({ accepted: true, commandId: b.commandId });
      }
      if (path === '/api/unpair') {
        if(d.role==='native')throw new ApiError(403,'USE_NATIVE_REVOKE');
        const related = new Set([d.id, d.peer, d.pending, d.nativePeer].filter(Boolean) as string[]);
        for (const id of related) { const value = await load<Device>(env, 'device:' + id);
          if(value && (id===d.id||related.has(value.peer??'')||value.pending===d.id)){if(value.nativePeer)related.add(value.nativePeer);
            if(value.role==='native'&&value.peer&&env.CAMERA)await env.CAMERA.getByName(value.peer).revoke(value.id);
            await env.STATE.delete('token:' + value.tokenHash); await env.STATE.delete('device:' + id);
              for(const prefix of ['push:','android:','settings:','heartbeat:'])await env.STATE.delete(prefix+id);
              // Receipt keys contain metadata only and belong to this revoked device.
              let cursor:string|undefined;
              do{const page=await env.STATE.list({prefix:`status:${id}:`,limit:100,cursor});for(let i=0;i<page.keys.length;i+=20)await Promise.all(page.keys.slice(i,i+20).map(key=>env.STATE.delete(key.name)));cursor=page.list_complete?undefined:page.cursor;}while(cursor);
          }
        }
        return json({ revoked: true });
      }
      throw new ApiError(404, 'NOT_FOUND');
    } catch (e) {
      if (e instanceof ApiError) return json({ error: e.code }, e.status);
      if (e instanceof z.ZodError) return json({ error: 'INVALID_INPUT' }, 400);
      if (e instanceof DeliveryError) return json({ error: e.code }, e.status);
      console.warn('relay_error',e instanceof Error?e.name:'unknown');
      return json({ error: 'TEMPORARILY_UNAVAILABLE' }, 503);
    }
  } };
}
export default createApp();
