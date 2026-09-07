package com.lumora.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ContextLogDao {

    @Insert
    long insert(ContextLogEntity e);

    @Query("SELECT * FROM context_log WHERE ts >= :since ORDER BY ts ASC")
    List<ContextLogEntity> since(long since);

    @Query("SELECT * FROM context_log WHERE type = :type AND ts >= :since ORDER BY ts ASC")
    List<ContextLogEntity> sinceOfType(String type, long since);

    @Query("DELETE FROM context_log WHERE ts < :before")
    int purgeOlderThan(long before);

    @Query("DELETE FROM context_log")
    void clearAll();
}
