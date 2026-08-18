package com.whereisit.app.data;

import androidx.room.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class StringListConverter {

    private static final String SEP = "||";

    @TypeConverter
    public static String fromList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                String normalized = value.replace(SEP, " ").trim();
                if (!normalized.isEmpty()) {
                    cleaned.add(normalized);
                }
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        return String.join(SEP, cleaned);
    }

    @TypeConverter
    public static List<String> toList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = value.split(Pattern.quote(SEP));
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
