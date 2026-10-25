package kr.infosphere.galaxyrelay;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class DashboardTest {
 private TextView find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(text))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private TextView shown(View v,String text){if(!v.isShown())return null;if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(text))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=shown(((ViewGroup)v).getChildAt(i),text);if(t!=null)return t;}return null;}
 private View tab(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())&&v.hasOnClickListeners())return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View t=tab(((ViewGroup)v).getChildAt(i),text);if(t!=null)return t;}return null;}
 @Test public void locationIsDiscoverableAndDiagnosticsStayOutOfHome(){
  var instrumentation=InstrumentationRegistry.getInstrumentation();MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();
  instrumentation.runOnMainSync(()->{View decor=activity.getWindow().getDecorView();assertNotNull(shown(decor,"QR로 iPhone 연결"));assertNotNull(shown(decor,"마지막 연결 해제 위치"));assertNotNull(shown(decor,"위치 권한 설정"));assertNull(shown(decor,"지도에서 보기"));assertNull(shown(decor,"블루투스 연결 진단"));tab(decor,"설정").performClick();assertNotNull(shown(decor,"연결 해제 위치"));assertNotNull(shown(decor,"위치 권한 설정"));assertNotNull(shown(decor,"연결 해제 시에만 위치 기록"));assertNull(shown(decor,"블루투스 연결 진단"));tab(decor,"알림 기록").performClick();assertNotNull(shown(decor,"최근 전달한 알림"));assertNull(shown(decor,"연결 해제 시에만 위치 기록"));tab(decor,"홈").performClick();assertNotNull(shown(decor,"마지막 연결 해제 위치"));activity.finish();});
 }
 @Test public void qrDialogDoesNotDisappearWhenThePairIsReplaced()throws Exception{
  var instrumentation=InstrumentationRegistry.getInstrumentation();
  MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  android.app.AlertDialog[] dialog=new android.app.AlertDialog[1];
  instrumentation.runOnMainSync(()->{
   activity.credentials.put("nativePairId","fixture-old");
   android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);
   dialog[0]=NativeUi.showQrDialog(activity,image,"fixture-old",System.currentTimeMillis()+60000);
   activity.credentials.put("nativePairId","fixture-replaced");
  });
  Thread.sleep(900);instrumentation.waitForIdleSync();
  instrumentation.runOnMainSync(()->{assertTrue(dialog[0].isShowing());assertTrue(activity.status.getText().toString().contains("만료"));dialog[0].dismiss();activity.credentials.remove("nativePairId");activity.finish();});
 }
}
