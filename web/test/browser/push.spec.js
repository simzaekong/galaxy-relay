import {test,expect,chromium} from '@playwright/test';
import fs from 'node:fs';
test.use({permissions:['notifications']});
test('actual service worker push handler saves payload then displays notification',async()=>{
  fs.mkdirSync('test-results',{recursive:true});const profile=fs.mkdtempSync('test-results/push-profile-');const context=await chromium.launchPersistentContext(profile,{channel:'chromium',headless:true,permissions:['notifications']});const page=await context.newPage();try{
  const client=await context.newCDPSession(page);const registrations=[];client.on('ServiceWorker.workerRegistrationUpdated',event=>registrations.push(...event.registrations));await client.send('ServiceWorker.enable');await page.goto('/');await page.evaluate(async()=>{await navigator.serviceWorker.ready;});await expect.poll(()=>registrations.length).toBeGreaterThan(0);
  const registration=registrations.filter(r=>r.scopeURL==='http://127.0.0.1:8787/'&&!r.isDeleted).at(-1);const event={id:'evt_'+crypto.randomUUID(),type:'notification',app:'카카오톡',title:'김철수',body:'야 어디야?',notificationKey:'test-key',timestamp:Date.now()};
  await client.send('ServiceWorker.deliverPushMessage',{origin:'http://127.0.0.1:8787',registrationId:registration.registrationId,data:JSON.stringify(event)});await expect(page.locator('.notification')).toHaveCount(1);
  await expect.poll(()=>page.evaluate(async()=>(await(await navigator.serviceWorker.ready).getNotifications()).length)).toBe(1);
  const displayed=await page.evaluate(async()=>{const registration=await navigator.serviceWorker.ready;return(await registration.getNotifications()).map(n=>({title:n.title,body:n.body,data:n.data}));});expect(displayed[0].title).toBe('김철수 · 카카오톡');expect(displayed[0].body).toContain('야 어디야?');expect(displayed[0].data.eventId).toBe(event.id);
  await client.send('ServiceWorker.deliverPushMessage',{origin:'http://127.0.0.1:8787',registrationId:registration.registrationId,data:JSON.stringify({id:'evt_'+crypto.randomUUID(),type:'removed',targetEventId:event.id,timestamp:Date.now()})});await expect(page.locator('.notification')).toHaveCount(0);
  }finally{await context.close();}
});
test('retains 5000 notifications and evicts the oldest',async({page})=>{await page.goto('/');await page.waitForFunction(()=>!!globalThis.RelayDB);const result=await page.evaluate(async()=>{const db=await RelayDB.open();let oldest;await new Promise((resolve,reject)=>{const tx=db.transaction(['notifications'],'readwrite');for(let i=0;i<5000;i++){const id='evt_'+crypto.randomUUID();if(i===0)oldest=id;tx.objectStore('notifications').put({id,type:'notification',timestamp:Date.now()-500000+i,read:false,androidState:'active'});}tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:'sms',timestamp:Date.now()});return{count:(await RelayDB.all()).length,oldest:await RelayDB.get(oldest)};});expect(result.count).toBe(5000);expect(result.oldest).toBeUndefined();});

test('real ringing banner shows caller and closes quietly after answer; ended still synchronizes',async()=>{
  fs.mkdirSync('test-results',{recursive:true});const profile=fs.mkdtempSync('test-results/call-profile-');
  const context=await chromium.launchPersistentContext(profile,{channel:'chromium',headless:true,permissions:['notifications']});
  try{
    const page=await context.newPage(),client=await context.newCDPSession(page),registrations=[];
    client.on('ServiceWorker.workerRegistrationUpdated',e=>registrations.push(...e.registrations));await client.send('ServiceWorker.enable');
    await page.goto('/');await page.evaluate(()=>navigator.serviceWorker.ready);await expect.poll(()=>registrations.length).toBeGreaterThan(0);
    const reg=registrations.filter(r=>r.scopeURL==='http://127.0.0.1:8787/'&&!r.isDeleted).at(-1);
    const callId='evt_'+crypto.randomUUID(),timestamp=Date.now();
    await client.send('ServiceWorker.deliverPushMessage',{origin:'http://127.0.0.1:8787',registrationId:reg.registrationId,data:JSON.stringify({id:callId,callId,type:'call',state:'ringing',contact:'김철수',number:'01012345678',timestamp})});
    await expect.poll(()=>page.evaluate(async()=> (await(await navigator.serviceWorker.ready).getNotifications()).map(n=>n.title))).toEqual(['김철수 · 전화 수신']);
    let state='offhook';await page.route('**/api/sync',route=>route.fulfill({json:{states:[{id:'evt_'+crypto.randomUUID(),type:'call',callId,state,timestamp:Date.now()+100}]}}));
    await page.evaluate(async()=>{await RelayDB.setMeta('credential',{token:'t'.repeat(43)});await RelaySync.pull(await navigator.serviceWorker.ready);});
    expect(await page.evaluate(async()=> (await(await navigator.serviceWorker.ready).getNotifications()).length)).toBe(0);
    expect(await page.evaluate(async id=>(await RelayDB.get(id)).state,callId)).toBe('offhook');
    state='ended';await page.evaluate(async()=>RelaySync.pull(await navigator.serviceWorker.ready));
    expect(await page.evaluate(async id=>(await RelayDB.get(id)).state,callId)).toBe('ended');
    expect(await page.evaluate(async()=> (await RelayDB.all()).length)).toBe(1);
    expect(await page.evaluate(async()=> (await(await navigator.serviceWorker.ready).getNotifications()).length)).toBe(0);
  }finally{await context.close();}
});
