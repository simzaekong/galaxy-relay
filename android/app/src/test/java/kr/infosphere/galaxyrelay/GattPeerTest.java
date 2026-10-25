package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class GattPeerTest {
 @Test public void subscriptionClaimsOnlyOnePeer(){
  GattPeer<String> p=new GattPeer<>();assertNull(p.current());
  assertTrue(p.claim("iphone"));long ticket=p.generation();
  assertTrue(p.claim(new String("iphone")));assertEquals(ticket,p.generation());
  assertFalse(p.claim("other"));assertEquals("iphone",p.current());
  assertFalse(p.release("other"));assertTrue(p.current("iphone",ticket));
 }
 @Test public void timedOutOwnerIsReleasedWithoutWaitingForDisconnectCallback(){
  GattPeer<String> p=new GattPeer<>();p.claim("old");long old=p.generation();
  assertTrue(p.release("old"));assertTrue(p.claim("iphone"));
  assertFalse(p.current("old",old));assertFalse(p.release("old"));
  assertEquals("iphone",p.current());
 }
 @Test public void oldTimeoutCannotDisconnectReconnectedSameAddress(){
  GattPeer<String> p=new GattPeer<>();p.claim("iphone");long old=p.generation();
  p.release("iphone");p.claim("iphone");assertFalse(p.current("iphone",old));
  assertTrue(p.current("iphone",p.generation()));
 }
 @Test public void resetInvalidatesQueuedWorkAndNullNeverClaims(){
  GattPeer<String> p=new GattPeer<>();assertFalse(p.claim(null));
  p.claim("iphone");long old=p.generation();p.clear();
  assertNull(p.current());assertFalse(p.current("iphone",old));
 }
}
