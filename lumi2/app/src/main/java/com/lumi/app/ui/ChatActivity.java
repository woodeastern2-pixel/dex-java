package com.lumi.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.app.LumiApplication;
import com.lumi.app.R;
import com.lumi.app.data.CharacterRepository;
import com.lumi.app.data.LumiSettings;
import com.lumi.app.image.ImageGenerationResult;
import com.lumi.app.image.ImageGenerationService;
import com.lumi.app.image.ImageRequestDetector;
import com.lumi.app.image.ImageSaveManager;
import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.ConversationMessage;
import com.lumi.app.model.InteractionResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final int REQ_AUDIO = 4711;

    private CharacterRepository repository;
    private LumiSettings settings;
    private ChatAdapter adapter;
    private RecyclerView chatList;
    private EditText inputField;
    private ImageButton sendButton;
    private ImageButton attachImageButton;
    private ImageButton voiceButton;
    private LinearLayout chatRoot;
    private View chatMoodAura;
    private ObjectAnimator auraPulse;
    private TextView typingIndicator;
    private TextView chatMoodLine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ImageGenerationService imageGenerationService;
    private ImageSaveManager imageSaveManager;
    private String lastGeneratedImageUri;
    private String lastImagePrompt;
    private boolean sending = false;

    private SpeechRecognizer recognizer;
    private boolean listening = false;
    private long listenStartedAt;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        LumiApplication app = (LumiApplication) getApplication();
        repository = app.getRepository();
        settings = app.getSettings();
        imageGenerationService = new ImageGenerationService(this, settings);
        imageSaveManager = new ImageSaveManager(this);

        chatList = findViewById(R.id.chatList);
        inputField = findViewById(R.id.inputField);
        typingIndicator = findViewById(R.id.typingIndicator);
        chatMoodLine = findViewById(R.id.chatMoodLine);
        chatRoot = findViewById(R.id.chatRoot);
        chatMoodAura = findViewById(R.id.chatMoodAura);
        sendButton = findViewById(R.id.sendButton);
        attachImageButton = findViewById(R.id.attachImageButton);
        voiceButton = findViewById(R.id.voiceButton);
        adapter = new ChatAdapter(new ArrayList<>(), new ChatAdapter.ImageActionListener() {
            @Override
            public void onSaveImageRequested(String uriString) {
                saveImageFromUri(uriString);
            }

            @Override
            public void onShareImageRequested(String uriString) {
                shareImage(uriString);
            }

            @Override
            public void onRegenerateImageRequested(String prompt) {
                if (prompt == null || prompt.trim().isEmpty()) {
                    Toast.makeText(ChatActivity.this, "다시 생성할 프롬프트가 없어요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                handleImageRequest(prompt);
            }

            @Override
            public void onSpeakTextRequested(String text) {
                speakLumiText(text);
            }

            @Override
            public void onStopSpeakRequested() {
                stopLumiSpeech();
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(adapter);
        chatList.setOnClickListener(v -> collapseExpandedTextActions());
        chatRoot.setOnClickListener(v -> collapseExpandedTextActions());

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    sendAttachment(ConversationMessage.ATTACH_IMAGE, uri.toString(), null,
                            getString(R.string.chat_image_caption));
                });

        executor.execute(() -> {
            CharacterStateEntity initial = repository.loadState();
            java.util.List<ConversationMessage> recent = repository.loadRecentMessages(60);
            handler.post(() -> {
                chatMoodLine.setText(buildMoodLine(initial.mood));
                applyMoodVisual(initial.mood);
                adapter.setMessages(recent);
                scrollToBottom();
            });
        });

        sendButton.setOnClickListener(v -> {
            collapseExpandedTextActions();
            trySend();
        });
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                collapseExpandedTextActions();
                trySend();
                return true;
            }
            return false;
        });
        inputField.setOnClickListener(v -> collapseExpandedTextActions());
        attachImageButton.setOnClickListener(v -> {
            collapseExpandedTextActions();
            pickImageLauncher.launch("image/*");
        });
        voiceButton.setOnClickListener(v -> {
            collapseExpandedTextActions();
            toggleVoice();
        });
        initTextToSpeech();
    }

    private void collapseExpandedTextActions() {
        if (adapter != null) {
            adapter.collapseTextActionPanels();
        }
    }

    private String buildMoodLine(String mood) {
        String prefix = settings != null && settings.isRemoteEnabled() ? "온라인 모드 · " : "오프라인 모드 · ";
        return prefix + MoodPalette.moodLine(mood);
    }

    private void trySend() {
        if (sending) return;
        String text = inputField.getText().toString().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        if (ImageRequestDetector.isSaveLatestImageRequest(text) && lastGeneratedImageUri != null) {
            long now = System.currentTimeMillis();
            adapter.addMessage(new ConversationMessage(
                    "user", text, "user", now,
                    ConversationMessage.ATTACH_NONE, null, null));
            scrollToBottom();
            saveImageFromUri(lastGeneratedImageUri);
            return;
        }
        if (ImageRequestDetector.isImageGenerationRequest(text)) {
            handleImageRequest(text);
            return;
        }
        sendAttachment(ConversationMessage.ATTACH_NONE, null, null, text);
    }

    private void handleImageRequest(String userText) {
        long now = System.currentTimeMillis();
        ConversationMessage userPreview = new ConversationMessage(
                "user", userText, "user", now,
                ConversationMessage.ATTACH_NONE, null, null);
        adapter.addMessage(userPreview);
        String initialProgressText = getString(R.string.chat_drawing);
        adapter.addMessage(new ConversationMessage(
                "lumi",
                initialProgressText,
                "curious",
                now + 1,
                ConversationMessage.ATTACH_NONE,
                null,
                null));
        scrollToBottom();
        typingIndicator.setVisibility(View.VISIBLE);
        typingIndicator.setText(initialProgressText);
        sending = true;
        sendButton.setEnabled(false);
        attachImageButton.setEnabled(false);
        voiceButton.setEnabled(false);

        final String userRequest = userText;
        executor.execute(() -> {
            ImageGenerationResult generation = repository.shouldSkipImageGenerationForSafety(userRequest)
                    ? null
                    : imageGenerationService.generate(userRequest);
            InteractionResult result;
            String saveErr = null;
            try {
                result = repository.generateImageReply(
                        userRequest,
                        generation == null ? null : generation.imagePath,
                        generation == null ? null : generation.userFacingMessage,
                        generation == null ? null : generation.prompt);
            } catch (Throwable t) {
                result = null;
                saveErr = t.getMessage();
            }
            final ImageGenerationResult fGeneration = generation;
            final InteractionResult fr = result;
            final String fSaveErr = saveErr;
            handler.post(() -> {
                typingIndicator.setVisibility(View.GONE);
                typingIndicator.setText(R.string.chat_typing);
                sending = false;
                sendButton.setEnabled(true);
                attachImageButton.setEnabled(true);
                voiceButton.setEnabled(true);
                if (fr == null) {
                    adapter.updateLastLumiPreview("그림 응답 저장 실패: " + fSaveErr);
                    Toast.makeText(this, "그림 응답 저장 실패: " + fSaveErr, Toast.LENGTH_LONG).show();
                    return;
                }
                adapter.replaceLastUserPreview(fr.userMessage);
                if (!adapter.replaceLastLumiPreview(fr.lumiMessage)) {
                    adapter.addMessage(fr.lumiMessage);
                }
                if (fGeneration != null && fGeneration.success && fGeneration.imagePath != null) {
                    lastGeneratedImageUri = "file://" + fGeneration.imagePath;
                    lastImagePrompt = fGeneration.prompt;
                }
                chatMoodLine.setText(buildMoodLine(fr.state.mood));
                applyMoodVisual(fr.state.mood);
                scrollToBottom();
            });
        });
    }

    private void sendAttachment(String type, String uri, String meta, String text) {
        if (sending) return;
        long now = System.currentTimeMillis();
        ConversationMessage userPreview = new ConversationMessage(
                "user", text == null ? "" : text, "user", now, type, uri, meta);
        adapter.addMessage(userPreview);
        scrollToBottom();
        typingIndicator.setVisibility(View.VISIBLE);
        typingIndicator.setText(R.string.chat_typing);
        sending = true;
        sendButton.setEnabled(false);
        attachImageButton.setEnabled(false);
        voiceButton.setEnabled(false);

        final String fText = text;
        final String fType = type;
        final String fUri = uri;
        final String fMeta = meta;
        executor.execute(() -> {
            InteractionResult result;
            String error = null;
            try {
                result = repository.sendMessage(fText, fType, fUri, fMeta);
            } catch (Throwable t) {
                result = null;
                error = t.getMessage();
            }
            final InteractionResult fr = result;
            final String fe = error;
            handler.post(() -> {
                typingIndicator.setVisibility(View.GONE);
                sending = false;
                sendButton.setEnabled(true);
                attachImageButton.setEnabled(true);
                voiceButton.setEnabled(true);
                if (fr == null) {
                    Toast.makeText(this, "응답 중 오류: " + fe, Toast.LENGTH_LONG).show();
                    return;
                }
                adapter.replaceLastUserPreview(fr.userMessage);
                adapter.addMessage(fr.lumiMessage);
                chatMoodLine.setText(buildMoodLine(fr.state.mood));
                applyMoodVisual(fr.state.mood);
                if ("local".equals(repository.getLastReplySource())
                        && settings != null && settings.isRemoteEnabled()
                        && repository.getLastReplyError() != null) {
                    Toast.makeText(this,
                            "온라인 응답 실패, 오프라인 모드로 답했어요: " + repository.getLastReplyError(),
                            Toast.LENGTH_LONG).show();
                }
                scrollToBottom();
            });
        });
    }

    // ---------- 음성: SpeechRecognizer 로 한국어 STT ----------

    private void toggleVoice() {
        if (listening) {
            stopListening();
            return;
        }
        if (settings != null && !settings.isVoiceTransferConsented()) {
            Toast.makeText(this, R.string.legal_voice_consent_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.chat_voice_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        releaseRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                listenStartedAt = System.currentTimeMillis();
                typingIndicator.setText(R.string.chat_voice_listening);
                typingIndicator.setVisibility(View.VISIBLE);
                voiceButton.setBackgroundResource(R.drawable.bg_lumi_button_primary);
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                cleanupListeningUi();
                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(ChatActivity.this, R.string.chat_voice_no_speech, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this,
                            getString(R.string.chat_voice_error, errorName(error)),
                            Toast.LENGTH_SHORT).show();
                }
                releaseRecognizer();
            }
            @Override public void onResults(Bundle results) {
                java.util.ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                String best = (matches != null && !matches.isEmpty()) ? matches.get(0).trim() : "";
                int seconds = Math.max(1,
                        (int) Math.round((System.currentTimeMillis() - listenStartedAt) / 1000.0));
                cleanupListeningUi();
                releaseRecognizer();
                if (best.isEmpty()) {
                    Toast.makeText(ChatActivity.this, R.string.chat_voice_no_speech, Toast.LENGTH_SHORT).show();
                    return;
                }
                // STT 결과를 본문 텍스트로 보내고, 음성 첨부로도 표시 → Lumi 가 실제 발화 내용을 "듣게" 됨
                sendAttachment(ConversationMessage.ATTACH_VOICE, null,
                        String.valueOf(seconds), best);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                java.util.ArrayList<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    typingIndicator.setText("🎙 " + matches.get(0));
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String lang = Locale.KOREA.toLanguageTag();
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            recognizer.startListening(intent);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.chat_voice_permission_denied, Toast.LENGTH_SHORT).show();
            releaseRecognizer();
        }
    }

    private void stopListening() {
        if (recognizer != null && listening) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
    }

    private void cleanupListeningUi() {
        listening = false;
        typingIndicator.setVisibility(View.GONE);
        typingIndicator.setText(R.string.chat_typing);
        voiceButton.setBackgroundResource(R.drawable.bg_lumi_button_ghost);
    }

    private void releaseRecognizer() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private static String errorName(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "오디오";
            case SpeechRecognizer.ERROR_CLIENT: return "클라이언트";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "권한 부족";
            case SpeechRecognizer.ERROR_NETWORK: return "네트워크";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "네트워크 타임아웃";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "인식기 사용 중";
            case SpeechRecognizer.ERROR_SERVER: return "서버";
            case SpeechRecognizer.ERROR_NO_MATCH: return "결과 없음";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "발화 타임아웃";
            default: return "알 수 없음(" + code + ")";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                Toast.makeText(this, R.string.chat_voice_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() == 0) return;
        chatList.post(() -> chatList.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    private void saveImageFromUri(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            Toast.makeText(this, "저장할 이미지가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse(uriString);
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "이미지를 저장할 수 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            try {
                imageSaveManager.saveToGallery(path);
                handler.post(() -> {
                    Toast.makeText(this, "이미지를 저장했어요.", Toast.LENGTH_SHORT).show();
                    adapter.addMessage(new ConversationMessage(
                            "lumi",
                            "이미지를 저장했어요.",
                            "calm",
                            System.currentTimeMillis(),
                            ConversationMessage.ATTACH_NONE,
                            null,
                            null));
                    scrollToBottom();
                });
            } catch (Throwable t) {
                handler.post(() -> Toast.makeText(
                        this,
                        "이미지 저장 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareImage(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            Toast.makeText(this, "공유할 이미지가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri source = Uri.parse(uriString);
            Uri shareUri = source;
            if ("file".equalsIgnoreCase(source.getScheme()) && source.getPath() != null) {
                File f = new File(source.getPath());
                shareUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        f);
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, shareUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(getContentResolver(), "lumi_image", shareUri));
            startActivity(Intent.createChooser(intent, "이미지 공유"));
        } catch (Throwable t) {
            Toast.makeText(this, "이미지 공유에 실패했어요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyMoodVisual(String mood) {
        if (chatRoot != null) {
            chatRoot.setBackgroundResource(MoodPalette.backgroundRes(mood));
        }
        if (chatMoodAura == null) return;

        int color = MoodPalette.color(this, mood);
        chatMoodAura.setBackgroundColor(color);

        if (auraPulse != null) auraPulse.cancel();
        long duration = MoodPalette.moodPulseDuration(mood);
        auraPulse = ObjectAnimator.ofFloat(chatMoodAura, "alpha", 0.16f, 0.42f, 0.16f);
        auraPulse.setDuration(duration);
        auraPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        auraPulse.setRepeatCount(ValueAnimator.INFINITE);
        auraPulse.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
        CharacterStateEntity s = repository.loadState();
        chatMoodLine.setText(buildMoodLine(s.mood));
        applyMoodVisual(s.mood);
        executor.execute(() -> {
            java.util.List<ConversationMessage> recent = repository.loadRecentMessages(60);
            handler.post(() -> {
                adapter.setMessages(recent);
                scrollToBottom();
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        adapter.releasePlayer();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        if (listening) stopListening();
    }

    @Override
    protected void onDestroy() {
        if (auraPulse != null) auraPulse.cancel();
        releaseRecognizer();
        adapter.releasePlayer();
        if (textToSpeech != null) {
            try { textToSpeech.stop(); } catch (Exception ignored) {}
            try { textToSpeech.shutdown(); } catch (Exception ignored) {}
            textToSpeech = null;
        }
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                ttsReady = false;
                return;
            }
            int result = textToSpeech.setLanguage(Locale.KOREAN);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady && settings != null) {
                textToSpeech.setSpeechRate(settings.getTtsSpeechRate());
                applyConfiguredTtsVoice(textToSpeech);
            }
        });
    }

    private void speakLumiText(String text) {
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "들을 텍스트가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(this, "음성 읽기를 준비 중이에요. 잠시 후 다시 눌러주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (settings != null) {
            textToSpeech.setSpeechRate(settings.getTtsSpeechRate());
            applyConfiguredTtsVoice(textToSpeech);
        }
        textToSpeech.stop();
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "lumi_chat_tts");
    }

    private void applyConfiguredTtsVoice(TextToSpeech tts) {
        if (settings == null || tts == null) return;
        String voiceName = settings.getTtsVoiceName();
        if (voiceName == null || voiceName.trim().isEmpty()) return;
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return;
        for (Voice voice : voices) {
            if (voice != null && voiceName.equals(voice.getName())) {
                tts.setVoice(voice);
                return;
            }
        }
    }

    private void stopLumiSpeech() {
        if (textToSpeech == null || !ttsReady) {
            return;
        }
        textToSpeech.stop();
        Toast.makeText(this, R.string.chat_listen_stopped, Toast.LENGTH_SHORT).show();
    }

    public java.util.List<ConversationMessage> currentMessages() {
        return adapter.getItems();
    }

    public static Intent intent(Context c) {
        return new Intent(c, ChatActivity.class);
    }
}
