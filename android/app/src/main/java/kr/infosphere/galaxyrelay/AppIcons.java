package kr.infosphere.galaxyrelay;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
/** Small original launcher icons; no network lookup or additional permission. */
public final class AppIcons {
 private static final LruCache<String,String> CACHE=new LruCache<>(64);
 private AppIcons(){}
 public static String encoded(Context context,String packageName){
  try{
   PackageInfo info=context.getPackageManager().getPackageInfo(packageName,0);
   String key=packageName+":"+info.lastUpdateTime,cached=CACHE.get(key);if(cached!=null)return cached;
   Drawable icon=context.getPackageManager().getApplicationIcon(packageName);
   for(int edge:new int[]{48,32,24}){
    Bitmap bitmap=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
    byte[] data;
    try{Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);icon.setBounds(0,0,edge,edge);icon.draw(canvas);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,55,bytes);data=bytes.toByteArray();}finally{bitmap.recycle();}
    if(data.length<=1536){String encoded=NativeCrypto.b64(data);CACHE.put(key,encoded);return encoded;}
   }
  }catch(Exception ignored){/* Keep notification delivery working without an icon. */}
  return "";
 }
}
