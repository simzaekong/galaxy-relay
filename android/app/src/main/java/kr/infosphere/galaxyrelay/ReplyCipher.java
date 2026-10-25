package kr.infosphere.galaxyrelay;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** No Android dependencies: shares the authenticated reply format with Web Crypto. */
public final class ReplyCipher {
    public static boolean validText(String text) {
        return text != null && !text.trim().isEmpty() && text.length() <= 500
            && text.getBytes(StandardCharsets.UTF_8).length <= 1500
            && !text.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f].*");
    }
    public static String aad(Map<String,String> data, String actionId, String keyId) {
        return String.join("\n", "GalaxyRelay/reply/v1", data.get("commandId"), data.get("eventId"),
            data.get("timestamp"), data.get("nonce"), actionId, keyId);
    }
    public static String decrypt(String encodedKey, String keyId, String iv, String ciphertext,
                                 Map<String,String> data, String actionId) throws Exception {
        byte[] key = Base64.getUrlDecoder().decode(encodedKey);
        if(key.length != 32 || !keyId.matches("[0-9a-f]{32}") || !iv.matches("[A-Za-z0-9_-]{16}")
            || !ciphertext.matches("[A-Za-z0-9_-]{23,2022}")) throw new IllegalArgumentException();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
        StringBuilder fingerprint = new StringBuilder();
        for(int i=0; i<16; i++) fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]&255));
        if(!fingerprint.toString().equals(keyId)) throw new IllegalArgumentException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getUrlDecoder().decode(iv)));
        java.util.Arrays.fill(key,(byte)0);
        cipher.updateAAD(aad(data,actionId,keyId).getBytes(StandardCharsets.UTF_8));
        byte[] plain = cipher.doFinal(Base64.getUrlDecoder().decode(ciphertext));
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plain)).toString();
            if(!validText(text)) throw new IllegalArgumentException();
            return text;
        } finally { java.util.Arrays.fill(plain,(byte)0); }
    }
}
