package com.whereisit.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import com.whereisit.app.model.ItemEntity;

@Database(entities = {ItemEntity.class}, version = 1, exportSchema = false)
@TypeConverters({StringListConverter.class})
public abstract class ItemDatabase extends RoomDatabase {

    public abstract ItemDao itemDao();

    private static volatile ItemDatabase INSTANCE;

    public static ItemDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ItemDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ItemDatabase.class,
                            "where_is_it_db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
