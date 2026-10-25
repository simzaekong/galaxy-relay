package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class QueuePolicyTest {@Test public void boundedBackoff(){assertEquals(5000,RelayQueue.delay(0));assertEquals(15000,RelayQueue.delay(1));assertEquals(300000,RelayQueue.delay(99));}}
