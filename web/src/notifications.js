(function(root){
  const control=e=>e?.type==='removed'||e?.type==='command_result'||(e?.type==='call'&&e.state!=='ringing');
  function presentation(e,{hidePreview=false,hideName=false}={}){
    if(control(e))return null;
    let title='Galaxy Relay',body='새 알림이 도착했습니다.';
    if(!hidePreview){
      if(e.type==='call'){
        const caller=hideName?'':e.contact||e.number||'';
        title=caller?caller+' · 전화 수신':'전화 수신';
        body=hideName?'Galaxy로 전화가 오고 있습니다.':[e.contact?e.number:'',e.body||'Galaxy로 전화가 오고 있습니다.'].filter(Boolean).join('\n');
      }else{
        const app=e.app||(e.type==='sms'?'문자':'Galaxy'),person=hideName?'':e.title||e.sender||'';
        title=person&&person!==app?person+' · '+app:app;
        body=e.body||'새 알림이 도착했습니다.';
      }
    }
    return {title,body,tag:e.type==='call'?'call:'+(e.callId||e.id):e.id,data:{eventId:e.id,eventType:e.type,...(e.type==='call'?{callId:e.callId||e.id}:{})}};
  }
  root.RelayNotifications={control,presentation};
})(globalThis);
