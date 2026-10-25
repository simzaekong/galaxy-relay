// Isolated emulator + test fixture only. No messages are sent to real contacts.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {chromium} from 'playwright';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile),adb=process.env.ADB_PATH||'adb';
const link=JSON.parse(fs.readFileSync(new URL('../../.secrets/test-link.json',import.meta.url),'utf8'));
const run=async args=>(await exec(adb,['-s','emulator-5580',...args],{timeout:160000})).stdout;
async function instrument(name,args=[]){const output=await run(['shell','am','instrument','-w','-r',...args,'-e','class',name,'kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner']);assert.match(output,/OK \(\d+ tests?\)/,'Isolated instrumentation failed');}
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function poll(fn,description,timeout=90000){const end=Date.now()+timeout;while(Date.now()<end){try{if(await fn())return;}catch{}await delay(700);}throw new Error(description+' timed out');}
const profileArg=process.argv.find(a=>a.startsWith('--profile='))?.slice(10);if(profileArg&&!/^reply-chrome-[A-Za-z0-9]+$/.test(profileArg))throw new Error('Invalid isolated test profile');
const profile=profileArg?new URL('../../.tools/'+profileArg+'/',import.meta.url).pathname.replace(/^\/(\w:)/,'$1'):fs.mkdtempSync(new URL('../../.tools/reply-chrome-',import.meta.url).pathname.replace(/^\/(\w:)/,'$1'));
const context=await chromium.launchPersistentContext(profile,{channel:'chrome',headless:true,ignoreDefaultArgs:['--disable-background-networking'],permissions:['notifications'],viewport:{width:390,height:844}});
let cleaned=false;
try {
  const page=await context.newPage();await page.goto(link.backend);await page.waitForFunction(()=>!!globalThis.RelayDB);
  await page.evaluate(async l=>{await RelayDB.setMeta('credential',{token:l.webToken,deviceId:l.webId});await RelayDB.setMeta('e2ee',null);},link);await page.reload();
  await poll(async()=>page.evaluate(async token=>{const r=await fetch('/api/status',{headers:{Authorization:'Bearer '+token},cache:'no-store'});return r.ok&&(await r.json()).paired;},link.webToken),'Pair propagation to browser edge');
  await page.evaluate(async l=>{await RelayDB.setMeta('credential',{token:l.webToken,deviceId:l.webId});},link);
  await page.waitForFunction(()=>!document.querySelector('#enable-push').disabled);await page.locator('#settings-open').click();await page.locator('#enable-push').click();
  try{await poll(async()=> (await page.locator('#push-state').textContent()).includes('완료'),'Actual Chrome Push registration');}catch(error){console.log('Push registration UI status: '+await page.locator('#notice').textContent());throw error;}await page.locator('#settings-close').click();
  const fingerprint=await page.evaluate(async()=>{const endpoint=(await(await navigator.serviceWorker.ready).pushManager.getSubscription()).endpoint;return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(endpoint))),b=>b.toString(16).padStart(2,'0')).join('');});
  console.log('PASS: actual Chrome Push subscription registered.');
  await instrument('kr.infosphere.galaxyrelay.RelayProbeTest#awaitLatestPushRegistration',['-e','expectedPushFingerprint',fingerprint]);
  console.log('PASS: Push subscription propagated to Android edge.');
  for(const encrypted of [false,true]) {
    await instrument('kr.infosphere.galaxyrelay.ReplyIntegrationSetupTest#prepareIsolatedFixture',['-e','replyEncrypted',String(encrypted)]);
    const key=JSON.parse(await run(['exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/reply-key.json'])).key;
    await page.evaluate(async({encrypted,key})=>{await RelayDB.clearHistory();await RelayDB.setMeta('e2ee',encrypted?await EventCrypto.importKey(key):null);},{encrypted,key});
    const tag='relay-reply-'+Date.now();await instrument('kr.infosphere.galaxyrelay.ReplyIntegrationSetupTest#postIsolatedNotification',['-e','replyTag',tag]);
    console.log('Waiting for '+(encrypted?'encrypted':'plain')+' source notification push.');
    let event;await poll(async()=>{event=await page.evaluate(async tag=>(await RelayDB.all()).find(e=>e.notificationKey?.includes(tag)&&e.reply),tag);return !!event;},'Actual notification → Worker → Web Push → IndexedDB');
    console.log('PASS: actual incoming notification stored with original reply capability.');
    const replies=[];page.on('request',req=>{if(req.url().endsWith('/api/command/reply'))replies.push(req.postDataJSON());});
    await page.goto(link.backend+'/?event='+event.id);await page.waitForFunction(()=>document.querySelector('#detail').open&&!document.querySelector('#reply-form').hidden);
    await run(['shell','input','keyevent','KEYCODE_WAKEUP']);await run(['shell','input','keyevent','82']);
    const text='Relay reply test 한글\n🙂 '+(encrypted?'encrypted':'plain');let before=0;try{before=JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json'])).count;}catch{}
    await page.locator('#reply-text').fill(text);await page.locator('#reply-send').click();
    let lastStatus='';
    await poll(async()=>{const status=await page.locator('#command-status').textContent();if(status!==lastStatus){lastStatus=status;console.log('Reply UI status: '+status);}const r=JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json']));return r.text===text&&r.count===before+1;},'Actual PWA → Worker → FCM → RemoteInput reply');
    await poll(async()=> (await page.locator('#command-status').textContent()).includes('원본 앱에 답장을 전달'),'Quiet execution receipt');
    const request=replies.at(-1);assert.ok(request);assert.equal(request.reply.mode,encrypted?'encrypted':'plain');if(encrypted)assert.ok(!JSON.stringify(request).includes(text));
    // Resend the same command ID and nonce through the actual Worker/FCM channel.
    const response=await fetch(link.backend+'/api/command/reply',{method:'POST',headers:{Authorization:'Bearer '+link.webToken,'Content-Type':'application/json'},body:JSON.stringify(request)});assert.equal(response.status,200);await delay(6000);
    assert.equal(JSON.parse(await run(['exec-out','run-as','kr.infosphere.relayfixture','cat','files/reply-result.json'])).count,before+1,'Duplicate command executed twice');
    assert.equal(await page.evaluate(async id=>!!await RelayDB.get(id),event.id),true);
    fs.mkdirSync(new URL('../../artifacts/',import.meta.url),{recursive:true});await page.screenshot({path:new URL('../../artifacts/pwa-reply-preview.png',import.meta.url).pathname.replace(/^\/(\w:)/,'$1')});
    console.log('PASS: actual '+(encrypted?'AES-GCM ':'')+'notification push, PWA reply, FCM RemoteInput delivery, quiet receipt, retained history and duplicate suppression.');
    await page.locator('#detail-close').click();
  }
}finally{
  try{const response=await fetch(link.backend+'/api/unpair',{method:'POST',headers:{Authorization:'Bearer '+link.webToken,'Content-Type':'application/json'},body:JSON.stringify({timestamp:Date.now(),nonce:crypto.randomUUID()})});cleaned=response.ok;}catch{}
  await context.close();console.log('Isolated test pair revoked: '+cleaned);
}
