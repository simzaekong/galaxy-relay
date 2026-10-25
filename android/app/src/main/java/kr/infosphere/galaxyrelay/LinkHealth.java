package kr.infosphere.galaxyrelay;
/** Monotonic, one outstanding authenticated probe. No wall-clock scheduling. */
public final class LinkHealth {
 public static final long INTERVAL=300000,TIMEOUT=30000;
 private String pending="";private long next,deadline;
 public void reset(long now){pending="";next=now+INTERVAL;deadline=0;}
 public boolean due(long now){return pending.isEmpty()&&now>=next;}
 public boolean begin(String id,long now){if(!pending.isEmpty())return false;pending=id;deadline=now+TIMEOUT;return true;}
 public boolean acknowledge(String id,long now){if(pending.isEmpty()||!pending.equals(id))return false;reset(now);return true;}
 public boolean expired(long now){return !pending.isEmpty()&&now>=deadline;}
}
