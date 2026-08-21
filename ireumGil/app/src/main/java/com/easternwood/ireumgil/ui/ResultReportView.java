package com.easternwood.ireumgil.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.easternwood.ireumgil.R;
import com.easternwood.ireumgil.model.NameFortuneReport;

public class ResultReportView extends LinearLayout {

    private final TextView textFullName;
    private final TextView textScore;
    private final TextView textGrade;
    private final TextView textScoreBreakdown;
    private final TextView textInputBasis;
    private final TextView textFourPillars;
    private final TextView textMeaning;
    private final TextView textStroke;
    private final TextView textYinYang;
    private final TextView textFiveElements;
    private final TextView textStrength;
    private final TextView textWeakness;
    private final TextView textCalculationBasis;
    private final TextView textNotice;

    public ResultReportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_result_report, this, true);
        textFullName = findViewById(R.id.textFullName);
        textScore = findViewById(R.id.textScore);
        textGrade = findViewById(R.id.textGrade);
        textScoreBreakdown = findViewById(R.id.textScoreBreakdown);
        textInputBasis = findViewById(R.id.textInputBasis);
        textFourPillars = findViewById(R.id.textFourPillars);
        textMeaning = findViewById(R.id.textMeaning);
        textStroke = findViewById(R.id.textStroke);
        textYinYang = findViewById(R.id.textYinYang);
        textFiveElements = findViewById(R.id.textFiveElements);
        textStrength = findViewById(R.id.textStrength);
        textWeakness = findViewById(R.id.textWeakness);
        textCalculationBasis = findViewById(R.id.textCalculationBasis);
        textNotice = findViewById(R.id.textNotice);
    }

    public void render(NameFortuneReport report) {
        setVisibility(VISIBLE);
        textFullName.setText(report.fullName);
        textScore.setText("점수: " + report.score + " / 100");
        textGrade.setText("종합 평가: " + report.grade);
        textScoreBreakdown.setText(report.scoreBreakdown);
        textInputBasis.setText(report.inputBasis);
        textFourPillars.setText(report.fourPillars);
        textMeaning.setText(report.meaningInterpretation);
        textStroke.setText(report.strokeAnalysis);
        textYinYang.setText(report.yinYangAnalysis);
        textFiveElements.setText(report.fiveElementAnalysis + "\n" + report.complementAnalysis);
        textStrength.setText("좋은 점: " + report.strength);
        textWeakness.setText("아쉬운 점: " + report.weakness);
        textCalculationBasis.setText("계산 기준: " + report.calculationBasis);
        textNotice.setText("주의사항: " + report.caution);
    }

    public void clear() {
        setVisibility(GONE);
    }
}
