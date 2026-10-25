/* Single video-only P2P session. No TURN, audio capture, media uploads or retries of captures. */
(()=>{
'use strict';
const video=document.querySelector('#preview'),status=document.querySelector('#status'),photo=document.querySelector('#photo'),record=document.querySelector('#record');
let session;
const bytes=value=>Uint8Array.from(atob(value.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
const b64=value=>{let s='';for(const b of new Uint8Array(value))s+=String.fromCharCode(b);return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');};
const native=(event)=>{try{window.webkit?.messageHandlers.camera?.postMessage(event);window.CameraNative?.event(JSON.stringify(event));}catch{}};
const text=value=>{status.textContent=value;native({type:'status',text:value});};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function noAudio(sdp){if(typeof sdp!=='string'||sdp.length>24000||/^m=audio\s/m.test(sdp)||!/^m=video\s/m.test(sdp))throw Error('잘못된 영상 연결 요청입니다.');}
class CameraSession{
 constructor(config){
  this.config=config;this.ended=false;this.seq=0;this.after=0;this.pending=new Map();this.nativePending=new Map();this.seen=new Set();this.connectedAt=0;this.lastLease=Date.now();this.outgoing=Promise.resolve();this.incoming=Promise.resolve();this.timers=[];
 }
 async request(op,packet){
  const {sessionId,androidId,nativeId,expires,role,grant}=this.config;
  const response=await fetch('/api/camera/session',{method:'POST',headers:{'Content-Type':'application/json'},cache:'no-store',credentials:'omit',redirect:'error',signal:AbortSignal.timeout(7000),body:JSON.stringify({sessionId,androidId,nativeId,expires,role,grant,op,after:this.after,...(packet?{packet}:{})})});
  if(!response.ok)throw Error('연결 서버에 접속할 수 없습니다.');const result=await response.json();if(result.ended){void this.stop('카메라 연결이 종료되었습니다.');return;}if(this.ended)return;
  for(const packet of result.messages??[])this.incoming=this.incoming.then(()=>this.receive(packet)).catch(()=>this.stop('연결 정보를 확인하지 못했습니다.'));
 }
 aad(role,seq){return new TextEncoder().encode(['GalaxyRelay/camera-signal/v1',this.config.sessionId,this.config.nativeId,role,seq].join('\n'));}
 signal(value){
  this.outgoing=this.outgoing.then(async()=>{
   if(this.ended)return;const seq=++this.seq,iv=crypto.getRandomValues(new Uint8Array(12));
   const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:this.aad(this.config.role,seq)},this.key,new TextEncoder().encode(JSON.stringify(value)));
   await this.request('signal',{seq,iv:b64(iv),ciphertext:b64(ciphertext)});
  }).catch(()=>this.stop('연결 정보를 전송하지 못했습니다.'));
  return this.outgoing;
 }
 async receive(packet){
  if(this.ended||packet.seq<=this.after)return;
  if(packet.seq!==this.after+1)throw Error('signal order');
  const role=this.config.role==='viewer'?'sender':'viewer';
  const raw=await crypto.subtle.decrypt({name:'AES-GCM',iv:bytes(packet.iv),additionalData:this.aad(role,packet.seq)},this.key,bytes(packet.ciphertext)),value=JSON.parse(new TextDecoder().decode(raw));this.after=packet.seq;
  if(value.description){
   noAudio(value.description.sdp);
   if(value.description.type!==(this.config.role==='viewer'?'answer':'offer'))throw Error('signal role');
   await this.pc.setRemoteDescription(value.description);
   for(const candidate of this.candidates??[])await this.pc.addIceCandidate(candidate);this.candidates=[];
   if(this.config.role==='sender'){
    const stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720},frameRate:{ideal:24,max:30}}});
    if(this.ended){stream.getTracks().forEach(t=>t.stop());return;}
    if(stream.getAudioTracks().length)throw Error('audio forbidden');this.stream=stream;video.srcObject=stream;
    for(const track of stream.getVideoTracks()){this.pc.addTrack(track,stream);track.onended=()=>{void this.stop('Galaxy 카메라가 종료되었습니다.');};}
    const answer=await this.pc.createAnswer();noAudio(answer.sdp);await this.pc.setLocalDescription(answer);await this.signal({description:{type:answer.type,sdp:answer.sdp}});
   }
  }else if('candidate' in value){if(this.pc.remoteDescription)await this.pc.addIceCandidate(value.candidate);else(this.candidates??=[]).push(value.candidate);}
 }
 setupChannel(channel){
  if(this.channel)throw Error('duplicate channel');this.channel=channel;
  channel.onopen=()=>{
   this.connectedAt=Date.now();this.lastLease=Date.now();text('Galaxy 카메라와 연결됨');photo.disabled=record.disabled=this.config.role!=='viewer';
   native({type:'connected'});
   if(this.config.role==='viewer')this.timers.push(setInterval(()=>this.send({type:'lease'}),1000));
  };
  channel.onclose=()=>{void this.stop('카메라 연결이 종료되었습니다.');};
  channel.onerror=()=>{void this.stop('카메라 연결 오류로 종료했습니다.');};
  channel.onmessage=event=>{void this.message(event.data).catch(()=>this.stop('촬영 요청 처리 중 오류가 발생했습니다.'));};
 }
 send(value){if(this.channel?.readyState==='open')this.channel.send(JSON.stringify(value));}
 async message(raw){
  if(typeof raw!=='string'||raw.length>2048)throw Error('invalid command');const body=JSON.parse(raw);
  if(body.type==='end'){await this.stop('iPhone에서 카메라 연결을 종료했습니다.');return;}
  if(this.config.role==='sender'){
   if(body.type==='lease'){this.lastLease=Date.now();native({type:'lease'});return;}
   if(body.type!=='capture'||typeof body.id!=='string'||!/^[0-9a-f-]{36}$/.test(body.id)||this.seen.has(body.id))return;
   this.seen.add(body.id);if(this.seen.size>128)throw Error('capture limit');
   if(this.capturing){this.send({type:'receipt',id:body.id,ok:false,message:'이전 촬영 처리 중입니다.'});return;}
   this.capturing=true;
   try{
    if(body.action==='photo')await this.takePhoto(body.id);
    else if(body.action==='recordStart')await this.startRecording(body.id);
    else if(body.action==='recordStop')await this.stopRecording();
    else throw Error('지원하지 않는 촬영 요청입니다.');
    this.send({type:'receipt',id:body.id,ok:true,recording:!!this.recorder,message:body.action==='recordStart'?'Galaxy에서 무음 녹화 중':'Galaxy에 저장했습니다.'});
   }catch(error){this.send({type:'receipt',id:body.id,ok:false,recording:!!this.recorder,message:error.message||'Galaxy에서 저장하지 못했습니다.'});}
   finally{this.capturing=false;}
  }else if(body.type==='notice'&&body.recording===false){record.dataset.recording='false';record.textContent='동영상 촬영';text('3분 녹화를 종료하고 Galaxy에 저장했습니다.');
  }else if(body.type==='receipt'&&typeof body.id==='string'){
   const pending=this.pending.get(body.id);if(!pending)return;clearTimeout(pending);this.pending.delete(body.id);record.dataset.recording=String(body.recording===true);record.textContent=body.recording?'녹화 종료':'동영상 촬영';text(body.message);photo.disabled=record.disabled=false;
  }
 }
 async capture(action){
  if(this.ended||this.pending.size||this.channel?.readyState!=='open')return;const id=crypto.randomUUID();photo.disabled=record.disabled=true;text('Galaxy에서 촬영 결과를 확인하고 있습니다…');
  this.pending.set(id,setTimeout(()=>{this.pending.delete(id);photo.disabled=record.disabled=false;text('촬영 결과를 확인하지 못했습니다. 자동 재촬영하지 않습니다.');},12000));
  this.send({type:'capture',id,action});
 }
 async callNative(method,args){
  if(!window.CameraNative?.[method])throw Error('Galaxy 촬영 저장 기능을 사용할 수 없습니다.');
  const id=crypto.randomUUID();return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{this.nativePending.delete(id);reject(Error('Galaxy 저장 시간이 초과되었습니다.'));},10000);this.nativePending.set(id,{resolve,reject,timer});window.CameraNative[method](id,...args);});
 }
 async takePhoto(){
  if(!video.videoWidth||!this.stream)throw Error('카메라 준비 중입니다.');
  const canvas=document.createElement('canvas');canvas.width=video.videoWidth;canvas.height=video.videoHeight;canvas.getContext('2d').drawImage(video,0,0);
  const blob=await new Promise(r=>canvas.toBlob(r,'image/jpeg',0.95));if(!blob)throw Error('사진을 만들지 못했습니다.');await this.callNative('photo',[b64(await blob.arrayBuffer())]);
 }
 async startRecording(){
  if(this.recorder)throw Error('이미 녹화 중입니다.');
  const mime=['video/webm;codecs=vp8','video/webm','video/mp4'].find(v=>MediaRecorder.isTypeSupported(v));if(!mime)throw Error('이 Galaxy WebView는 무음 녹화를 지원하지 않습니다.');
  await this.callNative('recordStart',[mime]);
  this.recordWrites=Promise.resolve();const recorder=new MediaRecorder(this.stream,{mimeType:mime,videoBitsPerSecond:4000000});this.recorder=recorder;
  recorder.ondataavailable=event=>{if(event.data.size)this.recordWrites=this.recordWrites.then(async()=>{await this.callNative('recordChunk',[b64(await event.data.arrayBuffer())]);}).catch(error=>{void this.stop(error.message);throw error;});};
  this.recordFinished=new Promise((resolve,reject)=>{recorder.onstop=()=>{this.recordWrites.then(()=>this.callNative('recordFinish',[])).then(resolve,reject);};recorder.onerror=()=>reject(Error('Galaxy 녹화가 중단되었습니다.'));});
  // A rejection is consumed even if the viewer disappears before requesting stop.
  this.recordFinished.catch(()=>{});recorder.start(1000);this.recordTimer=setTimeout(()=>{void this.stopRecording().then(()=>{record.dataset.recording='false';this.send({type:'notice',recording:false});}).catch(()=>this.stop('녹화 저장에 실패했습니다.'));},180000);
 }
 async stopRecording(){if(!this.recorder)return;const recorder=this.recorder;this.recorder=null;clearTimeout(this.recordTimer);if(recorder.state!=='inactive')recorder.stop();await this.recordFinished;}
 async start(){
  const c=this.config;if(!['viewer','sender'].includes(c.role)||c.expires<=Date.now())throw Error('만료된 카메라 요청입니다.');
  this.key=await crypto.subtle.importKey('raw',bytes(c.linkKey),'AES-GCM',false,['encrypt','decrypt']);
  this.pc=new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}],bundlePolicy:'max-bundle'});
  this.pc.onicecandidate=event=>{if(event.candidate){if(event.candidate.type==='relay'){void this.stop('직접 연결만 지원합니다.');return;}void this.signal({candidate:event.candidate.toJSON()});}};
  this.pc.onconnectionstatechange=()=>{if(['failed','closed'].includes(this.pc.connectionState))void this.stop('직접 연결을 유지하지 못했습니다.');};
  this.pc.ontrack=event=>{if(c.role!=='viewer'||event.track.kind!=='video'){event.track.stop();void this.stop('허용되지 않은 미디어입니다.');return;}video.srcObject=event.streams[0]??new MediaStream([event.track]);void video.play().catch(()=>{});};
  if(c.role==='viewer'){
   this.pc.addTransceiver('video',{direction:'recvonly'});this.setupChannel(this.pc.createDataChannel('camera-control',{ordered:true}));
   const offer=await this.pc.createOffer();noAudio(offer.sdp);await this.pc.setLocalDescription(offer);await this.signal({description:{type:offer.type,sdp:offer.sdp}});
  }else{this.pc.ondatachannel=event=>this.setupChannel(event.channel);document.querySelector('#controls').hidden=true;}
  this.timers.push(setInterval(()=>{
   if(Date.now()>=c.expires||(!this.connectedAt&&Date.now()-this.started>30000)||(c.role==='sender'&&this.connectedAt&&Date.now()-this.lastLease>8000))void this.stop('연결 확인 시간이 지나 카메라를 종료했습니다.');
  },500));
  this.started=Date.now();
  while(!this.ended){await this.request('poll');await sleep(1000);}
 }
 async stop(reason='카메라 연결을 종료했습니다.'){
  if(this.ended)return;this.ended=true;text(reason);photo.disabled=record.disabled=true;for(const timer of this.timers)clearInterval(timer);for(const timer of this.pending.values())clearTimeout(timer);this.pending.clear();
  this.send({type:'end'});
  const recording=this.stopRecording().catch(()=>{});
  // Release the camera immediately; saving the final recording chunk may finish afterwards.
  this.stream?.getTracks().forEach(track=>{track.onended=null;track.stop();});video.srcObject=null;this.pc?.close();
  void this.request('end').catch(()=>{});
  await Promise.race([recording,sleep(1500)]);native({type:'closed',text:reason});
 }
}
window.cameraNativeResult=(id,ok,message)=>{const pending=session?.nativePending.get(id);if(!pending)return;clearTimeout(pending.timer);session.nativePending.delete(id);if(ok)pending.resolve();else pending.reject(Error(message||'Galaxy 저장 실패'));};
window.startCamera=config=>{if(session)return;session=new CameraSession(config);void session.start().catch(error=>session.stop(error.message||'카메라 연결을 시작하지 못했습니다.'));};
window.closeCamera=()=>session?.stop();
photo.onclick=()=>{void session?.capture('photo');};record.onclick=()=>{void session?.capture(record.dataset.recording==='true'?'recordStop':'recordStart');};
window.addEventListener('pagehide',()=>{void session?.stop();});
})();
