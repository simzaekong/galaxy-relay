importScripts('/src/model.js','/src/db.js','/src/crypto.js','/src/notifications.js','/src/state-sync.js');
const CACHE='galaxy-relay-v2';
const STATIC=['/','/index.html','/style.css','/src/app.js','/src/api.js','/src/db.js','/src/model.js','/src/crypto.js','/src/notifications.js','/src/state-sync.js','/src/groups.js','/src/gestures.js','/manifest.json','/icons/icon-192.png','/icons/icon-512.png','/icons/apple-touch-icon.png'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(STATIC)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil((async()=>{for(const name of await caches.keys())if(name.startsWith('galaxy-relay-')&&name!==CACHE)await caches.delete(name);await self.clients.claim();})()));
self.addEventListener('fetch',event=>{const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
  if(event.request.mode==='navigate')event.respondWith((async()=>{if(!self.navigator.onLine){const cached=await caches.match('/');if(cached)return cached;}return fetch(event.request).catch(()=>caches.match('/'));})());
  else if(STATIC.includes(url.pathname))event.respondWith(caches.match(url.pathname).then(cached=>cached||fetch(event.request)));
});
self.addEventListener('push',event=>event.waitUntil((async()=>{
  let data,stored=false;try{data=await EventCrypto.decode(event.data?.json(),await RelayDB.meta('e2ee'));if(!RelayModel.valid(data))throw new Error('INVALID_PUSH');await RelayDB.apply(data);stored=true;}catch{/* still display a visible notification: WebKit requires it */}
  const hidden=await RelayDB.meta('hidePreview').catch(()=>true),hideName=await RelayDB.meta('hideName').catch(()=>true);
  let title='Galaxy Relay',body='새 알림이 도착했습니다.',tag=data?.id||'relay-storage-error';
  if(!stored)body='알림을 저장하지 못했습니다. Relay 암호화 키와 저장 공간을 확인해주세요.';
  else if(data.type==='removed')body='Galaxy에서 알림이 제거되었습니다.';
  else if(data.type==='command_result')body=RelayModel.messages[data.result]||'Galaxy 요청 상태가 변경되었습니다.';
  // Old APKs may still push encrypted control events. Keep those visible until the APK is upgraded; never fake a silent WebKit push.
  const presentation=stored?RelayNotifications.presentation(data,{hidePreview:hidden,hideName}):null;
  await self.registration.showNotification(presentation?.title||title,{body:presentation?.body||body,tag:presentation?.tag||tag,icon:'/icons/icon-192.png',badge:'/icons/icon-192.png',data:presentation?.data||{eventId:data?.targetEventId||data?.id},renotify:false});
  await RelaySync.closeInactive(self.registration);
  if(stored&&!RelayNotifications.control(data))await RelaySync.pull(self.registration);
  for(const client of await self.clients.matchAll({type:'window',includeUncontrolled:true}))client.postMessage({type:'REFRESH',eventId:data?.targetEventId||data?.id});
})()));
self.addEventListener('notificationclick',event=>{event.notification.close();event.waitUntil((async()=>{
  const id=event.notification.data?.eventId;const target=new URL('/',self.location.origin);if(/^evt_[0-9a-f-]{36}$/.test(id||''))target.searchParams.set('event',id);
  for(const client of await self.clients.matchAll({type:'window',includeUncontrolled:true})){if('focus'in client){await client.navigate(target.href);return client.focus();}}
  return self.clients.openWindow(target.href);
})());});
