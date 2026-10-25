package kr.infosphere.galaxyrelay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Explicit remote request only. No location history, periodic work or plaintext upload. */
public final class RemoteLocateService extends Service {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LocationManager manager;private CancellationSignal cancellation;private LocationListener listener;
 private String requestId,grant,pairId;private long expires;private boolean finished;
 static boolean allowed(Context c){Credentials p=new Credentials(c);return p.flag("nativeMode",false)&&p.flag("nativePaired",false)&&p.flag("remoteLocationEnabled",false);}
 static boolean permitted(Context c){return (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)&&(Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED);}
 public static void request(Context c,String id,String raw)throws Exception{
  JSONObject g=new JSONObject(raw);if(!g.optString("deviceId").equals(new Credentials(c).get("nativeDeviceId","")))return;long expiration=g.getLong("expires");if(expiration<System.currentTimeMillis()||expiration>System.currentTimeMillis()+120000)return;
  if(!allowed(c)){upload(c,id,g,"DISABLED",null);return;}
  if(!permitted(c)){upload(c,id,g,"PERMISSION_REQUIRED",null);return;}
  Intent i=new Intent(c,RemoteLocateService.class).putExtra("requestId",id).putExtra("grant",g.getString("grant")).putExtra("expires",expiration);
  try{c.startForegroundService(i);}catch(RuntimeException e){upload(c,id,g,"BACKGROUND_RESTRICTED",null);}
 }
 private static void upload(Context c,String id,JSONObject g,String status,Location location){upload(c,id,g,status,location,()->{});}
 private static void upload(Context c,String id,JSONObject g,String status,Location location,Runnable done){
  Context app=c.getApplicationContext();Credentials p=new Credentials(app);String pair=p.get("nativePairId",""),device=p.get("nativeDeviceId",""),key=p.secret("nativeLinkKey"),token=p.secret("token");
  RelayQueue.IO.execute(()->{try{
   Credentials current=new Credentials(app);if(!current.flag("nativePaired",false)||!pair.equals(current.get("nativePairId",""))||g.getLong("expires")<System.currentTimeMillis())return;
   JSONObject value=new JSONObject().put("requestId",id).put("timestamp",System.currentTimeMillis()).put("status",status);
   if(location!=null&&allowed(app)&&permitted(app))value.put("latitude",location.getLatitude()).put("longitude",location.getLongitude()).put("accuracy",location.getAccuracy()).put("measuredAt",location.getTime());
   else if(location!=null)value.put("status","DISABLED");
   NativeCrypto.Sealed sealed=NativeCrypto.seal(NativeCrypto.bytes(key),value.toString().getBytes(StandardCharsets.UTF_8),NativeCrypto.locationAAD(id,device));
   new RelayApi(app).callWithToken("/api/native/locate/upload",new JSONObject().put("requestId",id).put("grant",g.getString("grant")).put("expires",g.getLong("expires")).put("packet",new JSONObject().put("version",2).put("iv",sealed.iv).put("ciphertext",sealed.ciphertext)),token);
  }catch(Exception ignored){/* No durable location queue or retry after expiry. */}finally{new Handler(Looper.getMainLooper()).post(done);}});
 }
 @Override public int onStartCommand(Intent i,int flags,int startId){
  if(i==null){stopSelf();return START_NOT_STICKY;}
  if(requestId!=null){try{upload(this,i.getStringExtra("requestId"),new JSONObject().put("grant",i.getStringExtra("grant")).put("expires",i.getLongExtra("expires",0)),"BUSY",null);}catch(Exception ignored){}return START_NOT_STICKY;}
  requestId=i.getStringExtra("requestId");grant=i.getStringExtra("grant");expires=i.getLongExtra("expires",0);pairId=new Credentials(this).get("nativePairId","");
  try{
   NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("remote-location","iPhone에서 기기 찾기",NotificationManager.IMPORTANCE_LOW));
   Notification n=new Notification.Builder(this,"remote-location").setSmallIcon(R.drawable.ic_relay).setContentTitle("iPhone에서 Galaxy 위치 확인 중").setContentText("요청에 따라 위치를 한 번 조회합니다.").setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();
   if(Build.VERSION.SDK_INT>=29)startForeground(2203,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(2203,n);
   if(!allowed(this)||!permitted(this)||expires<System.currentTimeMillis()){finish("PERMISSION_REQUIRED",null);return START_NOT_STICKY;}
   manager=getSystemService(LocationManager.class);
   String provider=manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:null;
   if(provider==null){finish("LOCATION_OFF",null);return START_NOT_STICKY;}
   handler.postDelayed(()->finish("NO_FIX",null),20000);
   if(Build.VERSION.SDK_INT>=30){cancellation=new CancellationSignal();manager.getCurrentLocation(provider,cancellation,getMainExecutor(),this::received);}
   else{listener=new LocationListener(){@Override public void onLocationChanged(Location l){received(l);}@Override public void onStatusChanged(String p,int s,Bundle b){}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){finish("LOCATION_OFF",null);}};manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());}
  }catch(SecurityException e){finish("PERMISSION_REQUIRED",null);}catch(RuntimeException e){finish("BACKGROUND_RESTRICTED",null);}
  return START_NOT_STICKY;
 }
 private void received(Location l){
  if(l==null||!l.hasAccuracy()||!Double.isFinite(l.getLatitude())||Math.abs(l.getLatitude())>90||!Double.isFinite(l.getLongitude())||Math.abs(l.getLongitude())>180||!Float.isFinite(l.getAccuracy())||l.getAccuracy()<0||Math.abs(System.currentTimeMillis()-l.getTime())>30000){finish("NO_FIX",null);return;}
  finish("OK",l);
 }
 private void finish(String status,Location l){
  if(finished)return;finished=true;cleanup();handler.postDelayed(this::uploaded,35000);
  try{if(pairId.equals(new Credentials(this).get("nativePairId",""))){upload(this,requestId,new JSONObject().put("grant",grant).put("expires",expires),status,l,this::uploaded);return;}}catch(Exception ignored){}
  uploaded();
 }
 private void uploaded(){handler.removeCallbacksAndMessages(null);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 private void cleanup(){handler.removeCallbacksAndMessages(null);if(cancellation!=null)cancellation.cancel();if(listener!=null&&manager!=null)try{manager.removeUpdates(listener);}catch(SecurityException ignored){}listener=null;}
 @Override public void onDestroy(){finished=true;cleanup();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
