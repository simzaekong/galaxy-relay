import test from 'node:test';
import assert from 'node:assert/strict';
import '../src/model.js';
const event={id:'evt_'+crypto.randomUUID(),type:'notification',timestamp:Date.now(),notificationKey:'key',title:'김철수',body:'야 어디야?'};
test('validates event and rejects malformed / stale pushes',()=>{assert.ok(RelayModel.valid(event));assert.equal(RelayModel.valid({...event,id:'../../bad'}),false);assert.equal(RelayModel.valid({...event,timestamp:1}),false);assert.equal(RelayModel.valid({...event,replacesEventId:'invalid'}),false);assert.equal(RelayModel.valid({...event,replacesEventId:event.id}),false);assert.equal(RelayModel.valid({...event,type:'call',state:'invalid'}),false);});
test('duplicates preserve read state; updated notification becomes unread',()=>{assert.equal(RelayModel.merge({...event,read:true},event).read,true);assert.equal(RelayModel.merge({...event,read:true},{...event,timestamp:event.timestamp+1}).read,false);});
test('a removal arriving before content stays removed',()=>{assert.equal(RelayModel.merge(undefined,event,{androidState:'removed'}).androidState,'removed');});
