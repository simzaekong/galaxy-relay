import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createApp } from '../src/index';
import { DeliveryError } from '../src/transport';
import { commandKey, sign,hash } from '../src/crypto';
import { CameraSession } from '../src/camera-session';
class MemoryKV {
  data = new Map<string, string>();
  ttl = new Map<string, number>();
  async get(key: string, type?: string) { const v = this.data.get(key); return v ? (type === 'json' ? JSON.parse(v) : v) : null; }
  async put(key: string, value: string, options?: {expirationTtl?:number}) { this.data.set(key, value);if(options?.expirationTtl)this.ttl.set(key,options.expirationTtl); }
  async delete(key: string) { this.data.delete(key); }
  async list({prefix}: {prefix:string}) {return {keys:[...this.data.keys()].filter(key=>key.startsWith(prefix)).map(name=>({name})),list_complete:true};}
}
const transport = { push: vi.fn(async () => {}), fcm: vi.fn(async () => {}) };
let kv: MemoryKV, env: Env, app: ReturnType<typeof createApp>;
const nonce = () => crypto.randomUUID();
async function request(path: string, data?: object, token?: string) {
  const response = await app.fetch(new Request('https://relay.example.com' + path, { method: data ? 'POST' : 'GET', headers: { ...(data ? {'content-type':'application/json'} : {}), ...(token ? {Authorization:'Bearer '+token} : {}) }, body: data ? JSON.stringify({timestamp:Date.now(),nonce:nonce(),...data}) : undefined }), env);
  return { status: response.status, body: await response.json() as Record<string, any> };
}
async function paired() {
  const android = (await request('/api/android/register', { name:'Galaxy',installSecret:'x'.repeat(40),fcmToken:'fcm-registration-token' })).body;
  const pair = (await request('/api/pair/create', {}, android.token)).body;
  const web = (await request('/api/pair/claim', {code:pair.code,name:'iPhone'})).body;
  expect((await request('/api/pair/approve',{webId:web.deviceId},android.token)).status).toBe(200);
  await request('/api/web/register',{subscription:{endpoint:'https://web.push.apple.com/mock',keys:{p256dh:'B'.repeat(87),auth:'A'.repeat(22)}}},web.token);
  return {android,web,pair};
}
beforeEach(() => {
  kv = new MemoryKV();
  env = {STATE:kv,PAIR_LIMIT:{limit:async()=>({success:true})},API_LIMIT:{limit:async()=>({success:true})},INSTALL_SECRET:'x'.repeat(40),COMMAND_SECRET:'secret',VAPID_PUBLIC_KEY:'public',VAPID_PRIVATE_KEY:'private',VAPID_SUBJECT:'mailto:a@example.com',FCM_PROJECT_ID:'test',FCM_CLIENT_EMAIL:'test',FCM_PRIVATE_KEY:'test'} as unknown as Env;
  const rooms=new Map<string,CameraSession>();
  Object.assign(env,{CAMERA:{getByName:(id:string)=>{if(!rooms.has(id))rooms.set(id,new CameraSession());const room=rooms.get(id)!;return {start:(...args:Parameters<CameraSession['start']>)=>room.start(...args),attach:(...args:Parameters<CameraSession['attach']>)=>room.attach(...args),exchange:(...args:Parameters<CameraSession['exchange']>)=>room.exchange(...args),revoke:(id:string)=>room.revoke(id)};}}});
  app = createApp(transport); vi.clearAllMocks();
});
describe('Backend independent mock clients', () => {
  it('signs and relays bounded replies without persisting their contents',async()=>{
    const {android,web}=await paired(),snapshot=new Map(kv.data);
    const base={commandId:'cmd_'+crypto.randomUUID(),eventId:'evt_'+crypto.randomUUID()};
    const reply={actionId:'a'.repeat(64),mode:'plain',text:'좋아요!\n곧 도착해요.'};
    expect((await request('/api/command/reply',{...base,reply},web.token)).status).toBe(200);
    const data=(transport.fcm.mock.calls.at(-1)! as unknown as [Env,string,Record<string,string>])[2];
    expect(JSON.parse(data.reply)).toEqual(reply);expect(data.type).toBe('REPLY');
    expect(data.signature).toBe(sign(commandKey('secret',android.deviceId),[data.commandId,data.type,data.eventId,data.timestamp,data.nonce,data.autoDismiss,data.reply].join('\n')));
    expect(kv.data).toEqual(snapshot);
    const encrypted={actionId:'a'.repeat(64),mode:'encrypted',version:1,keyId:'a'.repeat(32),iv:'B'.repeat(16),ciphertext:'C'.repeat(2022)};
    expect((await request('/api/command/reply',{...base,reply:encrypted},web.token)).status).toBe(200);
    expect(kv.data).toEqual(snapshot);
    expect((await request('/api/command/reply',{...base,reply:{...encrypted,ciphertext:'C'.repeat(23)}},web.token)).status).toBe(200);
    expect((await request('/api/command/reply',{...base,reply:{...encrypted,ciphertext:'C'.repeat(22)}},web.token)).status).toBe(400);
    expect((await request('/api/command/reply',{...base,reply},android.token)).status).toBe(403);
    for(const invalid of [{...reply,text:'  '},{...reply,text:'x'.repeat(501)},{...reply,text:'hello\u0000'},{...reply,remoteInputKey:'injected'},{...encrypted,text:'leak'},{...encrypted,ciphertext:'C'.repeat(2023)}])
      expect((await request('/api/command/reply',{...base,reply:invalid},web.token)).status).toBe(400);
    expect((await request('/api/command/reply',base,web.token)).status).toBe(400);
    expect((await request('/api/command/open',{...base,reply},web.token)).status).toBe(400);
    expect((await request('/api/command/reply',{commandId:base.commandId,reply},web.token)).status).toBe(400);
  });
  it('accepts reply capabilities and quiet execution receipts',async()=>{
    const {android,web}=await paired(),target='evt_'+crypto.randomUUID(),cmd='cmd_'+crypto.randomUUID();
    expect((await request('/api/event',{event:{id:target,type:'notification',timestamp:Date.now(),reply:{actionId:'a'.repeat(64),label:'답장'}}},android.token)).status).toBe(200);
    transport.push.mockClear();
    const result={id:'evt_'+crypto.randomUUID(),type:'command_result',commandId:cmd,targetEventId:target,result:'REPLY_SENT',timestamp:Date.now()};
    expect((await request('/api/state',{event:result},android.token)).status).toBe(200);
    expect((await request('/api/sync',{commandIds:[cmd]},web.token)).body.states).toEqual([result]);
    expect(transport.push).not.toHaveBeenCalled();
  });
  it('stores only temporary status metadata and never pushes command/removal/call updates',async()=>{
    const {android,web}=await paired();const target='evt_'+crypto.randomUUID(),command='cmd_'+crypto.randomUUID();
    const result={id:'evt_'+crypto.randomUUID(),type:'command_result',commandId:command,targetEventId:target,result:'REMOVED',timestamp:Date.now()};
    expect((await request('/api/state',{event:result},android.token)).status).toBe(200);
    expect(transport.push).not.toHaveBeenCalled();
    const response=await request('/api/sync',{commandIds:[command]},web.token);expect(response.body.states).toEqual([result]);
    expect((await request('/api/state',{event:{...result,body:'private body'}},android.token)).status).toBe(400);
    expect((await request('/api/sync',{commandIds:[command]},android.token)).status).toBe(403);
    expect((await request('/api/state',{event:result},web.token)).status).toBe(403);
    expect((await request('/api/sync',{eventIds:Array.from({length:21},()=>target)},web.token)).status).toBe(400);
    expect([...kv.data.keys()].some(key=>key.startsWith('status:'+web.deviceId+':'))).toBe(true);
    expect((await request('/api/unpair',{},web.token)).status).toBe(200);
    expect([...kv.data.keys()].some(key=>key.startsWith('status:'+web.deviceId+':'))).toBe(false);
  });
  it('quietly handles legacy plaintext control events and isolates state from other pairs',async()=>{
    const {android,web}=await paired(),other=await paired(),target='evt_'+crypto.randomUUID();
    const removed={id:'evt_'+crypto.randomUUID(),type:'removed',targetEventId:target,timestamp:Date.now()};
    expect((await request('/api/event',{event:removed},android.token)).body.delivery).toBe('state');
    expect(transport.push).not.toHaveBeenCalled();expect((await request('/api/sync',{eventIds:[target]},web.token)).body.states).toEqual([removed]);
    expect((await request('/api/sync',{eventIds:[target]},other.web.token)).body.states).toEqual([]);
  });
  it('keeps the latest phase of a call without persisting caller information',async()=>{
    const {android,web}=await paired(),callId='evt_'+crypto.randomUUID();
    for(const state of ['offhook','ended'])expect((await request('/api/state',{event:{id:'evt_'+crypto.randomUUID(),type:'call',callId,state,timestamp:Date.now()}},android.token)).status).toBe(200);
    const states=(await request('/api/sync',{callIds:[callId]},web.token)).body.states;expect(states).toHaveLength(1);expect(states[0].state).toBe('ended');
    expect(transport.push).not.toHaveBeenCalled();expect(JSON.stringify(states)).not.toContain('contact');
  });
  it('confirms the active web push fingerprint without returning subscription credentials',async()=>{
    const {android,web}=await paired();const a=(await request('/api/status',undefined,android.token)).body,w=(await request('/api/status',undefined,web.token)).body;
    expect(a.pushFingerprint).toBe(await hash('https://web.push.apple.com/mock'));expect(w.pushFingerprint).toBe(a.pushFingerprint);expect(JSON.stringify(a)).not.toContain('endpoint');expect((await request('/api/status')).body.pushFingerprint).toBeUndefined();
  });
  it('settings and heartbeat cannot overwrite independently registered push metadata',async()=>{
    const {android,web}=await paired();
    const key='device:'+web.deviceId, stale=JSON.parse(kv.data.get(key)!);stale.push={endpoint:'https://jmt17.google.com/stale',keys:{p256dh:'B'.repeat(87),auth:'A'.repeat(22)}};kv.data.set(key,JSON.stringify(stale));
    expect((await request('/api/settings',{commandId:'cmd_'+crypto.randomUUID(),autoDismiss:true},web.token)).status).toBe(200);
    await request('/api/ping',{},web.token);
    expect((await request('/api/event',{event:{id:'evt_'+crypto.randomUUID(),type:'sms',body:'test',timestamp:Date.now()}},android.token)).status).toBe(200);
    expect(transport.push).toHaveBeenLastCalledWith(env,expect.objectContaining({endpoint:'https://web.push.apple.com/mock'}),expect.objectContaining({autoDismiss:true}));
  });
  it('relays opaque E2EE envelopes without storing or accepting plaintext extras',async()=>{
    const {android}=await paired(), snapshot=new Map(kv.data);
    const event={id:'evt_'+crypto.randomUUID(),type:'encrypted',version:1,timestamp:Date.now(),keyId:'a'.repeat(32),iv:'B'.repeat(16),ciphertext:'C'.repeat(200)};
    expect((await request('/api/event',{event},android.token)).status).toBe(200);
    expect(transport.push).toHaveBeenCalledWith(env,expect.anything(),expect.objectContaining(event));expect(kv.data).toEqual(snapshot);
    expect((await request('/api/event',{event:{...event,body:'leaked'}},android.token)).status).toBe(400);
  });
  it('public status / rejects bad token and missing registration secret',async()=>{
    expect((await request('/api/status')).status).toBe(200);
    expect((await request('/api/ping',{},'x'.repeat(43))).status).toBe(401);
    expect((await request('/api/android/register',{name:'G',installSecret:'z'.repeat(40)})).status).toBe(401);
  });
  it('creates 12 digit code, claims once, requires Galaxy approval',async()=>{
    const {android,web,pair}=await paired(); expect(pair.code).toMatch(/^\d{12}$/);
    expect((await request('/api/pair/claim',{code:pair.code,name:'intruder'})).status).toBe(410);
    expect((await request('/api/status',undefined,web.token)).body.paired).toBe(true);
    expect(kv.data.has('token:'+android.token)).toBe(false);
  });
  it('rejects expired code independently of KV expiry',async()=>{
    const a=(await request('/api/android/register',{name:'G',installSecret:'x'.repeat(40)})).body;
    const p=(await request('/api/pair/create',{},a.token)).body;
    const key=[...kv.data.keys()].find(k=>k.startsWith('pair:'))!; const entry=JSON.parse(kv.data.get(key)!);entry.expires=Date.now()-1;kv.data.set(key,JSON.stringify(entry));
    expect((await request('/api/pair/claim',{code:p.code,name:'W'})).status).toBe(410);
  });
  it('forwards mock event without persisting any content',async()=>{
    const {android}=await paired(); const snapshot=new Map(kv.data);
    const e={id:'evt_'+crypto.randomUUID(),type:'notification',app:'카카오톡',title:'김철수',body:'야 어디야?',timestamp:Date.now()};
    expect((await request('/api/event',{event:e},android.token)).status).toBe(200);
    expect(transport.push).toHaveBeenCalledWith(env,expect.anything(),expect.objectContaining(e));expect(kv.data).toEqual(snapshot);
  });
  it('signs FCM commands and enforces role and ACK settings',async()=>{
    const {android,web}=await paired(); const c={commandId:'cmd_'+crypto.randomUUID(),eventId:'evt_'+crypto.randomUUID()};
    expect((await request('/api/command/open',c,web.token)).status).toBe(200);
    const d=transport.fcm.mock.calls[0] as unknown as [Env,string,Record<string,string>]; const b=d[2];
    expect(b.signature).toBe(sign(commandKey('secret',android.deviceId),[b.commandId,b.type,b.eventId,b.timestamp,b.nonce,b.autoDismiss].join('\n')));
    expect((await request('/api/command/ack',c,web.token)).body.skipped).toBe(true);
    expect((await request('/api/command/open',c,android.token)).status).toBe(403);
  });
  it('rejects stale timestamps, invalid input and SSRF subscription',async()=>{
    const {android,web}=await paired(); expect((await request('/api/ping',{timestamp:1},android.token)).status).toBe(400);
    expect((await request('/api/web/register',{subscription:{endpoint:'https://127.0.0.1/private',keys:{p256dh:'B'.repeat(87),auth:'A'.repeat(22)}}},web.token)).status).toBe(400);
    expect((await request('/api/ping',{extra:'x'.repeat(9000)},android.token)).status).toBe(413);
  });
  it('enforces rate limit without KV counter writes',async()=>{
    env.PAIR_LIMIT.limit=async()=>({success:false});expect((await request('/api/pair/claim',{code:'123456789012',name:'W'})).status).toBe(429);
  });
  it('handles Web Push expiry and FCM errors, revokes both credentials',async()=>{
    const {android,web}=await paired();transport.push.mockRejectedValueOnce(new DeliveryError('PUSH_EXPIRED'));
    expect((await request('/api/event',{event:{id:'evt_'+crypto.randomUUID(),type:'sms',body:'private',timestamp:Date.now()}},android.token)).body.error).toBe('PUSH_EXPIRED');
    transport.fcm.mockRejectedValueOnce(new DeliveryError('FCM_FAILED'));
    expect((await request('/api/command/open',{eventId:'evt_'+crypto.randomUUID(),commandId:'cmd_'+crypto.randomUUID()},web.token)).status).toBe(502);
    expect((await request('/api/unpair',{},web.token)).status).toBe(200);expect((await request('/api/ping',{},android.token)).status).toBe(401);
  });
});

describe('Native BLE companion cloud fallback',()=>{
  async function setup(){const android=(await request('/api/android/register',{name:'Galaxy',installSecret:'x'.repeat(40),fcmToken:'native-fcm-token'})).body;
    const native=(await request('/api/native/provision',{name:'iPhone'},android.token)).body;return{android,native};}
  it('isolates encrypted one-shot locations, roles, grant domains and expiry',async()=>{
    const {android,native}=await setup(),other=await setup();
    expect((await request('/api/native/locate/start',{},android.token)).status).toBe(403);
    const start=await request('/api/native/locate/start',{},native.token);expect(start.status).toBe(202);
    const grant={requestId:start.body.requestId,expires:start.body.expires,grant:start.body.grant};
    const data=(transport.fcm.mock.calls.at(-1)! as unknown as [Env,string,Record<string,string>])[2];
    expect(data.type).toBe('NATIVE_LOCATE');expect(JSON.parse(data.autoDismiss)).toEqual({expires:grant.expires,grant:grant.grant,deviceId:native.deviceId});
    expect(data.signature).toBe(sign(commandKey('secret',android.deviceId),[data.commandId,data.type,data.eventId,data.timestamp,data.nonce,data.autoDismiss].join('\n')));
    const packet={version:2,iv:'A'.repeat(16),ciphertext:'B'.repeat(160)};
    expect((await request('/api/native/locate/result',grant,native.token)).body.ready).toBe(false);
    expect((await request('/api/native/locate/upload',{...grant,packet},native.token)).status).toBe(403);
    expect((await request('/api/native/locate/upload',{...grant,packet},other.android.token)).status).toBe(403);
    expect((await request('/api/native/locate/upload',{...grant,packet:{...packet,latitude:37}},android.token)).status).toBe(400);
    expect((await request('/api/native/locate/upload',{...grant,latitude:37,packet},android.token)).status).toBe(400);
    expect((await request('/api/native/pull/result',grant,native.token)).status).toBe(403);
    expect((await request('/api/native/locate/upload',{...grant,packet},android.token)).status).toBe(200);
    const key='native:'+native.deviceId+':location:'+grant.requestId;
    expect(kv.ttl.get(key)).toBe(120);expect(JSON.parse(kv.data.get(key)!)).toEqual(packet);
    expect((await request('/api/native/locate/result',grant,other.native.token)).status).toBe(403);
    expect((await request('/api/native/locate/result',{...grant,grant:'X'.repeat(43)},native.token)).status).toBe(403);
    expect((await request('/api/native/locate/result',{...grant,expires:Date.now()-1},native.token)).status).toBe(410);
    expect((await request('/api/native/locate/result',grant,native.token)).body.packet).toEqual(packet);
    expect((await request('/api/native/locate/ack',grant,native.token)).status).toBe(200);expect(kv.data.has(key)).toBe(false);
    await request('/api/native/revoke',{},native.token);
    expect((await request('/api/native/locate/result',grant,native.token)).status).toBe(401);
    expect((await request('/api/native/locate/upload',{...grant,packet},android.token)).status).toBe(409);
  });
  it('issues a separate credential, rejects roles and isolates cloud packets',async()=>{
    const {android,native}=await setup(),other=await setup();
    expect((await request('/api/status',undefined,native.token)).body.paired).toBe(true);
    expect((await request('/api/native/provision',{name:'again'},android.token)).status).toBe(409);
    expect((await request('/api/native/pull/start',{},android.token)).status).toBe(403);
    const start=await request('/api/native/pull/start',{},native.token);expect(start.status).toBe(202);const grant={requestId:start.body.requestId,expires:start.body.expires,grant:start.body.grant};
    const data=(transport.fcm.mock.calls.at(-1)! as unknown as [Env,string,Record<string,string>])[2];expect(data.type).toBe('NATIVE_PULL');
    expect(data.signature).toBe(sign(commandKey('secret',android.deviceId),[data.commandId,data.type,start.body.requestId,data.timestamp,data.nonce,data.autoDismiss].join('\n')));
    expect((await request('/api/native/pull/result',grant,native.token)).body.ready).toBe(false);
    const packet={version:2,iv:'A'.repeat(16),ciphertext:'B'.repeat(40)};
    expect((await request('/api/native/pull/upload',{...grant,packet},other.android.token)).status).toBe(403);
    expect((await request('/api/native/pull/upload',{...grant,packet:{...packet,body:'leak'}},android.token)).status).toBe(400);
    expect((await request('/api/native/pull/upload',{...grant,packet},android.token)).status).toBe(200);
    expect((await request('/api/native/pull/result',grant,other.native.token)).status).toBe(403);
    expect((await request('/api/native/pull/result',grant,native.token)).body.packet).toEqual(packet);
    expect((await request('/api/native/pull/ack',grant,native.token)).status).toBe(200);
    expect([...kv.data.keys()].some(k=>k.includes(':packet:'))).toBe(false);
    expect((await request('/api/native/revoke',{},native.token)).body.revoked).toBe(true);
    expect((await request('/api/status',undefined,native.token)).status).toBe(401);
    expect((await request('/api/status',undefined,android.token)).status).toBe(200);
  });
  it('allows encrypted native commands only and expires transit requests',async()=>{
    const{android,native}=await setup();
    const reply={actionId:'a'.repeat(64),mode:'encrypted',version:1,keyId:'a'.repeat(32),iv:'A'.repeat(16),ciphertext:'B'.repeat(23)};
    const body={commandId:'cmd_'+crypto.randomUUID(),eventId:'evt_'+crypto.randomUUID(),type:'REPLY',reply};
    expect((await request('/api/native/command',body,native.token)).status).toBe(200);
    expect((await request('/api/native/command',{...body,reply:{actionId:'a'.repeat(64),mode:'plain',text:'must not store plaintext'}},native.token)).status).toBe(400);
    expect((await request('/api/native/command',body,android.token)).status).toBe(403);
    const r=(await request('/api/native/pull/start',{},native.token)).body,expires=Date.now()-1;
    const expired={requestId:r.requestId,expires,grant:sign('secret',['native-pull/v2',r.requestId,native.deviceId,android.deviceId,expires].join('\n'))};
    expect((await request('/api/native/pull/result',expired,native.token)).status).toBe(410);
    expect((await request('/api/native/pull/upload',{...expired,packet:{version:2,iv:'A'.repeat(16),ciphertext:'B'.repeat(40)}},android.token)).status).toBe(410);
    expect([...kv.data.values()].join(' ')).not.toContain('must not store plaintext');
  });
});



describe('Video-only camera request API',()=>{
 const nativePair=async()=>{const android=(await request('/api/android/register',{name:'Galaxy',installSecret:'x'.repeat(40),fcmToken:'fcm-registration-token'})).body;const native=(await request('/api/native/provision',{name:'iPhone'},android.token)).body;return {android,native};};
 it('authenticates each pair, signs the short launch request and scopes the session capabilities',async()=>{
  const {android,native}=await nativePair(),other=await nativePair();
  expect((await request('/api/native/camera/start',{},android.token)).status).toBe(403);
  const started=await request('/api/native/camera/start',{},native.token);expect(started.status).toBe(202);const viewer=started.body;
  const data=(transport.fcm.mock.calls.at(-1)! as unknown as [Env,string,Record<string,string>])[2];
  expect(data.type).toBe('NATIVE_CAMERA');expect(data.eventId).toBe(viewer.sessionId);expect(JSON.parse(data.autoDismiss).deviceId).toBe(native.deviceId);
  expect(data.signature).toBe(sign(commandKey('secret',android.deviceId),[data.commandId,data.type,data.eventId,data.timestamp,data.nonce,data.autoDismiss].join('\n')));
  expect((await request('/api/native/camera/start',{},native.token)).status).toBe(409);
  expect((await request('/api/native/camera/attach',{sessionId:viewer.sessionId},other.android.token)).status).toBe(410);
  const attached=await request('/api/native/camera/attach',{sessionId:viewer.sessionId},android.token);expect(attached.status).toBe(200);expect(attached.body.role).toBe('sender');
  expect((await request('/api/camera/session',{...viewer,role:'sender',op:'poll'})).status).toBe(403);
  expect((await request('/api/camera/session',{...viewer,op:'signal',packet:{seq:1,iv:'A'.repeat(16),ciphertext:'B'.repeat(32)}})).status).toBe(200);
  expect((await request('/api/camera/session',{...attached.body,op:'poll'})).body.messages).toHaveLength(1);
  expect((await request('/api/camera/session',{...viewer,op:'end'})).body.ended).toBe(true);
  expect((await request('/api/native/camera/attach',{sessionId:viewer.sessionId},android.token)).status).toBe(410);
 });
 it('cancels the session if FCM delivery fails and allows retrying with a fresh session',async()=>{
  const {native}=await nativePair();transport.fcm.mockRejectedValueOnce(new DeliveryError('FCM_FAILED',503));
  expect((await request('/api/native/camera/start',{},native.token)).status).toBe(503);
  expect((await request('/api/native/camera/start',{},native.token)).status).toBe(202);
 });
 it('revocation immediately invalidates an established session',async()=>{
  const {android,native}=await nativePair(),viewer=(await request('/api/native/camera/start',{},native.token)).body;
  await request('/api/native/camera/attach',{sessionId:viewer.sessionId},android.token);
  expect((await request('/api/native/revoke',{},native.token)).status).toBe(200);
  expect((await request('/api/camera/session',{...viewer,op:'poll'})).body.ended).toBe(true);
 });
});
