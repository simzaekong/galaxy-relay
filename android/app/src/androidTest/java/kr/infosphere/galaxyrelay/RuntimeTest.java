package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
@RunWith(AndroidJUnit4.class)
public class RuntimeTest {
    @Test public void keystoreRoomAndReplay() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Credentials p=new Credentials(c);String sealed=p.seal("private notification");assertFalse(sealed.contains("private"));assertEquals("private notification",p.open(sealed));
        RelayDao dao=RelayDatabase.get(c).dao();CommandRecord command=new CommandRecord();command.id="cmd_"+UUID.randomUUID();command.nonce=UUID.randomUUID().toString();command.timestamp=System.currentTimeMillis();assertTrue(dao.claim(command)!=-1);assertEquals(-1,dao.claim(command));CommandRecord replay=new CommandRecord();replay.id="cmd_"+UUID.randomUUID();replay.nonce=command.nonce;replay.timestamp=command.timestamp;assertEquals(-1,dao.claim(replay));
        String id="evt_"+UUID.randomUUID();RelayQueue.store(c,new JSONObject().put("id",id).put("type","sms").put("timestamp",System.currentTimeMillis()).put("body","test private"),"",false);assertTrue(dao.find(id).pending);assertFalse(dao.find(id).data.contains("test private"));dao.delivered(id,dao.find(id).timestamp);
    }
    @Test public void firebaseAndDeployedBackendPair() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();Context c=instrumentation.getTargetContext();Credentials p=new Credentials(c);
        ParcelFileDescriptor descriptor=instrumentation.getUiAutomation().executeShellCommand("cat /data/local/tmp/relay-runtime-config.json");String text;try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);text=out.toString("UTF-8");}
        JSONObject config=new JSONObject(text);p.put("backend",config.getString("backend"));String fcm=Tasks.await(FirebaseMessaging.getInstance().getToken(),60,TimeUnit.SECONDS);assertTrue(fcm.length()>20);
        RelayApi api=new RelayApi(c);JSONObject registration=api.call("/api/android/register",new JSONObject().put("installSecret",config.getString("installSecret")).put("name","Relay test emulator").put("fcmToken",fcm),false);
        p.secret("token",registration.getString("token"));p.secret("commandKey",registration.getString("commandKey"));p.secret("fcm",fcm);p.put("deviceId",registration.getString("deviceId"));
        JSONObject code=api.call("/api/pair/create",new JSONObject(),true);Thread.sleep(2000);
        JSONObject claim=api.call("/api/pair/claim",new JSONObject().put("code",code.getString("code")).put("name","Relay browser test"),false);Thread.sleep(2000);
        api.call("/api/pair/approve",new JSONObject().put("webId",claim.getString("deviceId")),true);Thread.sleep(2000);assertTrue(api.call("/api/status",null,true).getBoolean("paired"));
        // Test-only credentials for the later browser integration; never part of the application APK.
        try(FileWriter out=new FileWriter(new File(c.getFilesDir(),"relay-test-link.json"))){out.write(new JSONObject().put("backend",config.getString("backend")).put("webToken",claim.getString("token")).put("webId",claim.getString("deviceId")).put("androidId",registration.getString("deviceId")).toString());}
        JSONObject command=new JSONObject().put("commandId","cmd_"+UUID.randomUUID());assertTrue(api.callWithToken("/api/command/ping",command,claim.getString("token")).getBoolean("accepted"));
    }
}
