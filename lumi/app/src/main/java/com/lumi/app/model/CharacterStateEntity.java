package com.lumi.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "character_state")
public class CharacterStateEntity {
    @PrimaryKey
    public int singletonId;

    @NonNull
    public String name;

    @NonNull
    public String mood;

    public int affinity;
    public int totalInteractions;
    public int dailyInteractions;

    @NonNull
    public String lastInteractionDate;

    public int growthStage;
    public int shy;
    public int cheerful;
    public int calm;
    public int curious;
    public int playful;
    public int emotional;
    public String preferredNickname;

    public CharacterStateEntity() {
        singletonId = 1;
        name = "루미";
        mood = "curious";
        affinity = 12;
        totalInteractions = 0;
        dailyInteractions = 0;
        lastInteractionDate = "";
        growthStage = 1;
        shy = 68;
        cheerful = 42;
        calm = 55;
        curious = 74;
        playful = 28;
        emotional = 48;
        preferredNickname = null;
    }

    public CharacterStateEntity copy() {
        CharacterStateEntity copy = new CharacterStateEntity();
        copy.singletonId = singletonId;
        copy.name = name;
        copy.mood = mood;
        copy.affinity = affinity;
        copy.totalInteractions = totalInteractions;
        copy.dailyInteractions = dailyInteractions;
        copy.lastInteractionDate = lastInteractionDate;
        copy.growthStage = growthStage;
        copy.shy = shy;
        copy.cheerful = cheerful;
        copy.calm = calm;
        copy.curious = curious;
        copy.playful = playful;
        copy.emotional = emotional;
        copy.preferredNickname = preferredNickname;
        return copy;
    }
}