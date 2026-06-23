package com.lumi.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "memory_entry", indices = {@Index(value = {"keyword"}, unique = true)})
public class MemoryEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String keyword;

    @NonNull
    public String detail;

    @NonNull
    public String category;

    public int importance;
    public long timestamp;

    public MemoryEntry(@NonNull String keyword, @NonNull String detail, @NonNull String category, int importance, long timestamp) {
        this.keyword = keyword;
        this.detail = detail;
        this.category = category;
        this.importance = importance;
        this.timestamp = timestamp;
    }
}