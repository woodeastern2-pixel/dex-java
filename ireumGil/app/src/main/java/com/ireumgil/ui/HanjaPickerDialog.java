package com.ireumgil.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ireumgil.R;
import com.ireumgil.data.HanjaRepository;
import com.ireumgil.data.HanjaSearchService;
import com.ireumgil.engine.HandwritingCandidateMatcher;
import com.ireumgil.model.HanjaCharacter;

import java.util.ArrayList;
import java.util.List;

public class HanjaPickerDialog extends DialogFragment {

    public interface OnHanjaSelectedListener {
        void onSelected(HanjaCharacter character);
    }

    private static final String ARG_TITLE = "title";
    private static final String ARG_SURNAME_MODE = "surnameMode";
    private static final String ARG_PREFILL_READING = "prefillReading";

    private HanjaRepository repository;
    private HandwritingCandidateMatcher matcher;
    private HanjaSearchService searchService;
    private final List<HanjaCharacter> current = new ArrayList<>();

    private OnHanjaSelectedListener listener;
    private HanjaCharacter selected;

    public static HanjaPickerDialog newInstance(String title, boolean surnameMode, String prefillReading) {
        HanjaPickerDialog dialog = new HanjaPickerDialog();
        Bundle b = new Bundle();
        b.putString(ARG_TITLE, title);
        b.putBoolean(ARG_SURNAME_MODE, surnameMode);
        b.putString(ARG_PREFILL_READING, prefillReading == null ? "" : prefillReading);
        dialog.setArguments(b);
        return dialog;
    }

    public void setOnHanjaSelectedListener(OnHanjaSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        repository = new HanjaRepository(requireContext().getApplicationContext());
        matcher = new HandwritingCandidateMatcher(repository);
        searchService = new HanjaSearchService(repository);

        String title = getArguments() != null ? getArguments().getString(ARG_TITLE, "한자 선택") : "한자 선택";
        boolean surnameMode = getArguments() != null && getArguments().getBoolean(ARG_SURNAME_MODE, false);
        String prefillReading = getArguments() != null ? getArguments().getString(ARG_PREFILL_READING, "") : "";

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_hanja_picker, null);
        EditText editReading = root.findViewById(R.id.editReading);
        EditText editCharacter = root.findViewById(R.id.editCharacter);
        EditText editMeaning = root.findViewById(R.id.editMeaning);
        EditText editStroke = root.findViewById(R.id.editStroke);
        CheckBox checkAllowedOnly = root.findViewById(R.id.checkAllowedOnly);
        CheckBox checkSurnameOnly = root.findViewById(R.id.checkSurnameOnly);
        TextView textHint = root.findViewById(R.id.textHint);
        TextView textResultCount = root.findViewById(R.id.textResultCount);
        TextView textSelected = root.findViewById(R.id.textSelected);
        HandwritingCanvasView canvas = root.findViewById(R.id.handwritingCanvas);
        Button btnClear = root.findViewById(R.id.btnClearCanvas);
        Button btnFind = root.findViewById(R.id.btnFindCandidates);
        Button btnConfirm = root.findViewById(R.id.btnConfirmSelection);
        ListView listView = root.findViewById(R.id.listCandidates);

        HanjaSearchAdapter adapter = new HanjaSearchAdapter(requireContext(), character -> {
            selected = character;
            textSelected.setText("선택한 한자: " + character.character + " (" + character.reading + ") " + character.meaning);
            textHint.setText("여러 후보 중 원하는 한자를 선택해 주세요.");
            btnConfirm.setEnabled(true);
        });
        listView.setAdapter(adapter);

        editReading.setText(prefillReading);
        if (surnameMode) {
            checkSurnameOnly.setChecked(true);
            checkSurnameOnly.setEnabled(false);
        }
        runSearch(editReading, editCharacter, editMeaning, editStroke, checkAllowedOnly, checkSurnameOnly, surnameMode, adapter, textResultCount);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                runSearch(editReading, editCharacter, editMeaning, editStroke, checkAllowedOnly, checkSurnameOnly, surnameMode, adapter, textResultCount);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        editReading.addTextChangedListener(watcher);
        editCharacter.addTextChangedListener(watcher);
        editMeaning.addTextChangedListener(watcher);
        editStroke.addTextChangedListener(watcher);
        checkAllowedOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
                runSearch(editReading, editCharacter, editMeaning, editStroke, checkAllowedOnly, checkSurnameOnly, surnameMode, adapter, textResultCount));
        checkSurnameOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
                runSearch(editReading, editCharacter, editMeaning, editStroke, checkAllowedOnly, checkSurnameOnly, surnameMode, adapter, textResultCount));

        btnConfirm.setEnabled(false);

        btnClear.setOnClickListener(v -> {
            selected = null;
            canvas.clearAll();
            textSelected.setText("선택한 한자: 없음");
            btnConfirm.setEnabled(false);
        });
        btnFind.setOnClickListener(v -> {
            HandwritingCanvasView.DrawingSignature signature = canvas.createSignature();
            boolean useSurnamePool = surnameMode || checkSurnameOnly.isChecked();
            List<HanjaCharacter> matched = matcher.match(signature, editReading.getText().toString().trim(), useSurnamePool);
            current.clear();
            current.addAll(matched);
            adapter.submitList(current);
            textResultCount.setText("검색 결과 " + current.size() + "개");
            if (current.isEmpty()) {
                textHint.setText("획 입력이 적어 후보를 찾지 못했습니다. 지우기 후 다시 그려서 후보 찾기를 눌러주세요.");
            } else {
                textHint.setText("손글씨 입력 기반 후보입니다. 최종 후보는 공식 인명용 한자 데이터에서 검색되었습니다.");
            }
        });
        btnConfirm.setOnClickListener(v -> {
            if (selected == null) {
                textHint.setText("후보를 먼저 선택해 주세요.");
                return;
            }
            if (listener != null) {
                listener.onSelected(selected);
            }
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(root)
                .setNegativeButton("닫기", null)
                .create();
    }

    private void runSearch(
            EditText editReading,
            EditText editCharacter,
            EditText editMeaning,
            EditText editStroke,
            CheckBox checkAllowedOnly,
            CheckBox checkSurnameOnly,
            boolean surnameMode,
            HanjaSearchAdapter adapter,
            TextView textResultCount
    ) {
        String reading = editReading.getText() == null ? "" : editReading.getText().toString().trim();
        String characterKeyword = editCharacter.getText() == null ? "" : editCharacter.getText().toString().trim();
        String meaningKeyword = editMeaning.getText() == null ? "" : editMeaning.getText().toString().trim();
        Integer stroke = parseStroke(editStroke.getText() == null ? "" : editStroke.getText().toString().trim());

        HanjaSearchService.Query query = new HanjaSearchService.Query();
        query.reading = reading;
        query.character = characterKeyword;
        query.meaningKeyword = meaningKeyword;
        query.strokeCount = stroke;
        query.allowedForName = checkAllowedOnly.isChecked() ? true : null;
        query.surnameOnly = (surnameMode || checkSurnameOnly.isChecked()) ? true : null;
        query.limit = 500;

        current.clear();

        boolean hasFilter = !reading.isEmpty()
                || !characterKeyword.isEmpty()
                || !meaningKeyword.isEmpty()
                || stroke != null
                || query.allowedForName != null
                || query.surnameOnly != null;

        if (!hasFilter && !surnameMode) {
            current.addAll(repository.getAllAllowed());
        } else {
            current.addAll(searchService.search(query));
        }
        adapter.submitList(current);
        textResultCount.setText("검색 결과 " + current.size() + "개");
    }

    private Integer parseStroke(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
