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
        if (Boolean.TRUE.equals(c.isCommonSurname)) {
            textSource.setText("통계청 2015 인구주택총조사 성씨 자료");
            textNameFlag.setText("대한민국 성씨로 확인된 한자");
        } else {
            textSource.setText("대한민국 법원 인명용 한자 데이터");
            textNameFlag.setText(Boolean.TRUE.equals(c.allowedForName)
                    ? "이름에 사용할 수 있는 한자"
                    : "사용 가능 여부 확인 필요");
        }
        btnSelect.setOnClickListener(v -> listener.onSelect(c));

        return view;
    }

    private String safe(String text) {
        return isBlank(text) ? "정보 없음" : text;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
