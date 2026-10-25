package kr.infosphere.relayfixture;
import android.app.*;
import android.content.*;
public class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("test","Integration test",NotificationManager.IMPORTANCE_HIGH));
        PendingIntent original=PendingIntent.getActivity(c,0,new Intent(c,FixtureActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder notification=new Notification.Builder(c,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("RelayIntegration").setContentText("Original notification test").setContentIntent(original).setAutoCancel(true);
        if(intent.getBooleanExtra("reply",false)){
            PendingIntent target=PendingIntent.getBroadcast(c,991,new Intent(c,ReplyReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            notification.addAction(new Notification.Action.Builder(null,"답장",target).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).addRemoteInput(new RemoteInput.Builder("text").build()).build());
        }
        manager.notify(intent.getStringExtra("tag"),1,notification.build());
    }
}
