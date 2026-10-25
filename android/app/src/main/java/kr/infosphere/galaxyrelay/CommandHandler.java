package kr.infosphere.galaxyrelay;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;
import java.util.*;
public final class CommandHandler {
    public static void receive(Context c,Map<String,String> data){receive(c,data,false);}
    public static void receiveBle(Context c,Map<String,String> data){receive(c,data,true);}
    private static void receive(Context c,Map<String,String> data,boolean ble){
        if(ble&&(!new Credentials(c).flag("nativeMode",false)||!new Credentials(c).flag("nativePaired",false)))return;
        if(!CommandVerifier.valid(data,new Credentials(c).secret(ble?"nativeCommandKey":"commandKey"),System.currentTimeMillis()))return;
        RelayQueue.IO.execute(()->{Credentials prefs=new Credentials(c);RelayDao dao=RelayDatabase.get(c).dao();try{
            if(!ble&&"NATIVE_REVOKE".equals(data.get("type"))){NativeCloud.revokeLocal(c,data.get("autoDismiss"));return;}
            if(!ble&&!new RelayApi(c).call("/api/status",null,true).optBoolean("paired"))return;
            if(!CommandVerifier.valid(data,prefs.secret(ble?"nativeCommandKey":"commandKey"),System.currentTimeMillis()))return;
            CommandRecord command=new CommandRecord();command.id=data.get("commandId");command.nonce=data.get("nonce");command.timestamp=Long.parseLong(data.get("timestamp"));if(dao.claim(command)==-1)return;
            String type=data.get("type"),eventId=data.get("eventId"),result;
            if(type.equals("NATIVE_CAMERA")){if(!ble)CameraRequests.request(c,eventId,data.get("autoDismiss"));return;}
            if(type.equals("NATIVE_LOCATE")){if(!ble&&prefs.flag("nativeMode",false)&&prefs.flag("nativePaired",false))RemoteLocateService.request(c,eventId,data.get("autoDismiss"));return;}
            if(type.equals("NATIVE_PULL")||type.equals("NATIVE_ACK")){if(ble)return;if(type.equals("NATIVE_PULL"))NativeCloud.pull(c,eventId,data.get("autoDismiss"));else NativeCloud.ack(c,eventId);return;}
            if(type.equals("CALL_HANGUP"))result=CallControl.hangup(c,eventId,command.timestamp,ble);
            else if(type.equals("PING"))result="PONG";
            else if(type.equals("SETTINGS_SYNC")){prefs.setFlag("autoDismiss","true".equals(data.get("autoDismiss")));result="SETTINGS_APPLIED";}
            else if(type.equals("ACK")&&!"true".equals(data.get("autoDismiss")))result="SETTINGS_APPLIED";
            else {
                RelayNotificationListener listener=RelayNotificationListener.current;EventRecord event=dao.find(eventId);StatusBarNotification active=listener==null||event==null||!event.active?null:listener.findActive(event.notificationKey);
                if(listener==null)result="PERMISSION_REQUIRED";else if(active==null)result="NOT_FOUND";
                else if(type.equals("DISMISS")||type.equals("ACK")){listener.cancelNotification(active.getKey());result="REMOVED";}
                else if(type.equals("REPLY")){
                    JSONObject original=new JSONObject(prefs.open(event.data));
                    result=original.optLong("postTime",-1)!=active.getPostTime()?"REPLY_ACTION_CHANGED":ReplyActions.execute(c,active.getNotification(),active.getPackageName(),original,data);
                }
                else if(active.getNotification().contentIntent==null)result="NO_CONTENT_INTENT";
                else if(MainActivity.visible&&!c.getSystemService(KeyguardManager.class).isKeyguardLocked()){try{send(active.getNotification().contentIntent,c);result="OPEN_SENT";}catch(Exception e){result=fallback(c,event,command.id)?"USER_ACTION_REQUIRED":"PERMISSION_REQUIRED";}}
                else result=fallback(c,event,command.id)?"USER_ACTION_REQUIRED":"PERMISSION_REQUIRED";
            }
            result(c,command.id,eventId,result);
        }catch(Exception error){if(BuildConfig.DEBUG)android.util.Log.w("GalaxyRelay", "command failed: "+error.getClass().getSimpleName()+(error instanceof RelayApi.Failure ? " "+((RelayApi.Failure)error).code:""));}});
    }
    static void send(PendingIntent target,Context c) throws PendingIntent.CanceledException {
        android.os.Bundle options=null;if(Build.VERSION.SDK_INT>=34){ActivityOptions o=ActivityOptions.makeBasic();o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);options=o.toBundle();}target.send(c,0,null,null,null,null,options);
    }
    private static boolean fallback(Context c,EventRecord event,String commandId){
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return false;manager.createNotificationChannel(new NotificationChannel("remote-open","iPhone에서 열기 요청",NotificationManager.IMPORTANCE_HIGH));
        Intent intent=new Intent(c,OpenNotificationActivity.class).putExtra("eventId",event.id).putExtra("commandId",commandId);PendingIntent tap=PendingIntent.getActivity(c,commandId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String description="Galaxy에서 탭하여 원본 알림을 여세요.";try{JSONObject e=new JSONObject(new Credentials(c).open(event.data));if(!new Credentials(c).flag("privacy",true))description=e.optString("title")+" · "+e.optString("app");}catch(Exception ignored){}
        manager.notify("open:"+commandId,0,new Notification.Builder(c,"remote-open").setSmallIcon(kr.infosphere.galaxyrelay.R.drawable.ic_relay).setContentTitle("iPhone에서 열기 요청").setContentText(description).setContentIntent(tap).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"열기",tap).build()).build());return true;
    }
    public static void result(Context c,String commandId,String eventId,String result){try{JSONObject event=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","command_result").put("timestamp",System.currentTimeMillis()).put("commandId",commandId).put("result",result);if(eventId!=null&&!eventId.isEmpty())event.put("targetEventId",eventId);RelayQueue.enqueue(c,event,"",false);}catch(Exception ignored){}}
}
