package kr.infosphere.galaxyrelay;
import android.content.Context;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class NativeCloud {
 public static void revokeServer(Context c)throws Exception{try{new RelayApi(c).call("/api/native/revoke",new JSONObject(),true);}catch(RelayApi.Failure error){if(error.status!=409||!"NOT_PAIRED".equals(error.code))throw error;}}
 public static void provision(Context c)throws Exception{Credentials p=new Credentials(c);if(!p.secret("nativeToken").isEmpty())return;
  JSONObject r=new RelayApi(c).call("/api/native/provision",new JSONObject().put("name","iPhone Native"),true);
  p.nativePeer(r.getString("deviceId"),r.getString("token"));
 }
 public static void pull(Context c,String requestId,String grantJson)throws Exception{
  JSONObject grant=new JSONObject(grantJson);if(grant.getLong("expires")<System.currentTimeMillis())return;Credentials p=new Credentials(c);p.pruneNativePulls();if(!p.flag("nativeMode",false)||p.secret("nativeLinkKey").isEmpty())return;
  JSONArray events=new JSONArray(),ids=new JSONArray();long now=System.currentTimeMillis();
  for(EventRecord r:RelayDatabase.get(c).dao().queue()){if(now>r.expiresAt)continue;if(events.length()>=6)break;JSONObject candidate=new JSONObject(p.open(r.data));if(candidate.optBoolean("offlineOnly",false))continue;JSONArray trial=new JSONArray(events.toString());trial.put(candidate);if(trial.toString().getBytes(StandardCharsets.UTF_8).length>30000)break;events.put(candidate);ids.put(new JSONObject().put("id",r.id).put("timestamp",r.timestamp));}
  JSONObject value=new JSONObject().put("requestId",requestId).put("timestamp",now).put("events",events);
  NativeCrypto.Sealed sealed=NativeCrypto.seal(NativeCrypto.bytes(p.secret("nativeLinkKey")),value.toString().getBytes(StandardCharsets.UTF_8),NativeCrypto.cloudAAD(requestId,p.get("nativeDeviceId","")));
  p.secret("nativePull:"+requestId,new JSONObject().put("expires",now+180000).put("ids",ids).toString());
  new RelayApi(c).call("/api/native/pull/upload",new JSONObject().put("requestId",requestId).put("expires",grant.getLong("expires")).put("grant",grant.getString("grant")).put("packet",new JSONObject().put("version",2).put("iv",sealed.iv).put("ciphertext",sealed.ciphertext)),true);
 }
 public static void revokeLocal(Context c,String id){synchronized(Credentials.class){Credentials p=new Credentials(c);if(!id.equals(p.get("nativeDeviceId","")))return;if(RemoteCameraActivity.current!=null)RemoteCameraActivity.current.closeCamera();p.setFlag("remoteCameraEnabled",false);p.setFlag("nativeMode",false);p.setFlag("nativePaired",false);for(String key:new String[]{"nativeLinkKey","nativeBootstrap","nativeCommandKey","nativeToken","nativeDeviceId","nativePairId","nativeServiceUUID","disconnectLocation"})p.remove(key);c.stopService(new android.content.Intent(c,NativeBridge.class));c.stopService(new android.content.Intent(c,RemoteLocateService.class));}}
 public static void ack(Context c,String requestId)throws Exception{Credentials p=new Credentials(c);String raw=p.secret("nativePull:"+requestId);if(raw.isEmpty())return;JSONObject record=new JSONObject(raw);if(record.getLong("expires")<System.currentTimeMillis())return;JSONArray ids=record.getJSONArray("ids");for(int i=0;i<ids.length();i++){JSONObject id=ids.getJSONObject(i);RelayDatabase.get(c).dao().delivered(id.getString("id"),id.getLong("timestamp"));}p.remove("nativePull:"+requestId);}
 public static JSONObject config(Context c)throws Exception{Credentials p=new Credentials(c);return new JSONObject().put("kind","config").put("capabilities",new JSONArray().put("battery_v1").put("health_v1").put("network_v1").put("remote_v1").put("locate_v1").put("camera_v1")).put("pairId",p.get("nativePairId","")).put("serviceUUID",p.get("nativeServiceUUID","")).put("galaxyName",android.os.Build.MODEL).put("linkKey",p.secret("nativeLinkKey")).put("commandKey",p.secret("nativeCommandKey")).put("backend",p.get("backend","")).put("deviceId",p.get("nativeDeviceId","")).put("token",p.secret("nativeToken"));}
}

