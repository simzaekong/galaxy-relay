package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class BatterySyncPolicyTest {
 @Test public void unchangedBatteryNeverCreatesHeartbeat(){BatterySyncPolicy p=new BatterySyncPolicy();assertEquals(0,p.delay(80,"unplugged",0));p.sent(80,"unplugged",0);assertEquals(-1,p.delay(80,"unplugged",86400000));}
 @Test public void levelChangesCoalesceAndReturningToPreviousValueCancels(){BatterySyncPolicy p=new BatterySyncPolicy();p.sent(80,"unplugged",1000);assertEquals(290000,p.delay(79,"unplugged",11000));assertEquals(-1,p.delay(80,"unplugged",12000));assertEquals(0,p.delay(75,"unplugged",301000));}
 @Test public void chargingTransitionsUseOneMinuteMinimum(){BatterySyncPolicy p=new BatterySyncPolicy();p.sent(80,"unplugged",1000);assertEquals(50000,p.delay(80,"charging",11000));assertEquals(0,p.delay(82,"charging",61000));}
 @Test public void reconnectGetsImmediateSnapshotAndClockRollbackCannotFlood(){BatterySyncPolicy p=new BatterySyncPolicy();p.sent(80,"unplugged",1000);assertEquals(300000,p.delay(79,"unplugged",0));p.reset();assertEquals(0,p.delay(80,"unplugged",2000));}
}
