package kr.infosphere.galaxyrelay;
/** No conditional forwarding based on unknown/stale connectivity. */
public final class OfflineAppPolicy {
 public static boolean offline(boolean connected,String state,long receivedAt,long now){return connected&&"offline".equals(state)&&receivedAt>=0&&now>=receivedAt&&now-receivedAt<=1000;}
 public static boolean allowed(boolean always,boolean offlineOnly,boolean confirmedOffline){return always||offlineOnly&&confirmedOffline;}
}
