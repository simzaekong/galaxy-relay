package kr.infosphere.galaxyrelay;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
@RunWith(AndroidJUnit4.class)
public class ReplyActionsTest {
    @Test public void originalMutableRemoteInputReceivesReplyAndRejectsChangedAction() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertFalse(c.getSystemService(KeyguardManager.class).isKeyguardLocked());
        Credentials prefs=new Credentials(c);boolean previous=prefs.flag("e2eeEnabled",false);prefs.setFlag("e2eeEnabled",false);
        String action="relay.test.REPLY."+UUID.randomUUID();CountDownLatch received=new CountDownLatch(1);AtomicReference<String> text=new AtomicReference<>();
        BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){Bundle result=RemoteInput.getResultsFromIntent(intent);if(result!=null){text.set(result.getCharSequence("message").toString());received.countDown();}}};
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,new IntentFilter(action),Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(receiver,new IntentFilter(action));
        PendingIntent pi=PendingIntent.getBroadcast(c,141,new Intent(action).setPackage(c.getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
        try {
            Notification.Action reply=new Notification.Action.Builder(null,"답장",pi).addRemoteInput(new RemoteInput.Builder("message").setLabel("답장").build()).build();
            Notification notification=new Notification.Builder(c,"test").setSmallIcon(R.drawable.ic_relay).addAction(reply).build();JSONObject metadata=ReplyActions.describe(notification,c.getPackageName());assertNotNull(metadata);
            JSONObject event=new JSONObject().put("reply",metadata);Map<String,String> data=new HashMap<>();data.put("reply",new JSONObject().put("actionId",metadata.getString("actionId")).put("mode","plain").put("text","RemoteInput 테스트\n🙂").toString());
            assertEquals("REPLY_SENT",ReplyActions.execute(c,notification,c.getPackageName(),event,data));assertTrue(received.await(5,TimeUnit.SECONDS));assertEquals("RemoteInput 테스트\n🙂",text.get());
            data.put("reply",new JSONObject().put("actionId","a".repeat(64)).put("mode","plain").put("text","wrong action").toString());assertEquals("REPLY_ACTION_CHANGED",ReplyActions.execute(c,notification,c.getPackageName(),event,data));
            assertNull(ReplyActions.describe(notification,"wrong.package"));
        } finally {pi.cancel();c.unregisterReceiver(receiver);prefs.setFlag("e2eeEnabled",previous);}
    }
    @Test public void immutableAndChoiceOnlyActionsAreNotAdvertised() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();PendingIntent pi=PendingIntent.getBroadcast(c,142,new Intent("relay.test.UNSUPPORTED").setPackage(c.getPackageName()),PendingIntent.FLAG_IMMUTABLE);
        try {
            Notification.Action choice=new Notification.Action.Builder(null,"선택",pi).addRemoteInput(new RemoteInput.Builder("choice").setAllowFreeFormInput(false).setChoices(new String[]{"yes"}).build()).build();
            assertNull(ReplyActions.describe(new Notification.Builder(c,"test").setSmallIcon(R.drawable.ic_relay).addAction(choice).build(),c.getPackageName()));
            if(Build.VERSION.SDK_INT>=31){Notification.Action immutable=new Notification.Action.Builder(null,"답장",pi).addRemoteInput(new RemoteInput.Builder("text").build()).build();assertNull(ReplyActions.describe(new Notification.Builder(c,"test").setSmallIcon(R.drawable.ic_relay).addAction(immutable).build(),c.getPackageName()));}
        }finally{pi.cancel();}
    }
}
