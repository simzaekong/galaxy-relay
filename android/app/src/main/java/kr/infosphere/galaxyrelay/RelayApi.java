package kr.infosphere.galaxyrelay;
import android.content.Context;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public final class RelayApi {
    private final Credentials credentials;
    private final Context context;
    public RelayApi(Context c){context=c.getApplicationContext();credentials=new Credentials(c);}
    public static final class Failure extends Exception {public final int status;public final String code;Failure(int s,String c){super(c);status=s;code=c;}}
    public JSONObject call(String path,JSONObject body,boolean authenticated) throws Exception {
        try{return callWithToken(path,body,authenticated?credentials.secret("token"):null);}catch(Failure e){if(authenticated&&e.status==401){String backend=credentials.get("backend","");credentials.clear();credentials.put("backend",backend);RelayDatabase.get(context).dao().clearEvents();RelayDatabase.get(context).dao().clearCommands();}throw e;}
    }
    public JSONObject callWithToken(String path,JSONObject body,String token) throws Exception {
        URI base=new URI(credentials.get("backend",""));
        if(!"https".equals(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!(base.getPath().isEmpty()||base.getPath().equals("/")))throw new Failure(0,"INVALID_BACKEND");
        HttpsURLConnection connection=(HttpsURLConnection)base.resolve(path).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null){body.put("timestamp",System.currentTimeMillis());body.put("nonce",UUID.randomUUID().toString());connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);try(var output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=connection.getResponseCode();var stream=status<400?connection.getInputStream():connection.getErrorStream();
            String response;try(stream){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();if(stream!=null){byte[] buffer=new byte[1024];int count;while((count=stream.read(buffer))!=-1){if(bytes.size()+count>16384)throw new Failure(0,"RESPONSE_TOO_LARGE");bytes.write(buffer,0,count);}}response=stream==null?"{}":bytes.toString("UTF-8");}
            JSONObject result=new JSONObject(response);if(status<200||status>=300)throw new Failure(status,result.optString("error","SERVER_ERROR"));return result;
        }finally{connection.disconnect();}
    }
}
