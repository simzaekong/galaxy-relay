(function(root){
  const id=/^evt_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
  function valid(e){
    if(!e||typeof e!=='object'||Array.isArray(e)||!id.test(e.id)||!Number.isSafeInteger(e.timestamp)||e.timestamp>Date.now()+300000||e.timestamp<Date.now()-86400000||!['notification','sms','call','removed','command_result'].includes(e.type))return false;
    for(const k of ['title','body','app','sender','contact','number'])if(e[k]!==undefined&&(typeof e[k]!=='string'||e[k].length>600))return false;
    if(e.reply!==undefined&&(!['notification','sms'].includes(e.type)||!e.reply||typeof e.reply!=='object'||!/^[0-9a-f]{64}$/.test(e.reply.actionId)||typeof e.reply.label!=='string'||e.reply.label.length>80))return false;
    if(e.replacesEventId!==undefined&&(!id.test(e.replacesEventId)||e.replacesEventId===e.id||!['notification','sms'].includes(e.type)))return false;
    if(e.targetEventId!==undefined&&!id.test(e.targetEventId))return false;
    if(e.callId!==undefined&&!id.test(e.callId))return false;
    if(e.type==='removed'&&!id.test(e.targetEventId))return false;
    if(e.type==='call'&&!['ringing','offhook','ended'].includes(e.state))return false;
    if(e.type==='command_result'&&(!/^cmd_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(e.commandId)||!['OPEN_SENT','USER_ACTION_REQUIRED','REMOVED','NOT_FOUND','NO_CONTENT_INTENT','PERMISSION_REQUIRED','PONG','SETTINGS_APPLIED','FAILED','REPLY_SENT','REPLY_UNAVAILABLE','REPLY_ACTION_CHANGED','REPLY_REQUIRES_UNLOCK','E2EE_KEY_REQUIRED','INVALID_REPLY'].includes(e.result)))return false;
    return true;
  }
  function merge(previous,event,state){if(previous&&previous.timestamp>event.timestamp)return previous;return {...event,...(state?.callState?{state:state.callState}:{}),read:previous?.timestamp===event.timestamp?!!previous.read:false,androidState:['removed','updated'].includes(state?.androidState)?state.androidState:event.notificationKey?'active':'unavailable'};}
  const messages={REPLY_SENT:'Galaxy의 원본 앱에 답장을 전달했습니다.',REPLY_UNAVAILABLE:'원본 앱의 답장 동작을 사용할 수 없습니다.',REPLY_ACTION_CHANGED:'Galaxy 알림이 갱신되었습니다. 최신 알림에서 답장해주세요.',REPLY_REQUIRES_UNLOCK:'Galaxy 잠금을 해제한 뒤 다시 답장해주세요.',E2EE_KEY_REQUIRED:'양쪽 E2EE 키와 설정을 확인해주세요. 기존 수신 키는 같은 키를 다시 저장하면 답장에도 사용할 수 있습니다.',INVALID_REPLY:'답장은 공백 없이 1~500자로 입력해주세요.',OPEN_SENT:'Galaxy에 원본 알림 열기 요청을 전달했습니다.',USER_ACTION_REQUIRED:'Galaxy의 “iPhone에서 열기 요청” 알림을 눌러주세요.',REMOVED:'Galaxy 알림을 제거했습니다.',NOT_FOUND:'원본 Galaxy 알림이 이미 사라졌습니다.',NO_CONTENT_INTENT:'원본 앱이 이 알림의 열기 동작을 제공하지 않았습니다.',PERMISSION_REQUIRED:'Galaxy Relay의 알림 접근·표시 권한을 확인해주세요.',PONG:'Galaxy가 연결 요청에 응답했습니다.',SETTINGS_APPLIED:'Galaxy에 설정을 반영했습니다.',FAILED:'Galaxy에서 요청을 처리하지 못했습니다.'};
  root.RelayModel={valid,merge,messages};
})(globalThis);
