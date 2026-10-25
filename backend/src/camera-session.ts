import { z } from 'zod';
import { sign, equal } from './crypto';
export const cameraLifetime=600000;
const id=z.string().uuid();
export const cameraId=z.string().regex(/^evt_[0-9a-f-]{36}$/);
export const cameraGrantSchema=z.object({sessionId:cameraId,androidId:id,nativeId:id,expires:z.number().int(),role:z.enum(['viewer','sender']),grant:z.string().regex(/^[A-Za-z0-9_-]{43}$/)});
export type CameraGrant=z.infer<typeof cameraGrantSchema>;
export const cameraPacket=z.object({seq:z.number().int().positive().max(512),iv:z.string().regex(/^[A-Za-z0-9_-]{16}$/),ciphertext:z.string().regex(/^[A-Za-z0-9_-]{24,32000}$/)}).strict();
export type CameraPacket=z.infer<typeof cameraPacket>;
export const cameraRequest=cameraGrantSchema.extend({timestamp:z.number().int().optional(),nonce:z.string().uuid().optional(),op:z.enum(['poll','signal','end']),after:z.number().int().nonnegative().max(512).default(0),packet:cameraPacket.optional()}).strict().refine(v=>(v.op==='signal')===!!v.packet);
export const cameraAccess=(secret:string,sessionId:string,androidId:string,nativeId:string,expires:number,role:CameraGrant['role']):CameraGrant=>({sessionId,androidId,nativeId,expires,role,grant:sign(secret,['native-camera/v1',sessionId,androidId,nativeId,expires,role].join('\n'))});
export function cameraAuthorized(secret:string,g:CameraGrant,now=Date.now()){
 return g.expires>now&&g.expires<=now+cameraLifetime&&equal(g.grant,cameraAccess(secret,g.sessionId,g.androidId,g.nativeId,g.expires,g.role).grant);
}
export type CameraSessionState={sessionId:string;nativeId:string;expires:number;created:number;ended:boolean;joined:boolean;sent:{viewer:number;sender:number};to:{viewer:CameraPacket[];sender:CameraPacket[]}};
/** Only encrypted connection descriptions/candidates, never media or capture commands. */
export class CameraSession {
 constructor(public state?:CameraSessionState){}
 start(sessionId:string,nativeId:string,expires:number,now=Date.now()){
  if(this.state&&!this.state.ended&&this.state.expires>now)return false;
  this.state={sessionId,nativeId,expires,created:now,ended:false,joined:false,sent:{viewer:0,sender:0},to:{viewer:[],sender:[]}};return true;
 }
 attach(sessionId:string,nativeId:string,now=Date.now()){
  const s=this.state;if(!s||s.sessionId!==sessionId||s.nativeId!==nativeId||s.ended||s.expires<=now||(!s.joined&&now-s.created>35000))return null;
  s.joined=true;return s.expires;
 }
 exchange(sessionId:string,role:'viewer'|'sender',op:'poll'|'signal'|'end',after:number,packet?:CameraPacket,now=Date.now()){
  const s=this.state;
  if(!s||s.sessionId!==sessionId)return {ended:true,joined:false,messages:[]};
  if(s.expires<=now||op==='end'){s.ended=true;s.to={viewer:[],sender:[]};}
  if(s.ended)return {ended:true,joined:s.joined,messages:[]};
  if(role==='sender'&&!s.joined)throw new Error('CAMERA_NOT_ATTACHED');
  const other=role==='viewer'?'sender':'viewer';
  if(packet){
   if(packet.seq!==s.sent[role]+1||s.to[other].length>=96)throw new Error('CAMERA_SIGNAL_REJECTED');
   s.sent[role]=packet.seq;s.to[other].push(packet);
  }
  s.to[role]=s.to[role].filter(p=>p.seq>after);
  return {ended:false,joined:s.joined,messages:s.to[role]};
 }
 revoke(nativeId:string){if(this.state?.nativeId===nativeId){this.state.ended=true;this.state.to={viewer:[],sender:[]};}}
}
