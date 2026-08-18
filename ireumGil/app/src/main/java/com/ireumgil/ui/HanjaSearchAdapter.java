package com.ireumgil.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.ireumgil.R;
import com.ireumgil.model.HanjaCharacter;

import java.util.ArrayList;
import java.util.List;

public class HanjaSearchAdapter extends BaseAdapter {

    public interface OnSelectClickListener {
        void onSelect(HanjaCharacter character);
    }

    private final LayoutInflater inflater;
    private final List<HanjaCharacter> items = new ArrayList<>();
    private final OnSelectClickListener listener;

    public HanjaSearchAdapter(Context context, OnSelectClickListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void submitList(List<HanjaCharacter> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_hanja_candidate, parent, false);
        }

        HanjaCharacter c = items.get(position);
        TextView textChar = view.findViewById(R.id.textChar);
        TextView textReading = view.findViewById(R.id.textReading);
        TextView textMeaning = view.findViewById(R.id.textMeaning);
        TextView textStroke = view.findViewById(R.id.textStroke);
        TextView textSource = view.findViewById(R.id.textSource);
        TextView textNameFlag = view.findViewById(R.id.textNameFlag);
        Button btnSelect = view.findViewById(R.id.btnSelect);

        textChar.setText(c.character);
        textReading.setText("음: " + c.reading);
        textMeaning.setText("뜻: " + safe(c.meaning));
        String strokeText = c.strokeCount == null ? "미상" : c.strokeCount + "획";
        String fiveElementText = isBlank(c.elementCategory) ? "정보 없음" : c.elementCategory;
        textStroke.setText("획수: " + strokeText + " · 오행: " + fiveElementText + " (앱 분석용 보완 정보)");
        textSource.setText("출처: " + sourceLabel(c.source, c.sourceNote));
        textNameFlag.setText("인명용 여부: " + toYesNo(c.allowedForName));
        btnSelect.setOnClickListener(v -> listener.onSelect(c));

        return view;
    }

    private String safe(String text) {
        return isBlank(text) ? "정보 없음" : text;
    }

    private String sourceLabel(String sourceCode, String sourceNote) {
        if (!isBlank(sourceNote)) {
            return sourceNote;
        }
        if ("scourt".equalsIgnoreCase(sourceCode)) {
            return "대법원 인명용 한자표";
        }
        if ("nlaw".equalsIgnoreCase(sourceCode)) {
            return "국가법령정보센터 별표";
        }
        if ("moe".equalsIgnoreCase(sourceCode)) {
            return "교육부 한문교육용 기초한자";
        }
        return isBlank(sourceCode) ? "미기재" : sourceCode;
    }

    private String toYesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "O" : "X";
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
