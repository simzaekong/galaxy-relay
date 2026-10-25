// Isolated synthetic records only; no real pairing, messages or commands.
import {chromium} from '@playwright/test';
import fs from 'node:fs';
const origin=process.env.RELAY_PREVIEW_URL||'http://127.0.0.1:8787';
const output=new URL('../../artifacts/',import.meta.url);fs.mkdirSync(output,{recursive:true});
const browser=await chromium.launch();
try{
  const context=await browser.newContext({viewport:{width:390,height:844},colorScheme:'light'});
  const page=await context.newPage();
  await page.route('**/api/**',r=>r.fulfill({json:new URL(r.request().url()).pathname==='/api/status'?{paired:true}:new URL(r.request().url()).pathname==='/api/config'?{vapidPublicKey:''}:{states:[]}}));
  await page.goto(origin);await page.waitForFunction(()=>!!globalThis.RelayDB);
  await page.evaluate(async()=>{
    await RelayDB.setMeta('credential',{token:'synthetic-preview-only'});
    const now=Date.now();
    for(const [i,app,title,body,age] of [
      [0,'카카오톡','서연','오늘 저녁에 시간 있어? 같이 밥 먹자 🙂',120000],
      [1,'카카오톡','서연','새로 생긴 파스타집 가볼까?',180000],
      [2,'Instagram','지민','주말에 찍은 사진 보냈어. 이 사진 어때?',600000],
      [3,'Discord','디자인 스터디','이번 주 모임은 토요일 오후 2시예요.',1800000],
      [4,'문자','배송 안내','주문하신 상품이 오늘 도착할 예정입니다.',3600000],
      [5,'카카오톡','민준','내일 10시에 보자!',86340000]
    ])await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:app==='문자'?'sms':'notification',app,title,body,notificationKey:'preview-'+i,timestamp:now-age,...(app==='카카오톡'?{reply:{actionId:'a'.repeat(64),label:'답장'}}:{})});
  });await page.reload();await page.waitForFunction(()=>document.querySelectorAll('.notification').length===5);
  await page.waitForTimeout(200);await page.screenshot({path:new URL('pwa-inbox-light.png',output).pathname.slice(1),fullPage:true});
  await page.emulateMedia({colorScheme:'dark'});await page.waitForTimeout(200);await page.screenshot({path:new URL('pwa-inbox-dark.png',output).pathname.slice(1),fullPage:true});
  await page.emulateMedia({colorScheme:'light'});await page.locator('.notification').first().click();await page.locator('.expanded-group .notification').first().click();await page.locator('#reply-text').fill('좋아! 7시쯤 만나자.');await page.waitForTimeout(200);
  await page.screenshot({path:new URL('pwa-inbox-reply.png',output).pathname.slice(1)});
  await page.locator('#detail-close').click();await page.locator('#settings-open').click();await page.screenshot({path:new URL('pwa-inbox-settings.png',output).pathname.slice(1)});
  await context.close();console.log('Synthetic previews saved: light, dark, reply, settings.');
}finally{await browser.close();}
