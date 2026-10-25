// Local-only visual fixtures. No test endpoint or notification data is published.
import {chromium,webkit} from 'playwright';
import fs from 'node:fs';
const useWebKit=process.argv.includes('--webkit'),browser=await(useWebKit?webkit:chromium).launch();
try{
  const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,timezoneId:'Asia/Seoul',colorScheme:'light'}),page=await context.newPage();
  // Window fetch fixtures also work when WebKit does not route SW-controlled requests.
  await context.addInitScript(()=>{const original=window.fetch.bind(window);window.fetch=(input,options)=>{const path=new URL(input instanceof Request?input.url:String(input),location.href).pathname;if(path.startsWith('/api/'))return Promise.resolve(new Response(JSON.stringify(path==='/api/status'?{paired:true,autoDismiss:false}:{states:[]}),{headers:{'Content-Type':'application/json'}}));return original(input,options);};});
  await page.clock.setFixedTime(new Date('2026-10-04T00:41:00Z'));
  await page.route('**/api/**',route=>route.fulfill({json:new URL(route.request().url()).pathname==='/api/status'?{paired:true,autoDismiss:false}:{states:[]}}));
  await page.goto(process.argv[2]||'http://127.0.0.1:8787/');await page.waitForFunction(()=>!!globalThis.RelayDB);
  await page.evaluate(async()=>{await RelayDB.setMeta('credential',{token:'preview-only'});const rows=[['카카오톡','김철수','야 어디야?','com.kakao.talk'],['문자','이영희','오늘 몇 시에 와?','sms'],['Discord','Infosphere','새 메시지가 있습니다.','com.discord'],['Instagram','지민','사진을 보냈습니다.','com.instagram.android'],['카카오톡','김철수','지금 출발했어','com.kakao.talk'],['문자','이영희','역 앞에서 만나자','sms'],['Discord','Infosphere','채널에 새 소식이 있어요.','com.discord']];for(let i=0;i<rows.length;i++){const [app,title,body,packageName]=rows[i];await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:packageName==='sms'?'sms':'notification',app,title,body,packageName,notificationKey:'preview-'+i,timestamp:Date.now()-i*60000});}});
  await page.reload();await page.waitForFunction(()=>document.querySelectorAll('.notification').length===4&&document.querySelector('#pair-section').hidden);
  await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode().catch(()=>{}))));await page.locator('.wallpaper').evaluate(async()=>{const i=new Image();i.src='/images/wallpaper.png';await i.decode();});
  await page.locator('.notification').first().locator('canvas.glass-optics').waitFor();
  const output=new URL('../../artifacts/',import.meta.url),prefix=useWebKit?'pwa-webkit':'pwa';fs.mkdirSync(output,{recursive:true});
  await page.screenshot({path:new URL(prefix+'-preview.png',output).pathname.replace(/^\/(\w:)/,'$1')});
  await page.emulateMedia({colorScheme:'dark'});await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await page.screenshot({path:new URL(prefix+'-preview-dark.png',output).pathname.replace(/^\/(\w:)/,'$1')});
  await page.locator('.notification').first().click();await page.waitForFunction(()=>document.querySelectorAll('.expanded-group .notification').length===2);await page.locator('.expanded-group .notification').first().locator('canvas.glass-optics').waitFor();await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:new URL(prefix+'-group-preview.png',output).pathname.replace(/^\/(\w:)/,'$1')});await page.locator('.expanded-group .notification').first().click();await page.waitForFunction(async()=>document.querySelector('#detail').open&&(await RelayDB.all()).length===7&&(await RelayDB.all()).filter(e=>e.read).length===1);await page.locator('#detail-card.optical-glass canvas').waitFor();await page.screenshot({path:new URL(prefix+'-detail-preview.png',output).pathname.replace(/^\/(\w:)/,'$1')});
  console.log('Mobile light/dark/detail previews exported using synthetic local fixtures.');
}finally{await browser.close();}
