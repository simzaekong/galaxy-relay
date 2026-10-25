package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
public final class CallControl {
 @SuppressWarnings("deprecation")
 static String hangup(Context c,String id,long sent,boolean ble){synchronized(PhoneReceiver.class){
  Credentials p=new Credentials(c);
  if(!ble||!NativeBridge.ready()||!p.flag("calls",false))return "CALL_UNAVAILABLE";
  if(Build.VERSION.SDK_INT<28)return "CALL_UNSUPPORTED";
  if(c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return "CALL_PERMISSION_REQUIRED";
  try{
   if(!CallControlPolicy.allowed(id,p.get("callId",""),p.get("callState",""),p.flag("callControlSafe",false),sent,System.currentTimeMillis()))return "CALL_NOT_CURRENT";
   SubscriptionManager subscriptions=c.getSystemService(SubscriptionManager.class);
   if(subscriptions==null||subscriptions.getActiveSubscriptionInfoCount()!=1)return "CALL_AMBIGUOUS";
   TelephonyManager phone=c.getSystemService(TelephonyManager.class);
   int actual=phone.getCallState();
   if(actual==TelephonyManager.CALL_STATE_IDLE)return "CALL_NOT_CURRENT";
   if((actual==TelephonyManager.CALL_STATE_RINGING)!=p.get("callState","").equals("RINGING"))return "CALL_NOT_CURRENT";
   boolean ok=c.getSystemService(TelecomManager.class).endCall();if(ok&&actual==TelephonyManager.CALL_STATE_RINGING)p.put("callRejectedId",id);return ok?"CALL_END_REQUESTED":"CALL_END_FAILED";
  }catch(SecurityException e){return "CALL_PERMISSION_REQUIRED";}catch(Exception e){return "CALL_END_FAILED";}
 }}
}
