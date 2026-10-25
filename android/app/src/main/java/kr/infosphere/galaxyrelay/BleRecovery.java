package kr.infosphere.galaxyrelay;
/** Bounded retry and ATT payload policy; no keepalive traffic or wake locks. */
public final class BleRecovery {
 private BleRecovery(){}
 public static long delay(int attempt){return new long[]{1000,5000,15000,30000,60000}[Math.max(0,Math.min(4,attempt))];}
 public static int payload(int mtu){return Math.max(20,Math.min(185,mtu-3));}
}
