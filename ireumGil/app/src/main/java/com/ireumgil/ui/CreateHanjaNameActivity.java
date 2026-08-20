package com.ireumgil.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateHanjaNameActivity extends AppCompatActivity {

    private EditText editSurname;
    private EditText editYear;
    private EditText editPreferredName;
    private EditText editMonth;
    private EditText editDay;
    private EditText editHour;
    private EditText editMinute;
    private Spinner spinnerCalendar;
    private Spinner spinnerGender;
    private TextView chipSelectedSurname;
    private TextView textGenderFilterStatus;
    private LinearLayout layoutCandidates;
    private android.widget.Button btnPickDate;
    private android.widget.Button btnPickTime;

    private HanjaCharacter selectedSurnameHanja;
    private String selectedSurnameReading = "";
    private NameRecommendationService service;
    private RecentResultStore recentResultStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_hanja_name);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("이름온 맞춤 작명");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        recentResultStore = new RecentResultStore();

        editSurname = findViewById(R.id.editSurname);
        editPreferredName = findViewById(R.id.editPreferredName);
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
        btnPickDate = findViewById(R.id.btnPickDate);
        btnPickTime = findViewById(R.id.btnPickTime);

        spinnerCalendar.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("양력", "음력(평달)", "음력(윤달)")));
        spinnerGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("남자", "여자", "선택 안 함")));

        android.widget.Button btnGenerate = findViewById(R.id.btnGenerate);
        android.widget.Button btnReset = findViewById(R.id.btnReset);

        editSurname.setOnClickListener(v -> openSurnamePicker());
        btnPickDate.setOnClickListener(v -> openDatePicker());
        btnPickTime.setOnClickListener(v -> openTimePicker());
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

        String calendarType = spinnerCalendar.getSelectedItem().toString();
        boolean lunar = calendarType.startsWith("음력");
        boolean lunarLeapMonth = "음력(윤달)".equals(calendarType);
        String gender = spinnerGender.getSelectedItem().toString();
        SajuInput saju = new SajuInput(year, month, day, hour, minute, lunar, lunarLeapMonth, gender);
        String surnameForEngine = selectedSurnameReading.isEmpty() ? selectedSurnameHanja.reading : selectedSurnameReading;

        String preferredName = editPreferredName.getText().toString().trim();
        if (!preferredName.isEmpty() && preferredName.length() != 2) {
            Toast.makeText(this, "원하는 이름은 두 글자로 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<NameCandidate> list;
        try {
            list = preferredName.isEmpty()
                    ? service.generateDetailed(selectedSurnameHanja, saju, surnameForEngine, gender)
                    : service.generateForHangulName(selectedSurnameHanja, saju, surnameForEngine, gender, preferredName);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        layoutCandidates.removeAllViews();
        textGenderFilterStatus.setText(buildGenderStatusText(gender));

        if (list.isEmpty()) {
            Toast.makeText(this, "조건에 맞는 후보가 부족합니다. 입력값을 조정해 주세요.", Toast.LENGTH_SHORT).show();
        } else {
            LayoutInflater inflater = LayoutInflater.from(this);
            renderGroup(inflater, list, 85, 101, "균형이 뛰어난 이름 · 85점 이상", 3);
            renderGroup(inflater, list, 70, 85, "안정적인 이름 · 70–84점", 3);
            renderGroup(inflater, list, 55, 70, "함께 비교할 이름 · 55–69점", 3);
            recentResultStore.save(this, "생성: " + list.get(0).hangulName + "(" + list.get(0).score + "점)");
        }
    }

    private void openDatePicker() {
        Calendar now = Calendar.getInstance();
        Integer savedYear = parseInt(editYear.getText().toString());
        Integer savedMonth = parseInt(editMonth.getText().toString());
        Integer savedDay = parseInt(editDay.getText().toString());
        int year = savedYear == null ? now.get(Calendar.YEAR) : savedYear;
        int month = savedMonth == null ? now.get(Calendar.MONTH) : savedMonth - 1;
        int day = savedDay == null ? now.get(Calendar.DAY_OF_MONTH) : savedDay;
        new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            editYear.setText(String.valueOf(selectedYear));
            editMonth.setText(String.valueOf(selectedMonth + 1));
            editDay.setText(String.valueOf(selectedDay));
            btnPickDate.setText(String.format(Locale.KOREA, "%d년 %d월 %d일", selectedYear, selectedMonth + 1, selectedDay));
        }, year, month, day).show();
    }

    private void openTimePicker() {
        Calendar now = Calendar.getInstance();
        Integer savedHour = parseInt(editHour.getText().toString());
        Integer savedMinute = parseInt(editMinute.getText().toString());
        int hour = savedHour == null ? now.get(Calendar.HOUR_OF_DAY) : savedHour;
        int minute = savedMinute == null ? 0 : savedMinute;
        new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            editHour.setText(String.valueOf(selectedHour));
            editMinute.setText(String.valueOf(selectedMinute));
            btnPickTime.setText(String.format(Locale.KOREA, "%02d:%02d", selectedHour, selectedMinute));
        }, hour, minute, true).show();
    }

    private void resetAll() {
        editSurname.setText("");
        editPreferredName.setText("");
        editYear.setText("");
        editMonth.setText("");
        editDay.setText("");
        editHour.setText("");
        editMinute.setText("");
        btnPickDate.setText("생년월일 선택");
        btnPickTime.setText("태어난 시간 선택");
        spinnerCalendar.setSelection(0);
        spinnerGender.setSelection(2);
        selectedSurnameHanja = null;
        selectedSurnameReading = "";
        chipSelectedSurname.setText("선택된 성 한자: 없음");
        textGenderFilterStatus.setText("성별 구분 없이 추천되었습니다");
        layoutCandidates.removeAllViews();
    }

    private void renderGroup(LayoutInflater inflater, List<NameCandidate> list, int minScore, int maxScoreExclusive, String title, int maxCount) {
        int rendered = 0;
        TextView section = new TextView(this);
        section.setText(title);
        section.setTextSize(16f);
        section.setTextColor(ContextCompat.getColor(this, R.color.deep_navy));
        section.setPadding(0, 14, 0, 8);
        layoutCandidates.addView(section);

        for (NameCandidate c : list) {
            if (c.score < minScore || c.score >= maxScoreExclusive) {
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
