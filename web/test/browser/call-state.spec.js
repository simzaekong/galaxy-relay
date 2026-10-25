import {test,expect} from '@playwright/test';
test('quiet command receipt updates detail without creating any banner',async({page})=>{
  const eventId='evt_'+crypto.randomUUID(),commandId='cmd_'+crypto.randomUUID();
  await page.route('**/api/sync',route=>route.fulfill({json:{states:[{id:'evt_'+crypto.randomUUID(),type:'command_result',commandId,targetEventId:eventId,result:'REMOVED',timestamp:Date.now()}]}}));
  await page.goto('/');await page.waitForFunction(()=>!!globalThis.RelaySync);
  await page.evaluate(async({eventId,commandId})=>{await RelayDB.setMeta('credential',{token:'t'.repeat(43)});await RelayDB.apply({id:eventId,type:'notification',title:'Test',notificationKey:'key',timestamp:Date.now()});await RelaySync.pull(await navigator.serviceWorker.ready,{commandIds:[commandId]});}, {eventId,commandId});
  expect(await page.evaluate(async id=>await RelayDB.get(id),eventId)).toBeUndefined();
  expect(await page.evaluate(async id=>(await RelayDB.state(id)).androidState,eventId)).toBe('removed');
  expect(await page.evaluate(async()=> (await(await navigator.serviceWorker.ready).getNotifications()).length)).toBe(0);
});
test('out-of-order call end prevents a delayed ringing record from becoming active again',async({page})=>{
  await page.goto('/');await page.waitForFunction(()=>!!globalThis.RelayDB);
  const result=await page.evaluate(async()=>{const id='evt_'+crypto.randomUUID(),time=Date.now();await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:'call',state:'ended',callId:id,timestamp:time+1});await RelayDB.apply({id,callId:id,type:'call',state:'ringing',contact:'Test caller',timestamp:time});return {record:await RelayDB.get(id),count:(await RelayDB.all()).length};});
  expect(result.record.state).toBe('ended');expect(result.count).toBe(1);
});
