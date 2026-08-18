package com.lumora.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TaskEntity.class, HabitEntity.class, ContextLogEntity.class},
        version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract TaskDao taskDao();
    public abstract HabitDao habitDao();
    public abstract ContextLogDao contextLogDao();

    public static AppDatabase get(Context ctx) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(ctx.getApplicationContext(),
                                    AppDatabase.class, "lumora.db")
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries() // 단순 작업 위주, 호출지점에서 짧게 사용
                            .build();
                }
            }
        }
        return instance;
    }
}
