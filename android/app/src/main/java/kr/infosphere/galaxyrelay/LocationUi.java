package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
/** Permission inspection and explicit user actions only; never reads a location. */
public final class LocationUi {
 public static boolean basic(MainActivity a){return a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 public static String permission(MainActivity a){if(!basic(a))return "위치 권한 꺼짐";if(Build.VERSION.SDK_INT>=29&&a.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED)return "앱 사용 중 허용 · 항상 허용 필요";return "항상 허용됨 · 단절 시에만 사용";}
 public static void request(MainActivity a){
  if(!basic(a)){new AlertDialog.Builder(a).setTitle("연결이 끊긴 곳을 기억해요").setMessage("정상 Bluetooth 연결이 끊겼을 때만 이 Galaxy의 위치를 한 번 기록합니다. 연결 중에는 측정하지 않으며, 위치는 이 기기에만 저장합니다. 먼저 앱 사용 중 위치를 허용해주세요.").setNegativeButton("나중에",null).setPositiveButton("계속",(d,w)->a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},721)).show();return;}
  if(Build.VERSION.SDK_INT==29&&a.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},722);return;}
  new AlertDialog.Builder(a).setTitle("위치 권한 설정").setMessage(Build.VERSION.SDK_INT>=29?"앱 설정 → 권한 → 위치 → 항상 허용을 선택하세요. 화면이 꺼진 상태에서 연결이 끊겨도 한 번 기록할 수 있도록 하는 권한입니다. 연결 중에는 위치를 사용하지 않습니다.":"위치 권한이 허용되어 있습니다. 연결 해제 시에만 한 번 기록하며, 앱 설정에서 권한을 변경할 수 있습니다.").setNegativeButton("닫기",null).setPositiveButton("앱 설정 열기",(d,w)->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))).show();
 }
 public static void erase(MainActivity a){new AlertDialog.Builder(a).setTitle("위치 기록을 삭제할까요?").setMessage("이 Galaxy에 저장된 마지막 연결 해제 위치를 삭제합니다.").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->DisconnectLocation.clear(a)).show();}
}
