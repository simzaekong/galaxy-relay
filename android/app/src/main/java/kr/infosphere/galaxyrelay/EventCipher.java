package kr.infosphere.galaxyrelay;
import android.content.Context;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
public final class EventCipher {
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    public static String generate(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);return ENCODER.encodeToString(key);}
    public static String fingerprint(String encoded)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(encoded));StringBuilder s=new StringBuilder();for(int i=0;i<16;i++)s.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]&255));return s.toString();}
    public static JSONObject protect(Context c,JSONObject event)throws Exception{Credentials p=new Credentials(c);return p.flag("e2eeEnabled",false)?encrypt(event,p.secret("e2eeKey")):event;}
    public static JSONObject encrypt(JSONObject event,String encoded)throws Exception{
        byte[] key=Base64.getUrlDecoder().decode(encoded);if(key.length!=32)throw new IllegalArgumentException("Invalid encryption key");
        JSONObject copy=new JSONObject(event.toString());byte[] plain=copy.toString().getBytes(StandardCharsets.UTF_8);
        // Fit within the Web Push 4 KiB limit while preserving the local notification mapping.
        while(plain.length>2600&&copy.optString("body").length()>0){String b=copy.getString("body");copy.put("body",b.substring(0,b.offsetByCodePoints(b.length(),-1)));plain=copy.toString().getBytes(StandardCharsets.UTF_8);}
        if(plain.length>2600)throw new IllegalArgumentException("Event too large");
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);String keyId=fingerprint(encoded),id=copy.getString("id");long time=copy.getLong("timestamp");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(("GalaxyRelay/v1\n"+id+"\n"+time+"\n"+keyId).getBytes(StandardCharsets.UTF_8));
        return new JSONObject().put("id",id).put("type","encrypted").put("version",1).put("timestamp",time).put("keyId",keyId).put("iv",ENCODER.encodeToString(iv)).put("ciphertext",ENCODER.encodeToString(cipher.doFinal(plain)));
    }
}
