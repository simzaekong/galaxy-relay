package kr.infosphere.galaxyrelay;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="events",indices={@Index("notificationKey"),@Index("pending")})
public class EventRecord {
    @PrimaryKey @NonNull public String id="";
    public String notificationKey="",packageName="",data="";
    public long timestamp,expiresAt,nextAt;
    public int attempts;
    public boolean pending,active;
}
