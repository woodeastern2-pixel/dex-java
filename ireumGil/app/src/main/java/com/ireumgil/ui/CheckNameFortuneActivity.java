package com.ireumgil.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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

public class CheckNameFortuneActivity extends AppCompatActivity {

    private TextView chipSelected;
    private EditText editYear;
    private EditText editMonth;
    private EditText editDay;
    private EditText editHour;
    private EditText editMinute;
    private ResultReportView resultReportView;

    private HanjaCharacter surname;
    private HanjaCharacter first;
    private HanjaCharacter second;

    private NameRecommendationService service;
    private RecentResultStore recentStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_name_fortune);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("이름 운세 확인");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        service = new NameRecommendationService(new HanjaRepository(getApplicationContext()));
        recentStore = new RecentResultStore();

        chipSelected = findViewById(R.id.chipSelectedHanja);
        editYear = findViewById(R.id.editYear);
        editMonth = findViewById(R.id.editMonth);
        editDay = findViewById(R.id.editDay);
        editHour = findViewById(R.id.editHour);
        editMinute = findViewById(R.id.editMinute);
        resultReportView = findViewById(R.id.resultReportView);

        Button btnPickFullName = findViewById(R.id.btnPickFullName);
        Button btnAnalyze = findViewById(R.id.btnAnalyze);
        Button btnReset = findViewById(R.id.btnReset);

        btnPickFullName.setOnClickListener(v -> openFullNamePicker());
        btnAnalyze.setOnClickListener(v -> analyze());
        btnReset.setOnClickListener(v -> resetAll());

        updateChip();
        resultReportView.clear();
    }

    private void openFullNamePicker() {
        FullNameHanjaPickerDialog dialog = FullNameHanjaPickerDialog.newInstance(surname, first, second);
        dialog.setOnFullNameSelectedListener((pickedSurname, pickedFirst, pickedSecond) -> {
            surname = pickedSurname;
            first = pickedFirst;
            second = pickedSecond;
            updateChip();
        });
        dialog.show(getSupportFragmentManager(), "fullNamePicker");
    }

    private void updateChip() {
        if (surname == null || first == null || second == null) {
            chipSelected.setText("아직 선택된 한자가 없습니다");
            return;
        }
        chipSelected.setText("선택된 이름: " + surname.character + " " + first.character + " " + second.character);
    }

    private void analyze() {
        if (surname == null || first == null || second == null) {
            Toast.makeText(this, "한자 이름 선택하기에서 성/이름 한자를 모두 선택해 주세요.", Toast.LENGTH_SHORT).show();
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

        SajuInput saju = new SajuInput(year, month, day, hour, minute, false, "선택 안 함");
        NameFortuneReport report = service.buildFortuneReport("성", surname, first, second, saju);
        resultReportView.render(report);
        recentStore.save(this, "운세: " + report.fullName + "(" + report.score + "점)");
    }

    private void resetAll() {
        surname = null;
        first = null;
        second = null;
        editYear.setText("");
        editMonth.setText("");
        editDay.setText("");
        editHour.setText("");
        editMinute.setText("");
        updateChip();
        resultReportView.clear();
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
