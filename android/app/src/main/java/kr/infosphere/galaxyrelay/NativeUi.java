package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import java.util.*;
public final class NativeUi {
 private static final java.util.concurrent.atomic.AtomicBoolean creatingQr=new java.util.concurrent.atomic.AtomicBoolean();
 public static boolean allowed(Context c){return Build.VERSION.SDK_INT<31||c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED;}
 public static void add(MainActivity a){a.button("QR로 iPhone 연결",()->show(a));a.button("블루투스 연결 시작",()->connect(a));}
 public static void connect(MainActivity a){if(!allowed(a)){permission(a);return;}Credentials p=new Credentials(a);if(p.get("nativePairId","").isEmpty()){show(a);return;}p.setFlag("nativeMode",true);a.startForegroundService(new Intent(a,NativeBridge.class));a.status.setText("블루투스 연결 시작 · iPhone Relay를 열어주세요.");}
 public static void unpair(MainActivity a){new AlertDialog.Builder(a).setTitle("iPhone 연결을 해제할까요?").setMessage("페어링과 전용 인증을 폐기하고 이 Galaxy의 마지막 연결 해제 위치를 삭제합니다.").setNegativeButton("취소",null).setPositiveButton("해제",(d,w)->a.execute(()->{NativeCloud.revokeServer(a);a.credentials.setFlag("nativeMode",false);a.credentials.setFlag("nativePaired",false);for(String key:new String[]{"nativeLinkKey","nativeBootstrap","nativeCommandKey","nativeToken","nativeDeviceId","nativePairId","nativeServiceUUID","disconnectLocation"})a.credentials.remove(key);a.stopService(new Intent(a,NativeBridge.class));a.stopService(new Intent(a,RemoteLocateService.class));if(!a.credentials.flush())throw new java.io.IOException("Pairing persistence failed");return "연결을 해제했습니다. 새 QR로 다시 연결할 수 있습니다.";})).show();}
 private static void permission(MainActivity a){if(Build.VERSION.SDK_INT>=31)a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},720);a.status.setText("근처 기기 권한 허용 후 QR 연결을 다시 누르세요.");}
 public static AlertDialog showQrDialog(MainActivity a,Bitmap image,String pairId,long expires){
  ImageView qr=new ImageView(a);qr.setImageBitmap(image);qr.setAdjustViewBounds(true);qr.setContentDescription("iPhone Relay에서 스캔할 연결 QR");
  TextView hint=new TextView(a);hint.setTextSize(15);hint.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));hint.setGravity(android.view.Gravity.CENTER);
  LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(a.dp(16),0,a.dp(16),0);content.addView(qr);content.addView(hint);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("iPhone으로 QR 스캔").setView(content).setPositiveButton("닫기",null).create();
  dialog.setOnShowListener(d->dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));dialog.show();
  Handler watch=new Handler(Looper.getMainLooper());Runnable update=new Runnable(){public void run(){
   if(!dialog.isShowing()||a.isFinishing()||a.isDestroyed())return;
   if(NativeBridge.detected(pairId)){dialog.dismiss();a.status.setText(NativeBridge.ready()?"iPhone과 연결했습니다.":"iPhone을 발견했습니다. 보안 연결을 설정하고 있습니다…");return;}
   long remaining=(expires-System.currentTimeMillis()+999)/1000;
   if(remaining<=0||!pairId.equals(a.credentials.get("nativePairId",""))){qr.setVisibility(android.view.View.GONE);hint.setText("이 QR은 더 이상 사용할 수 없습니다. 닫은 뒤 새 QR을 만들어주세요.");a.status.setText("연결 QR이 만료되거나 교체됐습니다. 새 QR을 만들어주세요.");return;}
   String failure=a.credentials.get("nativeGattFailure","");
   hint.setText("iPhone Relay → Galaxy QR로 연결\n"+NativeBridge.connectionState()+(failure.isEmpty()?"":"\n최근 실패: "+failure)+"\n남은 시간 "+(remaining/60)+":"+String.format(java.util.Locale.ROOT,"%02d",remaining%60)+" · QR 인증이 확인되면 자동으로 닫힙니다.");watch.postDelayed(this,400);
  }};dialog.setOnDismissListener(d->watch.removeCallbacksAndMessages(null));watch.post(update);return dialog;
 }
 public static void show(MainActivity a){
  if(!allowed(a)){permission(a);return;}BluetoothAdapter adapter=a.getSystemService(BluetoothManager.class).getAdapter();
  if(adapter==null||!adapter.isMultipleAdvertisementSupported()){a.status.setText("이 기기는 BLE 광고를 지원하지 않습니다.");return;}
  if(!adapter.isEnabled()){try{a.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(SecurityException e){a.status.setText("근처 기기 권한을 허용해주세요.");return;}a.status.setText("블루투스를 켠 뒤 다시 눌러주세요.");return;}
  if(a.credentials.secret("token").isEmpty()){a.status.setText("이 Galaxy의 기존 Backend 등록을 먼저 완료해주세요. iPhone 서버 설정은 QR 연결 중 자동 처리됩니다.");return;}
  if(!creatingQr.compareAndSet(false,true)){a.status.setText("새 QR을 준비하고 있습니다. 잠시 기다려주세요.");return;}
  a.execute(()->{try{Credentials p=a.credentials;
   if(!p.secret("nativeToken").isEmpty())NativeCloud.revokeServer(a);
   p.remove("nativeToken");NativeCloud.provision(a);
   p.remove("nativeGattFailure");p.setFlag("nativePaired",false);p.secret("nativeBootstrap",NativeCrypto.b64(NativeCrypto.random(32)));p.secret("nativeLinkKey",NativeCrypto.b64(NativeCrypto.random(32)));p.secret("nativeCommandKey",NativeCrypto.b64(NativeCrypto.random(32)));
   String pairingId=UUID.randomUUID().toString();p.put("nativePairId",pairingId);p.put("nativeServiceUUID",UUID.randomUUID().toString());long expires=System.currentTimeMillis()+300000;p.put("nativeQrExpires",String.valueOf(expires));p.setFlag("nativeMode",true);
   if(!p.flush())throw new java.io.IOException("Pairing persistence failed");
   String value=new JSONObject().put("version",2).put("pairId",pairingId).put("serviceUUID",p.get("nativeServiceUUID","")).put("secret",p.secret("nativeBootstrap")).put("expires",expires).put("name",android.os.Build.MODEL).toString();
   BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,720,720,Map.of(EncodeHintType.CHARACTER_SET,"UTF-8",EncodeHintType.MARGIN,4));Bitmap image=Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888);for(int y=0;y<720;y++)for(int x=0;x<720;x++)image.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
   a.runOnUiThread(()->{if(a.isFinishing()||!MainActivity.visible)return;a.startForegroundService(new Intent(a,NativeBridge.class));showQrDialog(a,image,pairingId,expires);});
   return "iPhone 네이티브 앱에서 QR을 스캔하세요. 서버 주소와 인증은 자동 전달됩니다.";
  }finally{creatingQr.set(false);}});
 }
}

