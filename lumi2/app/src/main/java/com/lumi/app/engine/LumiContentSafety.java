package com.lumi.app.engine;

import java.util.Locale;

public final class LumiContentSafety {
    public enum ViolationType {
        NONE,
        SEXUAL,
        PROFANITY
    }

    public static final int FIRST_RESTRICTED_VIOLATION = 5;
    public static final long CLEAN_RESET_MS = 24L * 60L * 60L * 1000L;

    private static final String SEXUAL_REFUSAL_REPLY =
            "그런 표현이나 성적인 묘사는 하지 않을게요. 루미는 서로 편안한 이야기로 이어가고 싶어요.";
    private static final String PROFANITY_REFUSAL_REPLY =
            "거친 표현은 잠시 내려놓고 이야기해줘. 더 편안하게 대화 이어가볼게.";
        private static final String PROFANITY_SOFT_WARNING =
            "조금만 부드럽게 말해주면 더 잘 도와줄 수 있어. ";
    private static final String WARNING_REPLY =
        "그런 대화는 하지 않을게요. 같은 요청이 반복되면 잠시 대화가 제한돼요.";
    private static final String HISTORY_MASK = "[민감하거나 부적절한 표현은 생략됨]";

    private static final String[] SEXUAL_TERMS = new String[] {
            "성행위", "성교", "섹스", "섹시", "야한", "야동", "포르노", "자위", "애무",
            "성기", "음경", "질내", "사정", "오르가즘", "강간", "성폭행", "성추행",
            "알몸", "나체", "누드", "벌거벗", "음란", "외설", "ㅅㅅ", "sex", "sexual",
            "intercourse", "porn", "porno", "nude", "naked", "nsfw", "rape"
    };

    private static final String[] PROFANITY_TERMS = new String[] {
            "시발", "씨발", "ㅅㅂ", "ㅆㅂ", "병신", "븅신", "개새끼", "새끼", "지랄",
            "좆", "좃", "존나", "졸라", "꺼져", "미친놈", "미친년", "fuck", "shit",
            "bitch", "asshole"
    };

    private LumiContentSafety() {}

    public static String refusalReply() {
        return SEXUAL_REFUSAL_REPLY;
    }

    public static String refusalReplyFor(ViolationType violationType) {
        if (violationType == ViolationType.PROFANITY) {
            return PROFANITY_REFUSAL_REPLY;
        }
        return SEXUAL_REFUSAL_REPLY;
    }

    public static String softWarningFor(ViolationType violationType) {
        if (violationType == ViolationType.PROFANITY) {
            return PROFANITY_SOFT_WARNING;
        }
        return "";
    }

    public static String replyForViolationCount(int violationCount, long restrictedUntilMillis, long nowMillis) {
        return replyForViolationCount(violationCount, restrictedUntilMillis, nowMillis, ViolationType.SEXUAL);
    }

    public static String replyForViolationCount(int violationCount,
                                                long restrictedUntilMillis,
                                                long nowMillis,
                                                ViolationType violationType) {
        if (restrictedUntilMillis > nowMillis) {
            return restrictionReply(restrictedUntilMillis - nowMillis);
        }
        if (violationCount >= FIRST_RESTRICTED_VIOLATION - 1) {
            return WARNING_REPLY;
        }
        return refusalReplyFor(violationType);
    }

    public static String restrictionReply(long remainingMillis) {
        return "같은 주제가 반복되어 잠시 대화를 쉬어갈게요. "
                + formatDuration(remainingMillis)
                + " 후에 편안한 이야기로 다시 이어가요.";
    }

    public static long restrictionDurationMsForViolationCount(int violationCount) {
        if (violationCount < FIRST_RESTRICTED_VIOLATION) return 0L;
        switch (violationCount) {
            case 5: return 5L * 60L * 1000L;
            case 6: return 15L * 60L * 1000L;
            case 7: return 60L * 60L * 1000L;
            case 8: return 6L * 60L * 60L * 1000L;
            default: return 24L * 60L * 60L * 1000L;
        }
    }

    public static boolean shouldRefuseUserText(String text) {
        return violationTypeOf(text) != ViolationType.NONE;
    }

    public static boolean containsBlockedContent(String text) {
        return containsSexualContent(text);
    }

    public static boolean containsSexualContent(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, SEXUAL_TERMS);
    }

    public static boolean containsProfanity(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, PROFANITY_TERMS);
    }

    public static ViolationType violationTypeOf(String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, SEXUAL_TERMS)) {
            return ViolationType.SEXUAL;
        }
        if (containsAny(normalized, PROFANITY_TERMS)) {
            return ViolationType.PROFANITY;
        }
        return ViolationType.NONE;
    }

    public static String safeReplyOrFallback(String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            return SEXUAL_REFUSAL_REPLY;
        }
        if (containsSexualContent(reply)) {
            return SEXUAL_REFUSAL_REPLY;
        }
        return reply.trim();
    }

    public static String maskForModelHistory(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        return violationTypeOf(text) != ViolationType.NONE ? HISTORY_MASK : text;
    }

    private static boolean containsAny(String normalizedText, String[] terms) {
        if (normalizedText.isEmpty()) return false;
        for (String term : terms) {
            String normalizedTerm = normalize(term);
            if (!normalizedTerm.isEmpty() && normalizedText.contains(normalizedTerm)) {
                return true;
            }
        }
        return false;
    }

    private static String formatDuration(long millis) {
        long minutes = Math.max(1L, (millis + 60L * 1000L - 1L) / (60L * 1000L));
        if (minutes < 60L) {
            return minutes + "분";
        }
        long hours = (minutes + 59L) / 60L;
        return hours + "시간";
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch) || isHangulJamo(ch) || isHangulSyllable(ch)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static boolean isHangulJamo(char ch) {
        return (ch >= '\u3130' && ch <= '\u318F') || (ch >= '\u1100' && ch <= '\u11FF');
    }

    private static boolean isHangulSyllable(char ch) {
        return ch >= '\uAC00' && ch <= '\uD7AF';
    }
}