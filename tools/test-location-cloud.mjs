// Live Worker smoke test using newly-created synthetic devices only. No real FCM or location requests.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

if(!process.argv.includes('--production'))throw new Error('Pass --production to test the configured live Worker.');
const backend=process.env.RELAY_BACKEND_URL;
if(!backend)throw new Error('Set RELAY_BACKEND_URL to your own test server.');
const secrets=JSON.parse(fs.readFileSync(new URL('../.secrets/cloudflare.json',import.meta.url),'utf8'));
const recovery=new URL('../.secrets/remote-location-smoke.json',import.meta.url);
const clients=[];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function call(path,body={},token){
 const response=await fetch(backend+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify({timestamp:Date.now(),nonce:crypto.randomUUID(),...body}),signal:AbortSignal.timeout(15000)});
 return {status:response.status,body:await response.json()};
}
async function propagation(path,body,token,status=200){
 let result;for(let i=0;i<9;i++){result=await call(path,body,token);if(result.status===status)return result;if(![401,409].includes(result.status))break;await sleep(10000);}
 throw new Error(path+' returned '+result?.status);
}
async function setup(){
 const registered=await call('/api/android/register',{name:'Remote location smoke fixture',installSecret:secrets.INSTALL_SECRET});assert.equal(registered.status,201);
 const android=registered.body;clients.push(android);fs.writeFileSync(recovery,JSON.stringify(clients));
 const native=(await propagation('/api/native/provision',{name:'Synthetic iPhone fixture'},android.token,201)).body;
 await propagation('/api/status',{},native.token);return {android,native};
}
try{
 const health=await fetch(backend+'/api/status',{signal:AbortSignal.timeout(15000)});assert.equal(health.status,200);assert.equal((await health.json()).ok,true);
 console.log('PASS: existing public health endpoint.');
 assert.equal((await call('/api/native/locate/start')).status,401);
 const first=await setup(),other=await setup();
 // No FCM token is registered, so start exercises routing without messaging any device.
 const start=await call('/api/native/locate/start',{},first.native.token);assert.equal(start.status,409);assert.equal(start.body.error,'FCM_NOT_REGISTERED');
 console.log('PASS: deployed locate route, authenticated pairing and FCM precondition.');
 const requestId='evt_'+crypto.randomUUID(),expires=Date.now()+120000;
 const grant=crypto.createHmac('sha256',Buffer.from(secrets.COMMAND_SECRET,'base64url')).update(['native-locate/v1',requestId,first.native.deviceId,first.android.deviceId,expires].join('\n')).digest('base64url');
 const request={requestId,expires,grant},key=crypto.randomBytes(32),iv=crypto.randomBytes(12),aad='GalaxyRelay/native-location/v1\n'+requestId+'\n'+first.native.deviceId;
 const value={requestId,timestamp:Date.now(),status:'OK',latitude:0,longitude:0,accuracy:100,measuredAt:Date.now()};
 const cipher=crypto.createCipheriv('aes-256-gcm',key,iv);cipher.setAAD(Buffer.from(aad));
 const packet={version:2,iv:iv.toString('base64url'),ciphertext:Buffer.concat([cipher.update(JSON.stringify(value)),cipher.final(),cipher.getAuthTag()]).toString('base64url')};
 assert.equal((await call('/api/native/locate/upload',{...request,packet},first.native.token)).status,403);
 assert.equal((await call('/api/native/locate/upload',{...request,packet},other.android.token)).status,403);
 assert.equal((await call('/api/native/locate/upload',{...request,packet,latitude:37},first.android.token)).status,400);
 assert.equal((await call('/api/native/pull/result',request,first.native.token)).status,403);
 assert.equal((await call('/api/native/locate/upload',{...request,packet},first.android.token)).status,200);
 console.log('PASS: role, cross-pair, plaintext and wrong protocol rejection; encrypted upload accepted.');
 let result;for(let i=0;i<9;i++){result=await call('/api/native/locate/result',request,first.native.token);assert.equal(result.status,200);if(result.body.ready)break;await sleep(10000);}
 assert.equal(result.body.ready,true);assert.deepEqual(result.body.packet,packet);
 const encrypted=Buffer.from(packet.ciphertext,'base64url'),decipher=crypto.createDecipheriv('aes-256-gcm',key,iv);decipher.setAAD(Buffer.from(aad));decipher.setAuthTag(encrypted.subarray(-16));
 assert.deepEqual(JSON.parse(Buffer.concat([decipher.update(encrypted.subarray(0,-16)),decipher.final()]).toString()),value);
 assert.equal((await call('/api/native/locate/result',request,other.native.token)).status,403);
 assert.equal((await call('/api/native/locate/result',{...request,grant:'X'.repeat(43)},first.native.token)).status,403);
 assert.equal((await call('/api/native/locate/result',{...request,expires:Date.now()-1},first.native.token)).status,410);
 assert.equal((await call('/api/native/locate/ack',request,first.native.token)).status,200);
 console.log('PASS: encrypted live round trip, client decrypt, tamper/expiry rejection and ACK.');
}finally{
 let cleaned=true;
 for(const c of clients){try{const r=await call('/api/unpair',{},c.token);if(r.status!==200&&r.status!==401)cleaned=false;}catch{cleaned=false;}}
 if(cleaned){if(fs.existsSync(recovery))fs.unlinkSync(recovery);console.log('Synthetic test device credentials revoked.');}
 else console.log('Synthetic fixture cleanup requires retry; ignored recovery file retained.');
}
