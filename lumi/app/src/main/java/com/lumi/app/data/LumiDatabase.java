package com.lumi.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.ConversationMessage;
import com.lumi.app.model.MemoryEntry;

@Database(
        entities = {CharacterStateEntity.class, ConversationMessage.class, MemoryEntry.class},
        version = 2,
        exportSchema = false
)
public abstract class LumiDatabase extends RoomDatabase {
    private static volatile LumiDatabase instance;

    public abstract CharacterDao characterDao();

    /** v1 → v2: 기존 스키마 그대로 유지 (데이터 보존) */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // 스키마 변경 없음 — 데이터를 그대로 보존
        }
    };

    public static LumiDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LumiDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), LumiDatabase.class, "lumi.db")
                            .allowMainThreadQueries()
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}