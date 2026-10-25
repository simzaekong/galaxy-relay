package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.util.UUID;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CryptoInteropTest {
    @Test public void javaAesGcmVector()throws Exception{
        String key=EventCipher.generate();JSONObject original=new JSONObject().put("id","evt_"+UUID.randomUUID()).put("type","notification").put("title","김철수").put("body","야 어디야?").put("app","카카오톡").put("timestamp",System.currentTimeMillis());
        JSONObject wire=EventCipher.encrypt(original,key);assertFalse(wire.toString().contains("김철수"));assertFalse(wire.toString().contains("카카오톡"));assertNotEquals(wire.getString("iv"),EventCipher.encrypt(original,key).getString("iv"));
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        if("true".equals(InstrumentationRegistry.getArguments().getString("relayE2EE"))){Credentials prefs=new Credentials(context);prefs.secret("e2eeKey",key);prefs.setFlag("e2eeEnabled",true);assertTrue(context.getSharedPreferences("relay",android.content.Context.MODE_PRIVATE).edit().commit());}
        try(FileWriter out=new FileWriter(new File(context.getFilesDir(),"e2ee-test-vector.json"))){out.write(new JSONObject().put("key",key).put("wire",wire).put("original",original).toString());}
    }
}
