import test from 'node:test';
import assert from 'node:assert/strict';
import '../src/notifications.js';
test('notification titles show sender and app without repeating them in the body',()=>{
  const event={id:'evt_'+crypto.randomUUID(),type:'notification',title:'김철수',app:'카카오톡',body:'야 어디야?'};
  assert.equal(RelayNotifications.presentation(event).title,'김철수 · 카카오톡');assert.equal(RelayNotifications.presentation(event).body,'야 어디야?');
  assert.equal(RelayNotifications.presentation(event,{hidePreview:true}).title,'Galaxy Relay');
  assert.equal(RelayNotifications.presentation(event,{hideName:true}).title,'카카오톡');
});
test('call alerts retain a stable tag and identify the caller',()=>{
  const id='evt_'+crypto.randomUUID(),event={id,type:'call',state:'ringing',callId:id,contact:'이영희',number:'5551234'};
  const notice=RelayNotifications.presentation(event);assert.equal(notice.title,'이영희 · 전화 수신');assert.equal(notice.tag,'call:'+id);assert.ok(notice.body.includes('5551234'));
  assert.equal(RelayNotifications.presentation(event,{hideName:true}).title,'전화 수신');
  assert.equal(RelayNotifications.presentation({...event,state:'offhook'}),null);
  assert.equal(RelayNotifications.presentation({...event,state:'ended'}),null);
  assert.equal(RelayNotifications.presentation({type:'command_result'}),null);
  assert.equal(RelayNotifications.presentation({type:'removed'}),null);
});
