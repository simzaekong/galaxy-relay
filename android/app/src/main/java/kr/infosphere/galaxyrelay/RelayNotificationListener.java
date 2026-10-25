package kr.infosphere.galaxyrelay;
import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;
import java.util.*;
public class RelayNotificationListener extends NotificationListenerService {
    public static volatile RelayNotificationListener current;
    private static final java.util.concurrent.ConcurrentHashMap<String,StatusBarNotification> CONDITIONAL_PENDING=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService CONDITIONAL=java.util.concurrent.Executors.newSingleThreadExecutor();
    public static boolean defaultApp(String p){return Arrays.asList("com.kakao.talk","com.discord","com.instagram.android","com.samsung.android.messaging","com.google.android.apps.messaging").contains(p);}
    public static boolean messaging(String p){return p.equals("com.samsung.android.messaging")||p.equals("com.google.android.apps.messaging");}
    @Override public void onListenerConnected(){current=this;RelayQueue.IO.execute(()->{try{Set<String> keys=new HashSet<>();for(StatusBarNotification s:getActiveNotifications()){keys.add(s.getKey());onNotificationPosted(s);}for(EventRecord r:RelayDatabase.get(this).dao().activeRecords())if(!keys.contains(r.notificationKey))removed(r);}catch(Exception error){if(BuildConfig.DEBUG)android.util.Log.w("GalaxyRelay","notification processing failed: "+error.getClass().getSimpleName());}});}
    @Override public void onListenerDisconnected(){current=null;requestRebind(new android.content.ComponentName(this,RelayNotificationListener.class));}
    @Override public void onNotificationPosted(StatusBarNotification s){
        Credentials prefs=new Credentials(this);String p=s.getPackageName();Notification n=s.getNotification();
        if(prefs.flag("calls",false)&&java.util.Arrays.asList("com.samsung.android.dialer","com.samsung.android.incallui","com.android.incallui","com.google.android.dialer","com.android.dialer","com.android.phone").contains(p)&&Notification.CATEGORY_CALL.equals(n.category)){PhoneReceiver.enrich(this,n);return;}
        if(p.equals(getPackageName())||!prefs.flag("relayEnabled",true)||(!prefs.flag("app:"+p,defaultApp(p))&&!prefs.flag("offlineApp:"+p,false))||prefs.secret("token").isEmpty())return;
        if(messaging(p)&&(!prefs.flag("sms",true)||prefs.flag("directSms",false)))return;
        if((n.flags&(Notification.FLAG_ONGOING_EVENT|Notification.FLAG_GROUP_SUMMARY))!=0)return;
        boolean conditional=!prefs.flag("app:"+p,defaultApp(p));
        if(conditional){if(!prefs.flag("nativeMode",false))return;String key=s.getKey();if(CONDITIONAL_PENDING.size()>=256&&!CONDITIONAL_PENDING.containsKey(key))return;if(CONDITIONAL_PENDING.put(key,s)!=null)return;CONDITIONAL.execute(()->{boolean offline=NativeBridge.confirmedOffline();StatusBarNotification latest=CONDITIONAL_PENDING.remove(key);if(offline&&latest!=null)capture(latest,true);});}else capture(s,false);
    }
    private void capture(StatusBarNotification s,boolean conditional){Credentials prefs=new Credentials(this);String p=s.getPackageName();Notification n=s.getNotification();
        RelayQueue.IO.execute(()->{try{
            if(!prefs.flag("relayEnabled",true))return;
            if(!OfflineAppPolicy.allowed(!conditional&&prefs.flag("app:"+p,defaultApp(p)),conditional&&prefs.flag("offlineApp:"+p,false),conditional&&NativeBridge.confirmedOffline()))return;
            if(conditional){StatusBarNotification active=findActive(s.getKey());if(active==null||active.getPostTime()!=s.getPostTime())return;}
            Bundle x=n.extras;String title=clip(x.getCharSequence(Notification.EXTRA_TITLE),160),body=clip(x.getCharSequence(Notification.EXTRA_BIG_TEXT),500);if(body.isEmpty())body=clip(x.getCharSequence(Notification.EXTRA_TEXT),500);if(title.isEmpty()&&body.isEmpty())return;
            JSONObject reply=ReplyActions.describe(n,p);
            RelayDao dao=RelayDatabase.get(this).dao();EventRecord previous=dao.active(s.getKey());if(previous!=null){JSONObject old=new JSONObject(prefs.open(previous.data));if(old.optString("title").equals(title)&&old.optString("body").equals(body)&&Objects.equals(old.optJSONObject("reply")==null?null:old.optJSONObject("reply").toString(),reply==null?null:reply.toString())&&(reply==null||old.optLong("postTime")==s.getPostTime()))return;}
            String label;try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){label=p;}
            JSONObject event=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type",messaging(p)?"sms":"notification").put("packageName",p).put("app",clip(label,80)).put("title",title).put("body",body).put("timestamp",System.currentTimeMillis()).put("postTime",s.getPostTime()).put("notificationKey",clip(s.getKey(),300)).put("notificationId",s.getId()).put("hasContentIntent",n.contentIntent!=null).put("actionCount",n.actions==null?0:Math.min(64,n.actions.length));if(conditional)event.put("offlineOnly",true);if(prefs.flag("nativeMode",false)){String icon=AppIcons.encoded(this,p);if(!icon.isEmpty())event.put("appIcon",icon);}if(reply!=null)event.put("reply",reply);if(previous!=null){dao.removed(s.getKey());event.put("replacesEventId",previous.id);}RelayQueue.store(this,event,s.getKey(),true);RelayQueue.kick(this,0);
        }catch(Exception error){if(BuildConfig.DEBUG)android.util.Log.w("GalaxyRelay","notification processing failed: "+error.getClass().getSimpleName());}});
    }
    public static String clip(CharSequence s,int length){if(s==null)return "";String text=s.toString();return text.substring(0,Math.min(text.length(),length));}
    @Override public void onNotificationRemoved(StatusBarNotification s){RelayQueue.IO.execute(()->{EventRecord r=RelayDatabase.get(this).dao().active(s.getKey());if(r!=null)removed(r);});}
    private void removed(EventRecord r){try{RelayDatabase.get(this).dao().removed(r.notificationKey);JSONObject event=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","removed").put("timestamp",System.currentTimeMillis()).put("targetEventId",r.id);RelayQueue.store(this,event,"",false);RelayQueue.kick(this,0);}catch(Exception ignored){}}
    public StatusBarNotification findActive(String key){try{for(StatusBarNotification s:getActiveNotifications())if(s.getKey().equals(key))return s;}catch(Exception ignored){}return null;}
}
