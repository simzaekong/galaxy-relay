package kr.infosphere.galaxyrelay;
/** Bluetooth CCCD state for one connected central. Values contain no application data. */
public final class GattSubscription {
 public enum Result {OK,UNSUPPORTED,INVALID_OFFSET,INVALID_LENGTH}
 private int mode;
 /** Stable diagnostics contain transport metadata only, never device addresses or payloads. */
 public static String rejection(boolean descriptorMatches,boolean samePeer,byte[] value,boolean prepared,int offset){
  if(!descriptorMatches)return "G03-D";
  if(!samePeer)return "G03-C";
  if(prepared)return "G03-P";
  if(offset!=0)return "G03-O";
  if(value==null||value.length!=2)return "G03-L";
  if(value[1]!=0||(value[0]&255)>3)return "G03-V";
  return "";
 }
 public Result write(byte[] value,boolean prepared,int offset){
  if(prepared)return Result.UNSUPPORTED;
  if(offset!=0)return Result.INVALID_OFFSET;
  if(value==null||value.length!=2)return Result.INVALID_LENGTH;
  int next=value[0]&255;if(value[1]!=0||next>3)return Result.UNSUPPORTED;
  mode=next;return Result.OK;
 }
 public byte[] read(int offset){if(offset<0||offset>2)throw new IllegalArgumentException();return java.util.Arrays.copyOfRange(new byte[]{(byte)mode,0},offset,2);}
 public boolean enabled(){return mode!=0;}
 public boolean confirm(){return (mode&2)!=0;}
 public void reset(){mode=0;}
}
