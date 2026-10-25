import fs from 'node:fs';
import {chromium} from 'playwright';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const link=JSON.parse(fs.readFileSync(new URL('../../.secrets/test-link.json',import.meta.url),'utf8'));
const adb=process.env.ADB_PATH||'adb';
let encryptionKey;
async function waitStored(page,predicate,arg,timeout=60000){const until=Date.now()+timeout;do{if(await page.evaluate(predicate,arg))return;await new Promise(resolve=>setTimeout(resolve,500));}while(Date.now()<until);throw new Error('Expected stored state did not arrive within '+timeout+' ms');}
if(process.argv.includes('--encrypted')){
  const result=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','relayE2EE','true','-e','class','kr.infosphere.galaxyrelay.CryptoInteropTest','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:120000});
  if(!result.stdout.includes('OK (1 test)'))throw new Error('Android encryption test failed');
  encryptionKey=JSON.parse((await promisify(execFile)(adb,['-s','emulator-5580','exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/e2ee-test-vector.json'])).stdout).key;
}
fs.mkdirSync(new URL('../../.tools/',import.meta.url),{recursive:true});
const profile=fs.mkdtempSync(new URL('../../.tools/live-chrome-',import.meta.url).pathname.replace(/^\/(\w:)/,'$1'));
const context=await chromium.launchPersistentContext(profile,{channel:'chrome',headless:true,ignoreDefaultArgs:['--disable-background-networking'],permissions:['notifications'],viewport:{width:390,height:844}});
try {
  const page=await context.newPage();
  await page.goto(link.backend);await page.waitForFunction(()=>!!globalThis.RelayDB&&!!globalThis.EventCrypto);
  await page.evaluate(async({webToken,webId})=>{await RelayDB.setMeta('credential',{token:webToken,deviceId:webId});},link);await page.reload();
  if(encryptionKey)await page.evaluate(async key=>{await RelayDB.setMeta('e2ee',await EventCrypto.importKey(key));},encryptionKey);
  await page.waitForFunction(()=>!document.querySelector('#enable-push').disabled);await page.locator('#settings-open').click();await page.locator('#enable-push').click();
  await page.waitForFunction(()=>document.querySelector('#push-state').textContent.includes('완료')||!document.querySelector('#notice').hidden,{timeout:30000});
  const state=await page.locator('#push-state').textContent();
  if(!state.includes('완료'))console.log(await page.evaluate(async()=>{const s=(await(await navigator.serviceWorker.ready).pushManager.getSubscription())?.toJSON();return s?{host:new URL(s.endpoint).hostname,keyLengths:Object.fromEntries(Object.entries(s.keys).map(([k,v])=>[k,v.length])),padded:Object.values(s.keys).some(v=>v.includes('='))}:'No subscription';}));
  if(!state.includes('완료'))throw new Error('Desktop push subscription was not accepted: '+await page.locator('#notice').textContent());
  await page.locator('#settings-close').click();
  const fingerprint=await page.evaluate(async()=>{const endpoint=(await(await navigator.serviceWorker.ready).pushManager.getSubscription()).endpoint;return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(endpoint))),b=>b.toString(16).padStart(2,'0')).join('');});
  const propagated=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','expectedPushFingerprint',fingerprint,'-e','class','kr.infosphere.galaxyrelay.RelayProbeTest#awaitLatestPushRegistration','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:150000});if(!propagated.stdout.includes('OK (1 test)'))throw new Error('Latest push registration was not visible to Android');
  console.log('PASS: latest subscription propagated to the Android relay edge.');
  if(process.argv.includes('--probe')){const result=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','class','kr.infosphere.galaxyrelay.RelayProbeTest#deployedPushRequest','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:60000});if(!result.stdout.includes('OK (1 test)'))throw new Error('Probe failed: '+result.stdout);try{await waitStored(page,async()=>(await RelayDB.all()).some(e=>e.title==='RelayProbe'),null,30000);}catch(error){console.log('Probe delivery metadata:',await page.evaluate(async()=>{const notices=await(await navigator.serviceWorker.ready).getNotifications();return{records:(await RelayDB.all()).length,banners:notices.length,storageFailure:notices.some(n=>n.body.includes('암호화 키'))};}));throw error;}console.log('PASS: direct encrypted cloud transport probe.');}
  if(process.argv.includes('--integration')){
    await page.evaluate(()=>RelayDB.clearHistory());
    const result=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','relayOffline',String(process.argv.includes('--offline')),'-e','class','kr.infosphere.galaxyrelay.RelayIntegrationTest','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:360000});
    if(!result.stdout.includes('OK (1 test)'))throw new Error('Android integration failed: '+result.stdout);
    await waitStored(page,async()=>{const events=await RelayDB.all();return events.some(e=>e.title==='RelayIntegration'&&e.androidState==='removed');},null,60000);
    console.log('PASS: Android notification → '+(encryptionKey?'AES-GCM ciphertext → ':'')+'HTTPS Worker → real Web Push → browser IndexedDB; FCM OPEN direct/fallback, DISMISS, ACK and replay protection'+(process.argv.includes('--offline')?'; offline Room queue recovery.':'.'));
  }
  if(process.argv.includes('--telephony')){
    await page.evaluate(()=>RelayDB.clearHistory());
    const armed=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','class','kr.infosphere.galaxyrelay.TelephonyRelayTest#armNativeScenario','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:60000});if(!armed.stdout.includes('OK (1 test)'))throw new Error('Native telephony setup failed');
    const telephonyStarted=Date.now();let ready=false;for(let i=0;i<40;i++){try{ready=(await promisify(execFile)(adb,['-s','emulator-5580','exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/telephony-test-ready'])).stdout&&Number((await promisify(execFile)(adb,['-s','emulator-5580','exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/telephony-test-ready'])).stdout)>0;}catch{}if(ready)break;await new Promise(resolve=>setTimeout(resolve,500));}if(!ready)throw new Error('Telephony test was not ready');
    await promisify(execFile)(adb,['-s','emulator-5580','emu','sms','send','01012345678','RelaySmsIntegration']);
    await promisify(execFile)(adb,['-s','emulator-5580','emu','gsm','call','5551234']);
    let ringing=false;for(let i=0;i<90;i++){const registry=(await promisify(execFile)(adb,['-s','emulator-5580','shell','dumpsys','telephony.registry'])).stdout;if(/^\s*mCallState=1\s*$/m.test(registry)){ringing=true;break;}await new Promise(resolve=>setTimeout(resolve,500));}if(!ringing)throw new Error('Emulator modem did not enter the ringing state');
    const nativeStart=Number((await promisify(execFile)(adb,['-s','emulator-5580','exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/telephony-test-ready'])).stdout);
    await waitStored(page,async start=>(await RelayDB.all()).some(e=>e.type==='call'&&e.state==='ringing'&&e.timestamp>=start&&e.number?.replace(/[^0-9]/g,'')==='5551234'),nativeStart,60000);
    const currentCall=await page.evaluate(async start=>(await RelayDB.all()).find(e=>e.type==='call'&&e.timestamp>=start),nativeStart);
    await waitStored(page,async()=>(await(await navigator.serviceWorker.ready).getNotifications()).some(n=>n.data?.eventType==='call'&&n.title.replace(/[^0-9]/g,'').includes('5551234')),null,30000);
    await promisify(execFile)(adb,['-s','emulator-5580','emu','gsm','cancel','5551234']);
    
    await waitStored(page,async({start,callId})=>{const records=await RelayDB.all();return records.some(e=>e.type==='sms'&&e.body==='RelaySmsIntegration'&&e.timestamp>=start)&&records.some(e=>e.type==='call'&&(e.callId||e.id)===callId&&e.state==='ended');},{start:nativeStart,callId:currentCall.callId||currentCall.id},120000);
    await waitStored(page,async()=>!(await(await navigator.serviceWorker.ready).getNotifications()).some(n=>n.data?.eventType==='call'),null,120000);
    const verified=await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','class','kr.infosphere.galaxyrelay.TelephonyRelayTest#verifyNativeScenario','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:60000});if(!verified.stdout.includes('OK (1 test)'))throw new Error('Native telephony verification failed: '+verified.stdout);
    console.log('PASS: native emulator SMS, caller number on real ringing Web Push, quiet call end receipt and automatic banner close.');
  }
  await page.screenshot({path:new URL('../../.tools/pwa-live.png',import.meta.url).pathname.replace(/^\/(\w:)/,'$1')});
  console.log('PASS: deployed PWA, real browser subscription and backend registration.');
}finally{if(process.argv.includes('--telephony')){await promisify(execFile)(adb,['-s','emulator-5580','emu','gsm','cancel','5551234']).catch(()=>{});await promisify(execFile)(adb,['-s','emulator-5580','shell','am','instrument','-w','-r','-e','class','kr.infosphere.galaxyrelay.TelephonyRelayTest#restoreNativeSettings','kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner'],{timeout:60000}).catch(()=>{});}if(process.argv.includes('--offline')){for(const service of ['wifi','data'])await promisify(execFile)(adb,['-s','emulator-5580','shell','svc',service,'enable']).catch(()=>{});}await context.close();}
