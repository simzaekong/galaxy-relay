package kr.infosphere.galaxyrelay;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class CommandVerifier {
    public static boolean valid(Map<String,String> data,String key,long now){
        try {
            long time=Long.parseLong(data.get("timestamp"));if(Math.abs(now-time)>120000)return false;
            String uuid="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
            if(!data.getOrDefault("commandId","").matches("cmd_"+uuid)||!data.getOrDefault("nonce","").matches(uuid))return false;
            if(!java.util.Arrays.asList("CALL_HANGUP","OPEN","DISMISS","ACK","REPLY","PING","SETTINGS_SYNC","NATIVE_PULL","NATIVE_ACK","NATIVE_REVOKE","NATIVE_LOCATE","NATIVE_CAMERA").contains(data.get("type")))return false;
            if(java.util.Arrays.asList("CALL_HANGUP","OPEN","DISMISS","ACK","REPLY","NATIVE_PULL","NATIVE_ACK","NATIVE_LOCATE","NATIVE_CAMERA").contains(data.get("type"))&&!data.getOrDefault("eventId","").matches("evt_"+uuid))return false;
            String value=String.join("\n",data.get("commandId"),data.get("type"),data.getOrDefault("eventId",""),data.get("timestamp"),data.get("nonce"),data.getOrDefault("autoDismiss",""));
            if("REPLY".equals(data.get("type"))) {
                String reply=data.getOrDefault("reply","");if(reply.isEmpty() || reply.length()>3000)return false;
                value+="\n"+reply;
            }
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(key),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.getUrlDecoder().decode(data.get("signature")));
        }catch(Exception e){return false;}
    }
}
