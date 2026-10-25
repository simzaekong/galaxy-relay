package kr.infosphere.galaxyrelay;
/** Ownership is acquired by a Relay subscription, never a generic BLE connection/read. */
public final class GattPeer<T> {
 private T peer;private long generation;
 public synchronized T current(){return peer;}
 public synchronized long generation(){return generation;}
 public synchronized boolean matches(T candidate){return peer!=null&&peer.equals(candidate);}
 public synchronized boolean current(T candidate,long ticket){return generation==ticket&&matches(candidate);}
 public synchronized boolean claim(T candidate){
  if(candidate==null)return false;
  if(peer!=null)return matches(candidate);
  peer=candidate;generation++;return true;
 }
 public synchronized boolean release(T candidate){if(!matches(candidate))return false;clear();return true;}
 public synchronized void clear(){peer=null;generation++;}
}
