package kr.infosphere.galaxyrelay;
import android.app.*;
import android.widget.*;
public final class EncryptionUi {
    public static void add(MainActivity a){
        a.button("E2EE 키 생성·표시",()->{
            if(a.getSystemService(KeyguardManager.class).isKeyguardLocked()){a.status.setText("Galaxy 잠금을 해제한 뒤 키를 확인하세요.");return;}
            a.execute(()->{String key=a.credentials.secret("e2eeKey");if(key.isEmpty()){key=EventCipher.generate();a.credentials.secret("e2eeKey",key);}final String shown=key,fingerprint=EventCipher.fingerprint(key);
                a.runOnUiThread(()->{TextView view=new TextView(a);view.setPadding(32,24,32,24);view.setTextIsSelectable(true);view.setText("키를 iPhone PWA 설정에 직접 입력하세요. 메시지·서버로 보내지 마세요.\n\n"+shown+"\n\n지문: "+fingerprint);new AlertDialog.Builder(a).setTitle("개인용 E2EE 키").setView(view).setPositiveButton("닫기",null).show();});return "iPhone 키 지문을 비교한 후 E2EE 전송을 켜세요.";});
        });
        Switch encrypted=new Switch(a);encrypted.setText("E2EE 전송 사용");encrypted.setChecked(a.credentials.flag("e2eeEnabled",false));a.root.addView(encrypted);
        encrypted.setOnClickListener(view->{boolean on=encrypted.isChecked();encrypted.setChecked(!on);
            if(on&&a.credentials.secret("e2eeKey").isEmpty()){a.status.setText("E2EE 키를 먼저 생성하세요.");return;}
            new AlertDialog.Builder(a).setTitle(on?"iPhone 키 지문이 일치하나요?":"E2EE를 끌까요?").setMessage(on?"PWA에 키를 입력하고 지문이 일치할 때만 사용하세요. 키가 다르면 알림을 읽을 수 없습니다.":"PWA의 E2EE 키도 삭제해야 평문 알림을 다시 받을 수 있습니다. 서버가 본문을 처리하게 됩니다.").setNegativeButton("취소",null).setPositiveButton("적용",(d,w)->{a.credentials.setFlag("e2eeEnabled",on);encrypted.setChecked(on);}).show();
        });
    }
}
