package kr.infosphere.galaxyrelay;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
public final class NativeFrames {
 private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
 private int expected=-1;private long started;
 public synchronized byte[] feed(byte[] part,long now){
  if(part==null||part.length==0||part.length>512)throw new IllegalArgumentException();
  if(started!=0&&now-started>30000)reset();if(started==0)started=now;bytes.write(part,0,part.length);
  if(bytes.size()>65540){reset();throw new IllegalArgumentException();}
  byte[] all=bytes.toByteArray();
  if(expected<0&&all.length>=4){expected=ByteBuffer.wrap(all,0,4).getInt();if(expected<2||expected>65536){reset();throw new IllegalArgumentException();}}
  if(expected<0||all.length<expected+4)return null;
  if(all.length!=expected+4){reset();throw new IllegalArgumentException();}
  byte[] result=java.util.Arrays.copyOfRange(all,4,all.length);reset();return result;
 }
 public synchronized void reset(){bytes.reset();expected=-1;started=0;}
 public static byte[] pack(byte[] data){if(data.length<2||data.length>65536)throw new IllegalArgumentException();return ByteBuffer.allocate(data.length+4).putInt(data.length).put(data).array();}
}

