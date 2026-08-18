package com.ireumgil.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

public class HanjaSearchDialog extends DialogFragment {

    public interface OnHanjaSelectedListener {
        void onSelected(HanjaCharacter character);
    }

    private static final String ARG_MODE = "mode";
    private static final String ARG_SURNAME = "surname";
    private static final String MODE_SURNAME = "SURNAME";
    private static final String MODE_GENERAL = "GENERAL";

    private final HanjaRepository repository = new HanjaRepository();
    private OnHanjaSelectedListener listener;
    private final List<HanjaCharacter> current = new ArrayList<>();

    public static HanjaSearchDialog newSurnamePicker(String surnameHangul) {
        HanjaSearchDialog dialog = new HanjaSearchDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, MODE_SURNAME);
        args.putString(ARG_SURNAME, surnameHangul);
        dialog.setArguments(args);
        return dialog;
    }

    public static HanjaSearchDialog newGeneralPicker() {
        HanjaSearchDialog dialog = new HanjaSearchDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, MODE_GENERAL);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnHanjaSelectedListener(OnHanjaSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_hanja_search, null);
        EditText editReading = v.findViewById(R.id.editReading);
        Button btnSearch = v.findViewById(R.id.btnSearch);
        Button btnFallback = v.findViewById(R.id.btnHandwritingFallback);
        ListView listView = v.findViewById(R.id.listCandidates);
        TextView textHint = v.findViewById(R.id.textHint);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        String mode = getArguments() != null ? getArguments().getString(ARG_MODE, MODE_GENERAL) : MODE_GENERAL;
        String surname = getArguments() != null ? getArguments().getString(ARG_SURNAME, "") : "";

        Runnable loadAll = () -> {
            current.clear();
            if (MODE_SURNAME.equals(mode)) {
                current.addAll(repository.getSurnameCandidates(surname));
                textHint.setText("성씨 한자 후보를 선택하세요.");
            } else {
                current.addAll(repository.getAllAllowed());
                textHint.setText("한글 음을 입력하거나 목록에서 직접 선택하세요.");
            }
            updateAdapter(adapter);
        };

        btnSearch.setOnClickListener(view -> {
            String reading = editReading.getText().toString().trim();
            current.clear();
            if (MODE_SURNAME.equals(mode) && TextUtils.isEmpty(reading)) {
                current.addAll(repository.getSurnameCandidates(surname));
            } else if (TextUtils.isEmpty(reading)) {
                current.addAll(repository.getAllAllowed());
            } else {
                current.addAll(repository.searchByReading(reading));
            }
            if (current.isEmpty()) {
                textHint.setText("일치 항목이 없습니다. 다른 음으로 검색하거나 전체 목록을 확인해 주세요.");
            } else {
                textHint.setText("검색 결과에서 한자를 선택하세요.");
            }
            updateAdapter(adapter);
        });

        btnFallback.setOnClickListener(view -> loadAll.run());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (listener != null && position >= 0 && position < current.size()) {
                listener.onSelected(current.get(position));
            }
            dismiss();
        });

        loadAll.run();

        return new AlertDialog.Builder(requireContext())
                .setTitle("인명용 한자 선택")
                .setView(v)
                .setNegativeButton("닫기", null)
                .create();
    }

    private void updateAdapter(ArrayAdapter<String> adapter) {
        adapter.clear();
        for (HanjaCharacter h : current) {
            adapter.add(h.character + " (" + h.reading + ") · " + h.meaning + " · " + h.strokeCount + "획 · " + h.elementCategory);
        }
        adapter.notifyDataSetChanged();
    }
}
