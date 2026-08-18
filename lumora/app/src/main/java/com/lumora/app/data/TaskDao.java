package com.lumora.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TaskEntity t);

    @Update
    void update(TaskEntity t);

    @Delete
    void delete(TaskEntity t);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity getById(long id);

    @Query("SELECT * FROM tasks ORDER BY due_at = 0, due_at ASC, created_at DESC")
    List<TaskEntity> all();

    @Query("SELECT * FROM tasks WHERE status != 'DONE' AND (due_at = 0 OR (due_at >= :startOfDay AND due_at < :endOfDay)) ORDER BY due_at ASC")
    List<TaskEntity> today(long startOfDay, long endOfDay);

    @Query("SELECT * FROM tasks WHERE status != 'DONE' AND due_at >= :now ORDER BY due_at ASC")
    List<TaskEntity> upcoming(long now);

    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY completed_at DESC LIMIT 200")
    List<TaskEntity> recentDone();

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'DONE' AND completed_at >= :startOfDay AND completed_at < :endOfDay")
    int countDoneInRange(long startOfDay, long endOfDay);

    @Query("SELECT COUNT(*) FROM tasks WHERE created_at >= :startOfDay AND created_at < :endOfDay")
    int countCreatedInRange(long startOfDay, long endOfDay);

    @Query("DELETE FROM tasks")
    void clearAll();
}
