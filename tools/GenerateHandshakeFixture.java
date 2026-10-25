import kr.infosphere.galaxyrelay.NativeCrypto;
import java.security.*;
import java.security.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class GenerateHandshakeFixture {
 public static void main(String[] args)throws Exception{
  String id="550e8400-e29b-41d4-a716-446655440000";
  byte[] root=new byte[32],cn=new byte[32],sn=new byte[32];Arrays.fill(root,(byte)17);Arrays.fill(cn,(byte)34);Arrays.fill(sn,(byte)51);
  AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));ECParameterSpec spec=parameters.getParameterSpec(ECParameterSpec.class);
  KeyFactory kf=KeyFactory.getInstance("EC");KeyPair client=new KeyPair(kf.generatePublic(new ECPublicKeySpec(spec.getGenerator(),spec)),kf.generatePrivate(new ECPrivateKeySpec(java.math.BigInteger.ONE,spec)));
  KeyPair server=NativeCrypto.pair();String nonce=NativeCrypto.b64(cn),serverNonce=NativeCrypto.b64(sn),pub=NativeCrypto.publicKey(client),serverPub=NativeCrypto.publicKey(server);
  String hello="hello\n"+id+"\n"+nonce+"\n"+pub,transcript="welcome\n"+id+"\n"+nonce+"\n"+serverNonce+"\n"+pub+"\n"+serverPub;
  byte[] session=NativeCrypto.hkdf(NativeCrypto.shared(server,pub),NativeCrypto.mac(root,transcript.getBytes(StandardCharsets.UTF_8)),"GalaxyRelay BLE v2 session");
  NativeCrypto.Sealed config=NativeCrypto.seal(session,"fixture server configuration".getBytes(StandardCharsets.UTF_8),NativeCrypto.wireAAD(id,"android-to-ios"));
  String[] keys={"pairId","root","nonce","publicKey","helloProof","serverNonce","serverPublicKey","welcomeProof","session","iv","ciphertext"};
  String[] values={id,NativeCrypto.b64(root),nonce,pub,NativeCrypto.proof(NativeCrypto.b64(root),hello),serverNonce,serverPub,NativeCrypto.proof(NativeCrypto.b64(root),transcript),NativeCrypto.b64(session),config.iv,config.ciphertext};
  StringBuilder out=new StringBuilder("{");for(int i=0;i<keys.length;i++){if(i>0)out.append(',');out.append('"').append(keys[i]).append("\":\"").append(values[i]).append('"');}System.out.print(out.append('}'));
 }
}