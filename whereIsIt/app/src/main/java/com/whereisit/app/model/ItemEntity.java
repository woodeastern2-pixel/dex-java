package com.whereisit.app.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "items")
public class ItemEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "item_name")
    public String itemName;

    @ColumnInfo(name = "location_name")
    public String locationName;

    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "photo_uris")
    public List<String> photoUris;

    @ColumnInfo(name = "memo")
    public String memo;

    @ColumnInfo(name = "created_date")
    public long createdDate;

    @ColumnInfo(name = "updated_date")
    public long updatedDate;

    @ColumnInfo(name = "favorite")
    public boolean favorite;

    @ColumnInfo(name = "tags")
    public List<String> tags;

    public ItemEntity() {
        this.photoUris = new ArrayList<>();
        this.tags = new ArrayList<>();
    }
}
