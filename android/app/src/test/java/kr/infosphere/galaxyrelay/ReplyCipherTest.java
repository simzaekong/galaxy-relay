package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.*;
public class ReplyCipherTest {
    @Test public void boundedTextAndEncryptedBindings() throws Exception {
        assertTrue(ReplyCipher.validText("곧 도착해요!\n🙂"));assertFalse(ReplyCipher.validText(" "));
        assertFalse(ReplyCipher.validText("x".repeat(501)));assertFalse(ReplyCipher.validText("hi\u0000"));
        byte[] raw=new byte[32],iv=new byte[12];new java.security.SecureRandom().nextBytes(raw);new java.security.SecureRandom().nextBytes(iv);
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString(raw),keyId=EventCipher.fingerprint(key),action="a".repeat(64);
        Map<String,String> data=new HashMap<>();data.put("commandId","cmd_"+UUID.randomUUID());data.put("eventId","evt_"+UUID.randomUUID());data.put("timestamp","1791540000000");data.put("nonce",UUID.randomUUID().toString());
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(ReplyCipher.aad(data,action,keyId).getBytes(StandardCharsets.UTF_8));
        String cipher=Base64.getUrlEncoder().withoutPadding().encodeToString(c.doFinal("곧 도착해요!\n🙂".getBytes(StandardCharsets.UTF_8))),nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(iv);
        assertEquals("곧 도착해요!\n🙂",ReplyCipher.decrypt(key,keyId,nonce,cipher,data,action));
        data.put("eventId","evt_"+UUID.randomUUID());try{ReplyCipher.decrypt(key,keyId,nonce,cipher,data,action);fail("Wrong target accepted");}catch(java.security.GeneralSecurityException expected){}
    }
    @Test public void replySignatureBindsTextAndAction() throws Exception {
        byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);String key=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Map<String,String> data=new HashMap<>();long now=System.currentTimeMillis();data.put("commandId","cmd_"+UUID.randomUUID());data.put("eventId","evt_"+UUID.randomUUID());data.put("nonce",UUID.randomUUID().toString());data.put("timestamp",String.valueOf(now));data.put("type","REPLY");data.put("autoDismiss","");data.put("reply","{\"actionId\":\""+"a".repeat(64)+"\",\"mode\":\"plain\",\"text\":\"곧 도착\\n🙂\"}");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(raw,"HmacSHA256"));String canonical=String.join("\n",data.get("commandId"),data.get("type"),data.get("eventId"),data.get("timestamp"),data.get("nonce"),"",data.get("reply"));data.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        assertTrue(CommandVerifier.valid(data,key,now));data.put("reply",data.get("reply").replace("도착","삭제"));assertFalse(CommandVerifier.valid(data,key,now));
    }
}
