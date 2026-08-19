package com.signpdf.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Stores one reusable Pro signature locally on the device. */
public final class SignatureStore {

    private static final String PREFS = "signpdf_saved_signature";
    private static final String KEY_SIGNATURE = "signature_json";

    private SignatureStore() { }

    public static void save(Context context, List<List<float[]>> signature) {
        if (context == null || signature == null || signature.isEmpty()) return;

        JSONArray strokes = new JSONArray();
        try {
            for (List<float[]> stroke : signature) {
                if (stroke == null || stroke.isEmpty()) continue;
                JSONArray points = new JSONArray();
                for (float[] point : stroke) {
                    if (point == null || point.length < 2) continue;
                    JSONArray item = new JSONArray();
                    item.put((double) point[0]);
                    item.put((double) point[1]);
                    points.put(item);
                }
                if (points.length() > 0) strokes.put(points);
            }
        } catch (JSONException ignored) {
            return;
        }

        context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGNATURE, strokes.toString())
            .apply();
    }

    public static List<List<float[]>> load(Context context) {
        List<List<float[]>> result = new ArrayList<>();
        if (context == null) return result;

        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SIGNATURE, null);
        if (raw == null || raw.trim().isEmpty()) return result;

        try {
            JSONArray strokes = new JSONArray(raw);
            for (int i = 0; i < strokes.length(); i++) {
                JSONArray points = strokes.optJSONArray(i);
                if (points == null) continue;
                List<float[]> stroke = new ArrayList<>();
                for (int j = 0; j < points.length(); j++) {
                    JSONArray item = points.optJSONArray(j);
                    if (item == null || item.length() < 2) continue;
                    stroke.add(new float[]{
                        (float) item.optDouble(0, 0.0),
                        (float) item.optDouble(1, 0.0)
                    });
                }
                if (stroke.size() >= 2) result.add(stroke);
            }
        } catch (JSONException ignored) {
            clear(context);
        }
        return result;
    }

    public static boolean hasSaved(Context context) {
        return !load(context).isEmpty();
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SIGNATURE)
            .apply();
    }
}
