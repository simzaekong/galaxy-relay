package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
public final class RemoteLocationUi {
 public static void request(MainActivity a){
  new AlertDialog.Builder(a).setTitle("iPhone에서 Galaxy 찾기").setMessage("연결된 iPhone에서 요청하면 이 Galaxy의 위치를 한 번 조회해 암호화 전송합니다. 위치·정확도·측정 시각을 iPhone에서 볼 수 있습니다. 지속적으로 추적하지 않으며 서버 암호문은 최대 2분 뒤 만료됩니다. 백그라운드 요청을 받으려면 위치를 항상 허용해주세요.")
   .setNegativeButton("닫기",null).setPositiveButton("권한 설정",(d,w)->{
    if(!LocationUi.basic(a))a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},721);
    else if(Build.VERSION.SDK_INT==29&&a.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},722);
    else a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())));
   }).show();
 }
}
