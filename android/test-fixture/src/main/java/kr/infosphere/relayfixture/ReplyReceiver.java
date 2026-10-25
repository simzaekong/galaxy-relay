package kr.infosphere.relayfixture;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import org.json.JSONObject;
/** Only part of the separate debug fixture APK; records synthetic test replies, never logs them. */
public class ReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        Bundle data=RemoteInput.getResultsFromIntent(intent);if(data==null)return;
        var prefs=c.getSharedPreferences("replies",0);int count=prefs.getInt("count",0)+1;prefs.edit().putInt("count",count).commit();
        try(FileWriter out=new FileWriter(new File(c.getFilesDir(),"reply-result.json"))){out.write(new JSONObject().put("text",data.getCharSequence("text")).put("count",count).toString());}catch(Exception ignored){}
    }
}
