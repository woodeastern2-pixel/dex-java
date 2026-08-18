package com.lumora.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "habits")
public class HabitEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    /** "HH:mm" 형식. null 이면 알림 없음. */
    @ColumnInfo(name = "time_of_day")
    public String timeOfDay;

    /** 비트0=월 ... 비트6=일. 0이면 매일. */
    @ColumnInfo(name = "weekday_mask")
    public int weekdayMask;

    @ColumnInfo(name = "streak")
    public int streak;

    @ColumnInfo(name = "last_check_day")
    public long lastCheckDay; // yyyymmdd

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
