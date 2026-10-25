import './model.js';
import './db.js';
import './crypto.js';
import './notifications.js';
import './state-sync.js';
import './groups.js';
import {swipeCard} from './gestures.js';
import {api,command,readable} from './api.js';
const $=id=>document.getElementById(id);
$('settings').append($('encryption-controls').content.cloneNode(true));
if('serviceWorker'in navigator&&navigator.serviceWorker.controller){let reloading=false;navigator.serviceWorker.addEventListener('controllerchange',()=>{if(!reloading){reloading=true;location.reload();}});}
let filter='all',selected,preferences={},registration,vapidKey,selectedCommandId,pendingDetailId;
const pendingCommands=new Set();let lastFullSync=0,hasRinging=false;
const expandedGroups=new Set();let openingDetail=false,commandBusy=false,deleting=false,gestureActive=false,renderDeferred=false;
let clockTimer,replyPending=null;
function updateClock(){clearTimeout(clockTimer);const now=new Date();$('clock-date').textContent=`${now.getMonth()+1}월 ${now.getDate()}일 (${['일','월','화','수','목','금','토'][now.getDay()]})`;$('clock-time').textContent=`${now.getHours()}:${String(now.getMinutes()).padStart(2,'0')}`;$('clock-time').dateTime=now.toISOString();if(document.visibilityState==='visible')clockTimer=setTimeout(updateClock,60000-Date.now()%60000);}
updateClock();
function trackCommand(id){if(id){pendingCommands.add(id);while(pendingCommands.size>8)pendingCommands.delete(pendingCommands.values().next().value);}}
function callBody(e){return e.state==='ended'?'통화가 종료되었거나 거절되었습니다.':e.state==='offhook'?'통화가 연결되었습니다.':e.body||'Galaxy로 전화가 오고 있습니다.';}
function callTitle(e){return [preferences.hideName?'':e.contact||e.number||'',({ringing:'전화 수신',offhook:'통화 연결',ended:'전화 종료'}[e.state]||'전화')].filter(Boolean).join(' · ');}
async function syncStatus(force=false){
  if(document.visibilityState!=='visible'||!navigator.onLine||!registration)return;
  const full=force||Date.now()-lastFullSync>=15000;if(!full&&!hasRinging&&!pendingCommands.size)return;if(full)lastFullSync=Date.now();
  await RelaySync.pull(registration,{commandIds:[...pendingCommands],...(full?{}:{eventIds:[]}),priorityEventIds:selected?.androidState==='active'?[selected.id]:[],callIds:selected?.type==='call'?[selected.callId||selected.id]:[]});
  await RelaySync.closeInactive(registration);
  for(const id of pendingCommands)if(await RelayDB.meta('command:'+id))pendingCommands.delete(id);
  await render();await refreshDetail();
}
setInterval(()=>syncStatus().catch(()=>{}),5000);
function notice(text){$('notice').textContent=text;$('notice').hidden=!text;}
function el(tag,text,className){const node=document.createElement(tag);node.textContent=text;if(className)node.className=className;return node;}
function time(timestamp){return new Date(timestamp).toLocaleString('ko-KR',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function appIcon(event){const label=event.app||({call:'전화',sms:'문자'}[event.type]||'Galaxy'),name=(event.packageName||'')+' '+label;let kind=event.type==='call'?'call':event.type==='sms'||/messages|문자/i.test(name)?'sms':/kakao|카카오/i.test(name)?'kakao':/discord/i.test(name)?'discord':/instagram/i.test(name)?'instagram':'app';const icon=el('span',kind==='kakao'?'톡':kind==='app'?label.slice(0,1):'',`app-icon ${kind}`);icon.setAttribute('aria-hidden','true');const paths={call:'M7 3H4a1 1 0 0 0-1 1c0 9 8 17 17 17a1 1 0 0 0 1-1v-3l-5-2-2 2a17 17 0 0 1-7-7l2-2Z',sms:'M5 4h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-5 3v-3H3V6a2 2 0 0 1 2-2 M7 9h10 M7 13h7',discord:'M7 7h10l3 9-4 2-2-2h-4l-2 2-4-2Z M9 11v2 M15 11v2',instagram:'M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M17 7h.01'};if(paths[kind]){const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'),path=document.createElementNS(svg.namespaceURI,'path');svg.setAttribute('viewBox','0 0 24 24');path.setAttribute('d',paths[kind]);svg.append(path);icon.append(svg);}return icon;}
async function loadPreferences(){for(const key of ['hidePreview','hideBody','hideName']){preferences[key]=!!await RelayDB.meta(key);$(key.replace(/[A-Z]/g,c=>'-'+c.toLowerCase())).checked=preferences[key];}}
function card(events,tap){const event=events[0],count=events.length,wrap=el('div','',`swipe-shell ${count>1?'stacked':''}`),button=el('button','',`notification ${event.read?'':'unread'} ${count>1?'grouped':''}`),content=el('span','','notification-content'),top=el('span','','row-top'),stamp=el('time',new Date(event.timestamp).toLocaleTimeString('ko-KR',{hour:'numeric',minute:'2-digit'}));button.type='button';stamp.dateTime=new Date(event.timestamp).toISOString();top.append(el('span',event.app||({call:'전화',sms:'문자'}[event.type]||'Galaxy'),'app-name'),stamp);content.append(top,el('strong',event.type==='call'?callTitle(event):preferences.hideName?'새 알림':event.title||event.contact||event.number||'Galaxy 알림'),el('span',preferences.hideBody?'본문 숨김':event.type==='call'?callBody(event):event.body||event.number||'','preview'));const icon=appIcon(event);if(count>1){icon.append(el('span',String(count),'group-count'));button.setAttribute('aria-expanded','false');button.setAttribute('aria-label',`${event.app||'Galaxy'} ${preferences.hideName?'알림':event.title||event.sender||'알림'} ${count}개 펼치기`);}wrap.append(el('span',count>1?'모두 지우기':'지우기','swipe-delete'),button);wrap.firstChild.setAttribute('aria-hidden','true');button.append(icon,content);if(count>1){const chevron=el('span','›','group-chevron');chevron.setAttribute('aria-hidden','true');button.append(chevron);}swipeCard(button,{tap:()=>Promise.resolve(tap()).catch(()=>notice('알림을 열 수 없습니다. 저장 공간을 확인해주세요.')),remove:()=>dismissEvents(events)});return wrap;}
function dayLabel(timestamp){const date=new Date(timestamp),today=new Date(),yesterday=new Date();yesterday.setDate(today.getDate()-1);return date.toDateString()===today.toDateString()?'오늘':date.toDateString()===yesterday.toDateString()?'어제':date.toLocaleDateString('ko-KR',{month:'long',day:'numeric'});}
async function render(){
  if(gestureActive){renderDeferred=true;return;}renderDeferred=false;
  const all=(await RelayDB.all()).sort((a,b)=>b.timestamp-a.timestamp);if(gestureActive){renderDeferred=true;return;}
  hasRinging=all.some(e=>e.type==='call'&&e.state!=='ended')||selected?.type==='call'&&selected.state!=='ended';
  $('notification-count').textContent='안 읽은 알림 '+all.filter(e=>!e.read).length+'개';$('total-count').textContent=String(all.length);
  const query=$('search').value.toLocaleLowerCase(),rows=all.filter(e=>(filter==='all'||e.type===filter)&&[e.app,e.title,e.body,e.sender,e.contact,e.number].join(' ').toLocaleLowerCase().includes(query));
  $('notifications').replaceChildren();$('empty-state').hidden=rows.length>0;$('empty').hidden=rows.length>0;
  $('empty-title').textContent=all.length?'찾는 알림이 없어요':'알림함이 조용해요';$('empty').textContent=all.length?'다른 검색어나 분류를 선택해보세요.':'새 알림이 오면 여기에 표시됩니다.';
  let previousDay='';
  for(const group of RelayGroups.collect(rows)){
    const dateKey=new Date(group.events[0].timestamp).toDateString();
    if(dateKey!==previousDay){$('notifications').append(el('h3',dayLabel(group.events[0].timestamp),'day-heading'));previousDay=dateKey;}
    if(group.events.length>1&&expandedGroups.has(group.id)){
      const section=el('section','','expanded-group'),head=el('div','','group-header'),collapse=el('button','간략히 보기','group-collapse');
      head.append(el('h3',preferences.hideName?group.events[0].app||'Galaxy':[group.events[0].app,group.events[0].title||group.events[0].sender].filter(Boolean).join(' · ')),collapse);
      collapse.addEventListener('click',()=>{expandedGroups.delete(group.id);render().catch(()=>{});});section.append(head);
      for(const event of group.events)section.append(card([event],()=>showDetail(event.id)));$('notifications').append(section);
    }else $('notifications').append(card(group.events,()=>{if(group.events.length>1){expandedGroups.add(group.id);return render();}return showDetail(group.events[0].id);}));
  }
}
async function dismissEvents(events){if(deleting)return;deleting=true;const detailId=selected?.id;try{const states=await RelayDB.stateRows(),targets=new Map(),local=[];for(const event of events){const state=states.find(s=>s.id===event.id),sourceId=state?.sourceId||event.sourceId;if(state?.androidState==='removed'){local.push(event);continue;}const latest=sourceId?states.filter(s=>s.sourceId===sourceId&&s.androidState==='active').sort((a,b)=>b.timestamp-a.timestamp)[0]:null;if(latest||event.androidState==='active'){const id=latest?.id||event.id,key=sourceId||event.notificationKey||id;if(!targets.has(key))targets.set(key,{id,events:[]});targets.get(key).events.push(event);}else local.push(event);}
    if(targets.size&&!navigator.onLine){notice('오프라인에서는 Galaxy 알림을 함께 삭제할 수 없습니다. 연결 후 다시 밀어주세요.');return;}
    for(const target of targets.values()){const result=await command('dismiss',target.id);trackCommand(result.commandId);await RelayDB.apply({id:'evt_'+crypto.randomUUID(),type:'removed',targetEventId:target.id,timestamp:Date.now()});for(const e of target.events)await RelayDB.consume(e.id);}
    for(const e of local)await RelayDB.consume(e.id);
    if(events.some(e=>e.id===detailId)&&$('detail').open)$('detail').close();if(registration)await RelaySync.closeInactive(registration);await render();
  }catch(e){notice(readable(e));await render();}finally{deleting=false;}}
// Do not replace card nodes under an active finger during background status refresh.
document.addEventListener('pointerdown',e=>{if(e.target.closest('.swipe-shell'))gestureActive=true;});
for(const event of ['pointerup','pointercancel'])document.addEventListener(event,()=>{gestureActive=false;if(renderDeferred)render().catch(()=>{});});
function paintDetail(event){selected=event;$('detail-icon').replaceChildren(appIcon(event));$('detail-title').textContent=event.type==='call'?callTitle(event):preferences.hideName?'Galaxy 알림':event.title||event.contact||event.number||'Galaxy 알림';$('detail-app').textContent=event.app||({call:'전화',sms:'문자'}[event.type]||'Galaxy');$('detail-body').textContent=preferences.hideBody?'본문 숨김':event.type==='call'?callBody(event):event.body||event.number||'';$('detail-time').textContent=time(event.timestamp);$('detail-state').textContent=event.androidState==='removed'?'Galaxy에서 제거됨':event.androidState==='updated'?'Galaxy에서 알림 갱신됨':event.androidState==='active'?'Galaxy 알림 활성':'원본 알림 동작 없음';onlineControls();}
async function refreshDetail(){await refreshReply();if(!selected||!$('detail').open)return;const state=await RelayDB.state(selected.callId||selected.id);if(state)paintDetail({...selected,...(state.androidState?{androidState:state.androidState}:{}),...(state.callState&&selected.type==='call'?{state:state.callState}:{})});const result=selectedCommandId?await RelayDB.meta('command:'+selectedCommandId):null;if(result)$('command-status').textContent=RelayModel.messages[result.result]||'Galaxy 요청 상태가 변경되었습니다.';}
async function showDetail(id){if(document.visibilityState!=='visible'){pendingDetailId=id;return;}if(openingDetail||selected?.id===id&&$('detail').open)return;openingDetail=true;try{const event=await RelayDB.get(id);if(!event)return notice('삭제되었거나 이 기기에 저장되지 않은 알림입니다.');replyPending=null;$('reply-text').value='';paintDetail(event);selectedCommandId=null;$('command-status').textContent='';await refreshReply();if(!$('detail').open)$('detail').showModal();await RelayDB.markRead(id);selected={...selected,read:true};history.replaceState(null,'','/');await render();if(registration?.getNotifications)for(const n of await registration.getNotifications())if(n.data?.eventId===id)n.close();}finally{openingDetail=false;}}
function onlineControls(){replyControls();const available=navigator.onLine&&selected?.androidState==='active'&&!commandBusy&&!replyPending;$('detail-card').setAttribute('aria-disabled',String(!available));$('detail-hint').textContent=!navigator.onLine?'오프라인 · Galaxy 동작은 연결 후 사용할 수 있습니다.':selected?.androidState!=='active'?'원본 Galaxy 알림을 더 이상 열 수 없습니다. 왼쪽으로 밀면 이 기록만 지웁니다.':'한 번 더 누르면 Galaxy에서 열립니다. 왼쪽으로 밀어 지우세요.';for(const id of ['test-command','unpair'])$(id).disabled=!navigator.onLine;$('enable-push').disabled=!navigator.onLine||!registration||!vapidKey;$('pair-form').querySelector('button').disabled=!navigator.onLine;}
async function sendCommand(type){if(replyPending)return;if(openingDetail||commandBusy||!selected)return;if(!navigator.onLine)return notice('오프라인에서는 Galaxy에 명령을 보낼 수 없습니다.');if(selected.androidState!=='active')return;const eventId=selected.id;commandBusy=true;onlineControls();$('command-status').textContent='Galaxy로 요청 보내는 중…';try{const result=await command(type,eventId);trackCommand(result.commandId);if(selected?.id!==eventId)return;selectedCommandId=result.commandId;$('command-status').textContent='Galaxy로 열기 요청을 보냈습니다.';setTimeout(async()=>{if(selectedCommandId===result.commandId&&!await RelayDB.meta('command:'+result.commandId))$('command-status').textContent='Galaxy 응답을 아직 확인하지 못했습니다. Galaxy 인터넷과 알림 권한을 확인해주세요.';},20000);}catch(e){if(selected?.id===eventId)$('command-status').textContent=readable(e);}finally{commandBusy=false;onlineControls();}}
function replyControls(){
  const supported=!!selected?.reply;
  $('reply-form').hidden=!supported;
  $('reply-count').textContent=`${$('reply-text').value.length} / 500`;
  const available=navigator.onLine&&selected?.androidState==='active'&&!commandBusy&&!replyPending;
  $('reply-text').disabled=!available;
  $('reply-send').disabled=!supported||!available||!EventCrypto.validReply($('reply-text').value);
  $('reply-hint').textContent=!supported?'이 알림은 답장 기능을 제공하지 않습니다.':!navigator.onLine?'오프라인에서는 답장을 보낼 수 없습니다.':selected?.androidState!=='active'?'원본이 갱신되거나 사라졌습니다. 최신 알림에서 답장해주세요.':replyPending?'Galaxy 응답을 확인하는 중입니다. 중복 전송을 막기 위해 자동으로 재전송하지 않습니다.':'답장은 Galaxy의 원본 앱을 통해 보냅니다. Galaxy가 잠겨 있으면 잠금을 먼저 해제해주세요.';
}
async function refreshReply(){
  if(!selected)return;
  const id=selected.id,pending=await RelayDB.meta('replyPending:'+id);
  if(selected?.id!==id)return;
  replyPending=pending&&Date.now()-pending.timestamp<300000?pending:null;
  if(pending){
    const result=await RelayDB.meta('command:'+pending.commandId);
    if(selected?.id!==id)return;
    if(result){
      replyPending=null;await RelayDB.setMeta('replyPending:'+id,null);
      if(result.result==='REPLY_SENT')$('reply-text').value='';
      $('command-status').textContent=RelayModel.messages[result.result]||'Galaxy 답장 상태가 변경되었습니다.';
    }else if(replyPending){
      trackCommand(pending.commandId);selectedCommandId=pending.commandId;
      $('command-status').textContent=Date.now()-pending.timestamp>20000?'답장 결과를 아직 확인하지 못했습니다. Galaxy의 원본 앱에서 전송 여부를 확인해주세요.':'Galaxy에 답장을 요청했습니다. 결과 확인 중…';
    }else $('command-status').textContent='이전 답장 결과를 확인하지 못했습니다. 다시 보내기 전에 Galaxy에서 전송 여부를 확인해주세요.';
  }
  onlineControls();
}
$('reply-text').addEventListener('input',replyControls);
$('reply-form').addEventListener('submit',async event=>{
  event.preventDefault();
  if(!selected?.reply||selected.androidState!=='active'||!navigator.onLine||commandBusy||replyPending||!EventCrypto.validReply($('reply-text').value))return;
  const target={...selected},text=$('reply-text').value;
  const envelope={commandId:'cmd_'+crypto.randomUUID(),eventId:target.id,timestamp:Date.now(),nonce:crypto.randomUUID()};
  commandBusy=true;onlineControls();$('command-status').textContent='Galaxy로 답장 보내는 중…';
  let started=false;
  try {
    const reply=await EventCrypto.reply(text,target.reply.actionId,envelope,await RelayDB.meta('e2ee'));
    await RelayDB.beginReply(target.id,{commandId:envelope.commandId,timestamp:envelope.timestamp});
    started=true;replyPending={commandId:envelope.commandId,timestamp:envelope.timestamp};trackCommand(envelope.commandId);
    const response=await command('reply',target.id,{...envelope,reply});
    if(selected?.id===target.id){selectedCommandId=response.commandId;$('command-status').textContent='Galaxy에 답장을 요청했습니다. 결과 확인 중…';}
  }catch(error){
    if(selected?.id===target.id)$('command-status').textContent=readable(error)+(started?' 결과가 불확실하므로 자동 재전송하지 않습니다.':'');
  }finally{commandBusy=false;onlineControls();}
});
function setConnection(text,state='idle'){$('connection').textContent=text;document.querySelector('.connection-strip').dataset.state=state;}
async function refreshStatus(){
  const credential=await RelayDB.meta('credential');$('pair-section').hidden=!!credential;
  if(!navigator.onLine){setConnection(credential?'오프라인 · 연결 등록됨':'Galaxy 연결을 준비하세요','offline');onlineControls();return notice('오프라인 · 기존 알림은 조회할 수 있습니다.');}
  if(!credential){setConnection('Galaxy 연결을 준비하세요');return;}
  try{
    const state=await api('/api/status');setConnection(state.paired?'Galaxy 연결 등록됨':'Galaxy에서 연결 승인 대기',state.paired?'paired':'pending');
    if(!state.paired)notice('Galaxy 앱에서 연결 상태 확인 → 승인 코드를 비교하고 승인해주세요.');else notice('');
    const sub=registration?await registration.pushManager.getSubscription():null;
    $('push-state').textContent=sub?'이 기기에 Push 구독이 있습니다. 필요하면 알림 활성화로 다시 등록하세요.':'알림 활성화를 눌러 Push를 등록하세요.';
  }catch(e){setConnection('연결 상태를 확인할 수 없습니다.','unknown');notice(readable(e));if(!await RelayDB.meta('credential'))$('pair-section').hidden=false;}
}
$('pair-form').addEventListener('submit',async event=>{event.preventDefault();const button=$('pair-form').querySelector('button');button.disabled=true;try{const result=await api('/api/pair/claim',{code:$('pair-code').value.trim(),name:'iPhone PWA'});await RelayDB.setMeta('credential',{token:result.token,deviceId:result.deviceId});$('pair-code').value='';$('pair-section').hidden=true;setConnection('Galaxy 연결 승인 대기','pending');notice('승인 코드: '+result.approvalCode+' · Galaxy 앱에서 같은 코드를 확인하고 승인한 뒤 새로고침하세요.');}catch(e){notice(readable(e));}finally{button.disabled=!navigator.onLine;}});
$('refresh').addEventListener('click',()=>refreshStatus().then(()=>syncStatus(true)).catch(()=>notice('연결 상태를 확인할 수 없습니다.')));
swipeCard($('detail-card'),{tap:()=>sendCommand('open'),remove:()=>selected?dismissEvents([selected]):Promise.resolve()});
$('test-command').addEventListener('click',async()=>{try{trackCommand((await command('ping')).commandId);notice('Galaxy에 연결 테스트를 보냈습니다. 응답은 이 화면에서 확인할 수 있습니다.');}catch(e){notice(readable(e));}});
$('unpair').addEventListener('click',async()=>{if(!confirm('Galaxy 연결과 양쪽 인증 토큰을 폐기할까요?'))return;try{await api('/api/unpair',{});const subscription=registration?await registration.pushManager.getSubscription():null;if(subscription)await subscription.unsubscribe();await RelayDB.setMeta('credential',null);preferences.autoDismiss=false;await RelayDB.setMeta('autoDismiss',false);$('settings').close();await refreshStatus();notice('연결을 해제했습니다. 알림 기록은 기록 삭제에서 별도로 삭제할 수 있습니다.');}catch(e){notice(readable(e));}});
window.addEventListener('offline',()=>{onlineControls();notice('오프라인 · 기존 알림은 조회할 수 있습니다. Galaxy 명령은 비활성화됐습니다.');});window.addEventListener('online',()=>{onlineControls();refreshStatus().then(()=>syncStatus(true)).catch(()=>{});});document.addEventListener('visibilitychange',()=>{updateClock();if(document.visibilityState==='visible'){if(pendingDetailId){const id=pendingDetailId;pendingDetailId=null;showDetail(id).catch(()=>{});}refreshStatus().then(()=>syncStatus(true)).catch(()=>{});}});
document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{filter=button.dataset.filter;for(const item of document.querySelectorAll('[data-filter]'))item.setAttribute('aria-pressed',String(item===button));render().catch(()=>notice('알림을 읽을 수 없습니다.'));}));
$('search').addEventListener('input',()=>render().catch(()=>notice('알림을 읽을 수 없습니다.')));
$('settings-open').addEventListener('click',()=>{$('settings').showModal();});$('settings-close').addEventListener('click',()=>$('settings').close());$('detail-close').addEventListener('click',()=>$('detail').close());$('detail').addEventListener('close',()=>{selected=null;selectedCommandId=null;replyPending=null;$('reply-text').value='';$('detail-body').textContent='';$('detail-title').textContent='';history.replaceState(null,'','/');render().catch(()=>{});});
for(const [key,id]of [['hidePreview','hide-preview'],['hideBody','hide-body'],['hideName','hide-name']])$(id).addEventListener('change',async()=>{preferences[key]=$(id).checked;await RelayDB.setMeta(key,preferences[key]);await render();});
$('enable-push').addEventListener('click',async()=>{
  if(!('PushManager'in window)||!('Notification'in window))return notice('iOS 16.4 이상에서 홈 화면에 추가한 Relay 앱으로 실행해주세요.');
  if(!registration||!vapidKey)return notice('알림 준비 중입니다. 인터넷 연결 후 다시 눌러주세요.');
  const permission=await Notification.requestPermission();if(permission!=='granted')return notice('알림 권한이 허용되지 않았습니다. iPhone 설정에서 Relay 알림을 확인해주세요.');
  try{let subscription=await registration.pushManager.getSubscription();if(!subscription){const bytes=Uint8Array.from(atob(vapidKey.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));subscription=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:bytes});}await api('/api/web/register',{subscription:subscription.toJSON()});$('push-state').textContent='알림 활성화 완료';notice('Galaxy에서 테스트 이벤트를 보내보세요.');}catch(e){notice(readable(e));}
});
$('clear-history').addEventListener('click',async()=>{if(confirm('이 기기에 저장된 모든 알림 기록을 삭제할까요?')){await RelayDB.clearHistory();await render();notice('알림 기록을 삭제했습니다.');}});
async function encryptionState(){const material=await RelayDB.meta('e2ee');$('e2ee-state').textContent=material?'E2EE 수신 사용 · 지문 '+material.keyId:'E2EE 수신 키 없음 · HTTPS 기본 모드';}
$('e2ee-import').addEventListener('click',async()=>{try{const material=await EventCrypto.importKey($('e2ee-key').value.trim());await RelayDB.setMeta('e2ee',material);$('e2ee-key').value='';await encryptionState();notice('Galaxy 키 지문을 비교한 뒤 Galaxy에서 E2EE 전송을 켜세요. 키는 서버에 전송되지 않았습니다.');}catch{notice('Galaxy에 표시된 43자리 암호화 키를 확인해주세요.');}});
$('e2ee-remove').addEventListener('click',async()=>{if(confirm('수신 키를 삭제할까요? Galaxy에서 E2EE를 끄지 않으면 새 알림을 읽을 수 없습니다.')){await RelayDB.setMeta('e2ee',null);await encryptionState();}});
async function bootstrap(){await RelayDB.migrate();$('pair-section').hidden=!!await RelayDB.meta('credential');await loadPreferences();await render();onlineControls();if('serviceWorker'in navigator){registration=await navigator.serviceWorker.register('/service-worker.js',{scope:'/'});registration=await navigator.serviceWorker.ready;navigator.serviceWorker.addEventListener('message',async()=>{try{await render();await refreshDetail();}catch{notice('저장 공간을 확인해주세요.');}});}try{const response=await fetch('/api/config',{cache:'no-store'});if(response.ok)vapidKey=(await response.json()).vapidPublicKey;}catch{}onlineControls();await refreshStatus();await syncStatus(true);const id=new URL(location.href).searchParams.get('event');if(id)await showDetail(id);}
bootstrap().then(encryptionState).catch(()=>notice('Relay를 시작할 수 없습니다. Safari 저장 공간과 인터넷 연결을 확인해주세요.'));
