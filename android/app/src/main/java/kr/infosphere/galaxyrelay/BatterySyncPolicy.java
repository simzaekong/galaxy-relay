package kr.infosphere.galaxyrelay;
/** Event-driven, coalesced updates. Time uses elapsed realtime, never the device wall clock. */
public final class BatterySyncPolicy {
 private int level;private String state;private long sentAt;
 public long delay(int nextLevel,String nextState,long now){
  if(state==null)return 0;
  if(level==nextLevel&&state.equals(nextState))return -1;
  long interval=state.equals(nextState)?300000:60000;
  return Math.max(0,interval-Math.max(0,now-sentAt));
 }
 public void sent(int value,String status,long now){level=value;state=status;sentAt=now;}
 public void reset(){state=null;}
}
