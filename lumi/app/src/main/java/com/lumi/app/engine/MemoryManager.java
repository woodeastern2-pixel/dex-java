package com.lumi.app.engine;

import com.lumi.app.data.CharacterDao;
import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.MemoryEntry;

import java.util.ArrayList;
import java.util.List;

public class MemoryManager {
    public List<MemoryEntry> captureMemories(CharacterDao dao, CharacterStateEntity state, String userMessage, long timestamp) {
        List<MemoryEntry> captured = new ArrayList<>();

        // ── 이름 ─────────────────────────────────────────────────────────────
        for (String marker : new String[]{"내 이름은", "나는", "저는", "제 이름은"}) {
            String name = extractPreference(userMessage, marker);
            if (name != null && name.length() <= 10) {
                maybeStore(dao, captured, name, "기본 정보", "이름", 4, timestamp);
                break;
            }
        }

        // ── 나이 / 직업 ───────────────────────────────────────────────────────
        for (String marker : new String[]{"살이야", "살이에요", "살이야 나", "나이는", "나이가"}) {
            String age = extractBefore(userMessage, marker, 3);
            if (age != null) {
                maybeStore(dao, captured, age + "세", "기본 정보", "나이", 3, timestamp);
                break;
            }
        }
        for (String marker : new String[]{"직업은", "직업이", "일은", "하는 일은"}) {
            String job = extractPreference(userMessage, marker);
            if (job != null) {
                maybeStore(dao, captured, job, "기본 정보", "직업", 3, timestamp);
                break;
            }
        }

        // ── 음식 취향 ─────────────────────────────────────────────────────────
        for (String marker : new String[]{"좋아하는 음식", "좋아하는 음식은", "좋아하는 음식이"}) {
            String v = extractPreference(userMessage, marker);
            if (v != null) { maybeStore(dao, captured, v, "음식", "좋아하는 음식", 3, timestamp); break; }
        }
        // "피자 좋아해", "라면이 좋아" 형태
        String foodLike = extractObjectBefore(userMessage, new String[]{"좋아해", "좋아함", "좋아요", "맛있어", "즐겨 먹어", "즐겨먹어"},
                new String[]{"피자", "라면", "치킨", "파스타", "스시", "초밥", "떡볶이", "김밥", "비빔밥", "삼겹살", "삼계탕",
                        "카레", "햄버거", "샌드위치", "샐러드", "케이크", "아이스크림", "초콜릿", "커피", "차", "주스"});
        if (foodLike != null) maybeStore(dao, captured, foodLike, "음식", "좋아하는 음식", 2, timestamp);

        // ── 음악 취향 ──────────────────────────────────────────────────────────
        for (String marker : new String[]{"좋아하는 음악", "즐겨 듣는", "즐겨듣는", "좋아하는 장르"}) {
            String v = extractPreference(userMessage, marker);
            if (v != null) { maybeStore(dao, captured, v, "취향", "좋아하는 음악", 2, timestamp); break; }
        }

        // ── 색 / 분위기 ───────────────────────────────────────────────────────
        for (String marker : new String[]{"좋아하는 색", "좋아하는 색깔"}) {
            String v = extractPreference(userMessage, marker);
            if (v != null) { maybeStore(dao, captured, v, "취향", "좋아하는 색", 2, timestamp); break; }
        }

        // ── 취미 ──────────────────────────────────────────────────────────────
        for (String marker : new String[]{"취미는", "취미가", "취미는", "취미로", "즐기는 건", "즐기는 게", "좋아하는 건", "좋아하는 게"}) {
            String v = extractPreference(userMessage, marker);
            if (v != null) { maybeStore(dao, captured, v, "취미", "취미", 3, timestamp); break; }
        }
        // "독서해", "게임해", "운동해" 같은 자동 탐지
        String hobbyLike = extractObjectBefore(userMessage, new String[]{"해", "해요", "좋아해", "즐겨"},
                new String[]{"독서", "게임", "운동", "요리", "그림", "피아노", "기타", "드럼", "수영", "등산", "낚시",
                        "사진", "영화", "드라마", "여행", "캠핑", "쇼핑", "유튜브", "노래", "춤"});
        if (hobbyLike != null) maybeStore(dao, captured, hobbyLike, "취미", "취미", 2, timestamp);

        // ── 호칭 ──────────────────────────────────────────────────────────────
        String nickname = extractNickname(userMessage);
        if (nickname != null) {
            state.preferredNickname = nickname;
            maybeStore(dao, captured, nickname, "호칭", "선호 호칭", 4, timestamp);
        }

        return captured;
    }

    /** text에서 keywords 중 하나 직전에 나타나는 targetWords 중 하나를 반환 */
    private String extractObjectBefore(String text, String[] keywords, String[] targetWords) {
        if (text == null) return null;
        for (String tw : targetWords) {
            for (String kw : keywords) {
                String pattern = tw + kw;
                if (text.contains(pattern)) return tw;
                // "tw이 kw" / "tw을 kw" 형태
                for (String particle : new String[]{"이 ", "을 ", "가 ", "를 ", "은 ", "은", "이", "을", "가", "를"}) {
                    if (text.contains(tw + particle + kw)) return tw;
                }
            }
        }
        return null;
    }

    /** marker 직전 숫자를 최대 maxLen 자 추출 (나이 등) */
    private String extractBefore(String text, String marker, int maxLen) {
        if (text == null) return null;
        int idx = text.indexOf(marker);
        if (idx <= 0) return null;
        String before = text.substring(0, idx).trim();
        // 마지막 연속 숫자
        int end = before.length();
        int start = end;
        while (start > 0 && Character.isDigit(before.charAt(start - 1))) start--;
        if (start == end) return null;
        String num = before.substring(start, end);
        return num.length() <= maxLen ? num : null;
    }

    private void maybeStore(CharacterDao dao,
                            List<MemoryEntry> captured,
                            String detail,
                            String category,
                            String keyword,
                            int importance,
                            long timestamp) {
        if (detail == null || detail.isEmpty()) {
            return;
        }
        MemoryEntry existing = dao.findMemory(keyword);
        if (existing == null) {
            MemoryEntry entry = new MemoryEntry(keyword, detail, category, importance, timestamp);
            entry.id = dao.insertMemory(entry);
            captured.add(entry);
            return;
        }
        existing.detail = detail;
        existing.category = category;
        existing.importance = Math.min(10, existing.importance + importance);
        existing.timestamp = timestamp;
        dao.updateMemory(existing);
        captured.add(existing);
    }

    private String extractPreference(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String tail = text.substring(index + marker.length()).trim();
        tail = tail.replaceFirst("^(은|는|이|가|을|를|:)", "").trim();
        if (tail.startsWith(" ")) {
            tail = tail.trim();
        }
        return cleanTail(tail);
    }

    private String extractNickname(String text) {
        if (text.contains("불러줘") || text.contains("별명")) {
            String extracted = text;
            extracted = extracted.replace("라고", " ");
            extracted = extracted.replace("으로", " ");
            extracted = extracted.replace("불러줘", " ");
            extracted = extracted.replace("별명은", " ");
            extracted = extracted.replace("별명", " ");
            extracted = cleanTail(extracted);
            if (extracted != null && extracted.length() <= 12) {
                return extracted;
            }
        }
        return null;
    }

    private String cleanTail(String tail) {
        if (tail == null) {
            return null;
        }
        String cleaned = tail.replace("?", "")
                .replace("!", "")
                .replace(".", "")
                .replace(",", "")
                .trim();
        if (cleaned.endsWith("야") || cleaned.endsWith("예요") || cleaned.endsWith("이에요") || cleaned.endsWith("입니다")) {
            cleaned = cleaned.replaceFirst("(야|예요|이에요|입니다)$", "").trim();
        }
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}