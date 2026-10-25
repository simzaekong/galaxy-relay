package kr.infosphere.galaxyrelay;
/** Session identity shared by BLE callbacks and the QR presentation. No credentials stored here. */
public final class PairingSession {
 private long generation;private String pairId="";private boolean authenticated;
 public synchronized long begin(String id){generation++;pairId=id;authenticated=false;return generation;}
 public synchronized void invalidate(){generation++;pairId="";authenticated=false;}
 public synchronized boolean current(long ticket){return ticket==generation&&!pairId.isEmpty();}
 public synchronized boolean authenticate(long ticket,String id){if(!current(ticket)||!pairId.equals(id))return false;authenticated=true;return true;}
 public synchronized void clearAuthentication(){authenticated=false;}
 public synchronized boolean authenticated(String id){return authenticated&&!id.isEmpty()&&pairId.equals(id);}
}
