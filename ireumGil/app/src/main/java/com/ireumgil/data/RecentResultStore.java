package com.ireumgil.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class RecentResultStore {
    private static final String PREF = "ireumgil_recent";
    private static final String KEY_ITEMS = "items";

    public void save(Context context, String label) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String old = sp.getString(KEY_ITEMS, "");
        String merged = label + "||" + old;
        String[] split = merged.split("\\|\\|");

        List<String> top = new ArrayList<>();
        for (String s : split) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            top.add(s);
            if (top.size() == 5) {
                break;
            }
        }
        sp.edit().putString(KEY_ITEMS, String.join("||", top)).apply();
    }

    public List<String> load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_ITEMS, "");
        List<String> out = new ArrayList<>();
        if (raw.trim().isEmpty()) {
            return out;
        }
        String[] arr = raw.split("\\|\\|");
        for (String s : arr) {
            if (!s.trim().isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
