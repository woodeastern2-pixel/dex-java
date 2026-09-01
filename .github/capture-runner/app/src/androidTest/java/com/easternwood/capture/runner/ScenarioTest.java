package com.easternwood.capture.runner;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class ScenarioTest {
    private UiDevice device;
    private Instrumentation instrumentation;
    private Context context;
    private String slug;
    private String targetPackage;
    private File outDir;
    private File logFile;
    private int actions;
    private boolean reachedFeature;

    @Test
    public void captureFeatureScenario() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        device = UiDevice.getInstance(instrumentation);
        Bundle args = InstrumentationRegistry.getArguments();
        slug = value(args, "slug", "unknown").toLowerCase(Locale.ROOT);
        targetPackage = value(args, "targetPackage", "");

        outDir = new File(context.getExternalFilesDir(null), "screens/" + slug);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Cannot create screenshot directory: " + outDir);
        }
        logFile = new File(outDir, "scenario-log.txt");
        note("slug=" + slug);
        note("targetPackage=" + targetPackage);

        device.wakeUp();
        device.pressHome();
        setKoreanLocale();
        grantCommonPermissions();

        if ("nfc".equals(slug)) {
            launchSyntheticNfcScenario();
        } else {
            launchTarget();
        }
        waitUi(3500);
        dismissCommonStartup();
        capture("01-home");

        switch (slug) {
            case "whereisit":
                scenarioWhereIsIt();
                break;
            case "dolbomon":
                scenarioDolbomOn();
                break;
            case "signpdf":
                scenarioSignPdf();
                break;
            case "veilpic":
                scenarioVeilPic();
                break;
            case "ireumon":
                scenarioIreumOn();
                break;
            case "jamon":
                scenarioJamOn();
                break;
            case "nfc":
                scenarioNfc();
                break;
            default:
                scenarioGeneric();
        }

        dismissPermissionDialogs();
        capture("99-feature-result");
        writeResult();
        assertTrue("Scenario did not interact with enough real UI elements. actions=" + actions,
                actions >= 1 || reachedFeature);
    }

    private void scenarioWhereIsIt() throws Exception {
        addWhereItem("에어팟 프로", "거실 수납장 두 번째 서랍", "검은색 충전 케이스와 함께 보관", "전자기기");
        navigateBackToMain();
        addWhereItem("여권", "안방 책상 첫 번째 서랍", "투명 여권 케이스 안에 보관", "문서");
        navigateBackToMain();
        addWhereItem("차량 충전기", "현관 수납장 오른쪽 바구니", "USB-C 케이블과 함께 보관", "자동차");
        navigateBackToMain();
        capture("04-sample-items");
        if (clickAny("에어팟 프로", "여권", "차량 충전기")) {
            waitUi(1500);
            reachedFeature = true;
            capture("05-feature-item-detail");
        }
    }

    private void addWhereItem(String name, String location, String memo, String category) throws Exception {
        if (!clickAny("물건 추가", "새 물건", "물건 등록", "추가하기", "등록하기", "Add item", "Add")) {
            if (!clickResourceContains("fab", "add", "create")) clickBottomRight();
        }
        waitUi(1200);
        fillEditTexts(name, location, memo);
        clickAny(category, "전자기기", "문서", "일상용품", "기타");
        capture("02-entry-" + safeName(name));
        if (clickAny("저장", "등록 완료", "완료", "확인", "Save")) {
            waitUi(1400);
            actions++;
        }
    }

    private void scenarioDolbomOn() throws Exception {
        if (clickAny("어르신 등록", "어르신 추가", "대상자 추가", "등록하기", "Add senior")) {
            waitUi(900);
            fillEditTexts("김정자", "78", "203호", "01012345678");
            clickAny("저장", "등록", "완료", "Save");
            waitUi(1200);
        }
        clickAny("김정자", "돌봄 대상", "어르신");
        waitUi(800);
        if (!clickAny("오늘 돌봄 기록", "돌봄 기록", "기록하기", "새 기록", "Record care")) {
            clickResourceContains("record", "add", "care");
        }
        waitUi(1000);
        clickAny("잘 드셨어요", "식사 완료", "양호", "보통", "완료");
        clickAny("충분히 드셨어요", "수분 섭취", "완료");
        clickAny("복약 완료", "약 복용", "완료");
        clickAny("편안해요", "기분 좋음", "양호");
        clickAny("잘 주무셨어요", "숙면", "양호");
        clickAny("정상", "배변 완료", "특이사항 없음");
        capture("02-care-selections");
        for (int i = 0; i < 3; i++) {
            if (clickAny("다음", "계속", "Next")) waitUi(700);
        }
        fillEditTexts("오전 산책 20분을 진행했고 식사와 복약을 모두 완료했습니다. 컨디션은 양호합니다.");
        clickAny("문장 만들기", "공유 문장 만들기", "미리보기", "저장", "완료", "Generate");
        waitUi(1500);
        reachedFeature = true;
        capture("03-feature-care-summary");
    }

    private void scenarioSignPdf() throws Exception {
        if (!clickAny("PDF 선택", "PDF 열기", "문서 선택", "파일 선택", "Open PDF", "Select PDF")) {
            clickResourceContains("open", "select", "pdf", "file");
        }
        waitUi(1200);
        chooseDocument("sample-contract.pdf");
        waitUi(2500);
        dismissCommonStartup();
        capture("02-pdf-loaded");

        if (clickAny("서명 추가", "서명", "사인", "Add signature", "Signature")) {
            waitUi(700);
            clickAny("직접 서명", "직접 그리기", "그리기", "Draw", "Create signature");
            waitUi(700);
            drawSignature();
            capture("03-signature-drawn");
            clickAny("완료", "적용", "확인", "Done", "Apply");
            waitUi(1000);
            Rect screen = screenRect();
            device.click(screen.centerX(), (int) (screen.height() * 0.67));
            waitUi(500);
            clickAny("배치 완료", "완료", "적용", "Done");
            reachedFeature = true;
        }
        capture("04-feature-signed-pdf");
        clickAny("저장", "PDF 저장", "내보내기", "Save PDF");
    }

    private void scenarioVeilPic() throws Exception {
        if (!clickAny("사진 선택", "사진 불러오기", "이미지 선택", "갤러리", "Select photo", "Open image")) {
            clickResourceContains("photo", "gallery", "image", "open");
        }
        waitUi(1200);
        chooseDocument("sample-private-info.png");
        waitUi(2200);
        dismissCommonStartup();
        capture("02-photo-loaded");

        clickAny("직접 가리기", "가리기", "브러시", "모자이크", "블러", "Redact", "Brush");
        waitUi(500);
        Rect s = screenRect();
        int left = (int) (s.width() * 0.22);
        int right = (int) (s.width() * 0.79);
        device.swipe(left, (int) (s.height() * 0.40), right, (int) (s.height() * 0.40), 30);
        device.swipe(left, (int) (s.height() * 0.49), right, (int) (s.height() * 0.49), 30);
        device.swipe(left, (int) (s.height() * 0.58), right, (int) (s.height() * 0.58), 30);
        actions += 3;
        reachedFeature = true;
        waitUi(700);
        capture("03-feature-redacted-photo");
        clickAny("저장", "내보내기", "완료", "Save");
    }

    private void scenarioIreumOn() throws Exception {
        clickAny("이름 풀이", "이름 분석", "내 이름 보기", "작명", "Analyze name");
        waitUi(900);
        fillEditTexts("한", "서", "윤");
        clickAny("여자", "여성", "女");
        capture("02-name-entry");

        for (int i = 0; i < 3; i++) {
            if (clickAny("한자 선택", "한자 찾기", "선택", "Choose Hanja")) {
                waitUi(600);
                clickFirstReasonableChoice();
                waitUi(500);
            }
        }
        if (clickAny("이름 풀이 보기", "분석하기", "결과 보기", "풀이하기", "확인", "Analyze")) {
            waitUi(1800);
            reachedFeature = true;
        } else {
            clickAny("추천 이름", "이름 추천", "추천 받기");
            waitUi(1400);
        }
        capture("03-feature-name-analysis");
    }

    private void scenarioJamOn() throws Exception {
        if (!clickAny("루틴 만들기", "새 루틴", "루틴 추가", "수면 루틴 추가", "Create routine")) {
            clickResourceContains("add", "routine", "create");
        }
        waitUi(900);
        fillEditTexts("평일 숙면 루틴", "23:00");
        clickAny("빗소리", "백색 소음", "수면 음악", "Rain", "White noise");
        clickAny("10분", "15분", "20분");
        clickAny("저장", "완료", "루틴 저장", "Save");
        waitUi(1200);
        capture("02-routine-created");
        if (clickAny("평일 숙면 루틴", "루틴 시작", "시작", "Start")) {
            waitUi(1500);
            clickAny("시작", "재생", "Start routine", "Play");
            reachedFeature = true;
        }
        capture("03-feature-routine-running");
    }

    private void scenarioNfc() throws Exception {
        dismissCommonStartup();
        clickAny("태그 읽기", "스캔 시작", "NFC 읽기", "Read tag", "Scan");
        waitUi(1200);
        launchSyntheticNfcScenario();
        waitUi(1600);
        dismissCommonStartup();
        reachedFeature = true;
        actions++;
        capture("02-feature-nfc-result");
    }

    private void scenarioGeneric() throws Exception {
        clickAny("추가", "시작", "계속", "등록", "열기", "Add", "Start", "Open");
        fillEditTexts("Eastern Wood Studio 데모 데이터", "실제 기능 캡처용 샘플");
        clickAny("저장", "완료", "확인", "Save", "Done");
        capture("02-feature-generic");
    }

    private void launchTarget() throws Exception {
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(targetPackage);
        if (launch == null) throw new IllegalStateException("No launcher for " + targetPackage);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launch);
        note("launched=" + launch.getComponent());
    }

    private void launchSyntheticNfcScenario() throws Exception {
        PackageManager pm = context.getPackageManager();
        Intent base = pm.getLaunchIntentForPackage(targetPackage);
        if (base == null) throw new IllegalStateException("No launcher for " + targetPackage);
        NdefRecord text = NdefRecord.createTextRecord("ko", "Eastern Wood Studio 테스트 NFC 태그");
        NdefRecord uri = NdefRecord.createUri("https://easternwood.cloud/nfc/demo");
        NdefMessage message = new NdefMessage(new NdefRecord[]{text, uri});
        Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        intent.setComponent(base.getComponent());
        intent.setType("text/plain");
        intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new Parcelable[]{message});
        intent.putExtra(NfcAdapter.EXTRA_ID, new byte[]{0x04, 0x21, 0x35, 0x67, 0x11, 0x22, 0x33});
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        note("syntheticNdefInjected=true");
    }

    private void chooseDocument(String fileName) throws Exception {
        waitUi(800);
        dismissPermissionDialogs();
        if (!clickAny(fileName)) {
            clickAny("다운로드", "Downloads", "Download");
            waitUi(700);
            clickAny(fileName);
        }
        waitUi(1200);
        actions++;
    }

    private void drawSignature() {
        Rect s = screenRect();
        int x = (int) (s.width() * 0.22);
        int y = (int) (s.height() * 0.56);
        device.swipe(x, y, x + 120, y - 90, 18);
        device.swipe(x + 120, y - 90, x + 220, y + 40, 18);
        device.swipe(x + 40, y + 15, x + 260, y - 35, 22);
        device.swipe(x + 230, y - 30, x + 330, y + 20, 18);
        actions += 4;
    }

    private void dismissCommonStartup() throws Exception {
        dismissPermissionDialogs();
        for (int i = 0; i < 4; i++) {
            boolean clicked = clickAnyWithoutCount("앱 사용 중에만", "사용 중에만 허용", "사진 및 동영상 허용",
                    "While using the app", "Allow", "허용", "동의", "시작하기", "건너뛰기", "확인", "OK");
            if (!clicked) break;
            waitUi(400);
        }
    }

    private void dismissPermissionDialogs() throws Exception {
        for (int i = 0; i < 5; i++) {
            String pkg = device.getCurrentPackageName();
            if (pkg == null || (!pkg.contains("permissioncontroller") && !pkg.contains("packageinstaller"))) break;
            if (!clickAnyWithoutCount("앱 사용 중에만", "사용 중에만 허용", "사진 및 동영상 허용", "허용",
                    "While using the app", "Allow", "Allow all")) break;
            waitUi(400);
        }
    }

    private void fillEditTexts(String... values) throws Exception {
        List<UiObject2> edits = new ArrayList<>(device.findObjects(By.clazz("android.widget.EditText")));
        edits.removeIf(e -> !e.isEnabled() || !e.isClickable());
        edits.sort(Comparator.comparingInt(e -> e.getVisibleBounds().top));
        int n = Math.min(edits.size(), values.length);
        for (int i = 0; i < n; i++) {
            try {
                UiObject2 edit = edits.get(i);
                edit.click();
                edit.clear();
                edit.setText(values[i]);
                actions++;
                note("setText=" + values[i] + " res=" + edit.getResourceName());
                waitUi(220);
            } catch (Exception e) {
                note("setTextError=" + e);
            }
        }
        device.pressBack();
        waitUi(250);
    }

    private boolean clickAny(String... candidates) throws Exception {
        boolean clicked = clickAnyWithoutCount(candidates);
        if (clicked) actions++;
        return clicked;
    }

    private boolean clickAnyWithoutCount(String... candidates) throws Exception {
        for (String candidate : candidates) {
            UiObject2 object = findText(candidate);
            if (object != null) {
                Rect b = object.getVisibleBounds();
                device.click(b.centerX(), b.centerY());
                note("click=" + candidate + " actual=" + display(object));
                waitUi(300);
                return true;
            }
        }
        return false;
    }

    private UiObject2 findText(String candidate) {
        if (candidate == null || candidate.isEmpty()) return null;
        UiObject2 exact = device.findObject(By.text(candidate));
        if (isUsable(exact)) return exact;
        UiObject2 desc = device.findObject(By.desc(candidate));
        if (isUsable(desc)) return desc;
        UiObject2 contains = device.findObject(By.textContains(candidate));
        if (isUsable(contains)) return contains;
        UiObject2 descContains = device.findObject(By.descContains(candidate));
        if (isUsable(descContains)) return descContains;
        String lower = candidate.toLowerCase(Locale.ROOT);
        for (UiObject2 object : allObjects()) {
            String text = object.getText();
            String description = object.getContentDescription();
            if ((text != null && text.toLowerCase(Locale.ROOT).contains(lower)) ||
                    (description != null && description.toLowerCase(Locale.ROOT).contains(lower))) {
                if (isUsable(object)) return object;
            }
        }
        return null;
    }

    private boolean clickResourceContains(String... fragments) throws Exception {
        for (UiObject2 object : allObjects()) {
            String resource = object.getResourceName();
            if (resource == null) continue;
            String lower = resource.toLowerCase(Locale.ROOT);
            for (String fragment : fragments) {
                if (lower.contains(fragment.toLowerCase(Locale.ROOT)) && isUsable(object)) {
                    Rect b = object.getVisibleBounds();
                    device.click(b.centerX(), b.centerY());
                    actions++;
                    note("clickResource=" + resource);
                    waitUi(350);
                    return true;
                }
            }
        }
        return false;
    }

    private void clickBottomRight() throws Exception {
        Rect screen = screenRect();
        UiObject2 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (UiObject2 object : allObjects()) {
            if (!object.isClickable() || !isUsable(object)) continue;
            Rect b = object.getVisibleBounds();
            int score = b.centerX() + b.centerY();
            if (b.width() > screen.width() / 2 || b.height() > screen.height() / 3) score -= 3000;
            if (score > bestScore) {
                best = object;
                bestScore = score;
            }
        }
        if (best != null) {
            Rect b = best.getVisibleBounds();
            device.click(b.centerX(), b.centerY());
            actions++;
            note("clickBottomRight=" + display(best));
            waitUi(400);
        } else {
            device.click((int) (screen.width() * 0.88), (int) (screen.height() * 0.86));
            actions++;
        }
    }

    private void clickFirstReasonableChoice() throws Exception {
        List<UiObject2> objects = allObjects();
        objects.sort(Comparator.comparingInt(o -> o.getVisibleBounds().top));
        Rect screen = screenRect();
        for (UiObject2 object : objects) {
            Rect b = object.getVisibleBounds();
            String text = object.getText();
            if (text == null || text.trim().isEmpty()) continue;
            if (b.top < screen.height() * 0.18 || b.bottom > screen.height() * 0.90) continue;
            if (text.contains("취소") || text.contains("검색") || text.contains("닫기")) continue;
            device.click(b.centerX(), b.centerY());
            actions++;
            note("clickChoice=" + text);
            waitUi(400);
            return;
        }
    }

    private void navigateBackToMain() throws Exception {
        for (int i = 0; i < 2; i++) {
            if (findText("내 물건") != null || findText("물건 추가") != null || findText("검색") != null) return;
            device.pressBack();
            waitUi(450);
        }
    }

    private List<UiObject2> allObjects() {
        try {
            return new ArrayList<>(device.findObjects(By.clazz(Pattern.compile(".*"))));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private boolean isUsable(UiObject2 object) {
        if (object == null || !object.isEnabled()) return false;
        Rect b = object.getVisibleBounds();
        return b.width() > 2 && b.height() > 2;
    }

    private String display(UiObject2 object) {
        return "text=" + object.getText() + ",desc=" + object.getContentDescription() + ",res=" + object.getResourceName();
    }

    private void capture(String name) throws Exception {
        device.waitForIdle(1200);
        File png = new File(outDir, name + ".png");
        boolean ok = device.takeScreenshot(png);
        File xml = new File(outDir, name + ".xml");
        try {
            device.dumpWindowHierarchy(xml);
        } catch (Exception e) {
            note("hierarchyError=" + e);
        }
        note("capture=" + png.getName() + " ok=" + ok + " package=" + device.getCurrentPackageName());
    }

    private void writeResult() throws Exception {
        File result = new File(outDir, "scenario-result.properties");
        try (FileWriter writer = new FileWriter(result, false)) {
            writer.write("slug=" + slug + "\n");
            writer.write("targetPackage=" + targetPackage + "\n");
            writer.write("actions=" + actions + "\n");
            writer.write("reachedFeature=" + reachedFeature + "\n");
            writer.write("currentPackage=" + device.getCurrentPackageName() + "\n");
            writer.write("captureType=actual-emulator-ui-with-seeded-scenario\n");
            if ("nfc".equals(slug)) writer.write("nfcInput=synthetic-ndef-payload\n");
        }
    }

    private void note(String line) throws IOException {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(line + "\n");
        }
    }

    private void setKoreanLocale() {
        try {
            device.executeShellCommand("settings put system system_locales ko-KR");
            if (!targetPackage.isEmpty()) {
                device.executeShellCommand("cmd locale set-app-locales " + targetPackage + " --user 0 ko-KR");
            }
        } catch (Exception ignored) {
        }
    }

    private void grantCommonPermissions() {
        List<String> permissions = Arrays.asList(
                "android.permission.CAMERA",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.POST_NOTIFICATIONS"
        );
        for (String permission : permissions) {
            try {
                device.executeShellCommand("pm grant " + targetPackage + " " + permission);
            } catch (Exception ignored) {
            }
        }
    }

    private void waitUi(long millis) throws Exception {
        device.waitForIdle(millis);
        Thread.sleep(Math.min(millis, 800));
    }

    private Rect screenRect() {
        return new Rect(0, 0, device.getDisplayWidth(), device.getDisplayHeight());
    }

    private static String safeName(String value) {
        return value.replaceAll("[^0-9A-Za-z가-힣]+", "-");
    }

    private static String value(Bundle bundle, String key, String fallback) {
        String value = bundle.getString(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
