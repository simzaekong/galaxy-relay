package kr.infosphere.galaxyrelay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** Visible, video-only WebRTC camera. The Activity owns and releases all camera resources. */
public final class RemoteCameraActivity extends Activity {
 static volatile RemoteCameraActivity current;
 private WebView web;
 private JSONObject grant;
 private String trustedUrl,origin,nativeId;
 private boolean closing,visible,connected;
 private long lastLease,openedAt;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService storage=Executors.newSingleThreadExecutor();
 private OutputStream recording;
 private Uri recordingUri;
 private long recordingBytes;
 @Override @SuppressLint("SetJavaScriptEnabled") public void onCreate(Bundle state){
  super.onCreate(state);
  if(current!=null){finish();return;}current=this;
  try{
   Credentials p=new Credentials(this);grant=new JSONObject(getIntent().getStringExtra("grant"));nativeId=grant.getString("nativeId");
   if(!p.flag("remoteCameraEnabled",false)||!nativeId.equals(p.get("nativeDeviceId",""))||grant.getLong("expires")<=System.currentTimeMillis()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new IllegalStateException();
   Uri base=Uri.parse(p.get("backend",""));if(!"https".equals(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null)throw new IllegalArgumentException();
   origin=base.buildUpon().path("").query(null).fragment(null).build().toString();trustedUrl=origin+"/api/camera/client";
   if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else{getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SECURE);
   LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);setContentView(root);
   TextView title=new TextView(this);title.setText("내 iPhone에 카메라 연결 중 · 마이크 사용 안 함");title.setTextColor(Color.WHITE);title.setPadding(24,24,24,16);root.addView(title);
   Button stop=new Button(this);stop.setText("카메라 연결 종료");root.addView(stop);stop.setOnClickListener(v->closeCamera());
   web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));web.setBackgroundColor(Color.BLACK);
   web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
   web.addJavascriptInterface(new Bridge(),"CameraNative");
   web.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !trustedUrl.equals(r.getUrl().toString());}
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){Uri uri=r.getUrl();if(!"https".equals(uri.getScheme())||!origin.equals(uri.buildUpon().path("").query(null).fragment(null).build().toString()))return new WebResourceResponse("text/plain","utf-8",403,"Blocked",java.util.Map.of(),new ByteArrayInputStream(new byte[0]));return null;}
    @Override public void onPageFinished(WebView v,String url){if(!trustedUrl.equals(url)||closing||!visible)return;try{JSONObject config=new JSONObject(grant.toString()).put("linkKey",new Credentials(RemoteCameraActivity.this).secret("nativeLinkKey"));v.evaluateJavascript("window.startCamera("+config.toString()+")",null);}catch(Exception e){closeCamera();}}
    @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())closeCamera();}
    @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();closeCamera();}
   });
   web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){
    boolean camera=request.getResources().length==1&&PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(request.getResources()[0]);
    if(visible&&!closing&&camera&&origin.equals(request.getOrigin().toString().replaceAll("/$",""))&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});else request.deny();
   }});
   openedAt=SystemClock.elapsedRealtime();lastLease=openedAt;web.loadUrl(trustedUrl);main.postDelayed(watchdog,1000);
   getSystemService(NotificationManager.class).cancel(CameraRequests.NOTIFICATION);
  }catch(Exception e){closeCamera();}
 }
 private final Runnable watchdog=new Runnable(){public void run(){if(closing)return;long now=SystemClock.elapsedRealtime();Credentials p=new Credentials(RemoteCameraActivity.this);if(!p.flag("nativePaired",false)||!p.flag("nativeMode",false)||!p.flag("remoteCameraEnabled",false)||!p.get("nativeDeviceId","").equals(nativeId)||now-openedAt>600000||(!connected&&now-openedAt>35000)||(connected&&now-lastLease>8000)){closeCamera();return;}main.postDelayed(this,1000);}};
 @Override protected void onResume(){super.onResume();visible=true;if(web!=null)web.onResume();}
 @Override protected void onPause(){visible=false;super.onPause();closeCamera();}
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);destroyWeb();storage.execute(()->discardRecording());storage.shutdown();if(current==this)current=null;super.onDestroy();}
 private void destroyWeb(){if(web!=null){web.removeJavascriptInterface("CameraNative");web.stopLoading();web.loadUrl("about:blank");web.onPause();web.destroy();web=null;}}
 void closeCamera(){
  if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::closeCamera);return;}if(closing)return;closing=true;main.removeCallbacks(watchdog);
  if(web!=null)web.evaluateJavascript("window.closeCamera && window.closeCamera()",null);
  if(grant!=null)RelayQueue.IO.execute(()->CameraRequests.end(getApplicationContext(),grant));
  main.postDelayed(()->{destroyWeb();finish();},1700);
 }
 private void result(String id,boolean ok,String message){main.post(()->{if(web!=null)web.evaluateJavascript("window.cameraNativeResult("+JSONObject.quote(id)+","+ok+","+JSONObject.quote(message)+")",null);});}
 private byte[] decode(String encoded,int max){if(encoded.length()>max*4/3+8)throw new IllegalArgumentException();return android.util.Base64.decode(encoded,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);}
 private Uri create(String mime,boolean video)throws Exception{
  if(Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
  ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"GalaxyRelay-"+System.currentTimeMillis()+(video?(mime.startsWith("video/mp4")?".mp4":".webm"):".jpg"));values.put(MediaStore.MediaColumns.MIME_TYPE,mime.split(";")[0]);
  if(Build.VERSION.SDK_INT>=29){values.put(MediaStore.MediaColumns.RELATIVE_PATH,video?"Movies/GalaxyRelay":"Pictures/GalaxyRelay");values.put(MediaStore.MediaColumns.IS_PENDING,1);}
  Uri uri=getContentResolver().insert(video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();return uri;
 }
 private void publish(Uri uri){if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);}}
 private void discardRecording(){try{if(recording!=null)recording.close();}catch(Exception ignored){}recording=null;if(recordingUri!=null){getContentResolver().delete(recordingUri,null,null);recordingUri=null;}}
 final class Bridge {
  @JavascriptInterface public void event(String raw){try{JSONObject e=new JSONObject(raw);String type=e.optString("type");main.post(()->{if(type.equals("lease")){lastLease=SystemClock.elapsedRealtime();}else if(type.equals("connected")){connected=true;lastLease=SystemClock.elapsedRealtime();}else if(type.equals("closed")){if(!closing){closing=true;if(grant!=null)RelayQueue.IO.execute(()->CameraRequests.end(getApplicationContext(),grant));}finish();}});}catch(Exception ignored){}}
  @JavascriptInterface public void photo(String id,String encoded){if(closing||!connected){result(id,false,"연결 종료 중입니다.");return;}storage.execute(()->{Uri uri=null;try{byte[] data=decode(encoded,2500000);if(data.length<4||data[0]!=(byte)0xff||data[1]!=(byte)0xd8)throw new IOException();uri=create("image/jpeg",false);try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(data);}publish(uri);result(id,true,"");}catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);result(id,false,"Galaxy 사진 저장에 실패했습니다.");}});}
  @JavascriptInterface public void recordStart(String id,String mime){if(closing||!connected){result(id,false,"연결 종료 중입니다.");return;}storage.execute(()->{try{if(recording!=null||!(mime.equals("video/webm;codecs=vp8")||mime.equals("video/webm")||mime.equals("video/mp4")))throw new IllegalStateException();recordingUri=create(mime,true);recording=getContentResolver().openOutputStream(recordingUri);if(recording==null)throw new IOException();recordingBytes=0;result(id,true,"");}catch(Exception e){discardRecording();result(id,false,"Galaxy 녹화를 시작하지 못했습니다.");}});}
  @JavascriptInterface public void recordChunk(String id,String encoded){storage.execute(()->{try{byte[] data=decode(encoded,2000000);recordingBytes+=data.length;if(recording==null||recordingBytes>256000000)throw new IOException();recording.write(data);result(id,true,"");}catch(Exception e){discardRecording();result(id,false,"Galaxy 녹화 저장에 실패했습니다.");main.post(RemoteCameraActivity.this::closeCamera);}});}
  @JavascriptInterface public void recordFinish(String id){storage.execute(()->{try{if(recording==null||recordingUri==null)throw new IOException();recording.close();recording=null;publish(recordingUri);recordingUri=null;result(id,true,"");}catch(Exception e){discardRecording();result(id,false,"Galaxy 녹화를 저장하지 못했습니다.");}});}
 }
}
