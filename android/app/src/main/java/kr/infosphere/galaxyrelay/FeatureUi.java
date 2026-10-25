package kr.infosphere.galaxyrelay;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.*;
public final class FeatureUi {
 public static void add(MainActivity a){
  android.widget.LinearLayout remoteParent=a.card("iPhone 리모트",null);
  a.toggle("음악·벨소리·기기 찾기 허용","연결된 iPhone에서 BLE로 조작합니다.","remoteControlEnabled",true);
  a.row("벨소리 모드 변경 권한","무음·진동 전환이 제한되면 방해금지 접근을 허용하세요.",()->a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
  a.toggle("서버를 통한 내 기기 위치 찾기","iPhone에서 요청할 때만 위치를 한 번 확인해 암호화 전송합니다. 서버의 암호문은 최대 2분 보관합니다.","remoteLocationEnabled",false);
  a.row("기기 찾기 위치 권한","원격 요청 수신에는 위치 항상 허용이 필요합니다.",()->RemoteLocationUi.request(a));a.root=remoteParent;
  android.widget.LinearLayout cameraParent=a.card("iPhone 원격 카메라",null);
  a.toggle("내 iPhone 카메라 요청 자동 수락","영상은 iPhone으로만 보내고 사진·무음 동영상은 Galaxy에 저장합니다. iPhone 종료 시 카메라를 끕니다.","remoteCameraEnabled",false);
  a.row("카메라 및 전체 화면 연결 권한","처음 한 번 카메라와 전체 화면 알림을 허용하세요.",()->CameraRequests.setup(a));a.root=cameraParent;
  android.widget.LinearLayout parent=a.card("연결 해제 위치","홈의 마지막 연결 해제 위치 카드에서 확인하세요.");
  a.toggle("연결 해제 시에만 위치 기록","연결 중에는 측정하지 않으며 위치는 이 Galaxy에만 저장합니다.","disconnectLocationEnabled",true);
  a.divider();a.row("위치 권한 설정","백그라운드 기록은 위치 → 항상 허용",()->LocationUi.request(a));a.row("마지막 위치 기록 삭제","이 기기에 저장된 위치 한 개를 삭제",()->LocationUi.erase(a));a.root=parent;
  parent=a.card("알림 전달",null);a.toggle("알림 전달",null,"relayEnabled",true);a.toggle("문자 알림 전달",null,"sms",true);a.divider();a.row("항상 전달할 앱","iPhone 인터넷 상태와 관계없이 전달",()->chooseApps(a,false));a.row("iPhone 오프라인일 때만 전달할 앱","블루투스 연결 중 네트워크가 없을 때만 추가 전달",()->chooseApps(a,true));a.row("알림 접근 허용","Galaxy의 앱 알림을 감지하는 권한",()->a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));a.row("Relay 알림 표시 허용","iPhone에서 열기 요청과 연결 상태 알림",()->{if(android.os.Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},203);else a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));});a.root=parent;
  parent=a.card("개인정보",null);a.toggle("최근 이벤트 내용 숨기기","알림 기록의 제목은 숨기고, 직접 눌렀을 때 내용을 표시합니다.","privacy",true);a.root=parent;
  parent=a.card("iPhone 연결",null);a.row("블루투스 연결 시작","기존 페어링으로 다시 연결",()->NativeUi.connect(a));a.row("테스트 알림 보내기","iPhone에 알림이 도착하는지 확인",a::testEvent);a.row("iPhone 연결 해제","페어링과 전용 인증을 폐기",()->NativeUi.unpair(a));a.root=parent;
  parent=a.section("전화 및 직접 SMS 수신",false);a.text("문자는 알림 접근만으로 전달할 수 있습니다. 직접 SMS 권한은 필요할 때만 허용하세요.",13);
  a.row("iPhone에서 전화 거절·끊기 권한","실제 Galaxy 통화를 종료합니다. 받기는 지원하지 않습니다.",()->a.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE,android.Manifest.permission.ANSWER_PHONE_CALLS},205));
  a.toggle("전화 상태 전달",null,"calls",false);a.row("전화 상태 권한","수신 · 통화 중 · 종료 상태 감지",()->a.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE},201));
  a.row("발신자 번호·연락처 권한","전화 수신자의 번호와 이름 표시",()->new AlertDialog.Builder(a).setTitle("전화 발신자 표시").setMessage("수신 번호와 해당 연락처 이름을 조회하기 위한 권한입니다. 통화 기록 목록·주소록 전체를 수집하지 않습니다.").setNegativeButton("취소",null).setPositiveButton("권한 요청",(d,w)->a.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE,android.Manifest.permission.READ_CALL_LOG,android.Manifest.permission.READ_CONTACTS},204)).show());
  a.toggle("SMS 직접 수신",null,"directSms",false);a.row("SMS 수신 권한","문자 앱 알림 대신 직접 수신할 때만 필요",()->a.requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS},202));a.root=parent;
  parent=a.section("고급 · 진단 및 보안",false);a.row("전화 수신 진단","최근 상태 감지와 전달 설정 확인",()->new AlertDialog.Builder(a).setTitle("전화 수신 진단").setMessage("전화 전달: "+a.credentials.flag("calls",false)+"\n상태 권한: "+(a.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED)+"\n최근 수신 시각: "+a.credentials.get("phoneReceivedAt","없음")+"\n진단: "+a.credentials.get("phoneDiagnostic","없음")).setPositiveButton("닫기",null).show());a.row("블루투스 연결 진단","연결 이력과 최근 실패 확인",()->new AlertDialog.Builder(a).setTitle("블루투스 연결 진단 · "+BuildConfig.VERSION_NAME).setMessage(NativeBridge.connectionState()+"\n\n"+a.credentials.get("nativeGattFailure","기록된 구독 거부가 없습니다.")+"\n\n연결 이력\n"+a.credentials.get("nativeBleTrace","")).setPositiveButton("닫기",null).show());a.row("전송 대기열 재시도","오프라인 중 쌓인 이벤트 재전송",()->{RelayQueue.kick(a,0);a.status.setText("전송 대기열을 다시 확인합니다.");});a.text("One UI 절전 앱 목록에서 Relay를 제외하면 자동 재연결에 도움이 됩니다. 알림 제거는 원본 앱의 메시지 읽음 처리와 별개입니다.",13);a.root=parent;
 }
 private static void chooseApps(MainActivity a,boolean offline){var apps=a.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);Map<String,String> map=new TreeMap<>();for(var app:apps)if(!app.activityInfo.packageName.equals(a.getPackageName()))map.put(app.activityInfo.packageName,app.loadLabel(a.getPackageManager()).toString());String[] packages=map.keySet().toArray(new String[0]),labels=map.values().toArray(new String[0]);boolean[] checked=new boolean[packages.length];for(int i=0;i<checked.length;i++)checked[i]=a.credentials.flag((offline?"offlineApp:":"app:")+packages[i],!offline&&RelayNotificationListener.defaultApp(packages[i]));new AlertDialog.Builder(a).setTitle(offline?"iPhone 오프라인일 때만 전달":"항상 전달할 앱").setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{for(int i=0;i<checked.length;i++){a.credentials.setFlag((offline?"offlineApp:":"app:")+packages[i],checked[i]);if(checked[i])a.credentials.setFlag((offline?"app:":"offlineApp:")+packages[i],false);}}).show();}
}
