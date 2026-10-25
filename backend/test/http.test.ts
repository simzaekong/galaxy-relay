import { it, expect } from 'vitest';
import { createServer } from 'node:http';
import { createApp } from '../src/index';
it('serves the independent mock client over an actual HTTP socket', async()=>{
  const app=createApp({push:async()=>{},fcm:async()=>{}});
  const server=createServer(async(req,res)=>{
    const response=await app.fetch(new Request('http://localhost'+req.url),{} as Env);
    res.writeHead(response.status,Object.fromEntries(response.headers));res.end(await response.text());
  });
  await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    const address=server.address() as {port:number};
    const response=await fetch('http://127.0.0.1:'+address.port+'/api/status');
    expect(response.status).toBe(200);expect((await response.json() as {service:string}).service).toBe('Galaxy Relay');
  } finally {await new Promise<void>(resolve=>server.close(()=>resolve()));}
});
