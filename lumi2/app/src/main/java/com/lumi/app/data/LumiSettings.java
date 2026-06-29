package com.lumi.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Stores Lumi LLM connection preferences.
 *
 * - 비민감 항목(프로바이더/모델/사용자 이름/플래그)은 일반 SharedPreferences 에 저장
 * - 민감 항목(API 키)은 EncryptedSharedPreferences (AES-256) 에 저장
 * - 동의/약관 수락 플래그도 일반 SharedPreferences 에 저장
 */
public class LumiSettings {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";

    public static final String AVATAR_FEMININE = "feminine";
    public static final String AVATAR_NEUTRAL = "neutral";
    public static final String AVATAR_MAID = "maid";
    public static final String AVATAR_CUSTOM = "custom";
    public static final String AVATAR_CLASSIC = "classic";

    public static final String IMAGE_GEN_PROVIDER_OPENAI = "openai";
    public static final String IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    public static final String IMAGE_GEN_PROVIDER_FAL = "fal";
    public static final String SOURCE_LLM_API_KEY = "";
    public static final String SOURCE_LLM_MODEL = "gemini-3.1-flash-lite";
    public static final String SOURCE_LLM_BASE_URL = "";
    private static final TimeZone SEOUL_TZ = TimeZone.getTimeZone("Asia/Seoul");

    private static final String TAG = "LumiSettings";
    private static final String PREF = "lumi_settings";
    private static final String SECURE_PREF = "lumi_settings_secure";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_GOOGLE_SEARCH_API_KEY = "google_search_api_key";
    private static final String KEY_GOOGLE_SEARCH_CX = "google_search_cx";
    private static final String KEY_MODEL = "model";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_USE_REMOTE = "use_remote";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_AVATAR_STYLE = "avatar_style";
    private static final String KEY_CUSTOM_AVATAR_URI = "custom_avatar_uri";
    private static final String KEY_PROACTIVE = "proactive_enabled";
    private static final String KEY_PROACTIVE_DAILY_MIN = "proactive_daily_min";
    private static final String KEY_PROACTIVE_INTERVAL_MIN = "proactive_interval_min";
    private static final String KEY_PROACTIVE_INTERVAL_MAX = "proactive_interval_max";
    private static final String KEY_PROACTIVE_SENT_DATE = "proactive_sent_date";
    private static final String KEY_PROACTIVE_SENT_COUNT = "proactive_sent_count";
    private static final String KEY_IMAGE_GEN_ENABLED = "image_gen_enabled";
    private static final String KEY_IMAGE_GEN_NSFW = "image_gen_nsfw";
    private static final String KEY_IMAGE_GEN_PROVIDER = "image_gen_provider";
    private static final String KEY_IMAGE_GEN_API_KEY = "image_gen_api_key";
    private static final String KEY_IMAGE_GEN_MODEL = "image_gen_model";
    private static final String KEY_IMAGE_GEN_BASE_URL = "image_gen_base_url";
    private static final String KEY_TTS_SPEECH_RATE = "tts_speech_rate";
    private static final String KEY_TTS_VOICE_NAME = "tts_voice_name";
    private static final String KEY_SAFETY_VIOLATION_COUNT = "safety_violation_count";
    private static final String KEY_SAFETY_LAST_VIOLATION_AT = "safety_last_violation_at";
    private static final String KEY_SAFETY_RESTRICTED_UNTIL = "safety_restricted_until";

    private static final int DEFAULT_PROACTIVE_DAILY_MIN = 4;
    private static final int DEFAULT_PROACTIVE_INTERVAL_MIN = 90;
    private static final int DEFAULT_PROACTIVE_INTERVAL_MAX = 240;
    private static final float DEFAULT_TTS_SPEECH_RATE = 0.9f;

    /** 약관/개인정보처리방침 동의 버전. 약관이 갱되면 숫자를 올려 재동의를 받는다. */
    public static final int CURRENT_LEGAL_VERSION = 2;
    private static final String KEY_LEGAL_ACCEPTED_VERSION = "legal_accepted_version";
    private static final String KEY_AGE_CONFIRMED = "age_confirmed";
    private static final String KEY_OVERSEAS_TRANSFER = "consent_overseas_transfer";
    private static final String KEY_VOICE_TRANSFER = "consent_voice_transfer";

    private final SharedPreferences prefs;
    private final SharedPreferences securePrefs;

    public LumiSettings(Context ctx) {
        Context app = ctx.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        this.securePrefs = openSecure(app);
        migrateApiKeyIfNeeded();
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
            // 암호화 보관소 초기화 실패 시 (예: 키 손상) 안전하게 일반 prefs 로 폴백.
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back: " + t.getMessage());
            return app.getSharedPreferences(SECURE_PREF + "_fallback", Context.MODE_PRIVATE);
        }
    }

    /** SharedPreferences 가 암호화되지 않은 구버전 앱에서 API Key 이동 */
    private void migrateApiKeyIfNeeded() {
        if (prefs.contains(KEY_API_KEY)) {
            String val = prefs.getString(KEY_API_KEY, null);
            if (val != null) {
                securePrefs.edit().putString(KEY_API_KEY, val).apply();
            }
            prefs.edit().remove(KEY_API_KEY).apply();
        }
    }

    // --- Getters / Setters ---

    public String getProvider() {
        return PROVIDER_GEMINI;
    }

    public void setProvider(String provider) {
        prefs.edit().putString(KEY_PROVIDER, PROVIDER_GEMINI).apply();
    }

    public String getApiKey() {
        return SOURCE_LLM_API_KEY.trim();
    }

    public void setApiKey(String apiKey) {
        securePrefs.edit().putString(KEY_API_KEY, SOURCE_LLM_API_KEY.trim()).apply();
    }

    public String getGoogleSearchApiKey() {
        return securePrefs.getString(KEY_GOOGLE_SEARCH_API_KEY, "");
    }

    public void setGoogleSearchApiKey(String apiKey) {
        securePrefs.edit().putString(KEY_GOOGLE_SEARCH_API_KEY, apiKey).apply();
    }

    public String getGoogleSearchCx() {
        return prefs.getString(KEY_GOOGLE_SEARCH_CX, "");
    }

    public void setGoogleSearchCx(String cx) {
        prefs.edit().putString(KEY_GOOGLE_SEARCH_CX, cx).apply();
    }

    public String getModel() {
        return SOURCE_LLM_MODEL;
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, SOURCE_LLM_MODEL).apply();
    }

    public String getBaseUrl() {
        return SOURCE_LLM_BASE_URL;
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(KEY_BASE_URL, SOURCE_LLM_BASE_URL).apply();
    }

    public boolean isUseRemoteServer() {
        return true;
    }

    public void setUseRemoteServer(boolean use) {
        prefs.edit().putBoolean(KEY_USE_REMOTE, true).apply();
    }

    public boolean isRemoteEnabled() {
        return isUseRemoteServer();
    }

    public void setRemoteEnabled(boolean enabled) {
        setUseRemoteServer(enabled);
    }

    public String getUserName() {
        String value = prefs.getString(KEY_USER_NAME, "");
        return "User".equals(value) ? "" : value;
    }

    public void setUserName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            prefs.edit().remove(KEY_USER_NAME).apply();
        } else {
            prefs.edit().putString(KEY_USER_NAME, value).apply();
        }
    }

    public String getAvatarStyle() {
        return prefs.getString(KEY_AVATAR_STYLE, AVATAR_FEMININE);
    }

    public void setAvatarStyle(String avatarStyle) {
        String value = avatarStyle == null ? AVATAR_FEMININE : avatarStyle;
        if (!AVATAR_NEUTRAL.equals(value)
            && !AVATAR_MAID.equals(value)
            && !AVATAR_CUSTOM.equals(value)
            && !AVATAR_CLASSIC.equals(value)) {
            value = AVATAR_FEMININE;
        }
        prefs.edit().putString(KEY_AVATAR_STYLE, value).apply();
    }

    public String getCustomAvatarUri() {
        return prefs.getString(KEY_CUSTOM_AVATAR_URI, "");
    }

    public void setCustomAvatarUri(String uri) {
        SharedPreferences.Editor editor = prefs.edit();
        if (uri == null || uri.trim().isEmpty()) {
            editor.remove(KEY_CUSTOM_AVATAR_URI);
        } else {
            editor.putString(KEY_CUSTOM_AVATAR_URI, uri.trim());
        }
        editor.apply();
    }

    public boolean isProactiveEnabled() {
        return true;
    }

    public void setProactiveEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PROACTIVE, true).apply();
    }

    public int getProactiveDailyMin() {
        return prefs.getInt(KEY_PROACTIVE_DAILY_MIN, DEFAULT_PROACTIVE_DAILY_MIN);
    }

    public int getProactiveIntervalMin() {
        return prefs.getInt(KEY_PROACTIVE_INTERVAL_MIN, DEFAULT_PROACTIVE_INTERVAL_MIN);
    }

    public int getProactiveIntervalMinMinutes() {
        return getProactiveIntervalMin();
    }

    public int getProactiveIntervalMax() {
        return prefs.getInt(KEY_PROACTIVE_INTERVAL_MAX, DEFAULT_PROACTIVE_INTERVAL_MAX);
    }

    public int getProactiveIntervalMaxMinutes() {
        return getProactiveIntervalMax();
    }

    public int getDailyProactiveMinimum() {
        return getProactiveDailyMin();
    }

    public int getTodayProactiveSentCount() {
        return updateAndGetDailyProactiveCount();
    }

    public void recordProactiveSentNow() {
        incrementProactiveCount();
    }

    public boolean isImageGenEnabled() {
        return false;
    }

    public void setImageGenEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_IMAGE_GEN_ENABLED, false).apply();
    }

    public boolean isImageGenerationEnabled() {
        return isImageGenEnabled();
    }

    public void setImageGenerationEnabled(boolean enabled) {
        setImageGenEnabled(enabled);
    }

    /** 이미지 생성 NSFW 허용 여부. 기본값 false. */
    public boolean isImageGenNsfwEnabled() {
        return false;
    }

    public void setImageGenNsfwEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_IMAGE_GEN_NSFW, false).apply();
    }

    public float getTtsSpeechRate() {
        float rate = prefs.getFloat(KEY_TTS_SPEECH_RATE, DEFAULT_TTS_SPEECH_RATE);
        return clampTtsSpeechRate(rate);
    }

    public void setTtsSpeechRate(float rate) {
        prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, clampTtsSpeechRate(rate)).apply();
    }

    public String getTtsVoiceName() {
        return prefs.getString(KEY_TTS_VOICE_NAME, "");
    }

    public void setTtsVoiceName(String voiceName) {
        if (voiceName == null || voiceName.trim().isEmpty()) {
            prefs.edit().remove(KEY_TTS_VOICE_NAME).apply();
            return;
        }
        prefs.edit().putString(KEY_TTS_VOICE_NAME, voiceName.trim()).apply();
    }

    private float clampTtsSpeechRate(float rate) {
        if (rate < 0.6f) return 0.6f;
        if (rate > 1.4f) return 1.4f;
        return rate;
    }

    public String getImageGenProvider() {
        String provider = prefs.getString(KEY_IMAGE_GEN_PROVIDER, IMAGE_GEN_PROVIDER_OPENAI);
        if ("local_on_device".equals(provider)) {
            return IMAGE_GEN_PROVIDER_OPENAI;
        }
        return provider;
    }

    public void setImageGenProvider(String provider) {
        prefs.edit().putString(KEY_IMAGE_GEN_PROVIDER, provider).apply();
    }

    public String getImageGenApiKey() {
        return securePrefs.getString(KEY_IMAGE_GEN_API_KEY, "");
    }

    public void setImageGenApiKey(String apiKey) {
        securePrefs.edit().putString(KEY_IMAGE_GEN_API_KEY, apiKey).apply();
    }

    public String getImageGenModel() {
        return prefs.getString(KEY_IMAGE_GEN_MODEL, "gpt-image-1");
    }

    public void setImageGenModel(String model) {
        prefs.edit().putString(KEY_IMAGE_GEN_MODEL, model).apply();
    }

    public String getImageGenBaseUrl() {
        return prefs.getString(KEY_IMAGE_GEN_BASE_URL, "");
    }

    public void setImageGenBaseUrl(String baseUrl) {
        prefs.edit().putString(KEY_IMAGE_GEN_BASE_URL, baseUrl).apply();
    }

    public void saveImageGenerationConfig(boolean enabled,
                                          String provider,
                                          String apiKey,
                                          String model,
                                          String baseUrl,
                                          boolean nsfwAllowed) {
        setImageGenEnabled(enabled);
        setImageGenProvider(provider);
        setImageGenApiKey(apiKey);
        setImageGenModel(model);
        setImageGenBaseUrl(baseUrl);
        setImageGenNsfwEnabled(nsfwAllowed);
    }

    // --- Proactive Scheduler Helpers ---

    private String getTodayString() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        fmt.setTimeZone(SEOUL_TZ);
        return fmt.format(new Date());
    }

    public int updateAndGetDailyProactiveCount() {
        String today = getTodayString();
        String saved = prefs.getString(KEY_PROACTIVE_SENT_DATE, "");
        if (!today.equals(saved)) {
            prefs.edit()
                    .putString(KEY_PROACTIVE_SENT_DATE, today)
                    .putInt(KEY_PROACTIVE_SENT_COUNT, 0)
                    .apply();
            return 0;
        }
        return prefs.getInt(KEY_PROACTIVE_SENT_COUNT, 0);
    }

    public void incrementProactiveCount() {
        int current = updateAndGetDailyProactiveCount();
        prefs.edit().putInt(KEY_PROACTIVE_SENT_COUNT, current + 1).apply();
    }

    public int getSafetyViolationCount() {
        return prefs.getInt(KEY_SAFETY_VIOLATION_COUNT, 0);
    }

    public long getSafetyLastViolationAt() {
        return prefs.getLong(KEY_SAFETY_LAST_VIOLATION_AT, 0L);
    }

    public long getSafetyRestrictedUntil() {
        return prefs.getLong(KEY_SAFETY_RESTRICTED_UNTIL, 0L);
    }

    public void saveSafetyState(int violationCount, long lastViolationAt, long restrictedUntil) {
        prefs.edit()
                .putInt(KEY_SAFETY_VIOLATION_COUNT, Math.max(0, violationCount))
                .putLong(KEY_SAFETY_LAST_VIOLATION_AT, Math.max(0L, lastViolationAt))
                .putLong(KEY_SAFETY_RESTRICTED_UNTIL, Math.max(0L, restrictedUntil))
                .apply();
    }

    public void setSafetyRestrictedUntil(long restrictedUntil) {
        prefs.edit().putLong(KEY_SAFETY_RESTRICTED_UNTIL, Math.max(0L, restrictedUntil)).apply();
    }

    public void clearSafetyState() {
        prefs.edit()
                .remove(KEY_SAFETY_VIOLATION_COUNT)
                .remove(KEY_SAFETY_LAST_VIOLATION_AT)
                .remove(KEY_SAFETY_RESTRICTED_UNTIL)
                .apply();
    }

    // --- Legal Consents ---

    public int getAcceptedLegalVersion() {
        return prefs.getInt(KEY_LEGAL_ACCEPTED_VERSION, 0);
    }

    public void setAcceptedLegalVersion(int version) {
        prefs.edit().putInt(KEY_LEGAL_ACCEPTED_VERSION, version).apply();
    }

    public boolean isLegalAccepted() {
        return getAcceptedLegalVersion() >= CURRENT_LEGAL_VERSION;
    }

    public void setLegalAccepted(boolean accepted) {
        if (accepted) {
            setAcceptedLegalVersion(CURRENT_LEGAL_VERSION);
        } else {
            setAcceptedLegalVersion(0);
        }
    }

    public void acceptLegal(boolean ageConfirmed, boolean overseasTransfer, boolean voiceTransfer) {
        setAcceptedLegalVersion(CURRENT_LEGAL_VERSION);
        setAgeConfirmed(ageConfirmed);
        setOverseasTransferConsented(overseasTransfer);
        setVoiceTransferConsented(voiceTransfer);
    }

    public void revokeConsent() {
        setAcceptedLegalVersion(0);
        setAgeConfirmed(false);
        setOverseasTransferConsented(false);
        setVoiceTransferConsented(false);
    }

    public void save(String provider, String apiKey, String model, String baseUrl, boolean useRemote, String userName) {
        save(provider, apiKey, model, baseUrl, useRemote, userName, getGoogleSearchApiKey(), getGoogleSearchCx());
    }

    public void save(String provider,
                     String apiKey,
                     String model,
                     String baseUrl,
                     boolean useRemote,
                     String userName,
                     String googleSearchApiKey,
                     String googleSearchCx) {
        setProvider(provider);
        setApiKey(apiKey);
        setModel(model);
        setBaseUrl(baseUrl);
        setUseRemoteServer(useRemote);
        setUserName(userName);
        setGoogleSearchApiKey(googleSearchApiKey);
        setGoogleSearchCx(googleSearchCx);
    }

    public void setProactivePlan(int dailyMin, int intervalMin, int intervalMax) {
        prefs.edit()
                .putInt(KEY_PROACTIVE_DAILY_MIN, dailyMin)
                .putInt(KEY_PROACTIVE_INTERVAL_MIN, intervalMin)
                .putInt(KEY_PROACTIVE_INTERVAL_MAX, intervalMax)
                .apply();
    }

    public boolean isAgeConfirmed() {
        return prefs.getBoolean(KEY_AGE_CONFIRMED, false);
    }

    public void setAgeConfirmed(boolean confirmed) {
        prefs.edit().putBoolean(KEY_AGE_CONFIRMED, confirmed).apply();
    }

    public boolean isOverseasTransferConsented() {
        return prefs.getBoolean(KEY_OVERSEAS_TRANSFER, false);
    }

    public void setOverseasTransferConsented(boolean consented) {
        prefs.edit().putBoolean(KEY_OVERSEAS_TRANSFER, consented).apply();
    }

    public boolean isVoiceTransferConsented() {
        return prefs.getBoolean(KEY_VOICE_TRANSFER, false);
    }

    public void setVoiceTransferConsented(boolean consented) {
        prefs.edit().putBoolean(KEY_VOICE_TRANSFER, consented).apply();
    }

    // --- Static Helpers ---

    public static String defaultBaseUrlFor(String provider) {
        switch (provider) {
            case PROVIDER_OPENAI:
                return "https://api.openai.com/v1";
            case PROVIDER_ANTHROPIC:
                return "https://api.anthropic.com/v1";
            case PROVIDER_GEMINI:
                return "https://generativelanguage.googleapis.com/v1beta/openai/";
            case PROVIDER_OPENAI_COMPATIBLE:
                return "";
            default:
                return "";
        }
    }
}
