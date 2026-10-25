package kr.infosphere.galaxyrelay;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.JSONObject;
import java.util.UUID;
public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()))return;Credentials p=new Credentials(c);if(!p.flag("sms",true)||!p.flag("directSms",false))return;
        try{SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);if(messages.length==0)return;StringBuilder body=new StringBuilder();for(SmsMessage message:messages)body.append(message.getMessageBody());JSONObject e=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","sms").put("app","SMS").put("sender",RelayNotificationListener.clip(messages[0].getOriginatingAddress(),80)).put("title",RelayNotificationListener.clip(messages[0].getOriginatingAddress(),160)).put("body",RelayNotificationListener.clip(body,500)).put("timestamp",System.currentTimeMillis());RelayQueue.enqueue(c,e,"",false);}catch(Exception ignored){}
    }
}
