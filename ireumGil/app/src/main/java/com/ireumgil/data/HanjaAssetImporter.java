package com.ireumgil.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HanjaAssetImporter {

    private static final String PREFS = "hanja_import_prefs";
    private static final String KEY_DATASET_VERSION = "dataset_version";
    private static final String KEY_DB_VERSION = "db_version";

    public static final String DATASET_VERSION = "2026-05";

    public static final String ASSET_DIR = "hanja";
    public static final String FILE_PERSON_CSV = "official_person_name_hanja.csv";
    public static final String FILE_PERSON_JSON = "official_person_name_hanja.json";
    public static final String FILE_VARIANTS_CSV = "official_allowed_variants.csv";
    public static final String FILE_BASIC_EDUCATION_CSV = "official_basic_education_hanja.csv";

    public static class ImportResult {
        public final boolean imported;
        public final int totalCount;

        public ImportResult(boolean imported, int totalCount) {
            this.imported = imported;
            this.totalCount = totalCount;
        }
    }

    public ImportResult importIfNeeded(Context context, HanjaDatabase database) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int dbVersion = 1;
        String savedVersion = prefs.getString(KEY_DATASET_VERSION, "");
        int savedDbVersion = prefs.getInt(KEY_DB_VERSION, -1);

        int existingCount = database.hanjaDao().countAll();
        boolean needsImport = existingCount == 0 || !DATASET_VERSION.equals(savedVersion) || savedDbVersion != dbVersion;

        if (!needsImport) {
            return new ImportResult(false, existingCount);
        }

        List<HanjaEntity> imported = loadFromAssets(context.getAssets());

        database.runInTransaction(() -> {
            HanjaDao dao = database.hanjaDao();
            dao.deleteAll();
            dao.insertAll(imported);
        });

        prefs.edit()
                .putString(KEY_DATASET_VERSION, DATASET_VERSION)
                .putInt(KEY_DB_VERSION, dbVersion)
                .apply();

        return new ImportResult(true, imported.size());
    }

    private List<HanjaEntity> loadFromAssets(AssetManager assetManager) {
        Map<String, HanjaEntity> merged = new LinkedHashMap<>();
        long nextId = 1L;

        nextId = mergeCsv(assetManager, FILE_PERSON_CSV, merged, nextId);
        nextId = mergeJson(assetManager, FILE_PERSON_JSON, merged, nextId);
        nextId = mergeCsv(assetManager, FILE_VARIANTS_CSV, merged, nextId);
        mergeCsv(assetManager, FILE_BASIC_EDUCATION_CSV, merged, nextId);

        return new ArrayList<>(merged.values());
    }

    private long mergeCsv(AssetManager am, String filename, Map<String, HanjaEntity> merged, long nextId) {
        try {
            if (!assetExists(am, filename)) {
                return nextId;
            }
            InputStream in = am.open(ASSET_DIR + "/" + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String header = reader.readLine();
            if (header == null) {
                reader.close();
                return nextId;
            }
            List<String> headers = parseCsvLine(header);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i);
                    String value = i < values.size() ? values.get(i) : "";
                    row.put(key, value);
                }
                nextId = mergeRow(row, merged, nextId);
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return nextId;
    }

    private long mergeJson(AssetManager am, String filename, Map<String, HanjaEntity> merged, long nextId) {
        try {
            if (!assetExists(am, filename)) {
                return nextId;
            }
            InputStream in = am.open(ASSET_DIR + "/" + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", optString(item, "id"));
                row.put("character", optString(item, "character"));
                row.put("koreanReading", optString(item, "koreanReading"));
                row.put("meaning", optString(item, "meaning"));
                row.put("strokeCount", optString(item, "strokeCount"));
                row.put("radical", optString(item, "radical"));
                row.put("fiveElement", optString(item, "fiveElement"));
                row.put("allowedForName", optString(item, "allowedForName"));
                row.put("isAdditionalNameHanja", optString(item, "isAdditionalNameHanja"));
                row.put("isBasicEducationHanja", optString(item, "isBasicEducationHanja"));
                row.put("isVariant", optString(item, "isVariant"));
                row.put("variantOf", optString(item, "variantOf"));
                row.put("isCommonSurname", optString(item, "isCommonSurname"));
                row.put("genderPreference", optString(item, "genderPreference"));
                row.put("source", optString(item, "source"));
                row.put("sourceVersion", optString(item, "sourceVersion"));
                row.put("sourceNote", optString(item, "sourceNote"));
                nextId = mergeRow(row, merged, nextId);
            }
        } catch (Exception ignored) {
        }
        return nextId;
    }

    private long mergeRow(Map<String, String> row, Map<String, HanjaEntity> merged, long nextId) {
        String character = safe(row.get("character"));
        if (character.isEmpty()) {
            return nextId;
        }

        HanjaEntity current = merged.get(character);
        if (current == null) {
            current = new HanjaEntity();
            current.character = character;
            current.id = parseLongOrDefault(row.get("id"), nextId);
            merged.put(character, current);
            nextId++;
        }

        current.koreanReading = pick(current.koreanReading, row.get("koreanReading"));
        current.meaning = pick(current.meaning, row.get("meaning"));
        current.strokeCount = pickInt(current.strokeCount, row.get("strokeCount"));
        current.radical = pick(current.radical, row.get("radical"));
        current.fiveElement = pick(current.fiveElement, row.get("fiveElement"));
        current.allowedForName = mergeBoolean(current.allowedForName, parseBoolean(row.get("allowedForName")));
        current.isAdditionalNameHanja = mergeBoolean(current.isAdditionalNameHanja, parseBoolean(row.get("isAdditionalNameHanja")));
        current.isBasicEducationHanja = mergeBoolean(current.isBasicEducationHanja, parseBoolean(row.get("isBasicEducationHanja")));
        current.isVariant = mergeBoolean(current.isVariant, parseBoolean(row.get("isVariant")));
        current.variantOf = pick(current.variantOf, row.get("variantOf"));
        current.isCommonSurname = mergeBoolean(current.isCommonSurname, parseBoolean(row.get("isCommonSurname")));
        current.genderPreference = pick(current.genderPreference, row.get("genderPreference"));
        current.source = mergeTextDistinct(current.source, row.get("source"), " | ");
        current.sourceVersion = pick(current.sourceVersion, row.get("sourceVersion"));
        current.sourceNote = mergeTextDistinct(current.sourceNote, row.get("sourceNote"), " ; ");

        return nextId;
    }

    private boolean assetExists(AssetManager am, String filename) {
        try {
            String[] names = am.list(ASSET_DIR);
            if (names == null) {
                return false;
            }
            for (String n : names) {
                if (filename.equals(n)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String optString(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long parseLongOrDefault(String value, long fallback) {
        try {
            String v = safe(value);
            if (v.isEmpty()) {
                return fallback;
            }
            return Long.parseLong(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer pickInt(Integer existing, String candidate) {
        if (existing != null) {
            return existing;
        }
        try {
            String v = safe(candidate);
            if (v.isEmpty()) {
                return null;
            }
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        String v = safe(value).toLowerCase();
        if (v.isEmpty()) {
            return null;
        }
        if ("true".equals(v) || "1".equals(v) || "y".equals(v) || "yes".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "n".equals(v) || "no".equals(v)) {
            return false;
        }
        return null;
    }

    private Boolean mergeBoolean(Boolean current, Boolean incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return current || incoming;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String pick(String current, String incoming) {
        if (current != null && !current.trim().isEmpty()) {
            return current;
        }
        String next = safe(incoming);
        return next.isEmpty() ? null : next;
    }

    private String mergeTextDistinct(String current, String incoming, String separator) {
        String base = safe(current);
        String add = safe(incoming);
        if (add.isEmpty()) {
            return base.isEmpty() ? null : base;
        }
        Set<String> values = new LinkedHashSet<>();
        if (!base.isEmpty()) {
            for (String s : base.split(separator.equals(" | ") ? "\\\\|" : separator.trim())) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        values.add(add);
        StringBuilder out = new StringBuilder();
        for (String s : values) {
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(s);
        }
        return out.toString();
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
