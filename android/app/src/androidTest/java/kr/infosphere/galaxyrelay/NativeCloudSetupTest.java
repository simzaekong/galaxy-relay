package kr.infosphere.galaxyrelay;
import android.content.*;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
public class NativeCloudSetupTest {
 @Test public void prepare()throws Exception{
  assertTrue("Dedicated emulator only",Build.HARDWARE.contains("ranchu")||Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"));
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  JSONObject config=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File("/data/local/tmp/relay-native-config.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
  Credentials p=new Credentials(c);p.clear();RelayDatabase.get(c).dao().clearEvents();RelayDatabase.get(c).dao().clearCommands();p.put("backend",config.getString("backend"));
  String fcm=Tasks.await(FirebaseMessaging.getInstance().getToken(),60,TimeUnit.SECONDS);
  JSONObject a=new RelayApi(c).call("/api/android/register",new JSONObject().put("name","Isolated native emulator").put("installSecret",config.getString("installSecret")).put("fcmToken",fcm),false);
  p.secret("token",a.getString("token"));p.secret("commandKey",a.getString("commandKey"));p.put("deviceId",a.getString("deviceId"));p.secret("fcm",fcm);
  NativeCloud.provision(c);p.secret("nativeLinkKey",NativeCrypto.b64(NativeCrypto.random(32)));p.secret("nativeCommandKey",NativeCrypto.b64(NativeCrypto.random(32)));p.setFlag("nativeMode",true);p.setFlag("nativePaired",true);
  p.setFlag("app:kr.infosphere.relayfixture",true);p.setFlag("relayEnabled",true);
  assertTrue("Persist test pairing before instrumentation process exits",p.flush());
  JSONObject client=NativeCloud.config(c).put("androidToken",p.secret("token"));
  try(FileWriter writer=new FileWriter(new File(c.getFilesDir(),"native-client.json"))){writer.write(client.toString());}
 }
 @Test public void post()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=InstrumentationRegistry.getArguments().getString("nativeTag");assertTrue(tag!=null&&tag.matches("native-fixture-[0-9]+"));
  NotificationListenerService.requestRebind(new ComponentName(c,RelayNotificationListener.class));long end=System.currentTimeMillis()+20000;while(RelayNotificationListener.current==null&&System.currentTimeMillis()<end)Thread.sleep(200);assertNotNull(RelayNotificationListener.current);
  c.sendBroadcast(new Intent().setComponent(new ComponentName("kr.infosphere.relayfixture","kr.infosphere.relayfixture.FixtureReceiver")).putExtra("tag",tag).putExtra("reply",true));
  end=System.currentTimeMillis()+20000;EventRecord record=null;while(record==null&&System.currentTimeMillis()<end){for(EventRecord r:RelayDatabase.get(c).dao().activeRecords())if(r.notificationKey.contains(tag))record=r;if(record==null)Thread.sleep(200);}assertNotNull(record);assertNotNull(new JSONObject(new Credentials(c).open(record.data)).optJSONObject("reply"));
 }
}

