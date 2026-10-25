package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class NativeProtocolTest {
 @Test public void authenticatedECDHAndGcmBindConnectionAndDirection()throws Exception{KeyPair a=NativeCrypto.pair(),b=NativeCrypto.pair();byte[] root=NativeCrypto.random(32);String transcript="welcome\nfixture-session";assertArrayEquals(NativeCrypto.shared(a,NativeCrypto.publicKey(b)),NativeCrypto.shared(b,NativeCrypto.publicKey(a)));
 byte[] key=NativeCrypto.hkdf(NativeCrypto.shared(a,NativeCrypto.publicKey(b)),NativeCrypto.mac(root,transcript.getBytes(StandardCharsets.UTF_8)),"GalaxyRelay BLE v2 session");
 NativeCrypto.Sealed packet=NativeCrypto.seal(key,"서연 · 안녕 🙂".getBytes(StandardCharsets.UTF_8),NativeCrypto.wireAAD("fixture","android-to-ios"));
 assertEquals("서연 · 안녕 🙂",new String(NativeCrypto.open(key,packet.iv,packet.ciphertext,NativeCrypto.wireAAD("fixture","android-to-ios")),StandardCharsets.UTF_8));
 try{NativeCrypto.open(key,packet.iv,packet.ciphertext,NativeCrypto.wireAAD("fixture","ios-to-android"));fail();}catch(javax.crypto.AEADBadTagException expected){}
 String proof=NativeCrypto.proof(NativeCrypto.b64(root),transcript);assertTrue(NativeCrypto.verify(NativeCrypto.b64(root),transcript,proof));assertFalse(NativeCrypto.verify(NativeCrypto.b64(root),transcript+"tampered",proof));}
 @Test public void frameBoundsFragmentationAndTimeout(){byte[] message=new byte[3072];Arrays.fill(message,(byte)71);byte[] packed=NativeFrames.pack(message);NativeFrames decoder=new NativeFrames();byte[] result=null;for(int i=0;i<packed.length;i+=20)result=decoder.feed(Arrays.copyOfRange(packed,i,Math.min(packed.length,i+20)),1000+i);assertArrayEquals(message,result);
 try{decoder.feed(new byte[]{127,127,127,127},1000);fail();}catch(IllegalArgumentException expected){}
 decoder.feed(new byte[]{0,0},1000);result=decoder.feed(NativeFrames.pack(new byte[]{1,2}),32000);assertArrayEquals(new byte[]{1,2},result);}
 @Test public void hkdfAndGcmMatchIndependentNodeVectors()throws Exception{byte[] ikm=new byte[22];Arrays.fill(ikm,(byte)11);byte[] salt=new byte[13];for(int i=0;i<13;i++)salt[i]=(byte)i;StringBuilder hex=new StringBuilder();for(byte b:NativeCrypto.hkdf(ikm,salt,"GalaxyRelay BLE v2 session"))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));assertEquals("e3d4e79ddd407ca643dd084f269bd05af0e56bcae37585263df661f7c502741a",hex.toString());assertEquals("native interop fixture",new String(NativeCrypto.open(NativeCrypto.bytes("ERERERERERERERERERERERERERERERERERERERERERE"),"FxcXFxcXFxcXFxcX","aY_BwSfSw6xJy7p7Rthc-glkro7z7JnxaPTpEqqAtk7sOQr-t98","GalaxyRelay/native-cloud/v2\nfixture-request\nfixture-device"),StandardCharsets.UTF_8));}
}
