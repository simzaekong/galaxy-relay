package kr.infosphere.relayfixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView t=new TextView(this);t.setText("Original notification destination");setContentView(t);}
}
