import {test,expect} from '@playwright/test';
async function activate(page,selector){await page.locator(selector).click();}
async function setup(page,{supported=true,encrypted=false,legacy=false,fail=false}={}){
  const sent=[];let receipt=false;
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname,body=route.request().postDataJSON()||{};
    if(path.endsWith('/reply')){sent.push(body);if(fail)return route.abort('failed');}
    const states=path==='/api/sync'&&receipt?sent.filter(c=>body.commandIds?.includes(c.commandId)).map(c=>({id:'evt_'+crypto.randomUUID(),type:'command_result',commandId:c.commandId,targetEventId:c.eventId,result:'REPLY_SENT',timestamp:Date.now()})):[];
    await route.fulfill({json:path==='/api/status'?{paired:true}:path==='/api/config'?{vapidPublicKey:''}:path==='/api/sync'?{states}:{accepted:true,commandId:body.commandId}});
  });
  await page.goto('/');await page.evaluate(()=>navigator.serviceWorker.ready);await page.waitForFunction(()=>!!globalThis.RelayDB&&!!navigator.serviceWorker.controller);
  await page.evaluate(async({supported,encrypted,legacy})=>{
    await RelayDB.setMeta('credential',{token:'t'.repeat(43),deviceId:crypto.randomUUID()});
    if(encrypted){const raw=new Uint8Array(32).fill(17),material=await EventCrypto.importKey(btoa(String.fromCharCode(...raw)).replace(/=+$/,''));if(legacy)material.key=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['decrypt']);await RelayDB.setMeta('e2ee',material);}
    await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:'notification',app:'카카오톡',title:'김철수',body:'어디야?',notificationKey:'reply-key',timestamp:Date.now(),...(supported?{reply:{actionId:'a'.repeat(64),label:'답장'}}:{})});
  },{supported,encrypted,legacy});await page.reload();await activate(page,'.notification');await expect(page.locator('#detail')).toBeVisible();
  return{requests:async()=>sent,confirm:async()=>{receipt=true;}};
}
test('plain reply submits once, confirms execution and retains original history',async({page})=>{
  const {requests,confirm}=await setup(page);await expect(page.locator('#reply-form')).toBeVisible();await expect(page.locator('#reply-send')).toBeDisabled();
  await page.locator('#reply-text').fill('곧 도착해요!\n🙂');await activate(page,'#reply-send');await expect.poll(async()=>(await requests()).length).toBe(1);expect((await requests())[0].reply).toEqual({actionId:'a'.repeat(64),mode:'plain',text:'곧 도착해요!\n🙂'});
  await expect(page.locator('#reply-send')).toBeDisabled();await page.evaluate(()=>document.querySelector('#reply-form').dispatchEvent(new Event('submit',{cancelable:true,bubbles:true})));expect(await requests()).toHaveLength(1);
  await confirm();await page.evaluate(()=>document.querySelector('#refresh').click());await expect(page.locator('#command-status')).toContainText('원본 앱에 답장을 전달');await expect(page.locator('#reply-text')).toHaveValue('');expect(await page.evaluate(async()=>(await RelayDB.all()).length)).toBe(1);
});
test('E2EE reply carries ciphertext and decrypts with the bound metadata',async({page})=>{
  const{requests}=await setup(page,{encrypted:true});await page.locator('#reply-text').fill('비공개 답장');await activate(page,'#reply-send');await expect.poll(async()=>(await requests()).length).toBe(1);expect(JSON.stringify((await requests())[0])).not.toContain('비공개');expect((await requests())[0].reply.mode).toBe('encrypted');
  const plain=await page.evaluate(async command=>{const material=await RelayDB.meta('e2ee'),r=command.reply,bytes=s=>Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));const additionalData=new TextEncoder().encode(['GalaxyRelay/reply/v1',command.commandId,command.eventId,command.timestamp,command.nonce,r.actionId,r.keyId].join('\n'));return new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:bytes(r.iv),additionalData},material.key,bytes(r.ciphertext)));},(await requests())[0]);expect(plain).toBe('비공개 답장');
});
test('unsupported and offline notifications cannot submit a reply',async({page,context})=>{
  await setup(page,{supported:false});await expect(page.locator('#reply-form')).toBeHidden();await expect(page.locator('#reply-hint')).toContainText('답장 기능을 제공하지');
  await page.evaluate(async()=>{const e=(await RelayDB.all())[0];e.reply={actionId:'a'.repeat(64),label:'답장'};await RelayDB.clearHistory();await RelayDB.apply(e);});await activate(page,'#detail-close');await page.reload();await activate(page,'.notification');await page.locator('#reply-text').fill('test');await context.setOffline(true);await expect(page.locator('#reply-text')).toBeDisabled();await expect(page.locator('#reply-send')).toBeDisabled();
});
test('ambiguous network result remains blocked after reopening and reload',async({page})=>{
  const{requests}=await setup(page,{fail:true});await page.locator('#reply-text').fill('한 번만');await activate(page,'#reply-send');await expect.poll(async()=>(await requests()).length).toBe(1);await expect(page.locator('#command-status')).toContainText('자동 재전송하지');await activate(page,'#detail-close');await page.reload();await activate(page,'.notification');await expect(page.locator('#reply-text')).toBeDisabled();await expect(page.locator('#reply-send')).toBeDisabled();expect(await requests()).toHaveLength(1);
});
test('legacy decrypt-only E2EE key requires reimport and never downgrades',async({page})=>{
  const{requests}=await setup(page,{encrypted:true,legacy:true});await page.locator('#reply-text').fill('암호화 필수');await activate(page,'#reply-send');await expect(page.locator('#command-status')).toContainText('같은 E2EE 키를 다시 저장');expect(await requests()).toHaveLength(0);
});
