package com.lumora.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 단말 컨텍스트/이벤트 로그.
 * type 예: SCREEN_ON, SCREEN_OFF, USER_PRESENT, POWER_CONNECTED, POWER_DISCONNECTED,
 *           APP_FOREGROUND, LOCATION_ENTER, HABIT_CHECK
 */
@Entity(tableName = "context_log")
public class ContextLogEntity {

    public static final String T_SCREEN_ON = "SCREEN_ON";
    public static final String T_SCREEN_OFF = "SCREEN_OFF";
    public static final String T_USER_PRESENT = "USER_PRESENT";
    public static final String T_POWER_CONNECTED = "POWER_CONNECTED";
    public static final String T_POWER_DISCONNECTED = "POWER_DISCONNECTED";
    public static final String T_APP_FOREGROUND = "APP_FOREGROUND";
    public static final String T_HABIT_CHECK = "HABIT_CHECK";
    public static final String T_LOCATION = "LOCATION";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "type")
    public String type = "";

    @ColumnInfo(name = "ts")
    public long ts;

    @ColumnInfo(name = "value")
    public String value;
}
