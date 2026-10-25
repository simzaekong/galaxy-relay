(function(root){
  let running,cursor=0;
  async function closeInactive(registration){
    if(!registration?.getNotifications)return;
    for(const n of await registration.getNotifications()){
      // Never turn a legacy control push into show-and-immediately-close silent push.
      if(!['notification','sms','call'].includes(n.data?.eventType))continue;
      const e=n.data?.eventId?await RelayDB.get(n.data.eventId):null;
      const state=await RelayDB.state(n.data?.callId||n.data?.eventId);
      if(e&&(['removed','updated'].includes(e.androidState)||(e.type==='call'&&e.state!=='ringing'))||state?.androidState==='removed'||state?.callState&&state.callState!=='ringing')n.close();
    }
  }
  async function pull(registration,{commandIds=[],eventIds,priorityEventIds=[],callIds:extraCallIds=[]}={}){
    if(running)return running;
    running=(async()=>{
      const credential=await RelayDB.meta('credential');if(!credential||root.navigator?.onLine===false)return 0;
      const records=(await RelayDB.all()).sort((a,b)=>b.timestamp-a.timestamp);
      const pool=eventIds??await RelayDB.syncTargets(),slots=Math.max(0,20-priorityEventIds.length),batch=[];
      for(let i=0;i<Math.min(slots,pool.length);i++)batch.push(pool[(cursor+i)%pool.length]);
      if(eventIds===undefined&&pool.length)cursor=(cursor+slots)%pool.length;
      const ids=[...new Set([...priorityEventIds,...batch])].slice(0,20);
      const callIds=[...new Set([...extraCallIds,...records.filter(e=>e.type==='call'&&e.state!=='ended').map(e=>e.callId||e.id)])].slice(0,10);
      const response=await fetch('/api/sync',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+credential.token},body:JSON.stringify({timestamp:Date.now(),nonce:crypto.randomUUID(),eventIds:[...new Set(ids)].slice(0,20),callIds,commandIds:[...new Set(commandIds)].slice(0,8)}),cache:'no-store',signal:AbortSignal.timeout(8000)});
      if(!response.ok)return 0;
      const value=await response.json();if(!Array.isArray(value.states)||value.states.length>38)return 0;
      let changed=0;
      for(const e of value.states){
        if(!RelayModel.valid(e)||!RelayNotifications.control(e)||['body','title','contact','number','sender'].some(k=>k in e))continue;
        if(e.type==='call'&&!callIds.includes(e.callId)||e.type==='removed'&&!ids.includes(e.targetEventId)||e.type==='command_result'&&!commandIds.includes(e.commandId))continue;
        if(!(await RelayDB.apply(e)).duplicate)changed++;
      }
      await closeInactive(registration);return changed;
    })().catch(()=>0).finally(()=>{running=null;});
    return running;
  }
  root.RelaySync={pull,closeInactive};
})(globalThis);
