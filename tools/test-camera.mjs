import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile,mkdir } from 'node:fs/promises';
import { randomBytes,randomUUID } from 'node:crypto';
import { chromium } from '../web/node_modules/@playwright/test/index.mjs';
const html=await readFile(new URL('../web/public/camera.html',import.meta.url)),js=await readFile(new URL('../web/public/camera-client.js',import.meta.url));
await mkdir(new URL('../artifacts/',import.meta.url),{recursive:true});
const sessions=new Map(),wire=[];
const server=createServer(async(req,res)=>{
 if(req.method==='GET'){res.setHeader('Content-Type',req.url==='/camera-client.js'?'application/javascript':'text/html');res.end(req.url==='/camera-client.js'?js:html);return;}
 let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>65536){res.writeHead(413);res.end();return;}}
 const b=JSON.parse(raw),s=sessions.get(b.sessionId);wire.push(b);
 const other=b.role==='viewer'?'sender':'viewer';if(b.op==='end')s.ended=true;
 if(b.packet)s.to[other].push(b.packet);
 s.to[b.role]=s.to[b.role].filter(p=>p.seq>b.after);
 res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ended:s.ended,joined:true,messages:s.ended?[]:s.to[b.role]}));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true,args:['--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--autoplay-policy=no-user-gesture-required']});
let context;
async function pair(){
 if(context)await context.close();context=await browser.newContext({permissions:['camera'],viewport:{width:390,height:844}});
 const viewer=await context.newPage(),sender=await context.newPage(),files={photos:[],chunks:[],finished:0},events=[];
 await sender.exposeFunction('saveCameraTest',async(method,id,...args)=>{
  if(method==='photo')files.photos.push(Buffer.from(args[0],'base64url'));
  else if(method==='recordStart')files.mime=args[0];else if(method==='recordChunk')files.chunks.push(Buffer.from(args[0],'base64url'));else if(method==='recordFinish')files.finished++;
  return id;
 });
 for(const page of [viewer,sender])await page.addInitScript(isViewer=>{
  window.__pcs=[];window.__captures=[];window.__events=[];const PC=window.RTCPeerConnection;window.RTCPeerConnection=class extends PC{constructor(...args){super(...args);window.__pcs.push(this);}};
  const original=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);navigator.mediaDevices.getUserMedia=async constraints=>{if(isViewer)throw Error('iPhone capture forbidden');if(constraints.audio!==false)throw Error('Microphone forbidden');const stream=await original(constraints);window.__captures.push(stream);return stream;};
  const send=RTCDataChannel.prototype.send;RTCDataChannel.prototype.send=function(data){if(window.__dropLease&&JSON.parse(data).type==='lease')return;return send.call(this,data);};
  window.CameraNative={event:raw=>window.__events.push(JSON.parse(raw))};
  if(!isViewer)for(const name of ['photo','recordStart','recordChunk','recordFinish'])window.CameraNative[name]=(id,...args)=>{window.saveCameraTest(name,id,...args).then(()=>window.cameraNativeResult(id,true,''));};
 },page===viewer);
 await Promise.all([viewer.goto(base+'/api/camera/client'),sender.goto(base+'/api/camera/client')]);
 const grant={sessionId:'evt_'+randomUUID(),androidId:randomUUID(),nativeId:randomUUID(),expires:Date.now()+600000,grant:randomBytes(32).toString('base64url'),linkKey:randomBytes(32).toString('base64url')};sessions.set(grant.sessionId,{ended:false,to:{viewer:[],sender:[]}});
 return {viewer,sender,files,grant};
}
async function connect(p){await p.viewer.evaluate(g=>window.startCamera({...g,role:'viewer'}),p.grant);await p.sender.evaluate(g=>window.startCamera({...g,role:'sender'}),p.grant);await p.viewer.waitForFunction(()=>window.__events.some(e=>e.type==='connected'),null,{timeout:25000});}
try{
 let p=await pair();await connect(p);
 assert.equal(await p.viewer.evaluate(()=>window.__captures.length),0);assert.equal(await p.sender.evaluate(()=>window.__captures[0].getAudioTracks().length),0);
 for(const page of [p.viewer,p.sender])assert.equal(await page.evaluate(()=>window.__pcs.some(pc=>/^m=audio\s/m.test(pc.localDescription?.sdp??''))),false);
 await p.viewer.click('#photo');await p.viewer.waitForFunction(()=>document.querySelector('#status').textContent.includes('저장했습니다'));
 assert.equal(p.files.photos.length,1);assert.equal(p.files.photos[0].subarray(0,2).toString('hex'),'ffd8');
 await p.viewer.click('#record');await p.viewer.waitForFunction(()=>document.querySelector('#record').dataset.recording==='true');await new Promise(r=>setTimeout(r,2400));await p.viewer.click('#record');await p.viewer.waitForFunction(()=>document.querySelector('#record').dataset.recording==='false');
 assert.ok(p.files.chunks.reduce((sum,b)=>sum+b.length,0)>1000);assert.equal(p.files.finished,1);
 await p.viewer.screenshot({path:new URL('../artifacts/camera-connected-preview.png',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1')});
 await p.viewer.evaluate(()=>window.closeCamera());await p.sender.waitForFunction(()=>window.__captures.every(s=>s.getTracks().every(t=>t.readyState==='ended')));
 console.log('PASS video-only direct connection, iPhone no capture, JPEG photo and silent recording on Galaxy, close releases tracks.');
 p=await pair();await connect(p);await p.viewer.evaluate(()=>{window.__dropLease=true;});await p.sender.waitForFunction(()=>window.__events.some(e=>e.type==='closed'),null,{timeout:12000});assert.equal(await p.sender.evaluate(()=>window.__captures[0].getVideoTracks()[0].readyState),'ended');
 console.log('PASS lost iPhone lease closes the sender camera within the 8-second lease plus watchdog interval.');
 p=await pair();await p.viewer.evaluate(g=>window.startCamera({...g,role:'viewer'}),p.grant);await p.viewer.evaluate(()=>window.closeCamera());await new Promise(r=>setTimeout(r,100));await p.sender.evaluate(g=>window.startCamera({...g,role:'sender'}),p.grant);await p.sender.waitForFunction(()=>window.__events.some(e=>e.type==='closed'));assert.equal(await p.sender.evaluate(()=>window.__captures.length),0);
 assert.ok(wire.every(b=>Object.keys(b).every(k=>['sessionId','androidId','nativeId','expires','role','grant','op','after','packet'].includes(k))));
 assert.ok(wire.filter(b=>b.packet).every(b=>Object.keys(b.packet).sort().join(',')==='ciphertext,iv,seq'));
 console.log('PASS close-before-accept never opens a camera; signaling transports only encrypted connection info, no media or capture commands.');
}finally{await browser.close();await new Promise(r=>server.close(r));}
