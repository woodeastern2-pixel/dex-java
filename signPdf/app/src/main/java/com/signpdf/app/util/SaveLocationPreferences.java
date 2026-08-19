package com.signpdf.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Persists the user-selected SAF output folder for generated PDF files. */
public final class SaveLocationPreferences {

    private static final String PREFS = "signpdf_save_location";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_LABEL = "tree_label";

    private SaveLocationPreferences() { }

    public static void set(Context context, Uri treeUri, String label) {
        if (context == null || treeUri == null) return;
        context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_LABEL, label == null ? "" : label)
            .apply();
    }

    public static Uri getTreeUri(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_TREE_URI, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Uri.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static String getLabel(Context context) {
        if (context == null) return "";
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LABEL, "");
    }

    public static boolean hasCustomLocation(Context context) {
        return getTreeUri(context) != null;
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
