package com.easternwood.ireumgil.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.mlkit.common.MlKitException;
import com.easternwood.ireumgil.R;
import com.easternwood.ireumgil.data.HanjaRepository;
import com.easternwood.ireumgil.engine.HandwritingCandidateMatcher;
import com.easternwood.ireumgil.engine.MlKitHanjaHandwritingRecognizer;
import com.easternwood.ireumgil.model.HanjaCharacter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HanjaSearchDialog extends DialogFragment {

    public interface OnHanjaSelectedListener {
        void onSelected(HanjaCharacter character);
    }

    private static final String ARG_SURNAME_MODE = "surnameMode";
    private static final String ARG_HINT = "hint";

    private HanjaRepository repository;
    private OnHanjaSelectedListener listener;
    private MlKitHanjaHandwritingRecognizer recognizer;

    public static HanjaSearchDialog newHandwritingPicker(boolean surnameMode, String hint) {
        HanjaSearchDialog dialog = new HanjaSearchDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SURNAME_MODE, surnameMode);
        args.putString(ARG_HINT, hint == null ? "" : hint);
        dialog.setArguments(args);
        return dialog;
    }

    public static HanjaSearchDialog newSurnamePicker(String surnameHangul) {
        return newHandwritingPicker(true, surnameHangul);
    }

    public static HanjaSearchDialog newGeneralPicker() {
        return newHandwritingPicker(false, "");
    }

    public void setOnHanjaSelectedListener(OnHanjaSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        repository = new HanjaRepository(requireContext().getApplicationContext());
        boolean surnameMode = getArguments() != null
                && getArguments().getBoolean(ARG_SURNAME_MODE, false);
        String initialHint = getArguments() == null ? "" : getArguments().getString(ARG_HINT, "");

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_hanja_search, null);
        EditText editHint = root.findViewById(R.id.editReadingHint);
        HandwritingCanvasView canvas = root.findViewById(R.id.handwritingCanvas);
        TextView btnUndo = root.findViewById(R.id.btnUndoStroke);
        TextView btnClear = root.findViewById(R.id.btnClearCanvas);
        Button btnRecognize = root.findViewById(R.id.btnRecognizeHandwriting);
        TextView textHint = root.findViewById(R.id.textHint);
        ListView listView = root.findViewById(R.id.listCandidates);

        editHint.setText(initialHint);
        editHint.setSelection(editHint.length());
        HanjaSearchAdapter adapter = new HanjaSearchAdapter(requireContext(), character -> {
            if (listener != null) {
                listener.onSelected(character);
            }
            dismiss();
        });
        listView.setAdapter(adapter);

        HandwritingCandidateMatcher localMatcher = new HandwritingCandidateMatcher(repository);
        try {
            recognizer = new MlKitHanjaHandwritingRecognizer();
        } catch (MlKitException error) {
            textHint.setText("기기에서 한자 인식 모델을 열 수 없어 로컬 획수 후보를 사용합니다.");
        }

        btnUndo.setOnClickListener(view -> canvas.undoLastStroke());
        btnClear.setOnClickListener(view -> {
            canvas.clearAll();
            adapter.submitList(new ArrayList<>());
            listView.setVisibility(View.GONE);
            textHint.setText("한자를 쓴 뒤 인식하기를 눌러 주세요.");
        });
        btnRecognize.setOnClickListener(view -> {
            if (canvas.isEmpty()) {
                textHint.setText("먼저 흰색 입력 칸에 한자를 한 글자 써 주세요.");
                return;
            }
            btnRecognize.setEnabled(false);
            textHint.setText("손글씨를 분석하고 있습니다…");
            String filterHint = editHint.getText() == null ? "" : editHint.getText().toString().trim();
            if (recognizer == null) {
                showLocalFallback(canvas, filterHint, surnameMode, localMatcher, adapter, listView, textHint);
                btnRecognize.setEnabled(true);
                return;
            }
            recognizer.recognize(canvas.copyInkStrokes(), new MlKitHanjaHandwritingRecognizer.Callback() {
                @Override
                public void onModelDownloadStarted() {
                    textHint.setText("최초 1회 한자 손글씨 모델을 내려받는 중입니다(약 20MB)…");
                }

                @Override
                public void onRecognized(List<String> candidates) {
                    List<HanjaCharacter> verified = verifyCandidates(candidates, filterHint, surnameMode);
                    if (verified.isEmpty()) {
                        showLocalFallback(canvas, filterHint, surnameMode, localMatcher, adapter, listView, textHint);
                    } else {
                        adapter.submitList(verified);
                        listView.setVisibility(View.VISIBLE);
                        textHint.setText("인식 후보 " + verified.size() + "개 · 원하는 한자를 선택하세요.");
                    }
                    btnRecognize.setEnabled(true);
                }

                @Override
                public void onFailure(Exception error) {
                    showLocalFallback(canvas, filterHint, surnameMode, localMatcher, adapter, listView, textHint);
                    btnRecognize.setEnabled(true);
                }
            });
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle(surnameMode ? "성씨 한자 손글씨 검색" : "한자 손글씨 검색")
                .setView(root)
                .setNegativeButton("닫기", null)
                .create();
    }

    private List<HanjaCharacter> verifyCandidates(List<String> rawCandidates, String hint, boolean surnameMode) {
        Map<String, HanjaCharacter> unique = new LinkedHashMap<>();
        for (String raw : rawCandidates) {
            if (raw == null) {
                continue;
            }
            for (int offset = 0; offset < raw.length(); ) {
                int codePoint = raw.codePointAt(offset);
                String value = new String(Character.toChars(codePoint));
                if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                    for (HanjaCharacter character : repository.searchByKeyword(value, surnameMode, 20)) {
                        if (value.equals(character.character) && matchesHint(character, hint)) {
                            unique.put(character.character + "|" + character.reading, character);
                        }
                    }
                }
                offset += Character.charCount(codePoint);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean matchesHint(HanjaCharacter character, String rawHint) {
        String hint = rawHint == null ? "" : rawHint.trim();
        if (hint.isEmpty()) {
            return true;
        }
        String searchable = character.character + " " + character.reading + " " + character.meaning;
        for (String token : hint.split("\\s+")) {
            if (!searchable.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private void showLocalFallback(
            HandwritingCanvasView canvas,
            String hint,
            boolean surnameMode,
            HandwritingCandidateMatcher matcher,
            HanjaSearchAdapter adapter,
            ListView listView,
            TextView textHint
    ) {
        List<HanjaCharacter> fallback = matcher.match(canvas.createSignature(), hint, surnameMode);
        adapter.submitList(fallback);
        listView.setVisibility(fallback.isEmpty() ? View.GONE : View.VISIBLE);
        textHint.setText(fallback.isEmpty()
                ? "인식 후보가 없습니다. 획순대로 더 크게 다시 써 주세요."
                : "정밀 인식이 어려워 획수·형태가 가까운 인명용 한자 후보를 보여드립니다.");
    }

    @Override
    public void onDestroy() {
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        super.onDestroy();
    }
}
