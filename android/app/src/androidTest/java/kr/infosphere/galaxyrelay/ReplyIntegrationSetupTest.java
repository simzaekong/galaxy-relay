package kr.infosphere.galaxyrelay;
import android.content.*;
import android.service.notification.NotificationListenerService;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ReplyIntegrationSetupTest {
    @Test public void prepareIsolatedFixture() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Credentials prefs=new Credentials(c);
        assertTrue(new RelayApi(c).call("/api/status",null,true).optBoolean("paired"));
        prefs.setFlag("app:kr.infosphere.relayfixture",true);prefs.setFlag("relayEnabled",true);
        String key=prefs.secret("e2eeKey");if(key.isEmpty()){key=EventCipher.generate();prefs.secret("e2eeKey",key);}
        boolean encrypted="true".equals(InstrumentationRegistry.getArguments().getString("replyEncrypted"));prefs.setFlag("e2eeEnabled",encrypted);
        try(FileWriter out=new FileWriter(new File(c.getFilesDir(),"reply-key.json"))){out.write(new JSONObject().put("key",key).toString());}
        NotificationListenerService.requestRebind(new ComponentName(c,RelayNotificationListener.class));
        long end=System.currentTimeMillis()+10000;while(RelayNotificationListener.current==null&&System.currentTimeMillis()<end)Thread.sleep(200);
        assertNotNull(RelayNotificationListener.current);
    }
    @Test public void postIsolatedNotification() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=InstrumentationRegistry.getArguments().getString("replyTag");assertTrue(tag!=null&&tag.matches("relay-reply-[0-9]+"));
        long end=System.currentTimeMillis()+15000;while(RelayNotificationListener.current==null&&System.currentTimeMillis()<end)Thread.sleep(200);assertNotNull("Notification access not connected",RelayNotificationListener.current);
        c.sendBroadcast(new Intent().setComponent(new ComponentName("kr.infosphere.relayfixture","kr.infosphere.relayfixture.FixtureReceiver")).putExtra("tag",tag).putExtra("reply",true));
        RelayDao dao=RelayDatabase.get(c).dao();EventRecord event=null;end=System.currentTimeMillis()+30000;
        while(event==null&&System.currentTimeMillis()<end){for(EventRecord row:dao.activeRecords())if(row.notificationKey.contains(tag))event=row;if(event==null)Thread.sleep(200);}
        assertNotNull("Original notification was not captured by listener",event);assertNotNull("RemoteInput capability missing",new JSONObject(new Credentials(c).open(event.data)).optJSONObject("reply"));
        String id=event.id;RelayQueue.kick(c,0);end=System.currentTimeMillis()+60000;
        while(dao.find(id).pending&&System.currentTimeMillis()<end)Thread.sleep(500);
        assertFalse("Notification still queued: "+new Credentials(c).get("queueDiagnostic","unknown"),dao.find(id).pending);
    }
}
