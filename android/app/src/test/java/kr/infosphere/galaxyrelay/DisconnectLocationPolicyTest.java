package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class DisconnectLocationPolicyTest {
 @Test public void onlyConfirmedConnectionArmsOneCapture(){DisconnectLocationPolicy p=new DisconnectLocationPolicy();assertFalse(p.lost(0,true));p.connected();assertTrue(p.lost(0,true));assertFalse(p.lost(400000,true));}
 @Test public void flappingConnectionsHaveFiveMinuteLimit(){DisconnectLocationPolicy p=new DisconnectLocationPolicy();p.connected();assertTrue(p.lost(1000,true));p.connected();assertFalse(p.lost(2000,true));p.connected();assertTrue(p.lost(301000,true));}
 @Test public void PermissionsAndExplicitStopNeverDeferAQuery(){DisconnectLocationPolicy p=new DisconnectLocationPolicy();p.connected();assertFalse(p.lost(0,false));assertFalse(p.lost(1000,true));p.connected();p.stopped();assertFalse(p.lost(400000,true));}
 @Test public void rejectsStaleAndInvalidFixes(){assertTrue(DisconnectLocationPolicy.valid(37,127,100,100000,100000,101000));assertFalse(DisconnectLocationPolicy.valid(91,127,100,100000,100000,101000));assertFalse(DisconnectLocationPolicy.valid(37,127,-1,100000,100000,101000));assertFalse(DisconnectLocationPolicy.valid(37,127,100,1,100000,101000));assertFalse(DisconnectLocationPolicy.valid(37,127,100,121000,100000,121000));assertFalse(DisconnectLocationPolicy.valid(Double.NaN,127,100,100000,100000,101000));}
}
