package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.UUID;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class RelayProbeTest {
    @Test public void awaitLatestPushRegistration()throws Exception{
        String expected=InstrumentationRegistry.getArguments().getString("expectedPushFingerprint");assertNotNull(expected);
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();long end=System.currentTimeMillis()+120000;boolean current=false;
        do{try{current=expected.equals(new RelayApi(c).call("/api/status",null,true).optString("pushFingerprint"));}catch(java.io.IOException transientNetwork){current=false;}if(!current)Thread.sleep(2500);}while(!current&&System.currentTimeMillis()<end);assertTrue("Push registration did not propagate to the Android relay edge",current);
    }
    @Test public void deployedPushRequest()throws Exception{
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();JSONObject event=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","notification").put("title","RelayProbe").put("body","Integration transport probe").put("timestamp",System.currentTimeMillis());
        try{assertTrue(new RelayApi(c).call("/api/event",new JSONObject().put("event",EventCipher.protect(c,event)),true).getBoolean("accepted"));}catch(RelayApi.Failure e){fail("Transport probe: "+e.status+":"+e.code);}
    }
}
