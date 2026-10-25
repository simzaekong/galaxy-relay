package kr.infosphere.galaxyrelay;
import android.content.*;
import android.telephony.TelephonyManager;
import org.json.JSONObject;
import java.util.UUID;
public class PhoneReceiver extends BroadcastReceiver {
    // Receiver completion must not wait behind HTTPS retries on RelayQueue.IO.
    private static final java.util.concurrent.ExecutorService CALLS=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context c,Intent intent){
        if(!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction()))return;
        if(BuildConfig.DEBUG)android.util.Log.d("RelayPhone","phone state received; enabled="+new Credentials(c).flag("calls",false));
        Credentials diagnostic=new Credentials(c);diagnostic.put("phoneReceivedAt",String.valueOf(System.currentTimeMillis()));if(!diagnostic.flag("calls",false)){diagnostic.put("phoneDiagnostic","DISABLED");return;}diagnostic.put("phoneDiagnostic","RECEIVED");
        String state=intent.getStringExtra(TelephonyManager.EXTRA_STATE),number=intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if(!java.util.Arrays.asList(TelephonyManager.EXTRA_STATE_RINGING,TelephonyManager.EXTRA_STATE_OFFHOOK,TelephonyManager.EXTRA_STATE_IDLE).contains(state))return;
        Context app=c.getApplicationContext();PendingResult pending=goAsync();
        CALLS.execute(()->{try{transition(app,state,number);}catch(Exception ignored){}finally{pending.finish();}});
    }
    static synchronized void transition(Context c,String state,String number)throws Exception{
        if(BuildConfig.DEBUG)android.util.Log.d("RelayPhone","phase="+state);
        Credentials p=new Credentials(c);String previous=p.get("callState",TelephonyManager.EXTRA_STATE_IDLE);
        if(state.equals(TelephonyManager.EXTRA_STATE_RINGING)){
            if(!previous.equals(state)){if(!previous.equals(TelephonyManager.EXTRA_STATE_IDLE)&&!p.get("callId","").isEmpty()){
                JSONObject end=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","call").put("state","ended").put("callId",p.get("callId","")).put("timestamp",System.currentTimeMillis());RelayQueue.store(c,end,"",false);RelayQueue.kick(c,0);
            }p.setFlag("callControlSafe",previous.equals(TelephonyManager.EXTRA_STATE_IDLE));p.put("callId","evt_"+UUID.randomUUID());p.secret("callNumber","");p.secret("callContact","");p.secret("callSent","");}
            p.put("callState",state);
            // PHONE_STATE may arrive twice, with and without a number. Do not discard the richer duplicate.
            if(number!=null&&!number.isEmpty())p.secret("callNumber",RelayNotificationListener.clip(number,80));
            scheduleRing(c,p.get("callId",""));return;
        }
        p.put("callState",state);String callId=p.get("callId","");
        if(state.equals(previous)||callId.isEmpty())return;
        String phase=state.equals(TelephonyManager.EXTRA_STATE_IDLE)?"ended":"offhook";
        JSONObject update=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","call").put("state",phase).put("callId",callId).put("targetEventId",callId).put("timestamp",System.currentTimeMillis());
        if(phase.equals("ended")&&previous.equals(TelephonyManager.EXTRA_STATE_RINGING)&&!callId.equals(p.get("callRejectedId",""))){update.put("unanswered",true).put("app","전화");if(!p.secret("callContact").isEmpty())update.put("contact",p.secret("callContact"));if(!p.secret("callNumber").isEmpty())update.put("number",p.secret("callNumber"));}
        RelayQueue.store(c,update,"",false);RelayQueue.kick(c,0);
        if(phase.equals("ended")){p.setFlag("callControlSafe",false);p.secret("callNumber","");p.secret("callContact","");p.secret("callSent","");p.put("callId","");}
    }
    private static void scheduleRing(Context c,String callId){new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->CALLS.execute(()->{try{ring(c,callId);}catch(Exception ignored){}}),1000);}
    private static void ring(Context c,String callId)throws Exception{
        Credentials p=new Credentials(c);if(callId.isEmpty()||!callId.equals(p.get("callId",""))||!p.get("callState","").equals(TelephonyManager.EXTRA_STATE_RINGING)||!p.flag("calls",false))return;
        String number=p.secret("callNumber"),contact=p.secret("callContact");if(contact.isEmpty())contact=PhoneIdentity.contact(c,number);
        String identity=number+"\n"+contact;if(identity.equals(p.secret("callSent")))return;
        JSONObject event=new JSONObject().put("id",callId).put("callId",callId).put("type","call").put("state","ringing").put("app","전화").put("title",!contact.isEmpty()?contact:!number.isEmpty()?number:"발신자 정보 없음").put("body","Galaxy로 전화가 오고 있습니다.").put("timestamp",System.currentTimeMillis());
        if(!number.isEmpty())event.put("number",number);if(!contact.isEmpty())event.put("contact",contact);
        RelayQueue.store(c,event,"",false);p.secret("callSent",identity);RelayQueue.kick(c,0);
    }
    static void enrich(Context c,android.app.Notification n){
        CALLS.execute(()->{try{Credentials p=new Credentials(c);if(!p.flag("calls",false)||!p.get("callState","").equals(TelephonyManager.EXTRA_STATE_RINGING))return;
            CharSequence name=null;if(android.os.Build.VERSION.SDK_INT>=31){android.app.Person person=android.os.Build.VERSION.SDK_INT>=33?n.extras.getParcelable(android.app.Notification.EXTRA_CALL_PERSON,android.app.Person.class):n.extras.getParcelable(android.app.Notification.EXTRA_CALL_PERSON);if(person!=null)name=person.getName();}
            if(name==null)name=n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
            String value=RelayNotificationListener.clip(name,100);if(value.isEmpty()||value.matches("(?i).*(전화|통화|incoming|call|unknown|알 수 없음).*"))return;
            if(value.matches("[+0-9() -]{3,80}"))p.secret("callNumber",value);else p.secret("callContact",value);
            scheduleRing(c,p.get("callId",""));
        }catch(Exception ignored){}});
    }
}
