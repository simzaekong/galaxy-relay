package kr.infosphere.galaxyrelay;

import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Main-thread, live BLE controls. Nothing is queued for a future connection. */
public final class RemoteControl {
 private final Context context;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Ringtone finder;
 private long generation;
 private boolean pending;
 public RemoteControl(Context context){this.context=context;}
 private AudioManager audio(){return context.getSystemService(AudioManager.class);}
 private MediaController media(){
  try{
   List<MediaController> sessions=context.getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(context,RelayNotificationListener.class));
   for(MediaController c:sessions)if(c.getPlaybackState()!=null&&c.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)return c;
   return sessions.isEmpty()?null:sessions.get(0);
  }catch(SecurityException e){return null;}
 }
 private int volume(int stream){AudioManager a=audio();int max=a.getStreamMaxVolume(stream);return max<=0?0:Math.round(100f*a.getStreamVolume(stream)/max);}
 private void volume(int stream,int percent){AudioManager a=audio();a.setStreamVolume(stream,Math.round(a.getStreamMaxVolume(stream)*percent/100f),0);}
 public JSONObject snapshot()throws JSONException{
  MediaController c=media();PlaybackState p=c==null?null:c.getPlaybackState();MediaMetadata m=c==null?null:c.getMetadata();long actions=p==null?0:p.getActions();
  return new JSONObject().put("mediaAvailable",c!=null).put("mediaId",c==null?"":c.getPackageName()).put("mediaApp",label(c))
   .put("title",text(m==null?null:m.getString(MediaMetadata.METADATA_KEY_TITLE)))
   .put("artist",text(m==null?null:m.getString(MediaMetadata.METADATA_KEY_ARTIST)))
   .put("playing",p!=null&&p.getState()==PlaybackState.STATE_PLAYING)
   .put("canPlay",(actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0)
   .put("canPause",(actions&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0)
   .put("canNext",(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0).put("canPrevious",(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)
   .put("mediaVolume",volume(AudioManager.STREAM_MUSIC)).put("ringVolume",volume(AudioManager.STREAM_RING))
   .put("mediaVolumeMax",Math.max(1,audio().getStreamMaxVolume(AudioManager.STREAM_MUSIC))).put("ringVolumeMax",Math.max(1,audio().getStreamMaxVolume(AudioManager.STREAM_RING))).put("ringerMode",audio().getRingerMode()).put("finding",finder!=null&&finder.isPlaying());
 }
 private static String text(String s){return s==null?"":s.substring(0,Math.min(s.length(),160));}
 private String label(MediaController c){if(c==null)return "";try{return text(context.getPackageManager().getApplicationInfo(c.getPackageName(),0).loadLabel(context.getPackageManager()).toString());}catch(Exception e){return "음악 앱";}}
 private static String track(MediaController c){MediaMetadata m=c==null?null:c.getMetadata();return m==null?"":String.valueOf(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"\n"+m.getString(MediaMetadata.METADATA_KEY_TITLE)+"\n"+m.getString(MediaMetadata.METADATA_KEY_ARTIST);}
 public void request(JSONObject request,Consumer<JSONObject> reply)throws Exception{
  String id=request.getString("requestId");UUID.fromString(id);String action=request.getString("action");
  if(pending){reply.accept(response(id,"BUSY"));return;}
  if(!new Credentials(context).flag("remoteControlEnabled",true)){reply.accept(response(id,"DISABLED"));return;}
  long ticket=generation;String result="OK";MediaController c=media();String priorTrack=track(c);long priorPosition=c==null||c.getPlaybackState()==null?0:c.getPlaybackState().getPosition();
  try{
   switch(action){
    case "state":break;
    case "play":case "pause":case "next":case "previous":{
     if(c==null)throw new IllegalStateException();
     if(!c.getPackageName().equals(request.optString("mediaId")))throw new IllegalStateException();
     long required=switch(action){case "play"->PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE;case "pause"->PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE;case "next"->PlaybackState.ACTION_SKIP_TO_NEXT;default->PlaybackState.ACTION_SKIP_TO_PREVIOUS;};
     if(c.getPlaybackState()==null||(c.getPlaybackState().getActions()&required)==0)throw new IllegalStateException();
     MediaController.TransportControls t=c.getTransportControls();
     switch(action){case "play":t.play();break;case "pause":t.pause();break;case "next":t.skipToNext();break;default:t.skipToPrevious();}break;
    }
    case "mediaVolume":case "ringVolume":{int value=request.getInt("value");if(value<0||value>100)throw new IllegalArgumentException();volume(action.equals("mediaVolume")?AudioManager.STREAM_MUSIC:AudioManager.STREAM_RING,value);break;}
    case "ringerMode":{int value=request.getInt("value");if(value<0||value>2)throw new IllegalArgumentException();audio().setRingerMode(value);break;}
    case "findStart":startFinder();break;
    case "findStop":stopFinder();break;
    default:throw new IllegalArgumentException();
   }
  }catch(SecurityException e){result="PERMISSION_REQUIRED";}catch(Exception e){result="UNAVAILABLE";}
  String finalResult=result;pending=true;
  handler.postDelayed(()->{
   if(ticket!=generation)return;pending=false;String checked=finalResult;
   if(checked.equals("OK")&&(action.equals("next")||action.equals("previous"))){
    MediaController after=media();boolean changed=after!=null&&c!=null&&after.getPackageName().equals(c.getPackageName())&&!priorTrack.equals(track(after));
    boolean restarted=action.equals("previous")&&after!=null&&after.getPlaybackState()!=null&&after.getPlaybackState().getPosition()+500<priorPosition;
    if(!changed&&!restarted)checked="UNVERIFIED";
   }
   reply.accept(response(id,checked));
  },action.equals("state")?0:900);
 }
 private JSONObject response(String id,String result){try{return new JSONObject().put("kind","remote_state").put("requestId",id).put("result",result).put("state",snapshot());}catch(Exception e){return new JSONObject();}}
 private void startFinder(){
  stopFinder();finder=RingtoneManager.getRingtone(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
  if(finder==null)throw new IllegalStateException();
  finder.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
  if(Build.VERSION.SDK_INT>=28)finder.setLooping(true);finder.play();handler.postDelayed(stopSound,15000);
 }
 private void stopFinder(){handler.removeCallbacks(stopSound);if(finder!=null){finder.stop();finder=null;}}
 private final Runnable stopSound=this::stopFinder;
 public void close(){generation++;pending=false;handler.removeCallbacksAndMessages(null);stopFinder();}
}
