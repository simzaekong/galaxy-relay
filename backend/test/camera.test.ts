import { describe,it,expect } from 'vitest';
import { CameraSession,cameraAccess,cameraAuthorized,cameraRequest } from '../src/camera-session';
const packet=(seq:number)=>({seq,iv:'A'.repeat(16),ciphertext:'B'.repeat(32)});
describe('Camera session isolation and cancellation',()=>{
 it('isolates role grants, lifetime and signaling domain',()=>{
  const now=Date.now(),grant=cameraAccess('secret','evt_'+crypto.randomUUID(),crypto.randomUUID(),crypto.randomUUID(),now+600000,'viewer');
  expect(cameraAuthorized('secret',grant,now)).toBe(true);
  for(const change of [{role:'sender'},{nativeId:crypto.randomUUID()},{androidId:crypto.randomUUID()},{sessionId:'evt_'+crypto.randomUUID()},{expires:now+1},{grant:'A'.repeat(43)}])expect(cameraAuthorized('secret',{...grant,...change} as typeof grant,now)).toBe(false);
  expect(cameraAuthorized('secret',grant,now+600000)).toBe(false);
  expect(cameraRequest.safeParse({...grant,op:'signal',after:0,packet:packet(1),video:'private'}).success).toBe(false);
 });
 it('cancels before a delayed request attaches and never revives the camera',()=>{
  const s=new CameraSession();s.start('one','native',600000,0);s.exchange('one','viewer','end',0,undefined,1);
  expect(s.attach('one','native',2)).toBeNull();expect(s.exchange('one','sender','poll',0,undefined,3).ended).toBe(true);
 });
 it('permits only one live session and rejects a stale close against its replacement',()=>{
  const s=new CameraSession();expect(s.start('one','native',600000,0)).toBe(true);expect(s.start('two','native',600000,1)).toBe(false);
  s.exchange('one','viewer','end',0,undefined,2);expect(s.start('two','native',600002,3)).toBe(true);
  expect(s.exchange('one','viewer','end',0,undefined,4).ended).toBe(true);expect(s.state?.ended).toBe(false);
 });
 it('requires current peer and an unexpired launch window',()=>{
  const s=new CameraSession();s.start('one','native',600000,0);
  expect(s.attach('one','other',1)).toBeNull();expect(s.attach('other','native',1)).toBeNull();expect(s.attach('one','native',35001)).toBeNull();
  expect(()=>s.exchange('one','sender','signal',0,packet(1),100)).toThrow('CAMERA_NOT_ATTACHED');
 });
 it('delivers only to the other role, acknowledges in order and rejects replay',()=>{
  const s=new CameraSession();s.start('one','native',600000,0);s.attach('one','native',1);
  expect(s.exchange('one','viewer','signal',0,packet(1),2).messages).toEqual([]);
  expect(s.exchange('one','sender','poll',0,undefined,3).messages).toEqual([packet(1)]);
  expect(s.exchange('one','sender','poll',1,undefined,4).messages).toEqual([]);
  expect(()=>s.exchange('one','viewer','signal',0,packet(1),5)).toThrow();expect(()=>s.exchange('one','viewer','signal',0,packet(3),5)).toThrow();
 });
 it('bounds pending connection data and wipes it on expiry and revocation',()=>{
  const s=new CameraSession();s.start('one','native',600000,0);for(let seq=1;seq<=96;seq++)s.exchange('one','viewer','signal',0,packet(seq),1);
  expect(()=>s.exchange('one','viewer','signal',0,packet(97),2)).toThrow();
  s.revoke('other');expect(s.state?.ended).toBe(false);s.revoke('native');expect(s.state?.to.sender).toEqual([]);expect(s.state?.ended).toBe(true);
  s.start('two','native',600001,1);expect(s.exchange('two','viewer','poll',0,undefined,600002).ended).toBe(true);
 });
});
