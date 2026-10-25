(function(root){
  function key(e){const app=e.packageName||e.app||e.type,chat=e.sender||e.contact||e.title||e.number||'';return JSON.stringify([e.type==='call'?'call':'message',app,chat.trim()]);}
  function collect(events){const groups=new Map();for(const event of [...events].sort((a,b)=>b.timestamp-a.timestamp)){const id=key(event);if(!groups.has(id))groups.set(id,{id,events:[]});groups.get(id).events.push(event);}return [...groups.values()];}
  root.RelayGroups={key,collect};
})(globalThis);
