package com.lumi.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.app.LumiApplication;
import com.lumi.app.R;
import com.lumi.app.data.CharacterRepository;
import com.lumi.app.engine.AffinityManager;
import com.lumi.app.engine.PersonalityEvolutionEngine;
import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.ConversationMessage;
import com.lumi.app.model.MemoryEntry;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusActivity extends AppCompatActivity {

    private CharacterRepository repository;
    private final AffinityManager affinityManager = new AffinityManager();
    private final PersonalityEvolutionEngine personality = new PersonalityEvolutionEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> exportTextLauncher;
    private String pendingExportContent;
    private ScrollView statusRoot;
    private TextView moodLine;
    private ObjectAnimator moodLinePulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        repository = ((LumiApplication) getApplication()).getRepository();

        exportTextLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/plain"),
                this::writeExportToUri);

        statusRoot = findViewById(R.id.statusRoot);
        moodLine = findViewById(R.id.statusMoodLine);

        Button exportChatButton = findViewById(R.id.exportChatButton);
        exportChatButton.setOnClickListener(v -> exportConversationAsText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    private void bind() {
        CharacterStateEntity state = repository.loadState();

        TextView relationship = findViewById(R.id.statusRelationship);
        TextView growth = findViewById(R.id.statusGrowth);
        TextView totalCount = findViewById(R.id.statusTotalCount);
        TextView todayCount = findViewById(R.id.statusTodayCount);
        ProgressBar affinityBar = findViewById(R.id.statusAffinityBar);

        moodLine.setText(MoodPalette.moodLine(state.mood, state.dailyInteractions, state.affinity));
        moodLine.setTextColor(MoodPalette.color(this, state.mood));
        applyMoodVisual(state.mood);
        relationship.setText(affinityManager.getRelationshipLabel(state.affinity)
                + " · 친밀도 " + state.affinity);
        growth.setText("성장 단계 " + state.growthStage + " · 가장 짙어진 성격: " + personality.dominantTrait(state));
        affinityBar.setProgress(state.affinity);
        totalCount.setText(String.valueOf(state.totalInteractions));
        todayCount.setText(String.valueOf(state.dailyInteractions));

        bindTraits(state);
        bindMemories(repository.loadTopMemories(20));
    }

    private void applyMoodVisual(String mood) {
        if (statusRoot != null) {
            statusRoot.setBackgroundResource(MoodPalette.backgroundRes(mood));
        }
        if (moodLine == null) return;

        if (moodLinePulse != null) moodLinePulse.cancel();
        moodLinePulse = ObjectAnimator.ofFloat(moodLine, "alpha", 0.72f, 1f, 0.72f);
        moodLinePulse.setDuration(MoodPalette.moodPulseDuration(mood));
        moodLinePulse.setInterpolator(new AccelerateDecelerateInterpolator());
        moodLinePulse.setRepeatCount(ValueAnimator.INFINITE);
        moodLinePulse.start();
    }

    private void bindTraits(CharacterStateEntity state) {
        LinearLayout container = findViewById(R.id.traitsContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        addTrait(container, inflater, getString(R.string.trait_shy), state.shy);
        addTrait(container, inflater, getString(R.string.trait_cheerful), state.cheerful);
        addTrait(container, inflater, getString(R.string.trait_calm), state.calm);
        addTrait(container, inflater, getString(R.string.trait_curious), state.curious);
        addTrait(container, inflater, getString(R.string.trait_playful), state.playful);
        addTrait(container, inflater, getString(R.string.trait_emotional), state.emotional);
    }

    private void addTrait(LinearLayout container, LayoutInflater inflater, String name, int value) {
        View row = inflater.inflate(R.layout.item_trait, container, false);
        ((TextView) row.findViewById(R.id.traitName)).setText(name);
        ((TextView) row.findViewById(R.id.traitValue)).setText(String.valueOf(value));
        ((ProgressBar) row.findViewById(R.id.traitBar)).setProgress(value);
        container.addView(row);
    }

    private void bindMemories(List<MemoryEntry> memories) {
        RecyclerView list = findViewById(R.id.memoriesList);
        TextView empty = findViewById(R.id.memoriesEmpty);
        if (memories == null || memories.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        list.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new MemoryAdapter(memories));
    }

    private void exportConversationAsText() {
        Toast.makeText(this, R.string.chat_export_working, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            List<ConversationMessage> messages = repository.loadRecentMessages(2000);
            if (messages == null || messages.isEmpty()) {
                handler.post(() -> Toast.makeText(this, R.string.chat_export_empty, Toast.LENGTH_SHORT).show());
                return;
            }
            String content = buildExportContent(messages);
            String fileName = "lumi-chat-"
                    + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA)
                    .format(new Date(System.currentTimeMillis()))
                    + ".txt";
            handler.post(() -> {
                pendingExportContent = content;
                exportTextLauncher.launch(fileName);
            });
        });
    }

    private String buildExportContent(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        String exportedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                .format(new Date(System.currentTimeMillis()));
        sb.append("LUMI 대화 내역\n");
        sb.append("내보낸 시각: ").append(exportedAt).append("\n\n");

        SimpleDateFormat lineFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        for (ConversationMessage m : messages) {
            String who = "lumi".equals(m.sender) ? "루미" : "나";
            sb.append("[")
                    .append(lineFormat.format(new Date(m.timestamp)))
                    .append("] ")
                    .append(who)
                    .append(": ")
                    .append(m.content == null ? "" : m.content)
                    .append("\n");

            if (m.attachmentType != null && !ConversationMessage.ATTACH_NONE.equals(m.attachmentType)) {
                sb.append("  - 첨부: ").append(m.attachmentType);
                if (m.attachmentMeta != null && !m.attachmentMeta.trim().isEmpty()) {
                    sb.append(" (").append(m.attachmentMeta).append(")");
                }
                if (m.attachmentUri != null && !m.attachmentUri.trim().isEmpty()) {
                    sb.append(" ").append(m.attachmentUri);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void writeExportToUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, R.string.chat_export_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        final String content = pendingExportContent;
        if (content == null) {
            Toast.makeText(this, R.string.chat_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) throw new IllegalStateException("output stream is null");
                os.write(content.getBytes(StandardCharsets.UTF_8));
                os.flush();
                handler.post(() -> Toast.makeText(this, R.string.chat_export_done, Toast.LENGTH_SHORT).show());
            } catch (Throwable t) {
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                handler.post(() -> Toast.makeText(this,
                        getString(R.string.chat_export_failed, msg), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (moodLinePulse != null) moodLinePulse.cancel();
        super.onDestroy();
    }
}
