package kr.infosphere.galaxyrelay;
import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.widget.Toast;
public class OpenNotificationActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);if(getSystemService(KeyguardManager.class).isKeyguardLocked()){finish();return;}
        String eventId=getIntent().getStringExtra("eventId"),commandId=getIntent().getStringExtra("commandId");RelayQueue.IO.execute(()->{EventRecord event=RelayDatabase.get(this).dao().find(eventId);RelayNotificationListener listener=RelayNotificationListener.current;var active=event==null||listener==null||!event.active?null:listener.findActive(event.notificationKey);
            runOnUiThread(()->{String result="NOT_FOUND";try{if(active!=null&&active.getNotification().contentIntent!=null){CommandHandler.send(active.getNotification().contentIntent,this);result="OPEN_SENT";}else Toast.makeText(this,"원본 알림을 더 이상 열 수 없습니다.",Toast.LENGTH_LONG).show();}catch(Exception e){result="FAILED";}CommandHandler.result(this,commandId,eventId,result);finish();});});
    }
}
