package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class CallControlPolicyTest {
 private final String id="evt_550e8400-e29b-41d4-a716-446655440000";
 @Test public void freshTrackedIncomingOnly(){assertTrue(CallControlPolicy.allowed(id,id,"RINGING",true,10000,10000));assertTrue(CallControlPolicy.allowed(id,id,"OFFHOOK",true,10000,10000));}
 @Test public void neverEndOtherOrAmbiguousCalls(){assertFalse(CallControlPolicy.allowed(id,"","RINGING",true,10000,10000));assertFalse(CallControlPolicy.allowed(id,id,"OFFHOOK",false,10000,10000));assertFalse(CallControlPolicy.allowed(id,id,"IDLE",true,10000,10000));}
 @Test public void delayedFutureAndMalformedRequestsFail(){assertFalse(CallControlPolicy.allowed(id,id,"OFFHOOK",true,4999,10000));assertFalse(CallControlPolicy.allowed(id,id,"RINGING",true,11001,10000));assertFalse(CallControlPolicy.allowed("bad","bad","RINGING",true,10000,10000));}
}
