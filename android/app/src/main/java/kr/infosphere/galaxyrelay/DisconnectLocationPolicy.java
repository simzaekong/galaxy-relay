package kr.infosphere.galaxyrelay;
/** Only one loss after a confirmed connection can initiate an attempt. */
public final class DisconnectLocationPolicy {
 private boolean armed;private Long lastAttempt;
 public void connected(){armed=true;}
 public void stopped(){armed=false;}
 public boolean lost(long elapsed,boolean allowed){boolean was=armed;armed=false;if(!was||!allowed)return false;if(lastAttempt!=null&&elapsed-lastAttempt<300000)return false;lastAttempt=elapsed;return true;}
 public static boolean valid(double lat,double lon,float accuracy,long measuredAt,long triggeredAt,long now){return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180&&Float.isFinite(accuracy)&&accuracy>=0&&accuracy<=10000&&measuredAt>=triggeredAt-30000&&measuredAt<=now+5000&&now>=triggeredAt&&now-triggeredAt<=20000;}
}
