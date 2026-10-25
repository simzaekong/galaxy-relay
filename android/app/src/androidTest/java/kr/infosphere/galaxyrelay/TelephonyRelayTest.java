package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TelephonyRelayTest {
    @Test public void armNativeScenario()throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();var c=instrumentation.getTargetContext();Credentials p=new Credentials(c);
        try(FileWriter out=new FileWriter(new File(c.getFilesDir(),"telephony-test-settings.json"))){out.write(new JSONObject().put("sms",p.flag("directSms",false)).put("calls",p.flag("calls",false)).toString());}
        synchronized(QueueWorker.class){RelayDatabase.get(c).dao().clearEvents();}
        p.setFlag("directSms",true);p.setFlag("sms",true);p.setFlag("calls",true);p.put("callState",android.telephony.TelephonyManager.EXTRA_STATE_IDLE);
        assertTrue(c.getSharedPreferences("relay",android.content.Context.MODE_PRIVATE).edit().commit());
        try(var fd=instrumentation.getUiAutomation().executeShellCommand("am start -n kr.infosphere.galaxyrelay/.MainActivity");var stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(stream.read(b)!=-1){}}
        try(FileWriter out=new FileWriter(new File(c.getFilesDir(),"telephony-test-ready"))){out.write(String.valueOf(System.currentTimeMillis()));}
        // Finish instrumentation BEFORE the modem events: exercise the normal app process.
    }
    @Test public void verifyNativeScenario()throws Exception{
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();Credentials p=new Credentials(c);boolean sms=false,ringing=false,ended=false,identified=false;
        try{for(EventRecord r:RelayDatabase.get(c).dao().since(0)){JSONObject event=new JSONObject(p.open(r.data));sms|=event.optString("type").equals("sms")&&event.optString("body").equals("RelaySmsIntegration");ringing|=event.optString("type").equals("call")&&event.optString("state").equals("ringing");ended|=event.optString("type").equals("call")&&event.optString("state").equals("ended");identified|=event.optString("type").equals("call")&&event.optString("state").equals("ringing")&&event.optString("number").replaceAll("[^0-9]","").equals("5551234");}assertTrue("Native normal-process results: SMS="+sms+" ringing="+ringing+" ended="+ended+" caller="+identified,sms&&ringing&&ended&&identified);}finally{restoreNativeSettings();}
    }
    @Test public void restoreNativeSettings()throws Exception{
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();File file=new File(c.getFilesDir(),"telephony-test-settings.json");if(!file.exists())return;
        StringBuilder text=new StringBuilder();try(FileReader reader=new FileReader(file)){char[] b=new char[256];int n;while((n=reader.read(b))!=-1)text.append(b,0,n);}JSONObject old=new JSONObject(text.toString());Credentials p=new Credentials(c);p.setFlag("directSms",old.getBoolean("sms"));p.setFlag("calls",old.getBoolean("calls"));file.delete();new File(c.getFilesDir(),"telephony-test-ready").delete();
    }
    @Test public void emulatorSmsAndIncomingCall()throws Exception{
        try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("am start -n kr.infosphere.galaxyrelay/.MainActivity");var stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(stream.read(b)!=-1){}}
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();Credentials p=new Credentials(c);boolean oldSms=p.flag("directSms",false),oldCalls=p.flag("calls",false);p.setFlag("directSms",true);p.setFlag("sms",true);p.setFlag("calls",true);p.put("callState",android.telephony.TelephonyManager.EXTRA_STATE_IDLE);long start=System.currentTimeMillis();
        synchronized(QueueWorker.class){RelayDatabase.get(c).dao().clearEvents();}
        try{try(FileWriter ready=new FileWriter(new File(c.getFilesDir(),"telephony-test-ready"))){ready.write(String.valueOf(start));}
            boolean sms=false,ringing=false,ended=false;long until=start+150000;
            while(System.currentTimeMillis()<until){for(EventRecord r:RelayDatabase.get(c).dao().since(0)){JSONObject event=new JSONObject(p.open(r.data));if(event.optString("type").equals("sms")&&event.optString("body").equals("RelaySmsIntegration"))sms=true;if(event.optString("type").equals("call")&&event.optString("state").equals("ringing"))ringing=true;if(event.optString("type").equals("call")&&event.optString("state").equals("ended"))ended=true;}if(sms&&ringing&&ended)break;Thread.sleep(500);}
            StringBuilder diagnostic=new StringBuilder();for(EventRecord r:RelayDatabase.get(c).dao().recent()){JSONObject e=new JSONObject(p.open(r.data));if(e.optString("type").equals("call"))diagnostic.append(e.optString("state")).append(':').append(r.timestamp-start).append(' ');}
            assertTrue("Native receiver results: SMS="+sms+" ringing="+ringing+" ended="+ended+" phases="+diagnostic+" current="+p.get("callState","unknown"),sms&&ringing&&ended);
        }finally{p.setFlag("directSms",oldSms);p.setFlag("calls",oldCalls);new File(c.getFilesDir(),"telephony-test-ready").delete();}
    }
}
