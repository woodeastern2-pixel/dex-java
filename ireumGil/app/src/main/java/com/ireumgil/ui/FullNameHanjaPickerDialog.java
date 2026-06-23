package com.ireumgil.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ireumgil.R;
import com.ireumgil.model.HanjaCharacter;

public class FullNameHanjaPickerDialog extends DialogFragment {

    public interface OnFullNameSelectedListener {
        void onSelected(HanjaCharacter surname, HanjaCharacter first, HanjaCharacter second);
    }

    private HanjaCharacter surname;
    private HanjaCharacter first;
    private HanjaCharacter second;
    private OnFullNameSelectedListener listener;

    public static FullNameHanjaPickerDialog newInstance(
            HanjaCharacter surname,
            HanjaCharacter first,
            HanjaCharacter second
    ) {
        FullNameHanjaPickerDialog dialog = new FullNameHanjaPickerDialog();
        dialog.surname = surname;
        dialog.first = first;
        dialog.second = second;
        return dialog;
    }

    public void setOnFullNameSelectedListener(OnFullNameSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_full_name_hanja_picker, null);

        TextView textSurname = root.findViewById(R.id.textSurnameSelected);
        TextView textFirst = root.findViewById(R.id.textFirstSelected);
        TextView textSecond = root.findViewById(R.id.textSecondSelected);
        TextView textHelper = root.findViewById(R.id.textHelper);

        Button btnSelectSurname = root.findViewById(R.id.btnSelectSurname);
        Button btnSelectFirst = root.findViewById(R.id.btnSelectFirst);
        Button btnSelectSecond = root.findViewById(R.id.btnSelectSecond);
        Button btnDone = root.findViewById(R.id.btnDone);

        updateSlot(textSurname, btnSelectSurname, surname);
        updateSlot(textFirst, btnSelectFirst, first);
        updateSlot(textSecond, btnSelectSecond, second);

        btnSelectSurname.setOnClickListener(v -> openPicker(1, textSurname, btnSelectSurname));
        btnSelectFirst.setOnClickListener(v -> openPicker(2, textFirst, btnSelectFirst));
        btnSelectSecond.setOnClickListener(v -> openPicker(3, textSecond, btnSelectSecond));

        btnDone.setOnClickListener(v -> {
            if (surname == null || first == null || second == null) {
                textHelper.setText("성 한자와 이름 두 글자 한자를 모두 선택해 주세요");
                return;
            }
            if (listener != null) {
                listener.onSelected(surname, first, second);
            }
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("한자 이름 선택")
                .setView(root)
                .setNegativeButton("닫기", null)
                .create();
    }

    private void openPicker(int slot, TextView slotText, Button slotButton) {
        String title;
        boolean surnameMode = false;
        String prefill = "";
        if (slot == 1) {
            title = "성 한자 선택";
            surnameMode = true;
            prefill = surname != null ? surname.reading : "";
        } else if (slot == 2) {
            title = "이름 첫 번째 한자 선택";
            prefill = first != null ? first.reading : "";
        } else {
            title = "이름 두 번째 한자 선택";
            prefill = second != null ? second.reading : "";
        }

        HanjaPickerDialog dialog = HanjaPickerDialog.newInstance(title, surnameMode, prefill);
        dialog.setOnHanjaSelectedListener(character -> {
            if (slot == 1) {
                surname = character;
            } else if (slot == 2) {
                first = character;
            } else {
                second = character;
            }
            updateSlot(slotText, slotButton, character);
        });
        dialog.show(getParentFragmentManager(), "fullNameSlotPicker" + slot);
    }

    private void updateSlot(TextView slotText, Button slotButton, HanjaCharacter character) {
        if (character == null) {
            slotText.setText("아직 선택되지 않았습니다");
            slotButton.setText("선택");
        } else {
            slotText.setText(character.character + "(" + character.reading + ") " + character.meaning);
            slotButton.setText("변경");
        }
    }
}
