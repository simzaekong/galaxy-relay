package kr.infosphere.galaxyrelay;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import androidx.annotation.NonNull;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.*;
/** Android BLE peripheral; one authenticated iPhone central, reliable indications. */
public final class NativeBridge extends Service {
 public static final UUID RX=UUID.fromString("58b80002-a8da-4eae-bba8-50938ca8ca5b"),TX=UUID.fromString("58b80003-a8da-4eae-bba8-50938ca8ca5b"),CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
 public static volatile NativeBridge current;
 private volatile String connectionState="블루투스 준비 중";
 public static String connectionState(){NativeBridge b=current;return b==null?"연결되지 않음":(!b.ready&&"iPhone과 연결됨".equals(b.connectionState)?"연결 확인 필요":b.connectionState);}
 private void state(String value){connectionState=value;NotificationManager m=getSystemService(NotificationManager.class);Notification n=new Notification.Builder(this,"native-ble").setSmallIcon(R.drawable.ic_relay).setContentTitle("Galaxy Relay").setContentText(value).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();m.notify(2201,n);}
 private final PairingSession pairingSession=new PairingSession();private long sessionTicket;
 private final Handler handler=new Handler(Looper.getMainLooper());private final NativeFrames frames=new NativeFrames();
 private RemoteControl remoteControl;
 private final ArrayDeque<byte[]> outgoing=new ArrayDeque<>();private final ConcurrentHashMap<String,CompletableFuture<Boolean>> receipts=new ConcurrentHashMap<>();
 private final ReplayWindow messageIds=new ReplayWindow();
 private int recoveryAttempt,payloadSize=20;private long recoveryTicket,sendTicket;
 private UUID serviceUuid;private long advertisementTicket;
 private final Map<BluetoothDevice,Integer> mtus=new HashMap<>();
 private BluetoothGattServer server;private BluetoothGattCharacteristic tx;private final GattPeer<BluetoothDevice> peers=new GattPeer<>();private BluetoothLeAdvertiser advertiser;
 private final GattSubscription subscription=new GattSubscription();
 private volatile byte[] sessionKey;private String pairId;private volatile boolean ready;private boolean subscribed,indicating;
 private AdvertiseCallback advertising;private DisconnectLocation disconnectLocation;
 public void cancelLocation(){if(disconnectLocation!=null)disconnectLocation.cancel();}
 private void locationForeground(boolean enabled){if(Build.VERSION.SDK_INT>=29){int types=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE|(enabled?android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION:0);try{startForeground(2201,connectionNote(connectionState),types);}catch(RuntimeException e){if(enabled)throw e;}}}
 private Notification connectionNote(String value){return new Notification.Builder(this,"native-ble").setSmallIcon(R.drawable.ic_relay).setContentTitle("Galaxy Relay · 블루투스").setContentText(value).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();}
 private volatile boolean networkSupported;private volatile String peerNetwork="unknown";private volatile long networkAt=-1;
 private final Object networkLock=new Object();private String networkProbe="";private CompletableFuture<Boolean> networkReply;
 public static boolean confirmedOffline(){NativeBridge b=current;if(b==null||!b.ready||!b.networkSupported)return false;long now=SystemClock.elapsedRealtime();
  if(now>=b.networkAt&&now-b.networkAt<=1000)return OfflineAppPolicy.offline(b.ready,b.peerNetwork,b.networkAt,now);
  CompletableFuture<Boolean> reply;String id;boolean owner;
  synchronized(b.networkLock){owner=b.networkReply==null;if(owner){b.networkReply=new CompletableFuture<>();b.networkProbe=UUID.randomUUID().toString();}reply=b.networkReply;id=b.networkProbe;}
  long lease=b.peers.generation();if(owner)b.handler.post(()->{try{if(current!=b||!b.ready||lease!=b.peers.generation()){reply.complete(false);return;}b.send(new JSONObject().put("kind","network_query").put("probeId",id));}catch(Exception e){reply.complete(false);}});
  try{return reply.get(3,TimeUnit.SECONDS)&&current==b&&lease==b.peers.generation()&&OfflineAppPolicy.offline(b.ready,b.peerNetwork,b.networkAt,SystemClock.elapsedRealtime());}catch(Exception e){if(current==b&&lease==b.peers.generation()){b.peerNetwork="unknown";b.networkAt=SystemClock.elapsedRealtime();}return false;}finally{if(owner)synchronized(b.networkLock){if(b.networkReply==reply){b.networkReply=null;b.networkProbe="";}}}
 }
 private void acceptNetwork(JSONObject plain)throws Exception{if(!networkSupported)return;String value=plain.optString("network","unknown");if(!java.util.List.of("online","offline","unknown").contains(value))throw new SecurityException();peerNetwork=value;networkAt=SystemClock.elapsedRealtime();}
 private final LinkHealth health=new LinkHealth();private boolean healthSupported;
 private final Runnable healthTick=this::probeHealth;
 private void probeHealth(){if(!ready||!healthSupported)return;long now=SystemClock.elapsedRealtime();if(health.expired(now)){ready=false;recoverPeer("health-timeout");return;}if(!health.due(now)){handler.postDelayed(healthTick,LinkHealth.INTERVAL);return;}if(health.due(now)){String id=UUID.randomUUID().toString();if(health.begin(id,now)){try{DeviceBattery.refresh(this);send(new JSONObject().put("kind","health_ping").put("probeId",id).put("battery",DeviceBattery.current().json()));}catch(Exception e){recoverPeer("health-send-failed");return;}}}handler.postDelayed(healthTick,LinkHealth.TIMEOUT);}

 private void acceptPeerBattery(JSONObject plain)throws Exception{DeviceBattery.Reading reading=DeviceBattery.parse(plain.getJSONObject("battery"));new Credentials(this).put("nativePeerBattery",new JSONObject().put("pairId",pairId).put("battery",reading.json()).put("reportedAt",plain.getLong("timestamp")).toString());}
 private final BatterySyncPolicy batteryPolicy=new BatterySyncPolicy();private boolean peerBatterySupported;
 private final Runnable batteryDispatch=this::sendBattery;
 private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){DeviceBattery.update(i);queueBattery();}};
 private void queueBattery(){handler.removeCallbacks(batteryDispatch);if(!ready||!peerBatterySupported)return;DeviceBattery.Reading b=DeviceBattery.current();long delay=batteryPolicy.delay(b.level(),b.state(),android.os.SystemClock.elapsedRealtime());if(delay>=0)handler.postDelayed(batteryDispatch,delay);}
 private void sendBattery(){if(!ready||!peerBatterySupported)return;DeviceBattery.Reading b=DeviceBattery.current();try{send(new JSONObject().put("kind","device_status").put("battery",b.json()));batteryPolicy.sent(b.level(),b.state(),android.os.SystemClock.elapsedRealtime());}catch(Exception ignored){}}

 private AdvertiseCallback advertisingCallback(long ticket,long advertisement){return new AdvertiseCallback(){
 @Override public void onStartSuccess(AdvertiseSettings settings){handler.post(()->{if(!pairingSession.current(ticket)||advertisement!=advertisementTicket)return;if(peers.current()==null)state("iPhone 연결 대기 중");new Credentials(NativeBridge.this).remove("nativeError");});}
 @Override public void onStartFailure(int error){handler.post(()->{if(!pairingSession.current(ticket)||advertisement!=advertisementTicket)return;state("블루투스 연결 준비 실패");new Credentials(NativeBridge.this).put("nativeError","블루투스를 확인하고 연결을 다시 시작해주세요.");scheduleRecovery("advertise-"+error);});}
 };}
 public static boolean connecting(){NativeBridge b=current;return b!=null&&b.peers.current()!=null&&!b.ready;}
 public static boolean detected(String expectedPair){NativeBridge b=current;return b!=null&&b.pairingSession.authenticated(expectedPair)&&b.peers.current()!=null;}
 public static boolean ready(){return current!=null&&current.ready;}
 public static boolean deliver(JSONObject event){return deliver(event,12);}
 public static boolean deliver(JSONObject event,int timeoutSeconds){
  NativeBridge bridge=current;if(bridge==null||!bridge.ready)return false;
  CompletableFuture<Boolean> candidate=new CompletableFuture<>();String id=event.optString("id");long lease=bridge.peers.generation();
  CompletableFuture<Boolean> previous=bridge.receipts.putIfAbsent(id,candidate);final CompletableFuture<Boolean> receipt=previous==null?candidate:previous;boolean owner=previous==null;
  if(owner)bridge.handler.post(()->{try{if(current!=bridge||!bridge.ready||lease!=bridge.peers.generation()){receipt.complete(false);return;}if(event.optBoolean("offlineOnly",false)&&!OfflineAppPolicy.offline(bridge.ready,bridge.peerNetwork,bridge.networkAt,SystemClock.elapsedRealtime())){receipt.complete(false);return;}bridge.send(new JSONObject().put("kind","event").put("event",event));}catch(Exception e){receipt.complete(false);}});
  try{return receipt.get(timeoutSeconds,TimeUnit.SECONDS);}catch(Exception e){if(owner)bridge.handler.post(()->{if(current==bridge&&bridge.ready&&lease==bridge.peers.generation())bridge.recoverPeer("event-ack-timeout");});return false;}finally{if(owner)bridge.receipts.remove(id,receipt);}
 }
 @Override public void onCreate(){super.onCreate();current=this;disconnectLocation=new DisconnectLocation(this,this::locationForeground);registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(adapterState,filter,Context.RECEIVER_EXPORTED);else registerReceiver(adapterState,filter);}
 private final BroadcastReceiver adapterState=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
  int next=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
  if(next==BluetoothAdapter.STATE_OFF||next==BluetoothAdapter.STATE_TURNING_OFF){recoveryTicket++;if(ready)disconnectLocation.lost();closeGatt();trace("adapter-off");state("블루투스 꺼짐 · 켜지면 자동 재연결");}
  else if(next==BluetoothAdapter.STATE_ON&&new Credentials(c).flag("nativeMode",false)){trace("adapter-on");scheduleRecovery("adapter-on");}
 }};
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(!new Credentials(this).flag("nativeMode",false)||new Credentials(this).get("nativePairId","").isEmpty()){stopSelf();return START_NOT_STICKY;}
  NotificationManager m=getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel("native-ble","iPhone 블루투스 연결",NotificationManager.IMPORTANCE_LOW));
  Notification note=new Notification.Builder(this,"native-ble").setSmallIcon(R.drawable.ic_relay).setContentTitle("Galaxy Relay · 블루투스").setContentText(connectionState()).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();
  if(Build.VERSION.SDK_INT>=29)startForeground(2201,note,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(2201,note);trace(intent==null?"service-restarted":"service-start");
  try{start();}catch(SecurityException e){trace("permission-revoked");stopSelf();return START_NOT_STICKY;}catch(Exception e){scheduleRecovery("start-failed");}return START_STICKY;
 }
 private void start(){Credentials p=new Credentials(this);if(server!=null&&p.get("nativePairId","").equals(pairId))return;closeGatt();state("블루투스 준비 중");pairId=p.get("nativePairId","");if(pairId.isEmpty())throw new IllegalStateException();
  BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager.getAdapter();if(adapter==null||!adapter.isMultipleAdvertisementSupported())throw new SecurityException();if(!adapter.isEnabled()){state("블루투스 꺼짐 · 켜지면 자동 재연결");return;}
  try{sessionTicket=pairingSession.begin(pairId);server=manager.openGattServer(this,callbacks(sessionTicket));if(server==null)throw new IllegalStateException();serviceUuid=UUID.fromString(p.get("nativeServiceUUID",""));BluetoothGattService service=new BluetoothGattService(serviceUuid,BluetoothGattService.SERVICE_TYPE_PRIMARY);
  tx=new BluetoothGattCharacteristic(TX,BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ);
  BluetoothGattDescriptor cccd=new BluetoothGattDescriptor(CCCD,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);tx.addDescriptor(cccd);
  service.addCharacteristic(tx);service.addCharacteristic(new BluetoothGattCharacteristic(RX,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE));if(!server.addService(service))throw new IllegalStateException();}catch(SecurityException e){stopSelf();}
 }
 private BluetoothGattServerCallback callbacks(long ticket){return new BluetoothGattServerCallback(){
  @Override public void onServiceAdded(int status,BluetoothGattService service){handler.post(()->{
   if(!pairingSession.current(ticket))return;if(status!=BluetoothGatt.GATT_SUCCESS){scheduleRecovery("service-"+status);return;}
   advertise(ticket);
  });}
  @Override public void onMtuChanged(BluetoothDevice d,int mtu){handler.post(()->{if(!pairingSession.current(ticket))return;if(mtus.size()<8||mtus.containsKey(d))mtus.put(d,BleRecovery.payload(mtu));if(peers.matches(d))payloadSize=BleRecovery.payload(mtu);});}
  @Override public void onConnectionStateChange(BluetoothDevice device,int status,int next){handler.post(()->{
   if(!pairingSession.current(ticket))return;
   if(next==BluetoothProfile.STATE_DISCONNECTED)mtus.remove(device);
   // BLE transport connection does not establish ownership of the Relay service.
   // Other centrals and delayed callbacks must not occupy the iPhone slot.
   if(next==BluetoothProfile.STATE_DISCONNECTED&&peers.matches(device)){
    trace("link-disconnected-"+status);releaseClient(device);state("연결 끊김 · 자동 재연결 대기");
   }
  });}
  @Override public void onDescriptorReadRequest(BluetoothDevice d,int request,int offset,BluetoothGattDescriptor descriptor){handler.post(()->{
   if(!pairingSession.current(ticket))return;
   if(!isCccd(descriptor)){respond(d,request,BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);return;}
   try{respond(d,request,BluetoothGatt.GATT_SUCCESS,offset,(peers.matches(d)?subscription:new GattSubscription()).read(offset));}
   catch(IllegalArgumentException e){respond(d,request,BluetoothGatt.GATT_INVALID_OFFSET);}
  });}
  @Override public void onCharacteristicReadRequest(BluetoothDevice d,int request,int offset,BluetoothGattCharacteristic characteristic){handler.post(()->{
   if(!pairingSession.current(ticket))return;
   if(!TX.equals(characteristic.getUuid())){respond(d,request,BluetoothGatt.GATT_READ_NOT_PERMITTED);return;}
   respond(d,request,offset==0?BluetoothGatt.GATT_SUCCESS:BluetoothGatt.GATT_INVALID_OFFSET,offset,new byte[0]);
  });}
  @Override public void onDescriptorWriteRequest(BluetoothDevice d,int request,BluetoothGattDescriptor descriptor,boolean prepared,boolean response,int offset,byte[] value){
   // Binder owns callback arguments. Keep a private snapshot across the main-thread handoff.
   byte[] snapshot=value==null?null:value.clone();
   handler.post(()->{
    if(!pairingSession.current(ticket))return;
    boolean matches=isCccd(descriptor),disable=snapshot!=null&&snapshot.length==2&&snapshot[0]==0&&snapshot[1]==0;
    boolean samePeer=disable||peers.current()==null||peers.matches(d);
    String rejection=GattSubscription.rejection(matches,samePeer,snapshot,prepared,offset);
    int status=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
    if(rejection.isEmpty()){
     // Disabling an unowned subscription must neither acquire nor reset another peer.
     if(disable&&!peers.matches(d)){if(response)respond(d,request,BluetoothGatt.GATT_SUCCESS);return;}
     if(!bindClient(d,ticket)){if(response)respond(d,request,BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);return;}
     GattSubscription.Result result=subscription.write(snapshot,prepared,offset);
     if(result==GattSubscription.Result.OK){
      status=BluetoothGatt.GATT_SUCCESS;descriptor.setValue(snapshot);subscribed=subscription.enabled();
      if(subscribed){if(!ready)state("iPhone 응답 수신 연결됨 · 인증 대기");}
      else{releaseClient(d);state("iPhone 응답 수신이 중지됨 · 재연결 대기");}
     }
    }else if("G03-O".equals(rejection))status=BluetoothGatt.GATT_INVALID_OFFSET;
    else if("G03-L".equals(rejection))status=BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
    boolean sent=!response||respond(d,request,status);
    if(!sent&&rejection.isEmpty()){rejection="G03-R";releaseClient(d);disconnect(d);}
    if(!rejection.isEmpty()){
     String detail=rejection+" · descriptor="+(descriptor!=null&&CCCD.equals(descriptor.getUuid()))
      +" · tx="+(descriptor!=null&&descriptor.getCharacteristic()!=null&&TX.equals(descriptor.getCharacteristic().getUuid()))
      +" · peer="+samePeer+" · prepared="+prepared+" · offset="+offset+" · length="+(snapshot==null?-1:snapshot.length)
      +" · response="+response+" · sent="+sent+" · status="+status;
     // Do not log addresses, QR material, authentication frames or notification content.
     new Credentials(NativeBridge.this).put("nativeGattFailure",detail);
     state("iPhone 응답 수신 설정 실패 ("+rejection+")");
    }
   });
  }
  @Override public void onCharacteristicWriteRequest(BluetoothDevice d,int request,BluetoothGattCharacteristic characteristic,boolean prepared,boolean response,int offset,byte[] value){handler.post(()->{if(!pairingSession.current(ticket))return;boolean ok=d.equals(peers.current())&&RX.equals(characteristic.getUuid())&&!prepared&&offset==0&&subscribed;try{if(ok){byte[] complete=frames.feed(value,System.currentTimeMillis());if(complete!=null)receive(new JSONObject(new String(complete,StandardCharsets.UTF_8)));}}catch(Exception e){ok=false;recoverPeer("authentication-failed");}if(response)respond(d,request,ok?BluetoothGatt.GATT_SUCCESS:BluetoothGatt.GATT_FAILURE);});}
  @Override public void onNotificationSent(BluetoothDevice d,int status){handler.post(()->{if(!pairingSession.current(ticket)||!d.equals(peers.current()))return;indicating=false;sendTicket++;if(status!=BluetoothGatt.GATT_SUCCESS){recoverPeer("notify-"+status);return;}drain();});}
 };}
 private void receive(JSONObject wire)throws Exception{
  if("hello".equals(wire.optString("kind"))){
   if(!pairId.equals(wire.optString("pairId"))||sessionKey!=null)throw new SecurityException();
   state("보안 연결 설정 중");Credentials p=new Credentials(this);boolean bootstrap=!p.flag("nativePaired",false);
   if(bootstrap&&Long.parseLong(p.get("nativeQrExpires","0"))<System.currentTimeMillis())throw new SecurityException();
   String root=bootstrap?p.secret("nativeBootstrap"):p.secret("nativeLinkKey"),nonce=wire.getString("nonce"),pub=wire.getString("publicKey");
   if(NativeCrypto.bytes(nonce).length!=32||!NativeCrypto.verify(root,"hello\n"+pairId+"\n"+nonce+"\n"+pub,wire.getString("proof")))throw new SecurityException();
   KeyPair local=NativeCrypto.pair();String serverNonce=NativeCrypto.b64(NativeCrypto.random(32)),serverPub=NativeCrypto.publicKey(local),transcript="welcome\n"+pairId+"\n"+nonce+"\n"+serverNonce+"\n"+pub+"\n"+serverPub;
   sessionKey=NativeCrypto.hkdf(NativeCrypto.shared(local,pub),NativeCrypto.mac(NativeCrypto.bytes(root),transcript.getBytes(StandardCharsets.UTF_8)),"GalaxyRelay BLE v2 session");
   enqueue(new JSONObject().put("kind","welcome").put("nonce",serverNonce).put("publicKey",serverPub).put("proof",NativeCrypto.proof(root,transcript)));
   send(NativeCloud.config(this));if(!pairingSession.authenticate(sessionTicket,pairId))throw new SecurityException();return;
  }
  if(sessionKey==null)throw new SecurityException();
  JSONObject plain=new JSONObject(new String(NativeCrypto.open(sessionKey,wire.getString("iv"),wire.getString("ciphertext"),NativeCrypto.wireAAD(pairId,"ios-to-android")),StandardCharsets.UTF_8));
  String messageId=plain.getString("messageId");if(!messageId.matches("[0-9a-f-]{36}")||Math.abs(System.currentTimeMillis()-plain.getLong("timestamp"))>120000||!messageIds.accept(messageId,plain.getLong("timestamp"),System.currentTimeMillis()))throw new SecurityException();
  switch(plain.getString("kind")){
   case "remote_request":{
    if(!ready||System.currentTimeMillis()-plain.getLong("timestamp")>10000)throw new SecurityException();
    if(remoteControl==null)remoteControl=new RemoteControl(this);long lease=peers.generation();
    remoteControl.request(plain,value->{if(ready&&lease==peers.generation())try{send(value);}catch(Exception ignored){}});break;
   }
   case "ready":{Credentials saved=new Credentials(this);saved.setFlag("nativePaired",true);saved.remove("nativeBootstrap");if(!saved.flush())throw new java.io.IOException("Pairing persistence failed");ready=true;disconnectLocation.connected();recoveryAttempt=0;trace("authenticated");saved.remove("nativeGattFailure");send(new JSONObject().put("kind","connected").put("pairId",pairId));state("iPhone과 연결됨");JSONArray capabilities=plain.optJSONArray("capabilities");peerBatterySupported=false;if(capabilities!=null)for(int i=0;i<capabilities.length();i++)if("battery_v1".equals(capabilities.optString(i)))peerBatterySupported=true;batteryPolicy.reset();queueBattery();healthSupported=false;if(capabilities!=null)for(int i=0;i<capabilities.length();i++)if("health_v1".equals(capabilities.optString(i)))healthSupported=true;networkSupported=false;if(capabilities!=null)for(int i=0;i<capabilities.length();i++)if("network_v1".equals(capabilities.optString(i)))networkSupported=true;health.reset(SystemClock.elapsedRealtime());handler.removeCallbacks(healthTick);if(healthSupported)handler.postDelayed(healthTick,LinkHealth.INTERVAL);RelayQueue.kick(this,0);break;}
   case "health_ping":case "health_pong":{
    if(!ready||!healthSupported)throw new SecurityException();String id=plain.getString("probeId");UUID.fromString(id);acceptPeerBattery(plain);acceptNetwork(plain);new Credentials(this).put("nativeHealthAt",String.valueOf(System.currentTimeMillis()));
    if("health_ping".equals(plain.getString("kind"))){DeviceBattery.refresh(this);send(new JSONObject().put("kind","health_pong").put("probeId",id).put("battery",DeviceBattery.current().json()));}
    else if(health.acknowledge(id,SystemClock.elapsedRealtime())){handler.removeCallbacks(healthTick);handler.postDelayed(healthTick,LinkHealth.INTERVAL);new Credentials(this).put("nativeHealthAt",String.valueOf(System.currentTimeMillis()));RelayQueue.kick(this,0);}break;
   }
   case "network_status":{if(!ready||!networkSupported)throw new SecurityException();acceptNetwork(plain);String probe=plain.optString("probeId","");if(!probe.isEmpty())UUID.fromString(probe);synchronized(networkLock){if(networkReply!=null&&networkProbe.equals(probe))networkReply.complete("offline".equals(peerNetwork));}break;}
   case "device_status":{
    if(!ready||!peerBatterySupported)throw new SecurityException();JSONObject battery=plain.getJSONObject("battery");DeviceBattery.Reading reading=DeviceBattery.parse(battery);
    new Credentials(this).put("nativePeerBattery",new JSONObject().put("pairId",pairId).put("battery",reading.json()).put("reportedAt",plain.getLong("timestamp")).toString());break;
   }
   case "ack":CompletableFuture<Boolean> receipt=receipts.get(plain.getString("eventId"));if(receipt!=null)receipt.complete(true);break;
   case "command":if(!ready)throw new SecurityException();JSONObject cmd=plain.getJSONObject("command");Map<String,String> fields=new HashMap<>();for(Iterator<String> keys=cmd.keys();keys.hasNext();){String key=keys.next();fields.put(key,cmd.getString(key));}CommandHandler.receiveBle(this,fields);break;
   default:throw new SecurityException();
  }
 }
 private void send(JSONObject plain)throws Exception{if(sessionKey==null)throw new SecurityException();plain.put("messageId",UUID.randomUUID().toString()).put("timestamp",System.currentTimeMillis());NativeCrypto.Sealed sealed=NativeCrypto.seal(sessionKey,plain.toString().getBytes(StandardCharsets.UTF_8),NativeCrypto.wireAAD(pairId,"android-to-ios"));enqueue(new JSONObject().put("iv",sealed.iv).put("ciphertext",sealed.ciphertext));}
 private void enqueue(JSONObject value){long ticket=sessionTicket,lease=peers.generation();byte[] data=NativeFrames.pack(value.toString().getBytes(StandardCharsets.UTF_8));handler.post(()->{
  if(!pairingSession.current(ticket)||lease!=peers.generation())return;int chunk=payloadSize;
  if(outgoing.size()+(data.length+chunk-1)/chunk>4096){recoverPeer("queue-full");return;}
  for(int i=0;i<data.length;i+=chunk)outgoing.add(Arrays.copyOfRange(data,i,Math.min(data.length,i+chunk)));drain();
 });}
 private void drain(){
  BluetoothDevice peer=peers.current();if(indicating||peer==null||!subscribed||outgoing.isEmpty())return;
  byte[] bytes=outgoing.remove();indicating=true;long send=++sendTicket,lease=peers.generation();
  try{
   boolean ok;if(Build.VERSION.SDK_INT>=33)ok=server.notifyCharacteristicChanged(peer,tx,subscription.confirm(),bytes)==BluetoothStatusCodes.SUCCESS;
   else{tx.setValue(bytes);ok=server.notifyCharacteristicChanged(peer,tx,subscription.confirm());}
   if(!ok){recoverPeer("notify-rejected");return;}
   handler.postDelayed(()->{if(send==sendTicket&&indicating&&peers.current(peer,lease))recoverPeer("notify-timeout");},15000);
  }catch(SecurityException e){trace("permission-revoked");stopSelf();}catch(Exception e){recoverPeer("notify-failed");}
 }
 private void advertise(long ticket){
  if(!pairingSession.current(ticket)||peers.current()!=null)return;
  try{
   if(advertiser!=null&&advertising!=null)advertiser.stopAdvertising(advertising);
   advertiser=getSystemService(BluetoothManager.class).getAdapter().getBluetoothLeAdvertiser();
   if(advertiser==null){scheduleRecovery("advertiser-unavailable");return;}
   advertising=advertisingCallback(ticket,++advertisementTicket);
   advertiser.startAdvertising(new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).setConnectable(true).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build(),new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(serviceUuid)).build(),advertising);
  }catch(SecurityException e){trace("permission-revoked");stopSelf();}catch(Exception e){scheduleRecovery("advertise-failed");}
 }
 private void recoverPeer(String reason){BluetoothDevice peer=peers.current();trace(reason);if(peer!=null){releaseClient(peer);disconnect(peer);}state("연결 복구 중 · 자동 재연결");}
 private void scheduleRecovery(String reason){
  trace(reason);if(ready)disconnectLocation.lost();closeGatt();long retry=++recoveryTicket;long delay=BleRecovery.delay(recoveryAttempt++);state("블루투스 복구 중 · 자동 재시도");
  handler.postDelayed(()->{if(retry!=recoveryTicket||!new Credentials(this).flag("nativeMode",false))return;try{start();}catch(SecurityException e){trace("permission-revoked");stopSelf();}catch(Exception e){scheduleRecovery("start-failed");}},delay);
 }
 private void trace(String code){Credentials p=new Credentials(this);String previous=p.get("nativeBleTrace","");String[] lines=(previous+System.currentTimeMillis()+" "+code+"\n").split("\n");p.put("nativeBleTrace",String.join("\n",Arrays.copyOfRange(lines,Math.max(0,lines.length-16),lines.length))+"\n");}
 private boolean isCccd(BluetoothGattDescriptor descriptor){return descriptor!=null&&CCCD.equals(descriptor.getUuid())&&descriptor.getCharacteristic()!=null&&TX.equals(descriptor.getCharacteristic().getUuid());}
 private boolean bindClient(BluetoothDevice device,long ticket){
  if(peers.current()!=null)return peers.matches(device);
  if(!peers.claim(device))return false;
  resetTransport();payloadSize=mtus.getOrDefault(device,20);state("iPhone 연결 확인 중");long lease=peers.generation();
  handler.postDelayed(()->{
   if(pairingSession.current(ticket)&&peers.current(device,lease)&&!ready){
    // Release synchronously: the OS disconnect callback may be delayed or missing.
    recoverPeer("handshake-timeout");
   }
  },30000);return true;
 }
 private void releaseClient(BluetoothDevice device){if(peers.release(device)){if(ready)disconnectLocation.lost();resetTransport();payloadSize=20;long ticket=sessionTicket;handler.postDelayed(()->{if(pairingSession.current(ticket)&&peers.current()==null)advertise(ticket);},500);}}
 private void resetTransport(){networkSupported=false;peerNetwork="unknown";networkAt=-1;synchronized(networkLock){if(networkReply!=null)networkReply.complete(false);networkReply=null;networkProbe="";}handler.removeCallbacks(healthTick);healthSupported=false;health.reset(SystemClock.elapsedRealtime());handler.removeCallbacks(batteryDispatch);batteryPolicy.reset();peerBatterySupported=false;
  if(remoteControl!=null)remoteControl.close();pairingSession.clearAuthentication();subscription.reset();ready=false;subscribed=false;sessionKey=null;
  frames.reset();messageIds.clear();outgoing.clear();indicating=false;sendTicket++;
  for(var receipt:receipts.values())receipt.complete(false);
 }
 private void disconnect(BluetoothDevice d){try{if(server!=null)server.cancelConnection(d);}catch(SecurityException e){stopSelf();}}
 private boolean respond(BluetoothDevice d,int id,int status){return respond(d,id,status,0,null);}
 private boolean respond(BluetoothDevice d,int id,int status,int offset,byte[] value){try{return server!=null&&server.sendResponse(d,id,status,offset,value);}catch(SecurityException e){stopSelf();return false;}}
 private void closeGatt(){pairingSession.invalidate();advertisementTicket++;mtus.clear();payloadSize=20;peers.clear();resetTransport();if(advertiser!=null)try{advertiser.stopAdvertising(advertising);}catch(SecurityException ignored){}if(server!=null)try{server.close();}catch(SecurityException ignored){}server=null;advertiser=null;}
 @Override public void onDestroy(){if(disconnectLocation!=null)disconnectLocation.stop();trace("service-destroyed");recoveryTicket++;unregisterReceiver(adapterState);unregisterReceiver(batteryReceiver);closeGatt();handler.removeCallbacksAndMessages(null);for(var f:receipts.values())f.complete(false);if(current==this)current=null;super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}

