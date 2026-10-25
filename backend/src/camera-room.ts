import { DurableObject } from 'cloudflare:workers';
import { CameraSession, type CameraSessionState, type CameraPacket } from './camera-session';
export class CameraRoom extends DurableObject<Env>{
 private session=new CameraSession();
 private limits=new Map<string,{minute:number;count:number}>();
 constructor(ctx:DurableObjectState,env:Env){super(ctx,env);ctx.blockConcurrencyWhile(async()=>{this.session=new CameraSession(await ctx.storage.get<CameraSessionState>('session'));});}
 async start(sessionId:string,nativeId:string,expires:number){
  const ok=this.session.start(sessionId,nativeId,expires);if(ok){await this.ctx.storage.put('session',this.session.state);await this.ctx.storage.setAlarm(expires);}return ok;
 }
 async attach(sessionId:string,nativeId:string){const expires=this.session.attach(sessionId,nativeId);if(expires)await this.ctx.storage.put('session',this.session.state);return expires;}
 async exchange(sessionId:string,role:'viewer'|'sender',op:'poll'|'signal'|'end',after:number,packet?:CameraPacket){
  const minute=Math.floor(Date.now()/60000),previous=this.limits.get(role),limit=previous?.minute===minute?previous:{minute,count:0};
  if(++limit.count>180)throw new Error('CAMERA_RATE_LIMITED');this.limits.set(role,limit);
  const before=JSON.stringify(this.session.state),result=this.session.exchange(sessionId,role,op,after,packet);
  if(JSON.stringify(this.session.state)!==before)await this.ctx.storage.put('session',this.session.state);
  return result;
 }
 async revoke(nativeId:string){this.session.revoke(nativeId);if(this.session.state)await this.ctx.storage.put('session',this.session.state);}
 async alarm(){await this.ctx.storage.deleteAll();this.session=new CameraSession();}
}
