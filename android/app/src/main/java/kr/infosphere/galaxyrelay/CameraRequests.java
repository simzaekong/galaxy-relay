package kr.infosphere.galaxyrelay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import org.json.JSONObject;

/** Authenticated request, one short-lived session, never a queued camera launch. */
public final class CameraRequests {
 static final String CHANNEL="remote-camera";
 static final int NOTIFICATION=7391;
 public static void request(Context c,String sessionId,String raw){
  JSONObject grant=null;
  try{
   Credentials p=new Credentials(c);JSONObject request=new JSONObject(raw);long now=System.currentTimeMillis();
   if(!p.flag("nativePaired",false)||!p.flag("nativeMode",false)||!p.get("nativeDeviceId","").equals(request.getString("deviceId"))||request.getLong("expires")<now||request.getLong("expires")>now+40000)return;
   grant=new RelayApi(c).call("/api/native/camera/attach",new JSONObject().put("sessionId",sessionId),true);
   if(!p.flag("remoteCameraEnabled",false)||c.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){end(c,grant);return;}
   NotificationManager manager=c.getSystemService(NotificationManager.class);
   manager.createNotificationChannel(new NotificationChannel(CHANNEL,"내 iPhone 카메라 연결",NotificationManager.IMPORTANCE_HIGH));
   Intent intent=new Intent(c,RemoteCameraActivity.class).putExtra("grant",grant.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   PendingIntent open=PendingIntent.getActivity(c,sessionId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_relay).setContentTitle("내 iPhone 카메라 연결 요청").setContentText("자동 연결이 허용되어 있습니다. 화면이 열리지 않으면 눌러 연결하세요.").setCategory(Notification.CATEGORY_CALL).setContentIntent(open).setFullScreenIntent(open,true).setTimeoutAfter(30000).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PUBLIC).build();
   manager.notify(NOTIFICATION,notification);
   // A visible Relay activity can launch immediately. Background launches use the OS call UI.
   if(MainActivity.visible)c.startActivity(intent);
  }catch(Exception ignored){if(grant!=null)end(c,grant);}
 }
 static void end(Context c,JSONObject grant){
  try{JSONObject body=new JSONObject(grant.toString());body.put("op","end").put("after",0);new RelayApi(c).call("/api/camera/session",body,false);}catch(Exception ignored){}
 }
 public static void setup(MainActivity a){
  new AlertDialog.Builder(a).setTitle("내 iPhone 원격 카메라").setMessage("켜두면 현재 연결된 내 iPhone의 요청을 자동 수락합니다. Galaxy 카메라 영상만 전송하며 두 기기 모두 마이크를 사용하지 않습니다. 사진과 무음 동영상은 Galaxy에 저장합니다. 사용 중 화면·알림을 표시하고, iPhone 종료·연결 끊김 시 카메라를 끕니다.")
   .setNegativeButton("닫기",null).setPositiveButton("권한 설정",(d,w)->{
    if(a.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{Manifest.permission.CAMERA},7390);
    else if(Build.VERSION.SDK_INT>=34&&!a.getSystemService(NotificationManager.class).canUseFullScreenIntent())a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,android.net.Uri.parse("package:"+a.getPackageName())));
    else if(Build.VERSION.SDK_INT<=28&&a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},7392);
    else a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())));
   }).show();
 }
}
