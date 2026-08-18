package com.lumi.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.ConversationMessage;
import com.lumi.app.model.MemoryEntry;

import java.util.List;

@Dao
public interface CharacterDao {
    @Query("SELECT * FROM character_state WHERE singletonId = 1")
    CharacterStateEntity getCharacterState();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCharacterState(CharacterStateEntity state);

    @Insert
    long insertConversationMessage(ConversationMessage message);

    @Query("SELECT * FROM conversation_message ORDER BY timestamp DESC LIMIT :limit")
    List<ConversationMessage> getRecentMessages(int limit);

    @Insert
    long insertMemory(MemoryEntry entry);

    @Update
    void updateMemory(MemoryEntry entry);

    @Query("SELECT * FROM memory_entry WHERE keyword = :keyword LIMIT 1")
    MemoryEntry findMemory(String keyword);

    @Query("SELECT * FROM memory_entry ORDER BY importance DESC, timestamp DESC LIMIT :limit")
    List<MemoryEntry> getTopMemories(int limit);

    @Query("DELETE FROM conversation_message")
    void clearConversation();

    @Query("DELETE FROM memory_entry")
    void clearMemories();

    @Query("DELETE FROM character_state")
    void clearCharacterState();
}