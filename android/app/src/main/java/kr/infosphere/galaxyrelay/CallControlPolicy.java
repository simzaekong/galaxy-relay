package kr.infosphere.galaxyrelay;
/** Only the single tracked incoming call may be ended. Never queue this action. */
public final class CallControlPolicy {
 public static boolean allowed(String requested,String current,String state,boolean safe,long sent,long now){
  return requested!=null&&requested.matches("evt_[0-9a-f-]{36}")&&requested.equals(current)&&safe&&
   ("RINGING".equals(state)||"OFFHOOK".equals(state))&&sent<=now+1000&&sent>=now-5000;
 }
}
