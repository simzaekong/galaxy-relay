package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class OfflineAppPolicyTest {
 @Test public void modesRemainDistinct(){assertTrue(OfflineAppPolicy.allowed(true,false,false));assertTrue(OfflineAppPolicy.allowed(false,true,true));assertFalse(OfflineAppPolicy.allowed(false,true,false));assertFalse(OfflineAppPolicy.allowed(false,false,true));}
 @Test public void freshOfflineProofOnly(){assertTrue(OfflineAppPolicy.offline(true,"offline",1000,2000));assertFalse(OfflineAppPolicy.offline(true,"online",1000,1001));assertFalse(OfflineAppPolicy.offline(true,"unknown",1000,1001));assertFalse(OfflineAppPolicy.offline(false,"offline",1000,1001));}
 @Test public void staleAndClockReversalCannotEnable(){assertFalse(OfflineAppPolicy.offline(true,"offline",1000,2001));assertFalse(OfflineAppPolicy.offline(true,"offline",1000,999));assertFalse(OfflineAppPolicy.offline(true,"offline",-1,0));}
}
