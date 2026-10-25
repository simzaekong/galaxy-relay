package kr.infosphere.galaxyrelay;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Uses only the original active notification's mutable, non-Activity reply action. */
public final class ReplyActions {
    private static RemoteInput input(Notification.Action action, String owner) {
        // Public PendingIntent type/mutability inspection is available from Android 12.
        if(Build.VERSION.SDK_INT < 31) return null;
        if(action == null || action.actionIntent == null || action.actionIntent.isActivity()
            || !owner.equals(action.actionIntent.getCreatorPackage())) return null;
        if(action.actionIntent.isImmutable()) return null;
        if(Build.VERSION.SDK_INT >= 28 && action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_NONE
            && action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_REPLY) return null;
        RemoteInput found=null;
        if(action.getRemoteInputs()!=null) for(RemoteInput candidate:action.getRemoteInputs()) {
            if(candidate.getAllowFreeFormInput()) { if(found!=null) return null; found=candidate; }
        }
        return found;
    }
    private static String identity(Notification.Action action, int index, RemoteInput input) throws Exception {
        if(Build.VERSION.SDK_INT < 31) throw new IllegalArgumentException();
        // Use public stable fields. Binder/PendingIntent.hashCode changes after process restart.
        // The command handler additionally binds this descriptor to the exact notification postTime.
        String value=index+"\n"+action.title+"\n"+input.getResultKey()+"\n"+action.actionIntent.getCreatorPackage()
            +"\n"+action.actionIntent.getCreatorUid()+"\n"+(action.actionIntent.isBroadcast()?"broadcast":"service")
            +"\n"+action.getSemanticAction()+"\n"+action.isAuthenticationRequired();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    public static JSONObject describe(Notification notification, String owner) {
        try {
            if(notification.actions==null) return null;
            // Prefer an explicitly designated reply action, then a compatible legacy free-form action.
            for(int pass=0;pass<2;pass++)for(int i=0;i<notification.actions.length;i++) {
                Notification.Action action=notification.actions[i];RemoteInput ri=input(action,owner);
                if(ri==null)continue;
                boolean reply=Build.VERSION.SDK_INT>=28 && action.getSemanticAction()==Notification.Action.SEMANTIC_ACTION_REPLY;
                if((pass==0)!=reply)continue;
                return new JSONObject().put("actionId",identity(action,i,ri)).put("label",RelayNotificationListener.clip(action.title,80));
            }
        } catch(Exception ignored) { /* Treat uninspectable actions as unsupported. */ }
        return null;
    }
    public static String execute(Context context, Notification notification, String owner,
                                 JSONObject originalEvent, Map<String,String> data) {
        try {
            JSONObject reply=new JSONObject(data.get("reply")), advertised=originalEvent.optJSONObject("reply");
            String actionId=reply.getString("actionId");
            if(advertised==null)return "REPLY_UNAVAILABLE";
            if(!actionId.equals(advertised.optString("actionId")))return "REPLY_ACTION_CHANGED";
            Notification.Action target=null;RemoteInput remoteInput=null;
            if(notification.actions!=null)for(int i=0;i<notification.actions.length;i++) {
                RemoteInput ri=input(notification.actions[i],owner);
                if(ri!=null && actionId.equals(identity(notification.actions[i],i,ri))) {
                    target=notification.actions[i];remoteInput=ri;break;
                }
            }
            if(target==null)return "REPLY_ACTION_CHANGED";
            KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
            // A remote browser request never bypasses device lock or action authentication.
            if(keyguard==null || keyguard.isKeyguardLocked())return "REPLY_REQUIRES_UNLOCK";
            Credentials prefs=new Credentials(context);String text;
            if("encrypted".equals(reply.optString("mode"))) {
                String replyKey=prefs.secret("e2eeKey");String nativeKey=prefs.secret("nativeLinkKey");if(prefs.flag("nativeMode",false)&&!nativeKey.isEmpty()&&EventCipher.fingerprint(nativeKey).equals(reply.optString("keyId")))replyKey=nativeKey;
                if(reply.optInt("version")!=1 || replyKey.isEmpty())return "E2EE_KEY_REQUIRED";
                try { text=ReplyCipher.decrypt(replyKey,reply.getString("keyId"),reply.getString("iv"),reply.getString("ciphertext"),data,actionId); }
                catch(Exception ignored) { return "E2EE_KEY_REQUIRED"; }
            } else if("plain".equals(reply.optString("mode"))) {
                if(prefs.flag("e2eeEnabled",false))return "E2EE_KEY_REQUIRED";
                text=reply.getString("text");
            } else return "INVALID_REPLY";
            if(!ReplyCipher.validText(text))return "INVALID_REPLY";
            Intent fillIn=new Intent();Bundle results=new Bundle();results.putCharSequence(remoteInput.getResultKey(),text);
            RemoteInput.addResultsToIntent(new RemoteInput[]{remoteInput},fillIn,results);
            if(Build.VERSION.SDK_INT>=28)RemoteInput.setResultsSource(fillIn,RemoteInput.SOURCE_FREE_FORM_INPUT);
            target.actionIntent.send(context,0,fillIn);
            return "REPLY_SENT"; // Original app accepted the Intent; recipient delivery is not observable here.
        } catch(PendingIntent.CanceledException ignored) { return "REPLY_UNAVAILABLE"; }
        catch(Exception ignored) { return "FAILED"; }
    }
}
