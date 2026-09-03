package com.easternwood.ireumgil.ui;

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

import com.easternwood.ireumgil.R;
import com.easternwood.ireumgil.data.HanjaRepository;
import com.easternwood.ireumgil.engine.NameRecommendationService;
import com.easternwood.ireumgil.monetization.RewardAccessPolicy;
import com.easternwood.ireumgil.monetization.RewardAccessStore;
import com.easternwood.ireumgil.monetization.RewardedAccessController;
import com.easternwood.ireumgil.model.NameCandidate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecommendNameActivity extends AppCompatActivity {

    private EditText editSurname;
    private Spinner spinnerGender;
    private LinearLayout layoutResults;
    private NameRecommendationService service;
    private RewardedAccessController rewardedAccessController;
    private List<NameCandidate> latestResults = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend_name);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("빠른 이름 추천");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        editSurname = findViewById(R.id.editSurname);
        spinnerGender = findViewById(R.id.spinnerGender);
        layoutResults = findViewById(R.id.layoutResults);
        Button btnRun = findViewById(R.id.btnRecommendRun);
        TextView btnReset = findViewById(R.id.btnReset);

        spinnerGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("남자", "여자", "선택 안 함")));

        btnRun.setOnClickListener(v -> runRecommend());
        btnReset.setOnClickListener(v -> {
            editSurname.setText("");
            spinnerGender.setSelection(2);
            layoutResults.removeAllViews();
            latestResults = Collections.emptyList();
        });

        rewardedAccessController = new RewardedAccessController(this, () -> {
            if (!latestResults.isEmpty()) renderResults();
        });
        rewardedAccessController.start();
    }

    private void runRecommend() {
        String surname = editSurname.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();

        if (surname.isEmpty()) {
            Toast.makeText(this, "성을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        latestResults = service.recommendBasic(surname, gender);

        if (latestResults.isEmpty()) {
            layoutResults.removeAllViews();
            Toast.makeText(this, "추천 결과를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        renderResults();
    }

    private void renderResults() {
        layoutResults.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (NameCandidate c : latestResults) {
            View card = inflater.inflate(R.layout.item_name_card, layoutResults, false);
            Button btnUnlock = card.findViewById(R.id.btnUnlockReward);
            if (RewardAccessPolicy.requiresReward(c.score) && !RewardAccessStore.hasAccess()) {
                ((TextView) card.findViewById(R.id.textName)).setText("🔒 95점 이상 추천 이름을 찾았습니다");
                ((TextView) card.findViewById(R.id.textHanja)).setText("광고 시청 후 이름과 한자 조합이 공개됩니다.");
                ((TextView) card.findViewById(R.id.textMeaning)).setText("작명 기준을 모두 통과한 우수 후보입니다.");
                card.findViewById(R.id.textReason).setVisibility(View.GONE);
                card.findViewById(R.id.textElement).setVisibility(View.GONE);
                card.findViewById(R.id.textStroke).setVisibility(View.GONE);
                ((TextView) card.findViewById(R.id.textScore)).setText("검증 점수: 95점 이상");
                ((TextView) card.findViewById(R.id.textNotice)).setText("보상형 전면광고 1회 시청 시 60분 동안 전체 정보를 볼 수 있습니다.");
                btnUnlock.setVisibility(View.VISIBLE);
                btnUnlock.setText(R.string.reward_watch_button_compact);
                btnUnlock.setOnClickListener(view -> rewardedAccessController.requestAccess());
                layoutResults.addView(card);
                continue;
            }
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
    protected void onResume() {
        super.onResume();
        if (rewardedAccessController != null) rewardedAccessController.onResume();
        if (!latestResults.isEmpty()) renderResults();
    }

    @Override
    protected void onDestroy() {
        if (rewardedAccessController != null) rewardedAccessController.destroy();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
