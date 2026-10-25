package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import org.json.JSONObject;
@RunWith(AndroidJUnit4.class)
public class RelayIntegrationTest {
    private final Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void shell(String command)throws Exception{try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);var stream=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(stream.read(b)!=-1){}}}
    private String output(String command){try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);var stream=new ParcelFileDescriptor.AutoCloseInputStream(fd);var out=new ByteArrayOutputStream()){byte[] b=new byte[2048];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}catch(Exception e){return "";}}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+45000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(500);assertTrue("Condition did not become true within 45 seconds",condition.getAsBoolean());}
    private void awaitRecovery(java.util.function.BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+180000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(500);assertTrue("Queue did not recover within 3 minutes",condition.getAsBoolean());}
    @Test public void realFcmOpenFallbackDismissAndReplay()throws Exception{
        shell("am start -n kr.infosphere.galaxyrelay/.MainActivity");android.service.notification.NotificationListenerService.requestRebind(new android.content.ComponentName(c,RelayNotificationListener.class));Credentials prefs=new Credentials(c);prefs.setFlag("app:kr.infosphere.relayfixture",true);prefs.setFlag("relayEnabled",true);await(()->RelayNotificationListener.current!=null);
        synchronized(QueueWorker.class){RelayDatabase.get(c).dao().clearEvents();}
        boolean offline="true".equals(InstrumentationRegistry.getArguments().getString("relayOffline"));
        String tag="relay-integration-"+System.currentTimeMillis();if(offline){shell("svc wifi disable");shell("svc data disable");Thread.sleep(2500);}
        shell("am broadcast -n kr.infosphere.relayfixture/.FixtureReceiver --es tag "+tag);
        RelayDao dao=RelayDatabase.get(c).dao();await(()->dao.activeRecords().stream().anyMatch(e->e.packageName.equals("kr.infosphere.relayfixture")&&e.notificationKey.contains(tag)));
        EventRecord event=dao.activeRecords().stream().filter(e->e.packageName.equals("kr.infosphere.relayfixture")&&e.notificationKey.contains(tag)).findFirst().get();assertNotNull("Test source notification must have an original contentIntent",RelayNotificationListener.current.findActive(event.notificationKey).getNotification().contentIntent);String web;
        if(offline){try{Thread.sleep(1000);assertTrue("Offline notification must stay in Room queue",dao.find(event.id).pending);}finally{shell("svc wifi enable");shell("svc data enable");}RelayQueue.kick(c,0);try{awaitRecovery(()->!dao.find(event.id).pending);}catch(AssertionError e){EventRecord current=dao.find(event.id);throw new AssertionError("Queue recovery: attempts="+current.attempts+" nextDelay="+(current.nextAt-System.currentTimeMillis())+" diagnostic="+prefs.get("queueDiagnostic","unknown"));}}
        try(var reader=new FileReader(new File(c.getFilesDir(),"relay-test-link.json"))){StringBuilder text=new StringBuilder();char[] b=new char[1024];int n;while((n=reader.read(b))!=-1)text.append(b,0,n);web=new JSONObject(text.toString()).getString("webToken");}
        shell("input keyevent KEYCODE_HOME");await(()->!MainActivity.visible);RelayApi api=new RelayApi(c);String open="cmd_"+UUID.randomUUID();JSONObject command=new JSONObject().put("commandId",open).put("eventId",event.id);api.callWithToken("/api/command/open",command,web);await(()->dao.commandCount(open)==1);
        await(()->Arrays.stream(c.getSystemService(android.app.NotificationManager.class).getActiveNotifications()).anyMatch(n->n.getTag()!=null&&n.getTag().equals("open:"+open)));
        api.callWithToken("/api/command/open",new JSONObject().put("commandId",open).put("eventId",event.id),web);Thread.sleep(3000);assertEquals(1,dao.commandCount(open));
        shell("am start -n kr.infosphere.galaxyrelay/.MainActivity");await(()->MainActivity.visible);Thread.sleep(1000);String direct="cmd_"+UUID.randomUUID();api.callWithToken("/api/command/open",new JSONObject().put("commandId",direct).put("eventId",event.id),web);await(()->dao.commandCount(direct)==1);try{await(()->Arrays.stream(output("dumpsys activity activities").split("\n")).anyMatch(line->(line.contains("mResumedActivity")||line.contains("topResumedActivity"))&&line.contains("kr.infosphere.relayfixture/.FixtureActivity")));}catch(AssertionError e){throw new AssertionError("Direct open destination not resumed; Relay focus="+MainActivity.visible);}
        String dismiss="cmd_"+UUID.randomUUID();api.callWithToken("/api/command/dismiss",new JSONObject().put("commandId",dismiss).put("eventId",event.id),web);await(()->dao.commandCount(dismiss)==1);await(()->RelayNotificationListener.current.findActive(event.notificationKey)==null);assertNotNull(dao.find(event.id));await(()->!dao.find(event.id).active);
        c.getSystemService(android.app.NotificationManager.class).cancel("open:"+open,0);
        String ackTag=tag+"-ack";shell("am broadcast -n kr.infosphere.relayfixture/.FixtureReceiver --es tag "+ackTag);await(()->dao.activeRecords().stream().anyMatch(e->e.notificationKey.contains(ackTag)));EventRecord ackEvent=dao.activeRecords().stream().filter(e->e.notificationKey.contains(ackTag)).findFirst().get();
        api.callWithToken("/api/settings",new JSONObject().put("commandId","cmd_"+UUID.randomUUID()).put("autoDismiss",true),web);
        // KV is eventually consistent: a fixed 1.5 second sleep can still read the prior setting.
        long settingsUntil=System.currentTimeMillis()+90000;boolean enabled=false;
        do{enabled=api.callWithToken("/api/status",null,web).optBoolean("autoDismiss");if(!enabled)Thread.sleep(2000);}while(!enabled&&System.currentTimeMillis()<settingsUntil);
        assertTrue("Auto-dismiss setting did not become visible",enabled);
        String ack="cmd_"+UUID.randomUUID();JSONObject accepted=api.callWithToken("/api/command/ack",new JSONObject().put("commandId",ack).put("eventId",ackEvent.id),web);assertFalse("Backend skipped ACK because its setting was stale",accepted.optBoolean("skipped"));await(()->dao.commandCount(ack)==1);await(()->!dao.find(ackEvent.id).active);api.callWithToken("/api/settings",new JSONObject().put("commandId","cmd_"+UUID.randomUUID()).put("autoDismiss",false),web);
    }
}
