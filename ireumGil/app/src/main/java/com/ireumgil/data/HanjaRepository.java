package com.ireumgil.data;

import android.content.Context;

import com.ireumgil.model.HanjaCharacter;

import java.util.ArrayList;
import java.util.List;

public class HanjaRepository {

    private static final String APP_METADATA_NOTE = "일부 획수/오행 정보는 성명학 기준 보완 데이터입니다";

    private final HanjaDao dao;
    private final HanjaAssetImporter importer;

    public HanjaRepository(Context context) {
        HanjaDatabase db = HanjaDatabase.getInstance(context.getApplicationContext());
        this.dao = db.hanjaDao();
        this.importer = new HanjaAssetImporter();
        importer.importIfNeeded(context.getApplicationContext(), db);
    }

    public List<HanjaCharacter> getSurnameCandidates(String surnameHangul) {
        if (surnameHangul == null || surnameHangul.trim().isEmpty()) {
            return getCommonSurnameCharacters();
        }
        return mapList(dao.searchSurnameByReadingPrefix(surnameHangul.trim()));
    }

    public List<HanjaCharacter> searchByReading(String reading) {
        if (reading == null || reading.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return mapList(dao.searchByReadingExact(reading.trim()));
    }

    public List<HanjaCharacter> searchByReadingLike(String readingPrefix) {
        String prefix = readingPrefix == null ? "" : readingPrefix.trim();
        if (prefix.isEmpty()) {
            return new ArrayList<>();
        }
        return mapList(dao.searchByReadingPrefix(prefix));
    }

    public List<HanjaCharacter> searchSurnameByReadingLike(String readingPrefix) {
        String prefix = readingPrefix == null ? "" : readingPrefix.trim();
        if (prefix.isEmpty()) {
            return new ArrayList<>();
        }
        return mapList(dao.searchSurnameByReadingPrefix(prefix));
    }

    public List<HanjaCharacter> getCommonSurnameCharacters() {
        return mapList(dao.getCommonSurnameCharacters());
    }

    public boolean isCommonSurnameReading(String reading) {
        if (reading == null || reading.trim().isEmpty()) {
            return false;
        }
        return !dao.searchSurnameByReadingPrefix(reading.trim()).isEmpty();
    }

    public HanjaCharacter getByCharacter(String character) {
        if (character == null || character.trim().isEmpty()) {
            return null;
        }
        HanjaEntity entity = dao.findByCharacter(character.trim());
        return entity == null ? null : map(entity);
    }

    public List<HanjaCharacter> getAllAllowed() {
        return mapList(dao.getAllAllowed());
    }

    public List<HanjaCharacter> search(
            String reading,
            String characterKeyword,
            String meaningKeyword,
            Integer strokeCount,
            Boolean allowedForName,
            Boolean surnameOnly,
            int limit
    ) {
        return mapList(dao.search(
                trimOrEmpty(reading),
                trimOrEmpty(characterKeyword),
                trimOrEmpty(meaningKeyword),
                strokeCount,
                allowedForName,
                surnameOnly,
                limit
        ));
    }

    private List<HanjaCharacter> mapList(List<HanjaEntity> entities) {
        List<HanjaCharacter> out = new ArrayList<>();
        for (HanjaEntity e : entities) {
            out.add(map(e));
        }
        return out;
    }

    private HanjaCharacter map(HanjaEntity e) {
        return new HanjaCharacter(
                e.id,
                e.character,
                e.koreanReading,
                e.meaning,
                e.strokeCount,
                e.radical,
                e.fiveElement,
                e.allowedForName,
                e.isAdditionalNameHanja,
                e.isBasicEducationHanja,
                e.isVariant,
                e.variantOf,
                e.isCommonSurname,
                normalizeGenderPreference(e.genderPreference),
                e.source,
                e.sourceVersion,
                e.sourceNote,
                APP_METADATA_NOTE
        );
    }

    private String normalizeGenderPreference(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "공용";
        }
        if ("NEUTRAL".equalsIgnoreCase(value) || "공용".equals(value)) {
            return "공용";
        }
        if ("MALE".equalsIgnoreCase(value) || "남".equals(value) || "남자".equals(value)) {
            return "남";
        }
        if ("FEMALE".equalsIgnoreCase(value) || "여".equals(value) || "여자".equals(value)) {
            return "여";
        }
        return value;
    }

    private String trimOrEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
