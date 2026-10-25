// Dedicated emulator-5580 + synthetic local fixture only. Never sends messages to real contacts.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile),adb=process.env.ADB_PATH||'adb';
const run=async args=>(await exec(adb,['-s','emulator-5580',...args],{timeout:160000})).stdout;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const backend=process.env.RELAY_BACKEND_URL;
if(!backend)throw new Error('Set RELAY_BACKEND_URL to your own test server.');
async function instrument(method,args=[]){const text=await run(['shell','am','instrument','-w','-r',...args,'-e','class','kr.infosphere.galaxyrelay.NativeCloudSetupTest#'+method,'kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner']);if(!/OK \(1 test\)/.test(text)){console.log(text.replace(/Bearer \S+/g,'Bearer [redacted]'));throw new Error('Native test preparation failed');}}
async function call(path,body={},token=client.token){const r=await fetch(backend+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({timestamp:Date.now(),nonce:crypto.randomUUID(),...body})});if(!r.ok)throw new Error('Native API '+path+' status '+r.status);return r.json();}
let client;
try{
 const secrets=JSON.parse(fs.readFileSync(new URL('../.secrets/cloudflare.json',import.meta.url),'utf8'));
 const config=new URL('../.secrets/relay-native-config.json',import.meta.url);fs.writeFileSync(config,JSON.stringify({backend,installSecret:secrets.INSTALL_SECRET}));
 await run(['push',config.pathname.replace(/^\/(\w:)/,'$1'),'/data/local/tmp/relay-native-config.json']);
 await run(['shell','pm','clear','kr.infosphere.relayfixture']);await run(['shell','pm','grant','kr.infosphere.relayfixture','android.permission.POST_NOTIFICATIONS']);
 await instrument('prepare');client=JSON.parse(await run(['exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/native-client.json']));
 console.log('PASS: isolated Android registered and native credential provisioned.');
 await run(['shell','am','start','-W','-n','kr.infosphere.galaxyrelay/.MainActivity']);
 await run(['shell','cmd','notification','disallow_listener','kr.infosphere.galaxyrelay/kr.infosphere.galaxyrelay.RelayNotificationListener']);await run(['shell','cmd','notification','allow_listener','kr.infosphere.galaxyrelay/kr.infosphere.galaxyrelay.RelayNotificationListener']);await sleep(5000);
 const tag='native-fixture-'+Date.now();await run(['shell','am','broadcast','-n','kr.infosphere.relayfixture/.FixtureReceiver','--es','tag',tag,'--ez','reply','true']);await sleep(3000);
 async function pull(){
  const req=await call('/api/native/pull/start'),grant={requestId:req.requestId,expires:req.expires,grant:req.grant};
  const end=Date.now()+100000;while(Date.now()<end){await sleep(2000);const result=await call('/api/native/pull/result',grant);if(result.ready){
   const key=Buffer.from(client.linkKey,'base64url'),cipher=Buffer.from(result.packet.ciphertext,'base64url'),d=crypto.createDecipheriv('aes-256-gcm',key,Buffer.from(result.packet.iv,'base64url'));d.setAAD(Buffer.from(['GalaxyRelay/native-cloud/v2',req.requestId,client.deviceId].join('\n')));d.setAuthTag(cipher.subarray(-16));const value=JSON.parse(Buffer.concat([d.update(cipher.subarray(0,-16)),d.final()]).toString('utf8'));assert.equal(value.requestId,req.requestId);await call('/api/native/pull/ack',grant);return value.events;
  }}throw new Error('Actual FCM cloud response timed out');
 }
 const events=await pull(),event=events.find(e=>e.notificationKey?.includes(tag));assert.ok(event?.reply);console.log('PASS: NotificationListener → local queue → actual FCM pull → encrypted Worker packet → native-compatible decrypt.');
 await run(['shell','input','keyevent','KEYCODE_WAKEUP']);await run(['shell','input','keyevent','82']);await run(['shell','wm','dismiss-keyguard']);
 const commandId='cmd_'+crypto.randomUUID(),timestamp=Date.now(),nonce=crypto.randomUUID(),key=Buffer.from(client.linkKey,'base64url'),keyId=crypto.createHash('sha256').update(key).digest('hex').slice(0,32),iv=crypto.randomBytes(12),c=crypto.createCipheriv('aes-256-gcm',key,iv);
 const text='Native relay test 한글\n🙂';c.setAAD(Buffer.from(['GalaxyRelay/reply/v1',commandId,event.id,timestamp,nonce,event.reply.actionId,keyId].join('\n')));const ciphertext=Buffer.concat([c.update(text),c.final(),c.getAuthTag()]).toString('base64url');
 let before=0;try{before=JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json'])).count;}catch{}
 const body={commandId,type:'REPLY',eventId:event.id,timestamp,nonce,reply:{actionId:event.reply.actionId,mode:'encrypted',version:1,keyId,iv:iv.toString('base64url'),ciphertext}};
 await call('/api/native/command',body);const until=Date.now()+65000;let success=false;while(Date.now()<until){await sleep(1000);try{const result=JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json']));if(result.text===text&&result.count===before+1){success=true;break;}}catch{}}
 assert.ok(success,'Actual native encrypted RemoteInput reply not confirmed');console.log('PASS: native encrypted command → Worker → real FCM → original RemoteInput fixture.');
 await call('/api/native/command',body);await sleep(6000);assert.equal(JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json'])).count,before+1);console.log('PASS: repeated native command does not execute twice.');
 const receipts=await pull();assert.ok(receipts.some(e=>e.commandId===commandId&&e.result==='REPLY_SENT'));console.log('PASS: execution receipt returns through the encrypted native cloud channel.');
 const revoked=await call('/api/native/revoke');assert.ok(revoked.revoked);const revokeEnd=Date.now()+65000;let cleared=false;while(Date.now()<revokeEnd){await sleep(1000);const prefs=await run(['exec-out','run-as','kr.infosphere.galaxyrelay','cat','shared_prefs/relay.xml']);if(!prefs.includes('name="nativeDeviceId"')){cleared=true;break;}}assert.ok(cleared,'Native revocation was not applied to Android');console.log('PASS: native credential revoke → signed real FCM → Android BLE credentials cleared.');
}finally{
 if(client){try{await call('/api/unpair',{},client.androidToken);console.log('Isolated cloud devices revoked.');}catch{console.log('Test device cleanup requires retry.');}}
 await run(['shell','rm','-f','/data/local/tmp/relay-native-config.json']).catch(()=>{});
}

