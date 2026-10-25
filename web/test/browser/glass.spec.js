import {test,expect} from '@playwright/test';
async function seed(page){await page.goto('/');await page.waitForFunction(()=>!!globalThis.RelayDB);const id=await page.evaluate(async()=>{const id='evt_'+crypto.randomUUID();await RelayDB.apply({id,type:'notification',app:'카카오톡',packageName:'com.kakao.talk',title:'김철수',body:'야 어디야?',notificationKey:'chat-key',timestamp:Date.now()});return id;});await page.reload();await expect(page.locator('.notification')).toHaveCount(1);return id;}
test('detail marks read and retains body across close, refresh, duplicate and reopening',async({page})=>{
  const id=await seed(page);await expect(page.locator('#notification-count')).toHaveText('안 읽은 알림 1개');
  await page.locator('.notification').click();await expect(page.locator('#detail-body')).toHaveText('야 어디야?');await expect(page.locator('.notification')).toHaveCount(1);await expect(page.locator('#notification-count')).toHaveText('안 읽은 알림 0개');
  const result=await page.evaluate(async id=>({event:await RelayDB.get(id),state:await RelayDB.state(id)}),id);expect(result.event.body).toBe('야 어디야?');expect(result.event.read).toBe(true);expect(result.event.readAt).toBeGreaterThan(0);expect(result.state.viewedAt).toBeUndefined();expect(JSON.stringify(result.state)).not.toMatch(/body|title|김철수|야 어디야/);
  await page.getByRole('button',{name:'닫기',exact:true}).click();await page.reload();await expect(page.locator('.notification')).toHaveCount(1);await expect(page.locator('#notification-count')).toHaveText('안 읽은 알림 0개');
  await page.evaluate(async id=>{const event=await RelayDB.get(id);await RelayDB.apply(event);},id);expect((await page.evaluate(()=>RelayDB.all()))[0].read).toBe(true);
  await page.locator('.notification').click();await expect(page.locator('#detail-body')).toHaveText('야 어디야?');await page.getByRole('button',{name:'닫기',exact:true}).click();
  await page.evaluate(()=>RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:'notification',app:'새 앱',title:'new-event',body:'new message',notificationKey:'new-key',timestamp:Date.now()+2}));await page.reload();await expect(page.locator('.notification')).toHaveCount(2);await expect(page.locator('#notification-count')).toHaveText('안 읽은 알림 1개');
});
test('read detail still receives a quiet removal result and deletes retained history',async({page})=>{
  const commandId='cmd_'+crypto.randomUUID();let receipt;
  await page.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;if(path==='/api/sync')return route.fulfill({json:{states:receipt?[receipt]:[]}});return route.fulfill({json:path==='/api/status'?{paired:true,autoDismiss:false}:path==='/api/config'?{vapidPublicKey:''}:{accepted:true,commandId}});});
  const id=await seed(page);await page.evaluate(()=>RelayDB.setMeta('credential',{token:'t'.repeat(43)}));await page.reload();await page.evaluate(()=>navigator.serviceWorker.ready);await page.locator('.notification').click();await page.locator('#detail-card').click();
  receipt={id:'evt_'+crypto.randomUUID(),type:'command_result',commandId,targetEventId:id,result:'REMOVED',timestamp:Date.now()};await page.getByRole('button',{name:'새로고침',exact:true}).evaluate(button=>button.click());
  await expect(page.locator('#detail-state')).toHaveText('Galaxy에서 제거됨');await expect(page.locator('#detail-card')).toHaveAttribute('aria-disabled','true');await expect(page.locator('#command-status')).toContainText('제거');expect(await page.evaluate(()=>RelayDB.all())).toHaveLength(0);
});
test('clean inbox works offline, fits mobile and retains read history',async({page,context})=>{
  const id=await seed(page);await page.evaluate(()=>navigator.serviceWorker.ready);await page.waitForFunction(()=>!!navigator.serviceWorker.controller);await context.setOffline(true);await page.reload();await expect(page.locator('.notification')).toHaveCount(1);
  expect(await page.evaluate(async()=>!!await caches.match('/images/wallpaper.png'))).toBe(false);await expect(page.locator('#clock-time')).toHaveText(/\d{1,2}:\d{2}/);
  for(const width of [320,390,430]){await page.setViewportSize({width,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
  const blur=await page.locator('.notification').evaluate(e=>getComputedStyle(e).backdropFilter);expect(blur).toBe('none');await page.locator('.notification').click();await expect(page.locator('#detail-card')).toHaveAttribute('aria-disabled','true');await expect(page.locator('#detail-body')).toHaveText('야 어디야?');expect(await page.evaluate(async id=>(await RelayDB.get(id)).read,id)).toBe(true);
  await page.getByRole('button',{name:'닫기',exact:true}).click();await page.reload();await expect(page.locator('.notification')).toHaveCount(1);await page.locator('.notification').click();await expect(page.locator('#detail-body')).toHaveText('야 어디야?');
});
