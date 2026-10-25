package kr.infosphere.galaxyrelay;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class PhoneLifecycleTest {
    @Test public void looksUpOnlyTheIncomingNumber()throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();var c=instrumentation.getTargetContext();
        instrumentation.getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.WRITE_CONTACTS,android.Manifest.permission.READ_CONTACTS);
        android.net.Uri raw=null;
        try{
            var resolver=c.getContentResolver();raw=resolver.insert(android.provider.ContactsContract.RawContacts.CONTENT_URI,new android.content.ContentValues());assertNotNull(raw);
            long id=android.content.ContentUris.parseId(raw);
            var name=new android.content.ContentValues();name.put(android.provider.ContactsContract.Data.RAW_CONTACT_ID,id);name.put(android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);name.put(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,"Relay Caller Test");resolver.insert(android.provider.ContactsContract.Data.CONTENT_URI,name);
            var phone=new android.content.ContentValues();phone.put(android.provider.ContactsContract.Data.RAW_CONTACT_ID,id);phone.put(android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);phone.put(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,"5551234");resolver.insert(android.provider.ContactsContract.Data.CONTENT_URI,phone);
            assertEquals("Relay Caller Test",PhoneIdentity.contact(c,"5551234"));assertEquals("",PhoneIdentity.contact(c,"5559999"));
        }finally{if(raw!=null)c.getContentResolver().delete(raw,null,null);instrumentation.getUiAutomation().dropShellPermissionIdentity();}
    }
    @Test public void richerDuplicateAndCallEndShareOneSession()throws Exception{
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();Credentials p=new Credentials(c);p.setFlag("calls",true);p.put("callState",android.telephony.TelephonyManager.EXTRA_STATE_IDLE);
        PhoneReceiver.transition(c,android.telephony.TelephonyManager.EXTRA_STATE_RINGING,null);String session=p.get("callId","");
        PhoneReceiver.transition(c,android.telephony.TelephonyManager.EXTRA_STATE_RINGING,"5551234");assertEquals(session,p.get("callId",""));assertEquals("5551234",p.secret("callNumber"));
        Thread.sleep(1500);EventRecord record=RelayDatabase.get(c).dao().find(session);assertNotNull(record);assertEquals("5551234",new JSONObject(p.open(record.data)).getString("number"));
        PhoneReceiver.transition(c,android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK,null);PhoneReceiver.transition(c,android.telephony.TelephonyManager.EXTRA_STATE_IDLE,null);
        assertTrue(p.get("callId","").isEmpty());assertTrue(p.secret("callNumber").isEmpty());
        assertTrue(RelayDatabase.get(c).dao().since(record.timestamp).stream().anyMatch(r->{try{JSONObject e=new JSONObject(p.open(r.data));return e.optString("state").equals("ended")&&e.optString("callId").equals(session)&&!e.has("number")&&!e.has("contact");}catch(Exception ex){return false;}}));p.setFlag("calls",false);
    }
}
