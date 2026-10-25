package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class BleRecoveryTest {
 @Test public void backoffRemainsBoundedAfterLongOutage(){assertEquals(1000,BleRecovery.delay(0));assertEquals(5000,BleRecovery.delay(1));assertEquals(60000,BleRecovery.delay(10000));}
 @Test public void negotiatedMtuPreservesFrameReassembly(){
  assertEquals(20,BleRecovery.payload(23));assertEquals(182,BleRecovery.payload(185));assertEquals(185,BleRecovery.payload(517));
  byte[] original=new byte[6000];new java.util.Random(1).nextBytes(original);byte[] framed=NativeFrames.pack(original);NativeFrames frames=new NativeFrames();byte[] result=null;
  for(int i=0;i<framed.length;i+=182)result=frames.feed(java.util.Arrays.copyOfRange(framed,i,Math.min(i+182,framed.length)),1000+i);
  assertArrayEquals(original,result);
 }
 @Test public void longLivedSessionDoesNotFailAfter4096HistoricalMessages(){ReplayWindow w=new ReplayWindow();for(int i=0;i<10000;i++){long now=1000000L+i*1000L;assertTrue(w.accept("message-"+i,now,now));}}
 @Test public void validWindowReplaysAreNotEvictedEvenForFutureTimestamps(){ReplayWindow w=new ReplayWindow();assertTrue(w.accept("future",1120000,1000000));assertFalse(w.accept("future",1120000,1120001));assertFalse(w.accept("future",1120000,1240001));assertFalse(w.accept("old",1,1000000));}
 @Test public void saturationRejectsWithoutEvictingFreshIds(){ReplayWindow w=new ReplayWindow();for(int i=0;i<4096;i++)assertTrue(w.accept("id-"+i,1000000,1000000));assertFalse(w.accept("extra",1000000,1000000));assertFalse(w.accept("id-0",1000000,1000000));w.clear();assertTrue(w.accept("new-session",1000000,1000000));}
}
