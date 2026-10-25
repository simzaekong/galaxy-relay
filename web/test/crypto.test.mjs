import {test} from 'node:test';
import assert from 'node:assert/strict';
import '../src/crypto.js';
const raw=crypto.getRandomValues(new Uint8Array(32)),encoded=Buffer.from(raw).toString('base64url');
async function fixture(){const material=await EventCrypto.importKey(encoded);const event={id:'evt_'+crypto.randomUUID(),type:'notification',title:'김철수',body:'야 어디야?',timestamp:Date.now()};const iv=crypto.getRandomValues(new Uint8Array(12));const key=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['encrypt']);const additionalData=new TextEncoder().encode(['GalaxyRelay/v1',event.id,event.timestamp,material.keyId].join('\n'));const cipher=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData,tagLength:128},key,new TextEncoder().encode(JSON.stringify(event)));return{material,event,wire:{id:event.id,type:'encrypted',version:1,timestamp:event.timestamp,keyId:material.keyId,iv:Buffer.from(iv).toString('base64url'),ciphertext:Buffer.from(cipher).toString('base64url')}};}
test('AES-GCM decrypts and rejects modified authenticated metadata',async()=>{const{material,event,wire}=await fixture();assert.deepEqual(await EventCrypto.decode(wire,material),event);assert.equal(material.key.extractable,false);await assert.rejects(EventCrypto.decode({...wire,timestamp:wire.timestamp+1},material));await assert.rejects(EventCrypto.decode({...wire,iv:'A'.repeat(16)},material));await assert.rejects(EventCrypto.decode({...wire,ciphertext:'A'.repeat(wire.ciphertext.length)},material));});
test('configured E2EE rejects plaintext and wrong keys',async()=>{const{material,event,wire}=await fixture();await assert.rejects(EventCrypto.decode(event,material));await assert.rejects(EventCrypto.decode(wire,await EventCrypto.importKey(Buffer.alloc(32,2).toString('base64url'))));assert.deepEqual(await EventCrypto.decode(event,null),event);});
test('malformed keys fail without a server request',async()=>{await assert.rejects(EventCrypto.importKey('short'));});

test('reply encryption binds command, event, nonce and action with fresh IVs',async()=>{
  const material=await EventCrypto.importKey(encoded),actionId='a'.repeat(64),envelope={commandId:'cmd_'+crypto.randomUUID(),eventId:'evt_'+crypto.randomUUID(),timestamp:Date.now(),nonce:crypto.randomUUID()};
  const text='곧 도착해요!\n🙂',wire=await EventCrypto.reply(text,actionId,envelope,material);
  assert.equal(wire.mode,'encrypted');assert.equal(wire.text,undefined);assert.notEqual(wire.iv,(await EventCrypto.reply(text,actionId,envelope,material)).iv);
  const aad=new TextEncoder().encode(['GalaxyRelay/reply/v1',envelope.commandId,envelope.eventId,envelope.timestamp,envelope.nonce,actionId,material.keyId].join('\n'));
  const plain=await crypto.subtle.decrypt({name:'AES-GCM',iv:Buffer.from(wire.iv,'base64url'),additionalData:aad},material.key,Buffer.from(wire.ciphertext,'base64url'));assert.equal(new TextDecoder().decode(plain),text);
  await assert.rejects(crypto.subtle.decrypt({name:'AES-GCM',iv:Buffer.from(wire.iv,'base64url'),additionalData:new TextEncoder().encode(new TextDecoder().decode(aad).replace(envelope.eventId,'evt_'+crypto.randomUUID()))},material.key,Buffer.from(wire.ciphertext,'base64url')));
  const legacy={...material,key:await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['decrypt'])};await assert.rejects(EventCrypto.reply(text,actionId,envelope,legacy),/E2EE_REPLY_KEY_REQUIRED/);
});
test('reply text bounds and plaintext mode are explicit',async()=>{
  assert.equal(EventCrypto.validReply(' '),false);assert.equal(EventCrypto.validReply('a'.repeat(501)),false);assert.equal(EventCrypto.validReply('hi\u0000'),false);
  assert.equal(EventCrypto.validReply('가'.repeat(500)),true);assert.deepEqual(await EventCrypto.reply('좋아요','a'.repeat(64),{},null),{actionId:'a'.repeat(64),mode:'plain',text:'좋아요'});
});
