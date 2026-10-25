import {test,expect} from '@playwright/test';
async function fixture(page){
  await page.route('**/api/**',r=>r.fulfill({json:new URL(r.request().url()).pathname==='/api/status'?{paired:true}:{states:[]}}));
  await page.goto('/');await page.waitForFunction(()=>!!globalThis.RelayDB);
  await page.evaluate(async()=>{
    await RelayDB.setMeta('credential',{token:'isolated-design-fixture'});
    for(const [i,app,type,age] of [[0,'카카오톡','notification',0],[1,'문자','sms',86340000]])
      await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type,app,title:'김철수',body:'새 화면에서도 기록과 기능을 유지해요.',notificationKey:'design-'+i,timestamp:Date.now()-age});
  });await page.reload();await expect(page.locator('.notification')).toHaveCount(2);
}
test('inbox separates dates, exposes search and preserves accessible detail without optical resources',async({page})=>{
  const requested=[];page.on('request',r=>requested.push(r.url()));await fixture(page);
  await expect(page.getByRole('searchbox')).toBeVisible();await expect(page.locator('.day-heading')).toHaveText(['오늘','어제']);await expect(page.locator('#total-count')).toHaveText('2');
  expect(requested.some(url=>/wallpaper|glass\.js/.test(url))).toBe(false);await expect(page.locator('canvas')).toHaveCount(0);
  await page.locator('.notification').first().click();await expect(page.locator('#detail-body')).toHaveText('새 화면에서도 기록과 기능을 유지해요.');
  expect((await page.evaluate(()=>RelayDB.all())).some(e=>e.read)).toBe(true);
  await page.locator('#detail-close').click();await page.getByRole('searchbox').fill('검색 결과 없음');await expect(page.locator('#empty-title')).toHaveText('찾는 알림이 없어요');
});
test('light and dark modes keep mobile sheets contained and settings keyboard accessible',async({page})=>{
  await fixture(page);
  for(const colorScheme of ['light','dark']){
    await page.emulateMedia({colorScheme});
    const bg=await page.evaluate(()=>getComputedStyle(document.body).backgroundColor);expect(bg).toBe(colorScheme==='light'?'rgb(246, 247, 251)':'rgb(19, 20, 26)');
    for(const width of [320,390,430,1024]){
      await page.setViewportSize({width,height:844});await page.locator('.notification').first().click();
      await expect(page.locator('#detail')).toBeVisible();const box=await page.locator('#detail').boundingBox();expect(box.x).toBeGreaterThanOrEqual(-1);expect(box.x+box.width).toBeLessThanOrEqual(width+1);expect(box.y+box.height).toBeLessThanOrEqual(845);
      await page.keyboard.press('Escape');await expect(page.locator('#detail')).toBeHidden();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    }
  }
  await page.locator('#settings-open').click();await page.locator('#hide-name').focus();await page.keyboard.press('Space');await expect(page.locator('#hide-name')).toBeChecked();await page.keyboard.press('Escape');
  await page.reload();await page.locator('#settings-open').click();await expect(page.locator('#hide-name')).toBeChecked();
});
test('offline cache excludes retired wallpaper and preserves clean inbox details',async({page,context})=>{
  await fixture(page);await page.evaluate(()=>navigator.serviceWorker.ready);await page.waitForFunction(()=>!!navigator.serviceWorker.controller);
  expect(await page.evaluate(async()=>!!await caches.match('/src/glass.js'))).toBe(false);
  await context.setOffline(true);await page.reload();await expect(page.locator('.notification')).toHaveCount(2);await page.locator('.notification').first().click();await expect(page.locator('#detail-card')).toHaveAttribute('aria-disabled','true');await expect(page.locator('#detail-body')).toHaveText('새 화면에서도 기록과 기능을 유지해요.');
});
