package com.whereisit.app.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class TagUtil {

    private TagUtil() {
    }

    public static List<String> normalize(List<String> tags) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (tags == null) {
            return new ArrayList<>();
        }
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String cleaned = raw.trim().replace("|", "");
            while (cleaned.startsWith("#")) {
                cleaned = cleaned.substring(1).trim();
            }
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
        }
        return new ArrayList<>(unique);
    }
}
