package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkHealthTest {
 @Test public void idleProbeIsSparse(){LinkHealth h=new LinkHealth();h.reset(0);assertFalse(h.due(299999));assertTrue(h.due(300000));}
 @Test public void onePendingAndMatchingResponseOnly(){LinkHealth h=new LinkHealth();h.reset(0);assertTrue(h.begin("one",300000));assertFalse(h.begin("two",300001));assertFalse(h.acknowledge("wrong",300001));assertTrue(h.expired(330000));assertTrue(h.acknowledge("one",330001));assertFalse(h.expired(999999));assertFalse(h.due(630000));assertTrue(h.due(630001));}
 @Test public void resetCancelsOldSessionProbe(){LinkHealth h=new LinkHealth();h.begin("old",1);h.reset(2);assertFalse(h.acknowledge("old",3));assertFalse(h.expired(999999));}
}
