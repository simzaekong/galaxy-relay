package kr.infosphere.galaxyrelay;
import android.content.Context;
import androidx.room.*;
@Database(entities={EventRecord.class,CommandRecord.class},version=1,exportSchema=false)
public abstract class RelayDatabase extends RoomDatabase {
    public abstract RelayDao dao();
    private static volatile RelayDatabase instance;
    public static RelayDatabase get(Context c){if(instance==null)synchronized(RelayDatabase.class){if(instance==null)instance=Room.databaseBuilder(c.getApplicationContext(),RelayDatabase.class,"relay.db").build();}return instance;}
}
