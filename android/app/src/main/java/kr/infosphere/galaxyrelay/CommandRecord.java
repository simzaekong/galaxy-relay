package kr.infosphere.galaxyrelay;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="commands",indices={@Index(value="nonce",unique=true)})
public class CommandRecord {
    @PrimaryKey @NonNull public String id="";
    @NonNull public String nonce="";
    public long timestamp;
}
