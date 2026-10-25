package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
public class CommandVerifierTest {
    @Test public void cameraSessionAndTargetAreCoveredBySignature() throws Exception {
        byte[] key=new byte[32];long now=System.currentTimeMillis();Map<String,String> d=new HashMap<>();
        d.put("commandId","cmd_"+UUID.randomUUID());d.put("type","NATIVE_CAMERA");d.put("eventId","evt_"+UUID.randomUUID());d.put("timestamp",String.valueOf(now));d.put("nonce",UUID.randomUUID().toString());d.put("autoDismiss","signed-camera-grant");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        d.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(String.join("\n",d.get("commandId"),d.get("type"),d.get("eventId"),d.get("timestamp"),d.get("nonce"),d.get("autoDismiss")).getBytes(StandardCharsets.UTF_8))));
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        assertTrue(CommandVerifier.valid(d,encoded,now));d.put("autoDismiss","tampered");assertFalse(CommandVerifier.valid(d,encoded,now));
        d.put("autoDismiss","signed-camera-grant");d.put("eventId","");assertFalse(CommandVerifier.valid(d,encoded,now));
    }
    @Test public void locateGrantAndTargetAreCoveredBySignature() throws Exception {
        byte[] key=new byte[32];long now=System.currentTimeMillis();Map<String,String> d=new HashMap<>();
        d.put("commandId","cmd_"+UUID.randomUUID());d.put("type","NATIVE_LOCATE");d.put("eventId","evt_"+UUID.randomUUID());d.put("timestamp",String.valueOf(now));d.put("nonce",UUID.randomUUID().toString());d.put("autoDismiss","signed-locate-grant");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        d.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(String.join("\n",d.get("commandId"),d.get("type"),d.get("eventId"),d.get("timestamp"),d.get("nonce"),d.get("autoDismiss")).getBytes(StandardCharsets.UTF_8))));
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        assertTrue(CommandVerifier.valid(d,encoded,now));d.put("autoDismiss","tampered");assertFalse(CommandVerifier.valid(d,encoded,now));
        d.put("autoDismiss","signed-locate-grant");d.put("eventId","");assertFalse(CommandVerifier.valid(d,encoded,now));
    }
    @Test public void authenticFreshCommandOnly() throws Exception {
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);long now=System.currentTimeMillis();Map<String,String> d=new HashMap<>();
        d.put("commandId","cmd_"+UUID.randomUUID());d.put("type","OPEN");d.put("eventId","evt_"+UUID.randomUUID());d.put("timestamp",String.valueOf(now));d.put("nonce",UUID.randomUUID().toString());d.put("autoDismiss","");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));d.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(String.join("\n",d.get("commandId"),d.get("type"),d.get("eventId"),d.get("timestamp"),d.get("nonce"),"").getBytes(StandardCharsets.UTF_8))));
        assertTrue(CommandVerifier.valid(d,Base64.getUrlEncoder().withoutPadding().encodeToString(key),now));assertFalse(CommandVerifier.valid(d,Base64.getUrlEncoder().withoutPadding().encodeToString(key),now+130000));d.put("eventId","evt_"+UUID.randomUUID());assertFalse(CommandVerifier.valid(d,Base64.getUrlEncoder().withoutPadding().encodeToString(key),now));
    }
}
