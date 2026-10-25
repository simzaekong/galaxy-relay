package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
/** Local-only one-shot capture. No location reads until a confirmed BLE link is lost. */
public final class DisconnectLocation {
 private final Context context;private final Handler handler=new Handler(Looper.getMainLooper());
 private final DisconnectLocationPolicy policy=new DisconnectLocationPolicy();
 private final java.util.function.Consumer<Boolean> foreground;
 private LocationManager manager;private CancellationSignal cancellation;private LocationListener listener;
 private long generation;private boolean requesting;
 private static volatile JSONObject saved;private static volatile String note="연결이 끊길 때만 이 Galaxy의 위치를 기록합니다.";
 public DisconnectLocation(Context context,java.util.function.Consumer<Boolean> foreground){this.context=context;this.foreground=foreground;load(context);}
 public static void load(Context c){try{String raw=new Credentials(c).secret("disconnectLocation");saved=raw.isEmpty()?null:new JSONObject(raw);}catch(Exception ignored){saved=null;}}
 public void connected(){cancel();policy.connected();note="연결 중 · 위치 측정 안 함";}
 private boolean permitted(){boolean basic=context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;return basic&&(Build.VERSION.SDK_INT<29||MainActivity.visible||context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED);}
 public void lost(){
  Credentials p=new Credentials(context);boolean enabled=p.flag("disconnectLocationEnabled",true),permission=permitted();
  if(!policy.lost(SystemClock.elapsedRealtime(),enabled&&permission)){note=!enabled?"위치 기록 꺼짐":!permission?"위치 권한을 허용하면 연결 해제 시 기록합니다.":"반복 단절 · 추가 측정 생략";return;}
  cancel();long ticket=generation,triggeredAt=System.currentTimeMillis();String pairId=p.get("nativePairId","");note="연결 해제 · 위치 기록 준비 중";
  handler.postDelayed(()->{if(ticket!=generation)return;begin(ticket,pairId,triggeredAt);},3000);
 }
 @android.annotation.SuppressLint("MissingPermission") private void begin(long ticket,String pairId,long triggeredAt){
  Credentials current=new Credentials(context);if(!current.flag("nativeMode",false)||pairId.isEmpty()||!pairId.equals(current.get("nativePairId",""))){cancel();return;}
  if(!current.flag("disconnectLocationEnabled",true)||!permitted()){finish("위치 권한이 없어 기록하지 못했습니다.");return;}
  try{
   manager=context.getSystemService(LocationManager.class);String provider=manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:null;
   if(provider==null){finish("사용 가능한 위치 서비스가 없습니다.");return;}
   requesting=true;foreground.accept(true);note="연결 해제 · 위치 1회 확인 중";
   handler.postDelayed(()->{if(ticket==generation)finish("제한 시간 안에 위치를 확인하지 못했습니다.");},15000);
   if(Build.VERSION.SDK_INT>=30){cancellation=new CancellationSignal();manager.getCurrentLocation(provider,cancellation,context.getMainExecutor(),location->complete(ticket,pairId,triggeredAt,location));}
   else{listener=new LocationListener(){@Override public void onLocationChanged(Location location){complete(ticket,pairId,triggeredAt,location);}@Override public void onProviderEnabled(String provider){}@Override public void onProviderDisabled(String provider){if(ticket==generation)finish("위치 서비스가 꺼져 기록하지 못했습니다.");}@Override public void onStatusChanged(String provider,int status,Bundle extras){}};manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());}
  }catch(Exception ignored){finish("위치 권한 또는 위치 서비스를 확인해주세요.");}
 }
 private void complete(long ticket,String pairId,long triggeredAt,Location location){
  if(ticket!=generation||!requesting)return;Credentials p=new Credentials(context);
  if(!p.flag("nativeMode",false)||!pairId.equals(p.get("nativePairId",""))){cancel();return;}
  if(!p.flag("disconnectLocationEnabled",true)||!permitted()){finish("위치 권한 또는 설정이 변경되어 기록을 중단했습니다.");return;}
  if(location==null||!location.hasAccuracy()||!DisconnectLocationPolicy.valid(location.getLatitude(),location.getLongitude(),location.getAccuracy(),location.getTime(),triggeredAt,System.currentTimeMillis())){finish("현재 시점의 위치를 확인하지 못했습니다.");return;}
  try{JSONObject record=new JSONObject().put("pairId",pairId).put("latitude",location.getLatitude()).put("longitude",location.getLongitude()).put("accuracy",location.getAccuracy()).put("triggeredAt",triggeredAt).put("measuredAt",location.getTime());p.secret("disconnectLocation",record.toString());saved=record;finish("이 Galaxy의 연결 해제 위치를 기록했습니다.");}catch(Exception ignored){finish("위치 기록을 저장하지 못했습니다.");}
 }
 private void finish(String message){cancel();note=message;}
 public void cancel(){generation++;handler.removeCallbacksAndMessages(null);if(cancellation!=null){cancellation.cancel();cancellation=null;}if(listener!=null&&manager!=null)try{manager.removeUpdates(listener);}catch(Exception ignored){}listener=null;if(requesting){requesting=false;foreground.accept(false);}}
 public void stop(){policy.stopped();cancel();}
 public static void clear(Context c){new Credentials(c).remove("disconnectLocation");saved=null;note="위치 기록을 삭제했습니다.";if(NativeBridge.current!=null)NativeBridge.current.cancelLocation();}
 public static boolean hasRecord(Context c){JSONObject r=saved;return r!=null&&r.optString("pairId").equals(new Credentials(c).get("nativePairId",""))&&Double.isFinite(r.optDouble("latitude"))&&Math.abs(r.optDouble("latitude"))<=90&&Double.isFinite(r.optDouble("longitude"))&&Math.abs(r.optDouble("longitude"))<=180;}
 public static String description(Context c){JSONObject r=saved;String text=note;if(r!=null&&r.optString("pairId").equals(new Credentials(c).get("nativePairId",""))){text+="\n마지막 기록 · "+android.text.format.DateUtils.getRelativeTimeSpanString(r.optLong("triggeredAt"),System.currentTimeMillis(),60000)+" · 정확도 약 "+Math.round(r.optDouble("accuracy"))+"m\n상대 기기의 실제 위치가 아닌 이 Galaxy의 위치입니다.";}return text;}
 public static void openMap(MainActivity a){try{JSONObject r=saved;if(r==null||!r.getString("pairId").equals(a.credentials.get("nativePairId",""))){a.status.setText("기록된 위치가 없습니다.");return;}String point=r.getDouble("latitude")+","+r.getDouble("longitude");a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("geo:"+point+"?q="+point)));}catch(Exception ignored){a.status.setText("지도 앱에서 위치를 열 수 없습니다.");}}
}
