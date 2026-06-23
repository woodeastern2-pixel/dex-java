package com.lumora.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HabitEntity h);

    @Update
    void update(HabitEntity h);

    @Delete
    void delete(HabitEntity h);

    @Query("SELECT * FROM habits ORDER BY created_at ASC")
    List<HabitEntity> all();

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    HabitEntity getById(long id);

    @Query("DELETE FROM habits")
    void clearAll();
}
