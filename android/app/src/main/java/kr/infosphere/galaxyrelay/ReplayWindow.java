package kr.infosphere.galaxyrelay;
import java.util.LinkedHashMap;
/** Keep every ID until its timestamp can no longer pass the receive freshness check. */
public final class ReplayWindow {
 private final LinkedHashMap<String,Long> ids=new LinkedHashMap<>();
 public boolean accept(String id,long timestamp,long now){
  if(timestamp<now-120000||timestamp>now+120000)return false;
  ids.entrySet().removeIf(e->e.getValue()<now);
  if(ids.containsKey(id)||ids.size()>=4096)return false;
  ids.put(id,timestamp+120000);return true;
 }
 public void clear(){ids.clear();}
}
