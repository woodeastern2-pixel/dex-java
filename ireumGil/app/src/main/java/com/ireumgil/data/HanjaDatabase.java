package com.ireumgil.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {HanjaEntity.class}, version = 1, exportSchema = false)
public abstract class HanjaDatabase extends RoomDatabase {

    private static volatile HanjaDatabase instance;

    public abstract HanjaDao hanjaDao();

    public static HanjaDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (HanjaDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    HanjaDatabase.class,
                                    "ireumgil_hanja.db"
                            )
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return instance;
    }
}
