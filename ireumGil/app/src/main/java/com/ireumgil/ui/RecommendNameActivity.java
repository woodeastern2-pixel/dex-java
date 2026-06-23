package com.ireumgil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ireumgil.R;
import com.ireumgil.data.HanjaRepository;
import com.ireumgil.engine.NameRecommendationService;
import com.ireumgil.model.NameCandidate;

import java.util.Arrays;
import java.util.List;

public class RecommendNameActivity extends AppCompatActivity {

    private EditText editSurname;
    private Spinner spinnerGender;
    private LinearLayout layoutResults;
    private NameRecommendationService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend_name);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("이름 추천");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        editSurname = findViewById(R.id.editSurname);
        spinnerGender = findViewById(R.id.spinnerGender);
        layoutResults = findViewById(R.id.layoutResults);
        Button btnRun = findViewById(R.id.btnRecommendRun);
        Button btnReset = findViewById(R.id.btnReset);

        spinnerGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("남자", "여자", "선택 안 함")));

        btnRun.setOnClickListener(v -> runRecommend());
        btnReset.setOnClickListener(v -> {
            editSurname.setText("");
            spinnerGender.setSelection(2);
            layoutResults.removeAllViews();
        });
    }

    private void runRecommend() {
        String surname = editSurname.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();

        if (surname.isEmpty()) {
            Toast.makeText(this, "성을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<NameCandidate> result = service.recommendBasic(surname, gender);
        layoutResults.removeAllViews();

        if (result.isEmpty()) {
            Toast.makeText(this, "추천 결과를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (NameCandidate c : result) {
            View card = inflater.inflate(R.layout.item_name_card, layoutResults, false);
            ((TextView) card.findViewById(R.id.textName)).setText(c.hangulName + " · " + c.grade);
            ((TextView) card.findViewById(R.id.textHanja)).setText("추천 한자 조합: " + c.hanjaCombination);
            ((TextView) card.findViewById(R.id.textMeaning)).setText("한자 뜻: " + c.hanjaMeaning);
            ((TextView) card.findViewById(R.id.textReason)).setText("추천 이유: " + c.reason);
            ((TextView) card.findViewById(R.id.textElement)).setText("오행 균형: " + c.fiveElementSummary);
            ((TextView) card.findViewById(R.id.textStroke)).setText("획수 평가: " + c.strokeSummary + "\n" + c.supplementSummary);
            ((TextView) card.findViewById(R.id.textScore)).setText("종합 점수: " + c.score + " / 100");
            ((TextView) card.findViewById(R.id.textNotice)).setText("기본 추천 안내: 사주 상세 미입력 기준의 일반 균형형 추천입니다.");
            layoutResults.addView(card);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
