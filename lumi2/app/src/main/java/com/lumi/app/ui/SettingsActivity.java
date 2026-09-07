package com.lumi.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.lumi.app.LumiApplication;
import com.lumi.app.R;
import com.lumi.app.data.CharacterRepository;
import com.lumi.app.data.LumiSettings;
import com.lumi.app.engine.LlmClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private final String[] providerKeys = new String[] {
            LumiSettings.PROVIDER_GEMINI
    };

    private LumiSettings settings;
    private CharacterRepository repository;
    private Spinner providerSpinner;
    private Spinner avatarStyleSpinner;
    private Spinner imageProviderSpinner;
    private ImageView avatarPreview;
    private EditText apiKeyField;
    private EditText modelField;
    private EditText baseUrlField;
    private EditText imageApiKeyField;
    private EditText imageModelField;
    private EditText imageBaseUrlField;
    private EditText userNameField;
    private CheckBox useRemoteCheck;
    private CheckBox proactiveCheck;
    private CheckBox imageGenEnableCheck;
    private CheckBox nsfwCheck;
    private Spinner proactiveDailyMinSpinner;
    private Spinner proactiveIntervalMinSpinner;
    private Spinner proactiveIntervalMaxSpinner;
    private Spinner ttsSpeedSpinner;
    private Spinner ttsVoiceSpinner;
    private Button ttsPreviewButton;
    private List<Integer> proactiveDailyOptions;
    private List<Integer> proactiveIntervalOptions;
    private List<Float> ttsSpeedOptions;
    private List<String> ttsVoiceNames;
    private TextToSpeech previewTextToSpeech;
    private boolean previewTtsReady;
    private ActivityResultLauncher<String[]> avatarPickerLauncher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String[] imageProviderKeys = new String[] {
            LumiSettings.IMAGE_GEN_PROVIDER_OPENAI,
            LumiSettings.IMAGE_GEN_PROVIDER_FAL,
            LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE
    };

    private final String[] avatarStyleKeys = new String[] {
            LumiSettings.AVATAR_FEMININE,
            LumiSettings.AVATAR_NEUTRAL,
            LumiSettings.AVATAR_MAID,
            LumiSettings.AVATAR_CUSTOM,
            LumiSettings.AVATAR_CLASSIC
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LumiApplication app = (LumiApplication) getApplication();
        settings = app.getSettings();
        repository = app.getRepository();
        avatarPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::onAvatarPicked);

        providerSpinner = findViewById(R.id.providerSpinner);
        avatarStyleSpinner = findViewById(R.id.avatarStyleSpinner);
        imageProviderSpinner = findViewById(R.id.imageProviderSpinner);
        avatarPreview = findViewById(R.id.avatarPreview);
        apiKeyField = findViewById(R.id.apiKeyField);
        modelField = findViewById(R.id.modelField);
        baseUrlField = findViewById(R.id.baseUrlField);
        imageApiKeyField = findViewById(R.id.imageApiKeyField);
        imageModelField = findViewById(R.id.imageModelField);
        imageBaseUrlField = findViewById(R.id.imageBaseUrlField);
        userNameField = findViewById(R.id.userNameField);
        useRemoteCheck = findViewById(R.id.useRemoteCheck);
        proactiveCheck = findViewById(R.id.proactiveCheck);
        imageGenEnableCheck = findViewById(R.id.imageGenEnableCheck);
        nsfwCheck = findViewById(R.id.nsfwCheck);
        proactiveDailyMinSpinner = findViewById(R.id.proactiveDailyMinSpinner);
        proactiveIntervalMinSpinner = findViewById(R.id.proactiveIntervalMinSpinner);
        proactiveIntervalMaxSpinner = findViewById(R.id.proactiveIntervalMaxSpinner);
        ttsSpeedSpinner = findViewById(R.id.ttsSpeedSpinner);
        ttsVoiceSpinner = findViewById(R.id.ttsVoiceSpinner);
        ttsPreviewButton = findViewById(R.id.ttsPreviewButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button testButton = findViewById(R.id.testButton);
        Button imageTestButton = findViewById(R.id.imageTestButton);
        Button chooseAvatarButton = findViewById(R.id.chooseAvatarButton);
        Button clearAvatarButton = findViewById(R.id.clearAvatarButton);

        List<String> labels = Arrays.asList(
            getString(R.string.settings_provider_gemini));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<>(labels));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);

        List<String> imageProviderLabels = Arrays.asList(
            getString(R.string.settings_image_provider_openai),
            getString(R.string.settings_image_provider_fal),
            getString(R.string.settings_image_provider_compat));
        ArrayAdapter<String> imageProviderAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new ArrayList<>(imageProviderLabels));
        imageProviderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageProviderSpinner.setAdapter(imageProviderAdapter);

        List<String> avatarLabels = Arrays.asList(
            getString(R.string.settings_avatar_feminine),
            getString(R.string.settings_avatar_neutral),
            getString(R.string.settings_avatar_maid),
            getString(R.string.settings_avatar_album),
            getString(R.string.settings_avatar_classic));
        ArrayAdapter<String> avatarAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new ArrayList<>(avatarLabels));
        avatarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        avatarStyleSpinner.setAdapter(avatarAdapter);

        // Pre-fill from settings
        int idx = indexOf(providerKeys, settings.getProvider());
        if (idx >= 0) providerSpinner.setSelection(idx);
        int avatarIdx = indexOf(avatarStyleKeys, settings.getAvatarStyle());
        if (avatarIdx >= 0) avatarStyleSpinner.setSelection(avatarIdx);
        apiKeyField.setText(settings.getApiKey());
        modelField.setText(settings.getModel());
        baseUrlField.setText(settings.getBaseUrl());
        userNameField.setText(settings.getUserName());
        imageGenEnableCheck.setChecked(settings.isImageGenEnabled());
        imageApiKeyField.setText(settings.getImageGenApiKey());
        imageModelField.setText(settings.getImageGenModel());
        imageBaseUrlField.setText(settings.getImageGenBaseUrl());
        useRemoteCheck.setChecked(true);
        proactiveCheck.setChecked(true);
        nsfwCheck.setChecked(settings.isImageGenNsfwEnabled());
        int imageProviderIdx = indexOf(imageProviderKeys, settings.getImageGenProvider());
        if (imageProviderIdx >= 0) imageProviderSpinner.setSelection(imageProviderIdx);
        refreshAvatarPreview();

        initProactivePlanSpinners();
        initTtsSpeedSpinner();
        initTtsVoiceControls();

        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String prov = providerKeys[position];
                applyProviderHints(prov);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        applyProviderHints(providerKeys[providerSpinner.getSelectedItemPosition()]);

        imageProviderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyImageProviderHints(imageProviderKeys[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        applyImageProviderHints(imageProviderKeys[imageProviderSpinner.getSelectedItemPosition()]);

        avatarStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshAvatarPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        saveButton.setOnClickListener(v -> save(true));
        testButton.setOnClickListener(v -> { if (save(false)) runTest(); });
        imageTestButton.setOnClickListener(v -> { if (save(false)) runImageApiTest(); });
        chooseAvatarButton.setOnClickListener(v -> avatarPickerLauncher.launch(new String[] {"image/*"}));
        clearAvatarButton.setOnClickListener(v -> clearCustomAvatar());
        ttsPreviewButton.setOnClickListener(v -> previewSelectedVoice());
        initResetButtons();
    }

    private void onAvatarPicked(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        settings.setCustomAvatarUri(uri.toString());
        settings.setAvatarStyle(LumiSettings.AVATAR_CUSTOM);
        int customIdx = indexOf(avatarStyleKeys, LumiSettings.AVATAR_CUSTOM);
        if (customIdx >= 0) {
            avatarStyleSpinner.setSelection(customIdx);
        }
        refreshAvatarPreview();
        Toast.makeText(this, R.string.settings_avatar_album_selected, Toast.LENGTH_SHORT).show();
    }

    private void clearCustomAvatar() {
        settings.setCustomAvatarUri("");
        settings.setAvatarStyle(LumiSettings.AVATAR_FEMININE);
        int feminineIdx = indexOf(avatarStyleKeys, LumiSettings.AVATAR_FEMININE);
        if (feminineIdx >= 0) {
            avatarStyleSpinner.setSelection(feminineIdx);
        }
        refreshAvatarPreview();
        Toast.makeText(this, R.string.settings_avatar_album_cleared, Toast.LENGTH_SHORT).show();
    }

    private void refreshAvatarPreview() {
        if (avatarPreview == null || avatarStyleSpinner == null) return;
        int position = avatarStyleSpinner.getSelectedItemPosition();
        String avatarStyle = position >= 0 && position < avatarStyleKeys.length
                ? avatarStyleKeys[position]
                : LumiSettings.AVATAR_FEMININE;
        if (LumiSettings.AVATAR_CUSTOM.equals(avatarStyle)) {
            String uriString = settings.getCustomAvatarUri();
            if (uriString != null && !uriString.trim().isEmpty()) {
                try {
                    avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    avatarPreview.setImageURI(Uri.parse(uriString));
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
        avatarPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatarPreview.setImageResource(avatarDrawableFor(avatarStyle));
    }

    private int avatarDrawableFor(String avatarStyle) {
        switch (avatarStyle) {
            case LumiSettings.AVATAR_NEUTRAL:
                return R.drawable.ic_lumi_avatar_neutral;
            case LumiSettings.AVATAR_MAID:
                return R.drawable.ic_lumi_avatar_maid;
            case LumiSettings.AVATAR_CLASSIC:
                return R.drawable.ic_lumi_emblem;
            case LumiSettings.AVATAR_CUSTOM:
            case LumiSettings.AVATAR_FEMININE:
            default:
                return R.drawable.ic_lumi_avatar_feminine;
        }
    }

    // 이미지 API 연결 테스트
    private void runImageApiTest() {
        Toast.makeText(this, getString(R.string.settings_image_test) + " 중…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                // 이미지 API 설정값으로 테스트
                String provider = imageProviderKeys[imageProviderSpinner.getSelectedItemPosition()];
                String apiKey = imageApiKeyField.getText().toString().trim();
                String model = imageModelField.getText().toString().trim();
                String baseUrl = imageBaseUrlField.getText().toString().trim();
                boolean compatibleProvider = LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE.equals(provider);
                if (apiKey.isEmpty() || (compatibleProvider && baseUrl.isEmpty())) {
                    handler.post(() -> Toast.makeText(this, "필수 입력값이 비어 있습니다.", Toast.LENGTH_SHORT).show());
                    return;
                }
                // 실제 API 호출: ImageGenClient의 testConnection 활용
                com.lumi.app.image.ImageGenClient client = new com.lumi.app.image.ImageGenClient(
                    getApplicationContext(),
                    settings
                );
                String resp = client.testConnection(
                    provider,
                    apiKey,
                    model,
                    baseUrl
                );
                sb.append(resp);
                handler.post(() -> Toast.makeText(this,
                        getString(R.string.settings_image_test_ok, resp), Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                sb.append(t.getMessage());
                handler.post(() -> Toast.makeText(this,
                        getString(R.string.settings_image_test_fail, sb.toString()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void initResetButtons() {

        Button resetChatButton = findViewById(R.id.resetChatButton);
        Button resetMemoryButton = findViewById(R.id.resetMemoryButton);
        Button resetAllButton = findViewById(R.id.resetAllButton);
        resetChatButton.setOnClickListener(v -> confirmReset(
            R.string.settings_reset_chat,
            R.string.settings_reset_chat_confirm,
            () -> { repository.resetConversation(); return R.string.settings_reset_done_chat; }));
        resetMemoryButton.setOnClickListener(v -> confirmReset(
            R.string.settings_reset_memory,
            R.string.settings_reset_memory_confirm,
            () -> { repository.resetMemories(); return R.string.settings_reset_done_memory; }));
        resetAllButton.setOnClickListener(v -> confirmReset(
            R.string.settings_reset_all,
            R.string.settings_reset_all_confirm,
            () -> { repository.resetAll(); return R.string.settings_reset_done_all; }));
    }

    private void initProactivePlanSpinners() {
        proactiveDailyOptions = Arrays.asList(1, 2, 3, 4, 5, 6, 8, 10, 12);
        proactiveIntervalOptions = Arrays.asList(30, 45, 60, 75, 90, 105, 120, 150, 180, 210, 240, 300);

        List<String> dailyLabels = new ArrayList<>();
        for (int v : proactiveDailyOptions) {
            dailyLabels.add(v + "건 이상");
        }
        List<String> intervalLabels = new ArrayList<>();
        for (int v : proactiveIntervalOptions) {
            intervalLabels.add(formatMinuteLabel(v));
        }

        ArrayAdapter<String> dailyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dailyLabels);
        dailyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        proactiveDailyMinSpinner.setAdapter(dailyAdapter);

        ArrayAdapter<String> minAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, intervalLabels);
        minAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        proactiveIntervalMinSpinner.setAdapter(minAdapter);

        ArrayAdapter<String> maxAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, intervalLabels);
        maxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        proactiveIntervalMaxSpinner.setAdapter(maxAdapter);

        proactiveDailyMinSpinner.setSelection(indexOfClosest(
                proactiveDailyOptions, settings.getDailyProactiveMinimum()));
        proactiveIntervalMinSpinner.setSelection(indexOfClosest(
                proactiveIntervalOptions, settings.getProactiveIntervalMinMinutes()));
        proactiveIntervalMaxSpinner.setSelection(indexOfClosest(
                proactiveIntervalOptions, settings.getProactiveIntervalMaxMinutes()));

        proactiveIntervalMinSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int min = proactiveIntervalOptions.get(position);
                int max = proactiveIntervalOptions.get(proactiveIntervalMaxSpinner.getSelectedItemPosition());
                if (min > max) {
                    proactiveIntervalMaxSpinner.setSelection(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        proactiveIntervalMaxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int max = proactiveIntervalOptions.get(position);
                int min = proactiveIntervalOptions.get(proactiveIntervalMinSpinner.getSelectedItemPosition());
                if (max < min) {
                    proactiveIntervalMinSpinner.setSelection(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private String formatMinuteLabel(int minute) {
        int h = minute / 60;
        int m = minute % 60;
        if (h == 0) return minute + "분";
        if (m == 0) return String.format(Locale.KOREA, "%d시간", h);
        return String.format(Locale.KOREA, "%d시간 %d분", h, m);
    }

    private void initTtsSpeedSpinner() {
        ttsSpeedOptions = Arrays.asList(0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f);
        List<String> labels = new ArrayList<>();
        for (float value : ttsSpeedOptions) {
            if (Math.abs(value - 1.0f) < 0.001f) {
                labels.add(String.format(Locale.KOREA, "%.1fx (기본)", value));
            } else if (value < 1.0f) {
                labels.add(String.format(Locale.KOREA, "%.1fx (느리게)", value));
            } else {
                labels.add(String.format(Locale.KOREA, "%.1fx (빠르게)", value));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ttsSpeedSpinner.setAdapter(adapter);
        ttsSpeedSpinner.setSelection(indexOfClosestFloat(ttsSpeedOptions, settings.getTtsSpeechRate()));
    }

    private void initTtsVoiceControls() {
        ttsVoiceNames = new ArrayList<>();
        ttsVoiceNames.add("");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>(Collections.singletonList(getString(R.string.settings_tts_voice_system_default))));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ttsVoiceSpinner.setAdapter(adapter);
        ttsPreviewButton.setEnabled(false);
        previewTextToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || previewTextToSpeech == null) {
                previewTtsReady = false;
                return;
            }
            int languageResult = previewTextToSpeech.setLanguage(Locale.KOREAN);
            previewTtsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
            populateTtsVoiceSpinner();
            ttsPreviewButton.setEnabled(previewTtsReady);
        });
    }

    private void populateTtsVoiceSpinner() {
        if (ttsVoiceSpinner == null) return;
        List<String> labels = new ArrayList<>();
        ttsVoiceNames.clear();
        ttsVoiceNames.add("");
        labels.add(getString(R.string.settings_tts_voice_system_default));
        if (previewTextToSpeech != null) {
            Set<Voice> voices = previewTextToSpeech.getVoices();
            if (voices != null) {
                List<Voice> candidates = new ArrayList<>();
                for (Voice voice : voices) {
                    if (voice == null || voice.getLocale() == null) continue;
                    if (!"ko".equalsIgnoreCase(voice.getLocale().getLanguage())) continue;
                    candidates.add(voice);
                }
                candidates.sort(Comparator.comparing(v -> v.getLocale().getDisplayName(Locale.KOREAN) + " " + v.getName()));
                for (Voice voice : candidates) {
                    ttsVoiceNames.add(voice.getName());
                    labels.add(voice.getLocale().getDisplayName(Locale.KOREAN) + " · " + voice.getName());
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ttsVoiceSpinner.setAdapter(adapter);
        String currentVoiceName = settings.getTtsVoiceName();
        int selected = 0;
        for (int i = 0; i < ttsVoiceNames.size(); i++) {
            if (ttsVoiceNames.get(i).equals(currentVoiceName)) {
                selected = i;
                break;
            }
        }
        ttsVoiceSpinner.setSelection(selected);
    }

    private void previewSelectedVoice() {
        if (!previewTtsReady || previewTextToSpeech == null) {
            Toast.makeText(this, R.string.settings_tts_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        previewTextToSpeech.stop();
        previewTextToSpeech.setSpeechRate(ttsSpeedOptions.get(ttsSpeedSpinner.getSelectedItemPosition()));
        applySelectedVoice(previewTextToSpeech);
        previewTextToSpeech.speak(getString(R.string.settings_tts_preview_text),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "settings_tts_preview");
    }

    private void applySelectedVoice(TextToSpeech tts) {
        if (tts == null || ttsVoiceSpinner == null || ttsVoiceNames == null || ttsVoiceNames.isEmpty()) {
            return;
        }
        int pos = ttsVoiceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= ttsVoiceNames.size()) return;
        String selectedVoiceName = ttsVoiceNames.get(pos);
        if (selectedVoiceName.isEmpty()) return;
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return;
        for (Voice voice : voices) {
            if (voice != null && selectedVoiceName.equals(voice.getName())) {
                tts.setVoice(voice);
                return;
            }
        }
    }

    private interface ResetAction {
        int run();
    }

    private void confirmReset(int titleRes, int messageRes, ResetAction action) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_confirm, (d, w) -> executor.execute(() -> {
                    int doneRes;
                    try {
                        doneRes = action.run();
                    } catch (Throwable t) {
                        final String err = t.getMessage();
                        handler.post(() -> Toast.makeText(this,
                                "초기화 실패: " + err, Toast.LENGTH_LONG).show());
                        return;
                    }
                    final int res = doneRes;
                    handler.post(() -> Toast.makeText(this, res, Toast.LENGTH_SHORT).show());
                }))
                .show();
    }

    private void applyProviderHints(String provider) {
        switch (provider) {
            case LumiSettings.PROVIDER_ANTHROPIC:
                modelField.setHint(getString(R.string.settings_hint_anthropic));
                baseUrlField.setEnabled(false);
                break;
            case LumiSettings.PROVIDER_GEMINI:
                modelField.setHint(getString(R.string.settings_hint_gemini));
                baseUrlField.setEnabled(false);
                break;
            case LumiSettings.PROVIDER_OPENAI_COMPATIBLE:
                modelField.setHint(getString(R.string.settings_hint_compat));
                baseUrlField.setEnabled(true);
                if (baseUrlField.getText().toString().trim().isEmpty()) {
                    baseUrlField.setText(LumiSettings.defaultBaseUrlFor(provider));
                }
                break;
            case LumiSettings.PROVIDER_OPENAI:
            default:
                modelField.setHint(getString(R.string.settings_hint_openai));
                baseUrlField.setEnabled(false);
                break;
        }
    }

    private boolean save(boolean toast) {
        String prov = LumiSettings.PROVIDER_GEMINI;
        String imageProv = imageProviderKeys[imageProviderSpinner.getSelectedItemPosition()];
        int dailyMin = proactiveDailyOptions.get(proactiveDailyMinSpinner.getSelectedItemPosition());
        int intervalMin = proactiveIntervalOptions.get(proactiveIntervalMinSpinner.getSelectedItemPosition());
        int intervalMax = proactiveIntervalOptions.get(proactiveIntervalMaxSpinner.getSelectedItemPosition());
        String avatarStyle = avatarStyleKeys[avatarStyleSpinner.getSelectedItemPosition()];
        if (LumiSettings.AVATAR_CUSTOM.equals(avatarStyle)
            && settings.getCustomAvatarUri().trim().isEmpty()) {
            Toast.makeText(this, R.string.settings_avatar_album_needed, Toast.LENGTH_LONG).show();
            return false;
        }
        boolean llmRemoteRequested = !settings.getApiKey().isEmpty();
        boolean imageRemoteRequested = false;
        if ((llmRemoteRequested || imageRemoteRequested)
                && !settings.isOverseasTransferConsented()) {
            Toast.makeText(this, R.string.settings_external_consent_required, Toast.LENGTH_LONG).show();
            return false;
        }
        settings.save(
                prov,
                settings.getApiKey(),
                settings.getModel(),
                settings.getBaseUrl(),
                true,
                userNameField.getText().toString());
        settings.setAvatarStyle(avatarStyle);
        boolean proactive = true;
        settings.setProactivePlan(dailyMin, intervalMin, intervalMax);
        settings.setProactiveEnabled(proactive);
        settings.saveImageGenerationConfig(
                false,
                imageProv,
                "",
                imageModelField.getText().toString(),
                imageBaseUrlField.getText().toString(),
                false);
        settings.setTtsSpeechRate(ttsSpeedOptions.get(ttsSpeedSpinner.getSelectedItemPosition()));
        if (ttsVoiceNames != null && !ttsVoiceNames.isEmpty() && ttsVoiceSpinner != null) {
            int selectedIndex = ttsVoiceSpinner.getSelectedItemPosition();
            if (selectedIndex >= 0 && selectedIndex < ttsVoiceNames.size()) {
                settings.setTtsVoiceName(ttsVoiceNames.get(selectedIndex));
            }
        }
        if (proactive) {
            com.lumi.app.notify.ProactiveScheduler.scheduleNext(getApplicationContext(), settings);
        } else {
            com.lumi.app.notify.ProactiveScheduler.cancel(getApplicationContext());
        }
        if (toast) {
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void applyImageProviderHints(String provider) {
        String currentBaseUrl = imageBaseUrlField.getText().toString().trim();
        String currentModel = imageModelField.getText().toString().trim();
        String compatDefaultUrl = getString(R.string.settings_image_compat_default_url);
        String falDefaultUrl = getString(R.string.settings_image_fal_default_url);
        String falDefaultModel = getString(R.string.settings_image_fal_default_model);
        if (LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            imageApiKeyField.setEnabled(true);
            imageApiKeyField.setHint(getString(R.string.settings_image_api_key_hint_openai));
            imageModelField.setHint(getString(R.string.settings_image_hint_compat));
            imageBaseUrlField.setEnabled(true);
            if (currentBaseUrl.isEmpty() || falDefaultUrl.equals(currentBaseUrl)) {
                imageBaseUrlField.setText(compatDefaultUrl);
            }
        } else if (LumiSettings.IMAGE_GEN_PROVIDER_FAL.equals(provider)) {
            imageApiKeyField.setEnabled(true);
            imageApiKeyField.setHint(getString(R.string.settings_image_api_key_hint_fal));
            imageModelField.setHint(getString(R.string.settings_image_hint_fal));
            imageBaseUrlField.setEnabled(true);
            if (currentModel.isEmpty()
                    || "gpt-image-1".equals(currentModel)
                    || "dall-e-3".equals(currentModel)
                    || "local-demo".equals(currentModel)) {
                imageModelField.setText(falDefaultModel);
            }
            if (currentBaseUrl.isEmpty() || compatDefaultUrl.equals(currentBaseUrl)) {
                imageBaseUrlField.setText(falDefaultUrl);
            }
        } else {
            imageApiKeyField.setEnabled(true);
            imageApiKeyField.setHint(getString(R.string.settings_image_api_key_hint_openai));
            imageModelField.setHint(getString(R.string.settings_image_hint_openai));
            imageBaseUrlField.setEnabled(false);
            if (falDefaultModel.equals(currentModel) || "local-demo".equals(currentModel)) {
                imageModelField.setText("gpt-image-1");
            }
            if (compatDefaultUrl.equals(currentBaseUrl) || falDefaultUrl.equals(currentBaseUrl)) {
                imageBaseUrlField.setText("");
            }
        }
    }

    private void runTest() {
        Toast.makeText(this, "테스트 중…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                if (settings.getApiKey().isEmpty()) {
                    handler.post(() -> Toast.makeText(this, "API 키가 소스에 설정되지 않았습니다.", Toast.LENGTH_SHORT).show());
                    return;
                }
                String resp = new LlmClient(settings, repository).testConnection();
                sb.append(resp);
            } catch (Throwable t) {
                sb.append(t.getMessage());
            }
            final String result = sb.toString();
            handler.post(() -> Toast.makeText(this,
                    "결과: " + result, Toast.LENGTH_LONG).show());
        });
    }

    private int indexOf(Object[] arr, Object val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(val)) return i;
        }
        return -1;
    }

    private int indexOfClosest(List<Integer> list, int val) {
        int best = 0;
        for (int i = 0; i < list.size(); i++) {
            if (Math.abs(list.get(i) - val) < Math.abs(list.get(best) - val)) best = i;
        }
        return best;
    }

    private int indexOfClosestFloat(List<Float> list, float val) {
        int best = 0;
        for (int i = 0; i < list.size(); i++) {
            if (Math.abs(list.get(i) - val) < Math.abs(list.get(best) - val)) best = i;
        }
        return best;
    }

    @Override
    protected void onDestroy() {
        if (previewTextToSpeech != null) {
            try { previewTextToSpeech.stop(); } catch (Exception ignored) {}
            try { previewTextToSpeech.shutdown(); } catch (Exception ignored) {}
            previewTextToSpeech = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
