package kr.infosphere.galaxyrelay;
import android.content.*;
import android.os.BatteryManager;
import org.json.JSONObject;
public final class DeviceBattery {
 public record Reading(int level,String state){
  public JSONObject json()throws Exception{return new JSONObject().put("level",level<0?JSONObject.NULL:level).put("state",state);}
  public String label(){return (level<0?"—":level+"%")+switch(state){case "charging"->" · 충전 중";case "full"->" · 충전 완료";case "plugged"->" · 전원 연결";default->"";};}
 }
 private static volatile Reading current=new Reading(-1,"unknown");
 public static Reading current(){return current;}
 public static void refresh(Context c){Intent i=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i!=null)update(i);}
 public static void update(Intent i){int value=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);int level=value>=0&&scale>0?Math.max(0,Math.min(100,Math.round(100f*value/scale))):-1;int status=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);String state=status==BatteryManager.BATTERY_STATUS_CHARGING?"charging":status==BatteryManager.BATTERY_STATUS_FULL?"full":i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)>0?"plugged":status<0?"unknown":"unplugged";current=new Reading(level,state);}
 public static Reading parse(JSONObject battery)throws Exception{
  int level=battery.isNull("level")?-1:battery.getInt("level");String state=battery.getString("state");
  if(level< -1||level>100||!java.util.List.of("charging","full","plugged","unplugged","unknown").contains(state))throw new IllegalArgumentException();return new Reading(level,state);
 }
 public static String peerLabel(Context c){try{Credentials p=new Credentials(c);JSONObject saved=new JSONObject(p.get("nativePeerBattery",""));if(!saved.getString("pairId").equals(p.get("nativePairId","")))return "아직 수신하지 않음";String label=parse(saved.getJSONObject("battery")).label();return label+" · 마지막 수신 "+android.text.format.DateUtils.getRelativeTimeSpanString(saved.getLong("reportedAt"),System.currentTimeMillis(),60000);}catch(Exception e){return "아직 수신하지 않음";}}
}
