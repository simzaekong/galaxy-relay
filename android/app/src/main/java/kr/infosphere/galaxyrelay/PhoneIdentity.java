package kr.infosphere.galaxyrelay;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
final class PhoneIdentity {
    static String contact(Context c,String number){
        if(number.isEmpty()||c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return "";
        Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));
        try(var cursor=c.getContentResolver().query(uri,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
            return cursor!=null&&cursor.moveToFirst()?RelayNotificationListener.clip(cursor.getString(0),100):"";
        }catch(Exception ignored){return "";}
    }
}
