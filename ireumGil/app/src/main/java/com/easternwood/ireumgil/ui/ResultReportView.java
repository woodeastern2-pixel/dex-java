package com.easternwood.ireumgil.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.easternwood.ireumgil.R;
import com.easternwood.ireumgil.model.NameFortuneReport;

public class ResultReportView extends LinearLayout {

    private final TextView textFullName;
    private final TextView textHanjaName;
    private final TextView textScore;
    private final TextView textScoreMessage;
    private final TextView textGrade;
    private final ProgressBar progressScore;
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
        textHanjaName = findViewById(R.id.textHanjaName);
        textScore = findViewById(R.id.textScore);
        textScoreMessage = findViewById(R.id.textScoreMessage);
        textGrade = findViewById(R.id.textGrade);
        progressScore = findViewById(R.id.progressScore);
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

        textFullName.setText(resolveHangulName(report));
        textHanjaName.setText(resolveHanjaName(report));
        textScore.setText(String.valueOf(report.score));
        textGrade.setText(report.grade);
        textScoreMessage.setText(scoreMessage(report.score));
        progressScore.setProgress(report.score);

        textScoreBreakdown.setText(report.scoreBreakdown);
        textInputBasis.setText(report.inputBasis);
        textFourPillars.setText(report.fourPillars);
        textMeaning.setText(report.meaningInterpretation);
        textStroke.setText(report.strokeAnalysis);
        textYinYang.setText(report.yinYangAnalysis);
        textFiveElements.setText(report.fiveElementAnalysis + "\n" + report.complementAnalysis);
        textStrength.setText("좋은 점  ·  " + report.strength);
        textWeakness.setText("검토할 점  ·  " + report.weakness);
        textCalculationBasis.setText("계산 기준: " + report.calculationBasis);
        textNotice.setText("주의사항: " + report.caution);
    }

    private String resolveHangulName(NameFortuneReport report) {
        if (report.hangulName != null && !report.hangulName.trim().isEmpty()) {
            return report.hangulName.trim();
        }
        if (report.fullName == null) {
            return "";
        }
        String value = report.fullName;
        int hanjaStart = value.indexOf(" (");
        if (hanjaStart >= 0) {
            value = value.substring(0, hanjaStart);
        }
        return value.replace(" ", "").trim();
    }

    private String resolveHanjaName(NameFortuneReport report) {
        if (report.hanjaName != null && !report.hanjaName.trim().isEmpty()) {
            return report.hanjaName.trim();
        }
        if (report.fullName == null) {
            return "";
        }
        int start = report.fullName.indexOf('(');
        int end = report.fullName.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return report.fullName.substring(start + 1, end).trim();
        }
        return "";
    }

    private String scoreMessage(int score) {
        if (score >= 95) {
            return "아주 좋은 이름이에요";
        }
        if (score >= 85) {
            return "균형이 좋은 이름이에요";
        }
        if (score >= 70) {
            return "전체적으로 좋은 흐름이에요";
        }
        if (score >= 55) {
            return "무난하지만 비교해 볼 부분이 있어요";
        }
        return "다른 후보와 함께 비교해 보세요";
    }

    public void clear() {
        setVisibility(GONE);
    }
}
