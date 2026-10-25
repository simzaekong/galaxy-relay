package kr.infosphere.galaxyrelay;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONObject;
public class QueueWorker extends Worker {
    public QueueWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){return flush(getApplicationContext());}
    public static synchronized Result flush(Context c){Credentials credentials=new Credentials(c);if(credentials.secret("token").isEmpty())return Result.success();RelayDao dao=RelayDatabase.get(c).dao();
        try {
            if(!credentials.flag("nativeMode",false)&&credentials.flag("fcmDirty",false)&&!credentials.secret("fcm").isEmpty()){new RelayApi(c).call("/api/android/update",new JSONObject().put("fcmToken",credentials.secret("fcm")),true);credentials.setFlag("fcmDirty",false);}
            for(EventRecord r:dao.queue()){
                long now=System.currentTimeMillis();if(now>r.expiresAt||r.attempts>=20){dao.delivered(r.id,r.timestamp);continue;}
                if(r.nextAt>now){RelayQueue.kick(c,r.nextAt-now);return Result.success();}
                try{
                    JSONObject event=new JSONObject(credentials.open(r.data));if(event.optBoolean("offlineOnly",false)&&(!credentials.flag("nativeMode",false)||!credentials.flag("offlineApp:"+event.optString("packageName"),false)||!NativeBridge.confirmedOffline())){dao.delivered(r.id,r.timestamp);continue;}if(credentials.flag("nativeMode",false)){if(!NativeBridge.deliver(event)){if(NativeBridge.ready())RelayQueue.kick(c,30000);return Result.success();}dao.delivered(r.id,r.timestamp);credentials.put("lastSync",String.valueOf(now));continue;}String type=event.optString("type");boolean callUpdate=type.equals("call")&&!event.optString("state").equals("ringing");
                    boolean state=type.equals("removed")||type.equals("command_result")||callUpdate;
                    if(type.equals("call")&&!callUpdate&&event.has("callId")&&(!event.optString("callId").equals(credentials.get("callId",""))||!credentials.get("callState","").equals(android.telephony.TelephonyManager.EXTRA_STATE_RINGING))){dao.delivered(r.id,r.timestamp);continue;}
                    if(state){JSONObject minimal=new JSONObject();for(String field:new String[]{"id","type","timestamp","targetEventId","commandId","result","callId","state"})if(event.has(field))minimal.put(field,event.get(field));if(!callUpdate||minimal.has("callId"))new RelayApi(c).call("/api/state",new JSONObject().put("event",minimal),true);}
                    else new RelayApi(c).call("/api/event",new JSONObject().put("event",EventCipher.protect(c,event)),true);
                    dao.delivered(r.id,r.timestamp);credentials.put("lastSync",String.valueOf(now));credentials.put("queueError","");
                }
                catch(Exception e){credentials.put("queueDiagnostic",e instanceof RelayApi.Failure failure?failure.status+":"+failure.code:e.getClass().getSimpleName());if(e instanceof RelayApi.Failure f){if(f.status==401){credentials.clear();dao.clearEvents();dao.clearCommands();return Result.success();}if(f.status==400||f.status==413){dao.delivered(r.id,r.timestamp);continue;}}long next=now+RelayQueue.delay(r.attempts);dao.retry(r.id,r.timestamp,next);credentials.put("queueError","전송 대기 중. 인터넷·연결·Push 권한을 확인하세요.");RelayQueue.kick(c,next-now);return Result.success();}
            }
            if(dao.queue().size()>0)RelayQueue.kick(c,0);dao.pruneEvents();dao.pruneCommands(System.currentTimeMillis()-86400000);return Result.success();
        }catch(Exception e){return Result.retry();}
    }
}
