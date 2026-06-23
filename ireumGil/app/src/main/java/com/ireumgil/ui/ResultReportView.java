package com.ireumgil.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ireumgil.R;
import com.ireumgil.model.NameFortuneReport;

public class ResultReportView extends LinearLayout {

    private final TextView textFullName;
    private final TextView textScore;
    private final TextView textGrade;
    private final TextView textMeaning;
    private final TextView textStroke;
    private final TextView textYinYang;
    private final TextView textFiveElements;
    private final TextView textStrength;
    private final TextView textWeakness;
    private final TextView textNotice;

    public ResultReportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_result_report, this, true);
        textFullName = findViewById(R.id.textFullName);
        textScore = findViewById(R.id.textScore);
        textGrade = findViewById(R.id.textGrade);
        textMeaning = findViewById(R.id.textMeaning);
        textStroke = findViewById(R.id.textStroke);
        textYinYang = findViewById(R.id.textYinYang);
        textFiveElements = findViewById(R.id.textFiveElements);
        textStrength = findViewById(R.id.textStrength);
        textWeakness = findViewById(R.id.textWeakness);
        textNotice = findViewById(R.id.textNotice);
    }

    public void render(NameFortuneReport report) {
        setVisibility(VISIBLE);
        textFullName.setText(report.fullName);
        textScore.setText("점수: " + report.score + " / 100");
        textGrade.setText("종합 평가: " + report.grade);
        textMeaning.setText(report.meaningInterpretation);
        textStroke.setText(report.strokeAnalysis);
        textYinYang.setText(report.yinYangAnalysis);
        textFiveElements.setText(report.fiveElementAnalysis + "\n" + report.complementAnalysis);
        textStrength.setText("좋은 점: " + report.strength);
        textWeakness.setText("아쉬운 점: " + report.weakness);
        textNotice.setText("주의사항: " + report.caution);

        if ("매우 좋음".equals(report.grade)) {
            textGrade.setTextColor(Color.parseColor("#3F6D4D"));
        } else if ("좋음".equals(report.grade)) {
            textGrade.setTextColor(Color.parseColor("#9E7B3F"));
        } else if ("보통".equals(report.grade)) {
            textGrade.setTextColor(Color.parseColor("#996515"));
        } else {
            textGrade.setTextColor(Color.parseColor("#8A3B33"));
        }
    }

    public void clear() {
        setVisibility(GONE);
    }
}
