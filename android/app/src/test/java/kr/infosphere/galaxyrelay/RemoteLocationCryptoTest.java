package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class RemoteLocationCryptoTest {
 @Test public void locationCannotBeReadForAnotherRequestDeviceOrProtocol()throws Exception{
  byte[] key=NativeCrypto.random(32),plain="one-shot location".getBytes(StandardCharsets.UTF_8);
  String aad=NativeCrypto.locationAAD("request","device");
  NativeCrypto.Sealed packet=NativeCrypto.seal(key,plain,aad);
  assertArrayEquals(plain,NativeCrypto.open(key,packet.iv,packet.ciphertext,aad));
  for(String wrong:new String[]{NativeCrypto.locationAAD("other","device"),NativeCrypto.locationAAD("request","other"),NativeCrypto.cloudAAD("request","device")}){
   try{NativeCrypto.open(key,packet.iv,packet.ciphertext,wrong);fail("Wrong context accepted");}catch(java.security.GeneralSecurityException expected){}
  }
 }
}
