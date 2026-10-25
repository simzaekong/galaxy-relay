import { DurableObject } from 'cloudflare:workers';
export { CameraRoom } from '../../src/camera-room';
export default {async fetch(request,env){const input=await request.json(),room=env.CAMERA.getByName('fixture');let value;if(input.op==='start')value=await room.start(input.sessionId,'native',Date.now()+60000);else if(input.op==='attach')value=await room.attach(input.sessionId,'native');else value=await room.exchange(input.sessionId,input.role,input.op,0,input.packet);return Response.json(value);}};
