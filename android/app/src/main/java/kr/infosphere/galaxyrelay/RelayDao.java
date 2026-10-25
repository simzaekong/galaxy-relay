package kr.infosphere.galaxyrelay;
import androidx.room.*;
import java.util.List;
@Dao
public interface RelayDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) void save(EventRecord record);
    @Query("SELECT * FROM events WHERE id=:id") EventRecord find(String id);
    @Query("SELECT * FROM events WHERE notificationKey=:key AND active=1 ORDER BY timestamp DESC LIMIT 1") EventRecord active(String key);
    @Query("SELECT * FROM events WHERE pending=1 ORDER BY timestamp ASC LIMIT 40") List<EventRecord> queue();
    @Query("SELECT * FROM events WHERE active=1") List<EventRecord> activeRecords();
    @Query("SELECT * FROM events WHERE timestamp>=:since ORDER BY timestamp DESC LIMIT 100") List<EventRecord> since(long since);
    @Query("SELECT * FROM events WHERE pending=0 OR active=1 ORDER BY timestamp DESC LIMIT 40") List<EventRecord> recent();
    @Query("UPDATE events SET pending=0 WHERE id=:id AND timestamp=:timestamp") void delivered(String id,long timestamp);
    @Query("UPDATE events SET attempts=attempts+1,nextAt=:nextAt WHERE id=:id AND timestamp=:timestamp") void retry(String id,long timestamp,long nextAt);
    @Query("UPDATE events SET pending=0 WHERE pending=1 AND id NOT IN (SELECT id FROM events WHERE pending=1 ORDER BY timestamp DESC LIMIT 5000)") void boundQueue();
    @Query("UPDATE events SET active=0 WHERE notificationKey=:key") void removed(String key);
    @Insert(onConflict=OnConflictStrategy.IGNORE) long claim(CommandRecord command);
    @Query("SELECT COUNT(*) FROM commands WHERE id=:id") int commandCount(String id);
    @Query("DELETE FROM commands WHERE timestamp<:before") void pruneCommands(long before);
    @Query("DELETE FROM events WHERE pending=0 AND active=0 AND id NOT IN (SELECT id FROM events ORDER BY timestamp DESC LIMIT 5000)") void pruneEvents();
    @Query("DELETE FROM events") void clearEvents();
    @Query("DELETE FROM commands") void clearCommands();
}
