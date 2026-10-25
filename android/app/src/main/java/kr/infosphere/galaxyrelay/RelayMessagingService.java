package kr.infosphere.galaxyrelay;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;
import java.util.UUID;
public class RelayMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){try{new Credentials(this).secret("fcm",token);}catch(Exception ignored){}RelayQueue.IO.execute(()->{try{if(!new Credentials(this).secret("token").isEmpty())new RelayApi(this).call("/api/android/update",new JSONObject().put("fcmToken",token),true);}catch(Exception ignored){new Credentials(this).setFlag("fcmDirty",true);}});}
    @Override public void onMessageReceived(RemoteMessage message){
        CommandHandler.receive(this,message.getData());
    }
}
