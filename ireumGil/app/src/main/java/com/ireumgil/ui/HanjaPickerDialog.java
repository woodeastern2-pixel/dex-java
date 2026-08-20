package com.ireumgil.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ireumgil.R;
import com.ireumgil.data.HanjaRepository;
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
    private OnHanjaSelectedListener listener;

    public static HanjaPickerDialog newInstance(String title, boolean surnameMode, String prefillReading) {
        HanjaPickerDialog dialog = new HanjaPickerDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putBoolean(ARG_SURNAME_MODE, surnameMode);
        args.putString(ARG_PREFILL_READING, prefillReading == null ? "" : prefillReading);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnHanjaSelectedListener(OnHanjaSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        repository = new HanjaRepository(requireContext().getApplicationContext());

        String title = getArguments() != null
                ? getArguments().getString(ARG_TITLE, "한자 선택")
                : "한자 선택";
        boolean surnameMode = getArguments() != null
                && getArguments().getBoolean(ARG_SURNAME_MODE, false);
        String prefill = getArguments() != null
                ? getArguments().getString(ARG_PREFILL_READING, "")
                : "";

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_hanja_picker, null);
        EditText editSearch = root.findViewById(R.id.editHanjaSearch);
        TextView textGuide = root.findViewById(R.id.textHanjaSearchGuide);
        TextView textResultCount = root.findViewById(R.id.textResultCount);
        TextView textEmpty = root.findViewById(R.id.textEmptyResult);
        ListView listView = root.findViewById(R.id.listCandidates);

        textGuide.setText(surnameMode
                ? "성의 한글 음을 입력하고 원하는 한자를 바로 선택하세요."
                : "이름의 한글 음이나 한자 한 글자를 입력하세요.");
        editSearch.setHint(surnameMode ? "예: 김, 이, 박" : "예: 민, 서, 珉");

        HanjaSearchAdapter adapter = new HanjaSearchAdapter(requireContext(), character -> {
            if (listener != null) {
                listener.onSelected(character);
            }
            dismiss();
        });
        listView.setAdapter(adapter);

        Runnable search = () -> runSearch(
                editSearch.getText() == null ? "" : editSearch.getText().toString(),
                surnameMode,
                adapter,
                textResultCount,
                textEmpty,
                listView
        );

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                search.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search.run();
                return true;
            }
            return false;
        });

        editSearch.setText(prefill);
        editSearch.setSelection(editSearch.length());
        search.run();

        return new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(root)
                .setNegativeButton("닫기", null)
                .create();
    }

    private void runSearch(
            String rawKeyword,
            boolean surnameMode,
            HanjaSearchAdapter adapter,
            TextView textResultCount,
            TextView textEmpty,
            ListView listView
    ) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        List<HanjaCharacter> results = new ArrayList<>();

        if (keyword.isEmpty()) {
            if (surnameMode) {
                results.addAll(repository.getCommonSurnameCharacters());
            }
        } else if (containsHanja(keyword)) {
            results.addAll(repository.search(
                    "",
                    keyword,
                    "",
                    null,
                    true,
                    surnameMode ? true : null,
                    120
            ));
        } else if (surnameMode) {
            results.addAll(repository.getSurnameCandidates(keyword));
        } else {
            results.addAll(repository.searchByReadingLike(keyword));
        }

        if (results.size() > 120) {
            results = new ArrayList<>(results.subList(0, 120));
        }
        adapter.submitList(results);
        textResultCount.setText(results.isEmpty() ? "" : "검색 결과 " + results.size() + "개");
        boolean empty = results.isEmpty();
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            textEmpty.setText(keyword.isEmpty()
                    ? "검색할 한글 음이나 한자를 입력해 주세요."
                    : "표시 가능한 인명용 한자를 찾지 못했습니다. 다른 음으로 검색해 주세요.");
        }
    }

    private boolean containsHanja(String value) {
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }
}
