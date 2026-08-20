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
import android.widget.Button;
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
        Button btnHandwriting = root.findViewById(R.id.btnOpenHandwriting);

        textGuide.setText(surnameMode
                ? "성씨의 음·뜻·한자를 검색하거나 손글씨로 찾으세요."
                : "한자의 음·뜻·글자를 한곳에서 검색하거나 손글씨로 찾으세요.");
        editSearch.setHint(surnameMode ? "예: 정, 나라, 鄭" : "예: 민, 옥돌, 珉 · 민 옥돌");

        HanjaSearchAdapter adapter = new HanjaSearchAdapter(requireContext(), character -> {
            if (listener != null) {
                listener.onSelected(character);
            }
            dismiss();
        });
        listView.setAdapter(adapter);

        btnHandwriting.setOnClickListener(view -> {
            String hint = editSearch.getText() == null ? "" : editSearch.getText().toString().trim();
            HanjaSearchDialog handwriting = HanjaSearchDialog.newHandwritingPicker(surnameMode, hint);
            handwriting.setOnHanjaSelectedListener(character -> {
                if (listener != null) {
                    listener.onSelected(character);
                }
                dismiss();
            });
            handwriting.show(getParentFragmentManager(), "handwritingHanjaPicker");
        });

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
        } else {
            results.addAll(repository.searchByKeyword(keyword, surnameMode, 120));
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
                    ? "검색할 음·뜻·한자를 입력하거나 손글씨 검색을 이용해 주세요."
                    : "일치하는 인명용 한자가 없습니다. 음이나 뜻을 나눠 다시 검색해 주세요.");
        }
    }
}
