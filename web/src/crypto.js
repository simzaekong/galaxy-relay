(function(root){
  const bytes=text=>{if(typeof text!=='string'||!/^[A-Za-z0-9_-]+$/.test(text))throw new Error('INVALID_CIPHERTEXT');return Uint8Array.from(atob(text.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));};
  async function importKey(text){if(!/^[A-Za-z0-9_-]{43}$/.test(text))throw new Error('INVALID_KEY');const raw=bytes(text);if(raw.length!==32)throw new Error('INVALID_KEY');const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',raw));const keyId=Array.from(digest.slice(0,16),v=>v.toString(16).padStart(2,'0')).join('');const key=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['encrypt','decrypt']);raw.fill(0);return {key,keyId};}
  async function decode(wire,material){
    if(wire?.type!=='encrypted'){if(material)throw new Error('PLAINTEXT_REJECTED');return wire;}
    if(!material||wire.version!==1||wire.keyId!==material.keyId||typeof wire.id!=='string'||!/^evt_[0-9a-f-]{36}$/.test(wire.id)||!Number.isSafeInteger(wire.timestamp)||wire.timestamp>Date.now()+300000||wire.timestamp<Date.now()-86400000||wire.ciphertext?.length>3500||wire.iv?.length!==16)throw new Error('E2EE_KEY_REQUIRED');
    const additionalData=new TextEncoder().encode(['GalaxyRelay/v1',wire.id,wire.timestamp,wire.keyId].join('\n'));
    const plaintext=await crypto.subtle.decrypt({name:'AES-GCM',iv:bytes(wire.iv),additionalData,tagLength:128},material.key,bytes(wire.ciphertext));
    const event=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(plaintext));if(event.id!==wire.id||event.timestamp!==wire.timestamp)throw new Error('INVALID_CIPHERTEXT');return event;
  }
  const encode=raw=>btoa(String.fromCharCode(...raw)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
  function validReply(text){return typeof text==='string'&&text.length<=500&&!!text.trim()&&!/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(text)&&new TextEncoder().encode(text).length<=1500;}
  async function reply(text,actionId,envelope,material){
    if(!validReply(text)||!/^[0-9a-f]{64}$/.test(actionId))throw new Error('INVALID_REPLY');
    if(!material)return {actionId,mode:'plain',text};
    if(!material.key?.usages.includes('encrypt'))throw new Error('E2EE_REPLY_KEY_REQUIRED');
    const iv=crypto.getRandomValues(new Uint8Array(12));
    const additionalData=new TextEncoder().encode(['GalaxyRelay/reply/v1',envelope.commandId,envelope.eventId,envelope.timestamp,envelope.nonce,actionId,material.keyId].join('\n'));
    const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData,tagLength:128},material.key,new TextEncoder().encode(text));
    return {actionId,mode:'encrypted',version:1,keyId:material.keyId,iv:encode(iv),ciphertext:encode(new Uint8Array(ciphertext))};
  }
  root.EventCrypto={importKey,decode,reply,validReply};
})(globalThis);
