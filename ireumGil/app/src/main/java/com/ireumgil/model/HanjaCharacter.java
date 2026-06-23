package com.ireumgil.model;

public class HanjaCharacter {
    public final long id;
    public final String character;
    public final String reading;
    public final String meaning;
    public final Integer strokeCount;
    public final String radical;
    public final String elementCategory;
    public final Boolean allowedForName;
    public final Boolean isAdditionalNameHanja;
    public final Boolean isBasicEducationHanja;
    public final Boolean isVariant;
    public final String variantOf;
    public final Boolean isCommonSurname;
    public final String genderPreference;
    public final String source;
    public final String sourceVersion;
    public final String sourceNote;
    public final String appMetadataNote;

    public HanjaCharacter(
            long id,
            String character,
            String reading,
            String meaning,
            Integer strokeCount,
            String radical,
            String elementCategory,
            Boolean allowedForName,
            Boolean isAdditionalNameHanja,
            Boolean isBasicEducationHanja,
            Boolean isVariant,
            String variantOf,
            Boolean isCommonSurname,
            String genderPreference,
            String source,
            String sourceVersion,
            String sourceNote,
            String appMetadataNote
    ) {
        this.id = id;
        this.character = character;
        this.reading = reading;
        this.meaning = meaning;
        this.strokeCount = strokeCount;
        this.radical = radical;
        this.elementCategory = elementCategory;
        this.allowedForName = allowedForName;
        this.isAdditionalNameHanja = isAdditionalNameHanja;
        this.isBasicEducationHanja = isBasicEducationHanja;
        this.isVariant = isVariant;
        this.variantOf = variantOf;
        this.isCommonSurname = isCommonSurname;
        this.genderPreference = genderPreference;
        this.source = source;
        this.sourceVersion = sourceVersion;
        this.sourceNote = sourceNote;
        this.appMetadataNote = appMetadataNote;
    }

    public HanjaCharacter(
            String character,
            String reading,
            String meaning,
            int strokeCount,
            String elementCategory,
            boolean allowedForName,
            String genderPreference,
            String usageNote
    ) {
        this(
                0L,
                character,
                reading,
                meaning,
                strokeCount,
                null,
                elementCategory,
                allowedForName,
                false,
                false,
                false,
                null,
                false,
                genderPreference,
                "legacy-sample",
                null,
                usageNote,
                usageNote
        );
    }
}
