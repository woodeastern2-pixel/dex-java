package com.ireumgil.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ireumgil.R;
import com.ireumgil.data.HanjaRepository;
import com.ireumgil.data.RecentResultStore;
import com.ireumgil.engine.NameRecommendationService;
import com.ireumgil.model.HanjaCharacter;
import com.ireumgil.model.NameFortuneReport;
import com.ireumgil.model.SajuInput;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class CheckNameFortuneActivity extends AppCompatActivity {

    private Button btnPickSurname;
    private Button btnPickFirst;
    private Button btnPickSecond;
    private Button btnPickDate;
    private Button btnPickTime;
    private TextView chipSelected;
    private TextView textAnalysisGuide;
    private Spinner spinnerCalendar;
    private Spinner spinnerGender;
    private ScrollView scrollView;
    private ResultReportView resultReportView;

    private HanjaCharacter surname;
    private HanjaCharacter first;
    private HanjaCharacter second;
    private Integer birthYear;
    private Integer birthMonth;
    private Integer birthDay;
    private Integer birthHour;
    private Integer birthMinute;

    private NameRecommendationService service;
    private RecentResultStore recentStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_name_fortune);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("이름 풀이");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        recentStore = new RecentResultStore();

        btnPickSurname = findViewById(R.id.btnPickSurname);
        btnPickFirst = findViewById(R.id.btnPickFirst);
        btnPickSecond = findViewById(R.id.btnPickSecond);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnPickTime = findViewById(R.id.btnPickTime);
        chipSelected = findViewById(R.id.chipSelectedHanja);
        textAnalysisGuide = findViewById(R.id.textAnalysisGuide);
        spinnerCalendar = findViewById(R.id.spinnerCalendar);
        spinnerGender = findViewById(R.id.spinnerGender);
        scrollView = findViewById(R.id.scrollNameAnalysis);
        resultReportView = findViewById(R.id.resultReportView);

        spinnerCalendar.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("양력", "음력")));
        spinnerGender.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("성별 선택", "남자", "여자")));

        btnPickSurname.setOnClickListener(v -> openHanjaPicker(1));
        btnPickFirst.setOnClickListener(v -> openHanjaPicker(2));
        btnPickSecond.setOnClickListener(v -> openHanjaPicker(3));
        btnPickDate.setOnClickListener(v -> openDatePicker());
        btnPickTime.setOnClickListener(v -> openTimePicker());
        findViewById(R.id.btnAnalyze).setOnClickListener(v -> analyze());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetAll());

        updateNameSelection();
        resultReportView.clear();
    }

    private void openHanjaPicker(int slot) {
        boolean surnameMode = slot == 1;
        String title = slot == 1 ? "성 한자 선택" : slot == 2 ? "이름 첫 글자 선택" : "이름 둘째 글자 선택";
        HanjaCharacter current = slot == 1 ? surname : slot == 2 ? first : second;
        String prefill = current == null ? "" : current.reading;

        HanjaPickerDialog dialog = HanjaPickerDialog.newInstance(title, surnameMode, prefill);
        dialog.setOnHanjaSelectedListener(character -> {
            if (slot == 1) {
                surname = character;
            } else if (slot == 2) {
                first = character;
            } else {
                second = character;
            }
            updateNameSelection();
        });
        dialog.show(getSupportFragmentManager(), "nameAnalysisPicker" + slot);
    }

    private void updateNameSelection() {
        btnPickSurname.setText(buttonLabel("성 한자 선택", surname));
        btnPickFirst.setText(buttonLabel("이름 첫 글자 한자 선택", first));
        btnPickSecond.setText(buttonLabel("이름 둘째 글자 한자 선택", second));
        if (surname == null || first == null || second == null) {
            chipSelected.setText("성명 한자 3글자를 차례로 선택해 주세요.");
        } else {
            chipSelected.setText("선택한 이름  " + surname.character + first.character + second.character
                    + "  ·  " + surname.reading + first.reading + second.reading);
        }
    }

    private String buttonLabel(String emptyLabel, HanjaCharacter character) {
        if (character == null) {
            return emptyLabel;
        }
        String meaning = character.meaning == null || character.meaning.trim().isEmpty()
                ? "뜻 정보 없음"
                : character.meaning;
        return character.character + "(" + character.reading + ")  ·  " + meaning;
    }

    private void openDatePicker() {
        Calendar now = Calendar.getInstance();
        int year = birthYear == null ? now.get(Calendar.YEAR) : birthYear;
        int month = birthMonth == null ? now.get(Calendar.MONTH) : birthMonth - 1;
        int day = birthDay == null ? now.get(Calendar.DAY_OF_MONTH) : birthDay;
        new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            birthYear = selectedYear;
            birthMonth = selectedMonth + 1;
            birthDay = selectedDay;
            btnPickDate.setText(String.format(Locale.KOREA, "%d년 %d월 %d일", birthYear, birthMonth, birthDay));
        }, year, month, day).show();
    }

    private void openTimePicker() {
        Calendar now = Calendar.getInstance();
        int hour = birthHour == null ? now.get(Calendar.HOUR_OF_DAY) : birthHour;
        int minute = birthMinute == null ? 0 : birthMinute;
        new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            birthHour = selectedHour;
            birthMinute = selectedMinute;
            btnPickTime.setText(String.format(Locale.KOREA, "%02d:%02d", birthHour, birthMinute));
        }, hour, minute, true).show();
    }

    private void analyze() {
        if (surname == null || first == null || second == null) {
            Toast.makeText(this, "성명 한자 3글자를 모두 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (birthYear == null || birthMonth == null || birthDay == null) {
            Toast.makeText(this, "생년월일을 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (birthHour == null || birthMinute == null) {
            Toast.makeText(this, "태어난 시간을 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean lunar = "음력".equals(spinnerCalendar.getSelectedItem().toString());
        String gender = spinnerGender.getSelectedItem().toString();
        SajuInput saju = new SajuInput(
                birthYear,
                birthMonth,
                birthDay,
                birthHour,
                birthMinute,
                lunar,
                gender
        );
        NameFortuneReport report = service.buildFortuneReport(surname.reading, surname, first, second, saju);
        resultReportView.render(report);
        textAnalysisGuide.setText("풀이가 완료되었습니다. 아래 결과를 확인해 주세요.");
        recentStore.save(this, "풀이: " + report.fullName + "(" + report.score + "점)");
        resultReportView.post(() -> scrollView.smoothScrollTo(0, resultReportView.getTop()));
    }

    private void resetAll() {
        surname = null;
        first = null;
        second = null;
        birthYear = null;
        birthMonth = null;
        birthDay = null;
        birthHour = null;
        birthMinute = null;
        btnPickDate.setText("생년월일 선택");
        btnPickTime.setText("태어난 시간 선택");
        spinnerCalendar.setSelection(0);
        spinnerGender.setSelection(0);
        textAnalysisGuide.setText("위 정보를 모두 선택하면 풀이 결과가 이곳에 표시됩니다.");
        updateNameSelection();
        resultReportView.clear();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
