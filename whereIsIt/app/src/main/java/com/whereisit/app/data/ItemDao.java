package com.whereisit.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.whereisit.app.model.ItemEntity;
import java.util.List;

@Dao
public interface ItemDao {

    @Insert
    long insert(ItemEntity item);

    @Update
    void update(ItemEntity item);

    @Delete
    void delete(ItemEntity item);

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    ItemEntity getById(long id);

    @Query("SELECT * FROM items ORDER BY updated_date DESC")
    List<ItemEntity> getAllItems();

    @Query("SELECT * FROM items WHERE favorite = 1 ORDER BY updated_date DESC")
    List<ItemEntity> getFavoriteItems();

    @Query("SELECT * FROM items ORDER BY created_date DESC LIMIT :limit")
    List<ItemEntity> getRecentItems(int limit);

    @Query("SELECT * FROM items WHERE (:category = '' OR category = :category) AND (item_name LIKE '%' || :keyword || '%' OR location_name LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%') ORDER BY updated_date DESC")
    List<ItemEntity> searchItems(String keyword, String category);
}
