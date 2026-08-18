package com.ireumgil.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "hanja",
        indices = {
                @Index("character"),
                @Index("koreanReading"),
                @Index("allowedForName"),
                @Index("isCommonSurname"),
                @Index("strokeCount")
        }
)
public class HanjaEntity {

    @PrimaryKey(autoGenerate = true)
    public long pk;

    public long id;

    @NonNull
    public String character;

    public String koreanReading;
    public String meaning;
    public Integer strokeCount;
    public String radical;
    public String fiveElement;
    public Boolean allowedForName;
    public Boolean isAdditionalNameHanja;
    public Boolean isBasicEducationHanja;
    public Boolean isVariant;
    public String variantOf;
    public Boolean isCommonSurname;
    public String genderPreference;
    public String source;
    public String sourceVersion;
    public String sourceNote;

    public HanjaEntity() {
        this.character = "";
    }
}
