package kr.infosphere.galaxyrelay;
import android.content.Context;
import androidx.work.*;
import org.json.JSONObject;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public final class RelayQueue {
    private static final java.util.concurrent.ExecutorService URGENT=Executors.newSingleThreadExecutor();
    public static final java.util.concurrent.ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"relay-retry");t.setDaemon(true);return t;});
    private static java.util.concurrent.ScheduledFuture<?> retry;
    public static void enqueue(Context c,JSONObject event,String key,boolean active){Context app=c.getApplicationContext();IO.execute(()->{try{store(app,event,key,active);kick(app,0);}catch(Exception ignored){new Credentials(app).put("queueError","로컬 저장 실패. 앱 저장 공간을 확인하세요.");}});}
    public static void store(Context c,JSONObject event,String key,boolean active) throws Exception {
        EventRecord r=new EventRecord();r.id=event.getString("id");r.notificationKey=key;r.packageName=event.optString("packageName");r.data=new Credentials(c).seal(event.toString());r.timestamp=event.getLong("timestamp");r.nextAt=System.currentTimeMillis();r.active=active;r.pending=true;
        String type=event.getString("type");long lifetime=type.equals("call")&&event.optString("state").equals("ringing")?60000:type.equals("sms")?86400000:type.equals("notification")?3600000:86400000;r.expiresAt=r.timestamp+lifetime;RelayDao dao=RelayDatabase.get(c).dao();dao.save(r);dao.boundQueue();dao.pruneEvents();
        if(new Credentials(c).flag("nativeMode",false)&&(type.equals("call")||type.equals("command_result"))){JSONObject snapshot=new JSONObject(event.toString());Context app=c.getApplicationContext();URGENT.execute(()->{if(NativeBridge.deliver(snapshot,3))RelayDatabase.get(app).dao().delivered(r.id,r.timestamp);});}
    }
    public static synchronized void kick(Context c,long delay){Context app=c.getApplicationContext();OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(QueueWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInitialDelay(delay,TimeUnit.MILLISECONDS).build();WorkManager.getInstance(app).enqueueUniqueWork("relay-delivery",ExistingWorkPolicy.REPLACE,work);if(retry!=null)retry.cancel(false);retry=TIMER.schedule(()->IO.execute(()->QueueWorker.flush(app)),delay,TimeUnit.MILLISECONDS);}
    public static long delay(int attempt){long[] steps={5000,15000,30000,60000,300000};return steps[Math.min(attempt,steps.length-1)];}
}
