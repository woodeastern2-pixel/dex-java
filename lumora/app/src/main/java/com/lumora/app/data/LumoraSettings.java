package com.lumora.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * 루모라 비서 앱의 모든 환경설정.
 * - 비민감 항목: SharedPreferences
 * - API 키: EncryptedSharedPreferences (AES-256)
 */
public class LumoraSettings {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";

    public static final int CURRENT_LEGAL_VERSION = 1;

    private static final String TAG = "LumoraSettings";
    private static final String PREF = "lumora_settings";
    private static final String SECURE_PREF = "lumora_settings_secure";

    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_USE_REMOTE = "use_remote";
    private static final String KEY_USER_NAME = "user_name";

    private static final String KEY_MORNING = "morning_time";   // HH:mm
    private static final String KEY_EVENING = "evening_time";
    private static final String KEY_QUIET_START = "quiet_start"; // HH (int)
    private static final String KEY_QUIET_END = "quiet_end";
    private static final String KEY_TOAST_ENABLED = "toast_enabled";

    private static final String KEY_COLLECT_SCREEN = "collect_screen";
    private static final String KEY_COLLECT_USAGE = "collect_usage";
    private static final String KEY_COLLECT_LOCATION = "collect_location";

    private static final String KEY_LEGAL_ACCEPTED_VERSION = "legal_accepted_version";
    private static final String KEY_AGE_CONFIRMED = "age_confirmed";
    private static final String KEY_OVERSEAS_TRANSFER = "consent_overseas_transfer";
    private static final String KEY_USAGE_CONSENT = "consent_usage";
    private static final String KEY_LOCATION_CONSENT = "consent_location";

    private final SharedPreferences prefs;
    private final SharedPreferences securePrefs;

    public LumoraSettings(Context ctx) {
        Context app = ctx.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        this.securePrefs = openSecure(app);
    }

    private SharedPreferences openSecure(Context app) {
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    app,
                    SECURE_PREF,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Throwable t) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back: " + t.getMessage());
            return app.getSharedPreferences(SECURE_PREF + "_fallback", Context.MODE_PRIVATE);
        }
    }

    // --- LLM ---
    public String getProvider() { return prefs.getString(KEY_PROVIDER, PROVIDER_OPENAI); }
    public String getApiKey() { return securePrefs.getString(KEY_API_KEY, ""); }
    public String getModel() {
        String m = prefs.getString(KEY_MODEL, "");
        return (m == null || m.isEmpty()) ? defaultModelFor(getProvider()) : m;
    }
    public String getBaseUrl() { return prefs.getString(KEY_BASE_URL, ""); }
    public boolean isRemoteEnabled() {
        return prefs.getBoolean(KEY_USE_REMOTE, false) && !getApiKey().isEmpty();
    }
    public String getUserName() { return prefs.getString(KEY_USER_NAME, ""); }

    public void saveLlm(String provider, String apiKey, String model, String baseUrl,
                        boolean useRemote, String userName) {
        prefs.edit()
                .putString(KEY_PROVIDER, provider)
                .putString(KEY_MODEL, model == null ? "" : model.trim())
                .putString(KEY_BASE_URL, baseUrl == null ? "" : baseUrl.trim())
                .putBoolean(KEY_USE_REMOTE, useRemote)
                .putString(KEY_USER_NAME, userName == null ? "" : userName.trim())
                .apply();
        securePrefs.edit()
                .putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim())
                .apply();
    }

    // --- 알림 ---
    public String getMorningTime() { return prefs.getString(KEY_MORNING, "07:30"); }
    public String getEveningTime() { return prefs.getString(KEY_EVENING, "21:00"); }
    public int getQuietStartHour() { return prefs.getInt(KEY_QUIET_START, 23); }
    public int getQuietEndHour() { return prefs.getInt(KEY_QUIET_END, 7); }
    public boolean isToastEnabled() { return prefs.getBoolean(KEY_TOAST_ENABLED, true); }

    public void saveNotify(String morning, String evening, int quietStart, int quietEnd, boolean toast) {
        prefs.edit()
                .putString(KEY_MORNING, morning)
                .putString(KEY_EVENING, evening)
                .putInt(KEY_QUIET_START, quietStart)
                .putInt(KEY_QUIET_END, quietEnd)
                .putBoolean(KEY_TOAST_ENABLED, toast)
                .apply();
    }

    // --- 수집 토글 ---
    public boolean isCollectScreen() { return prefs.getBoolean(KEY_COLLECT_SCREEN, true); }
    public boolean isCollectUsage() { return prefs.getBoolean(KEY_COLLECT_USAGE, false); }
    public boolean isCollectLocation() { return prefs.getBoolean(KEY_COLLECT_LOCATION, false); }
    public void saveCollect(boolean screen, boolean usage, boolean location) {
        prefs.edit()
                .putBoolean(KEY_COLLECT_SCREEN, screen)
                .putBoolean(KEY_COLLECT_USAGE, usage)
                .putBoolean(KEY_COLLECT_LOCATION, location)
                .apply();
    }

    // --- 약관/동의 ---
    public boolean isLegalAccepted() {
        return prefs.getInt(KEY_LEGAL_ACCEPTED_VERSION, 0) >= CURRENT_LEGAL_VERSION
                && prefs.getBoolean(KEY_AGE_CONFIRMED, false);
    }

    public void acceptLegal(boolean ageConfirmed,
                            boolean overseasConsent,
                            boolean usageConsent,
                            boolean locationConsent) {
        prefs.edit()
                .putInt(KEY_LEGAL_ACCEPTED_VERSION, CURRENT_LEGAL_VERSION)
                .putBoolean(KEY_AGE_CONFIRMED, ageConfirmed)
                .putBoolean(KEY_OVERSEAS_TRANSFER, overseasConsent)
                .putBoolean(KEY_USAGE_CONSENT, usageConsent)
                .putBoolean(KEY_LOCATION_CONSENT, locationConsent)
                .apply();
        // 동의한 항목만 수집 토글 ON
        saveCollect(isCollectScreen(), usageConsent, locationConsent);
    }

    public boolean isOverseasTransferConsented() { return prefs.getBoolean(KEY_OVERSEAS_TRANSFER, false); }
    public boolean isUsageConsented() { return prefs.getBoolean(KEY_USAGE_CONSENT, false); }
    public boolean isLocationConsented() { return prefs.getBoolean(KEY_LOCATION_CONSENT, false); }

    public void revokeConsent() {
        prefs.edit()
                .remove(KEY_LEGAL_ACCEPTED_VERSION)
                .remove(KEY_AGE_CONFIRMED)
                .remove(KEY_OVERSEAS_TRANSFER)
                .remove(KEY_USAGE_CONSENT)
                .remove(KEY_LOCATION_CONSENT)
                .apply();
    }

    public static String defaultModelFor(String provider) {
        switch (provider) {
            case PROVIDER_ANTHROPIC: return "claude-3-5-haiku-20241022";
            case PROVIDER_GEMINI: return "gemini-1.5-flash";
            case PROVIDER_OPENAI_COMPATIBLE: return "HCX-003";
            case PROVIDER_OPENAI:
            default: return "gpt-4o-mini";
        }
    }

    public static String defaultBaseUrlFor(String provider) {
        switch (provider) {
            case PROVIDER_OPENAI_COMPATIBLE:
                return "https://clovastudio.stream.ntruss.com/v3/chat-completions";
            default: return "";
        }
    }
}
