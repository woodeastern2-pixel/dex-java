package com.lumora.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {

    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_DOING = "DOING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SNOOZED = "SNOOZED";

    /** repeat: NONE / DAILY / WEEKDAYS / WEEKLY / MONTHLY */
    public static final String REPEAT_NONE = "NONE";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_WEEKDAYS = "WEEKDAYS";
    public static final String REPEAT_WEEKLY = "WEEKLY";
    public static final String REPEAT_MONTHLY = "MONTHLY";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @ColumnInfo(name = "due_at")
    public long dueAt; // epoch millis, 0 = no time

    @NonNull
    @ColumnInfo(name = "status")
    public String status = STATUS_TODO;

    @NonNull
    @ColumnInfo(name = "repeat_rule")
    public String repeatRule = REPEAT_NONE;

    @ColumnInfo(name = "weekday_mask")
    public int weekdayMask; // bit0=Mon ... bit6=Sun, used when WEEKLY

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "completed_at")
    public long completedAt;

    @ColumnInfo(name = "tag")
    public String tag;
}
