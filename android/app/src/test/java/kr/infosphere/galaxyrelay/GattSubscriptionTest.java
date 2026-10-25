package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class GattSubscriptionTest {
 @Test public void rejectionCodesIdentifyEachGate(){
  byte[] valid={2,0};
  assertEquals("G03-D",GattSubscription.rejection(false,true,valid,false,0));
  assertEquals("G03-C",GattSubscription.rejection(true,false,valid,false,0));
  assertEquals("G03-P",GattSubscription.rejection(true,true,valid,true,0));
  assertEquals("G03-O",GattSubscription.rejection(true,true,valid,false,1));
  assertEquals("G03-L",GattSubscription.rejection(true,true,null,false,0));
  assertEquals("G03-L",GattSubscription.rejection(true,true,new byte[0],false,0));
  assertEquals("G03-V",GattSubscription.rejection(true,true,new byte[]{4,0},false,0));
  assertEquals("G03-V",GattSubscription.rejection(true,true,new byte[]{1,1},false,0));
  assertEquals("",GattSubscription.rejection(true,true,valid,false,0));
 }
 @Test public void validationAndDiagnosticsAgreeForEveryTwoByteValue(){
  for(int first=0;first<256;first++)for(int second=0;second<256;second++){
   byte[] value={(byte)first,(byte)second};GattSubscription c=new GattSubscription();
   assertEquals(c.write(value,false,0)==GattSubscription.Result.OK,GattSubscription.rejection(true,true,value,false,0).isEmpty());
  }
 }
 @Test public void readsInitialDisabledAndBothSupportedModes(){GattSubscription c=new GattSubscription();assertArrayEquals(new byte[]{0,0},c.read(0));assertFalse(c.enabled());assertEquals(GattSubscription.Result.OK,c.write(new byte[]{1,0},false,0));assertTrue(c.enabled());assertFalse(c.confirm());assertEquals(GattSubscription.Result.OK,c.write(new byte[]{2,0},false,0));assertTrue(c.confirm());assertArrayEquals(new byte[]{2,0},c.read(0));assertEquals(GattSubscription.Result.OK,c.write(new byte[]{3,0},false,0));assertTrue(c.confirm());}
 @Test public void malformedOrPreparedWritesDoNotChangeExistingSubscription(){GattSubscription c=new GattSubscription();c.write(new byte[]{2,0},false,0);assertEquals(GattSubscription.Result.INVALID_LENGTH,c.write(new byte[]{1},false,0));assertEquals(GattSubscription.Result.INVALID_OFFSET,c.write(new byte[]{1,0},false,1));assertEquals(GattSubscription.Result.UNSUPPORTED,c.write(new byte[]{1,0},true,0));assertEquals(GattSubscription.Result.UNSUPPORTED,c.write(new byte[]{4,0},false,0));assertEquals(GattSubscription.Result.UNSUPPORTED,c.write(new byte[]{1,1},false,0));assertArrayEquals(new byte[]{2,0},c.read(0));}
 @Test public void disablingAndResetClearStateAndReadOffsetsAreBounded(){GattSubscription c=new GattSubscription();c.write(new byte[]{2,0},false,0);c.write(new byte[]{0,0},false,0);assertFalse(c.enabled());assertArrayEquals(new byte[]{0},c.read(1));assertArrayEquals(new byte[0],c.read(2));try{c.read(3);fail();}catch(IllegalArgumentException expected){}c.write(new byte[]{1,0},false,0);c.reset();assertFalse(c.enabled());}
}
