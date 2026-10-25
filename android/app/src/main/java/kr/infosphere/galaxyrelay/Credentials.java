package kr.infosphere.galaxyrelay;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Credentials {
    private final SharedPreferences prefs;
    public Credentials(Context c) { prefs=c.getSharedPreferences("relay",Context.MODE_PRIVATE); }
    private static synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if (!store.containsAlias("relay-credentials")) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("relay-credentials",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
        return (SecretKey)store.getKey("relay-credentials",null);
    }
    public synchronized void secret(String name,String value) throws Exception {
        prefs.edit().putString(name,seal(value)).apply();
    }
    public String seal(String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    public synchronized String secret(String name) {
        return open(prefs.getString(name,""));
    }
    public String open(String s) {
        try {if(s.isEmpty())return "";String[] parts=s.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
    public String get(String k,String fallback){return prefs.getString(k,fallback);}
    public void put(String k,String v){prefs.edit().putString(k,v).apply();}
    public boolean flag(String k,boolean fallback){return prefs.getBoolean(k,fallback);}
    public void setFlag(String k,boolean v){prefs.edit().putBoolean(k,v).apply();}
    public void nativePeer(String id,String token)throws Exception{synchronized(Credentials.class){prefs.edit().putString("nativeDeviceId",id).putString("nativeToken",seal(token)).commit();}}
    public void remove(String key){prefs.edit().remove(key).apply();}
    public void pruneNativePulls(){for(String name:prefs.getAll().keySet()){if(!name.startsWith("nativePull:"))continue;try{if(new org.json.JSONObject(secret(name)).getLong("expires")<System.currentTimeMillis())remove(name);}catch(Exception ignored){remove(name);}}}
    public boolean flush(){return prefs.edit().commit();}
    public void clear(){prefs.edit().clear().commit();}
}
