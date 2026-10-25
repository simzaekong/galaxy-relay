package kr.infosphere.galaxyrelay;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Standard P-256/ECDH, HKDF-SHA256 and AES-256-GCM. No Android dependencies. */
public final class NativeCrypto {
 public static byte[] bytes(String s){return Base64.getUrlDecoder().decode(s);}
 public static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static byte[] random(int count){byte[] b=new byte[count];new SecureRandom().nextBytes(b);return b;}
 public static byte[] mac(byte[] key,byte[] value)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(value);}
 public static String proof(String key,String value)throws Exception{return b64(mac(bytes(key),value.getBytes(StandardCharsets.UTF_8)));}
 public static boolean verify(String key,String value,String proof){try{return MessageDigest.isEqual(bytes(proof),bytes(proof(key,value)));}catch(Exception e){return false;}}
 public static byte[] hkdf(byte[] shared,byte[] salt,String info)throws Exception{byte[] prk=mac(salt,shared),data=info.getBytes(StandardCharsets.UTF_8),input=Arrays.copyOf(data,data.length+1);input[data.length]=1;return mac(prk,input);}
 public static KeyPair pair()throws Exception{KeyPairGenerator k=KeyPairGenerator.getInstance("EC");k.initialize(new ECGenParameterSpec("secp256r1"));return k.generateKeyPair();}
 private static byte[] coordinate(java.math.BigInteger v){byte[] b=v.toByteArray(),out=new byte[32];System.arraycopy(b,Math.max(0,b.length-32),out,Math.max(0,32-b.length),Math.min(b.length,32));return out;}
 public static String publicKey(KeyPair key){ECPublicKey p=(ECPublicKey)key.getPublic();byte[] out=new byte[65];out[0]=4;System.arraycopy(coordinate(p.getW().getAffineX()),0,out,1,32);System.arraycopy(coordinate(p.getW().getAffineY()),0,out,33,32);return b64(out);}
 public static byte[] shared(KeyPair local,String encoded)throws Exception{byte[] raw=bytes(encoded);if(raw.length!=65||raw[0]!=4)throw new GeneralSecurityException();ECPublicKey source=(ECPublicKey)local.getPublic();java.math.BigInteger x=new java.math.BigInteger(1,Arrays.copyOfRange(raw,1,33)),y=new java.math.BigInteger(1,Arrays.copyOfRange(raw,33,65));PublicKey remote=KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x,y),source.getParams()));KeyAgreement a=KeyAgreement.getInstance("ECDH");a.init(local.getPrivate());a.doPhase(remote,true);return a.generateSecret();}
 public static final class Sealed {public final String iv,ciphertext;Sealed(String i,String c){iv=i;ciphertext=c;}}
 public static Sealed seal(byte[] key,byte[] value,String aad)throws Exception{byte[] iv=random(12);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return new Sealed(b64(iv),b64(c.doFinal(value)));}
 public static byte[] open(byte[] key,String iv,String data,String aad)throws Exception{if(key.length!=32||bytes(iv).length!=12)throw new GeneralSecurityException();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,bytes(iv)));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return c.doFinal(bytes(data));}
 public static String wireAAD(String id,String direction){return "GalaxyRelay/ble/v2\n"+id+"\n"+direction;}
 public static String cloudAAD(String requestId,String deviceId){return "GalaxyRelay/native-cloud/v2\n"+requestId+"\n"+deviceId;}
 public static String locationAAD(String requestId,String deviceId){return "GalaxyRelay/native-location/v1\n"+requestId+"\n"+deviceId;}
}

