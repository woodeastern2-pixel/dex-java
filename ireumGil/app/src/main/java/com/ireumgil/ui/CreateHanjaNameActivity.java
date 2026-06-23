package com.ireumgil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ireumgil.R;
import com.ireumgil.data.HanjaRepository;
import com.ireumgil.data.RecentResultStore;
import com.ireumgil.engine.NameRecommendationService;
import com.ireumgil.model.HanjaCharacter;
import com.ireumgil.model.NameCandidate;
import com.ireumgil.model.SajuInput;

import java.util.Arrays;
import java.util.List;

public class CreateHanjaNameActivity extends AppCompatActivity {

    private EditText editSurname;
    private EditText editYear;
    private EditText editMonth;
    private EditText editDay;
    private EditText editHour;
    private EditText editMinute;
    private Spinner spinnerCalendar;
    private Spinner spinnerGender;
    private TextView chipSelectedSurname;
    private TextView textGenderFilterStatus;
    private LinearLayout layoutCandidates;

    private HanjaCharacter selectedSurnameHanja;
    private String selectedSurnameReading = "";
    private NameRecommendationService service;
    private RecentResultStore recentResultStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_hanja_name);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("한자 이름 만들기");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        recentResultStore = new RecentResultStore();

        editSurname = findViewById(R.id.editSurname);
        editYear = findViewById(R.id.editYear);
        editMonth = findViewById(R.id.editMonth);
        editDay = findViewById(R.id.editDay);
        editHour = findViewById(R.id.editHour);
        editMinute = findViewById(R.id.editMinute);
        spinnerCalendar = findViewById(R.id.spinnerCalendar);
        spinnerGender = findViewById(R.id.spinnerGender);
        chipSelectedSurname = findViewById(R.id.chipSelectedSurname);
        textGenderFilterStatus = findViewById(R.id.textGenderFilterStatus);
        layoutCandidates = findViewById(R.id.layoutCandidates);

        spinnerCalendar.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("양력", "음력")));
        spinnerGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("남자", "여자", "선택 안 함")));

        android.widget.Button btnGenerate = findViewById(R.id.btnGenerate);
        android.widget.Button btnReset = findViewById(R.id.btnReset);

        editSurname.setOnClickListener(v -> openSurnamePicker());
        btnGenerate.setOnClickListener(v -> generateCandidates());
        btnReset.setOnClickListener(v -> resetAll());
    }

    private void openSurnamePicker() {
        HanjaPickerDialog dialog = HanjaPickerDialog.newInstance("성 한자 선택", true, selectedSurnameReading);
        dialog.setOnHanjaSelectedListener(character -> {
            selectedSurnameHanja = character;
            selectedSurnameReading = character.reading;
            editSurname.setText(character.character);
            chipSelectedSurname.setText("선택된 성 한자: " + character.character + "(" + character.meaning + ")");
        });
        dialog.show(getSupportFragmentManager(), "surnamePicker");
    }

    private void generateCandidates() {
        if (selectedSurnameHanja == null) {
            Toast.makeText(this, "성 한자를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer year = parseInt(editYear.getText().toString());
        Integer month = parseInt(editMonth.getText().toString());
        Integer day = parseInt(editDay.getText().toString());
        Integer hour = parseInt(editHour.getText().toString());
        Integer minute = parseInt(editMinute.getText().toString());

        if (year == null || month == null || day == null || hour == null) {
            Toast.makeText(this, "년/월/일/시는 필수입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean lunar = "음력".equals(spinnerCalendar.getSelectedItem().toString());
        String gender = spinnerGender.getSelectedItem().toString();
        SajuInput saju = new SajuInput(year, month, day, hour, minute, lunar, gender);
        String surnameForEngine = selectedSurnameReading.isEmpty() ? selectedSurnameHanja.reading : selectedSurnameReading;

        List<NameCandidate> list = service.generateDetailed(selectedSurnameHanja, saju, surnameForEngine, gender);
        layoutCandidates.removeAllViews();
        textGenderFilterStatus.setText(buildGenderStatusText(gender));

        if (list.isEmpty()) {
            Toast.makeText(this, "조건에 맞는 후보가 부족합니다. 입력값을 조정해 주세요.", Toast.LENGTH_SHORT).show();
        } else {
            LayoutInflater inflater = LayoutInflater.from(this);
            renderGroup(inflater, list, 90, "90점 이상 좋은 이름", 3);
            renderGroup(inflater, list, 80, "80점 이상 안정적인 이름", 3);
            renderGroup(inflater, list, 70, "70점 이상 참고할 이름", 3);
            recentResultStore.save(this, "생성: " + list.get(0).hangulName + "(" + list.get(0).score + "점)");
        }
    }

    private void resetAll() {
        editSurname.setText("");
        editYear.setText("");
        editMonth.setText("");
        editDay.setText("");
        editHour.setText("");
        editMinute.setText("");
        spinnerCalendar.setSelection(0);
        spinnerGender.setSelection(2);
        selectedSurnameHanja = null;
        selectedSurnameReading = "";
        chipSelectedSurname.setText("선택된 성 한자: 없음");
        textGenderFilterStatus.setText("성별 구분 없이 추천되었습니다");
        layoutCandidates.removeAllViews();
    }

    private void renderGroup(LayoutInflater inflater, List<NameCandidate> list, int minScore, String title, int maxCount) {
        int rendered = 0;
        TextView section = new TextView(this);
        section.setText(title);
        section.setTextSize(16f);
        section.setTextColor(ContextCompat.getColor(this, R.color.deep_navy));
        section.setPadding(0, 14, 0, 8);
        layoutCandidates.addView(section);

        for (NameCandidate c : list) {
            if (c.score < minScore) {
                continue;
            }
            View card = inflater.inflate(R.layout.item_name_card, layoutCandidates, false);
            ((TextView) card.findViewById(R.id.textName)).setText("한글 이름: " + c.hangulName);
            ((TextView) card.findViewById(R.id.textHanja)).setText("한자 이름: " + c.hanjaCombination);
            ((TextView) card.findViewById(R.id.textMeaning)).setText("한자 뜻: " + c.hanjaMeaning);
            ((TextView) card.findViewById(R.id.textReason)).setText("추천 이유: " + c.reason);
            ((TextView) card.findViewById(R.id.textElement)).setText("오행 요약: " + c.fiveElementSummary + "\n" + c.supplementSummary);
            ((TextView) card.findViewById(R.id.textStroke)).setText("획수 요약: " + c.strokeSummary);
            ((TextView) card.findViewById(R.id.textScore)).setText("점수: " + c.score + "점");
            ((TextView) card.findViewById(R.id.textNotice)).setText(c.caution);
            layoutCandidates.addView(card);
            rendered++;
            if (rendered >= maxCount) {
                break;
            }
        }

        if (rendered == 0) {
            TextView empty = new TextView(this);
            empty.setText("조건에 맞는 후보를 생성하지 못했습니다.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.charcoal));
            empty.setTextSize(13f);
            layoutCandidates.addView(empty);
        }
    }

    private String buildGenderStatusText(String gender) {
        if ("남자".equals(gender)) {
            return "남자 이름 기준으로 추천되었습니다";
        }
        if ("여자".equals(gender)) {
            return "여자 이름 기준으로 추천되었습니다";
        }
        return "성별 구분 없이 추천되었습니다";
    }

    private Integer parseInt(String v) {
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
