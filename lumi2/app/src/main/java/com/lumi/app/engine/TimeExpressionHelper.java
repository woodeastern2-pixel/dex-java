package com.lumi.app.engine;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

public final class TimeExpressionHelper {

    public enum Bucket {
        DAWN,
        MORNING,
        LUNCH,
        AFTERNOON,
        EVENING,
        NIGHT
    }

    private static final TimeZone SEOUL_TZ = TimeZone.getTimeZone("Asia/Seoul");

    private TimeExpressionHelper() {
    }

    public static Bucket currentBucket() {
        int hour = Calendar.getInstance(SEOUL_TZ).get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return Bucket.DAWN;
        if (hour < 11) return Bucket.MORNING;
        if (hour < 14) return Bucket.LUNCH;
        if (hour < 18) return Bucket.AFTERNOON;
        if (hour < 22) return Bucket.EVENING;
        return Bucket.NIGHT;
    }

    public static String currentBucketKo() {
        switch (currentBucket()) {
            case DAWN:
                return "새벽";
            case MORNING:
                return "오전";
            case LUNCH:
                return "점심 무렵";
            case AFTERNOON:
                return "오후";
            case EVENING:
                return "저녁";
            case NIGHT:
            default:
                return "밤";
        }
    }

    public static String currentDateKo() {
        return formatNow("yyyy년 M월 d일 EEEE");
    }

    public static String currentTimeKo() {
        return formatNow("HH:mm");
    }

    public static String currentDateTimeKo() {
        return formatNow("yyyy년 M월 d일 EEEE HH:mm");
    }

    public static String currentPromptContextKo() {
        return "Asia/Seoul 기준 " + currentDateTimeKo() + " (" + currentBucketKo() + ")";
    }

    private static String formatNow(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.KOREA);
        format.setTimeZone(SEOUL_TZ);
        return format.format(new Date(System.currentTimeMillis()));
    }

    public static String randomGreeting(boolean closeStyle) {
        Random r = new Random();
        String[] pool;
        switch (currentBucket()) {
            case MORNING:
                pool = closeStyle
                        ? new String[] {"좋은 아침이야.", "아침부터 생각났어.", "오늘 하루는 어떻게 시작하고 있어?"}
                        : new String[] {"좋은 아침이에요.", "아침부터 생각났어요.", "오늘 하루는 어떻게 시작하고 있어요?"};
                break;
            case LUNCH:
                pool = closeStyle
                        ? new String[] {"벌써 점심시간이네.", "점심은 챙겼어?", "한낮인데 잠깐 쉬어가도 좋아."}
                        : new String[] {"벌써 점심시간이네요.", "점심은 챙겼어요?", "한낮인데 잠깐 쉬어가도 좋아요."};
                break;
            case AFTERNOON:
                pool = closeStyle
                        ? new String[] {"벌써 오후네.", "오후가 되니까 조금 나른하지 않아?", "오후엔 템포를 조금만 낮춰도 좋아."}
                        : new String[] {"벌써 오후네요.", "오후가 되니까 조금 나른하지 않아요?", "오후엔 템포를 조금만 낮춰도 좋아요."};
                break;
            case EVENING:
                pool = closeStyle
                        ? new String[] {"벌써 저녁이야.", "오늘 하루는 어땠어?", "저녁이 되니까 조금 조용해진 느낌이야."}
                        : new String[] {"벌써 저녁이에요.", "오늘 하루는 어땠어요?", "저녁이 되니까 조금 조용해진 느낌이에요."};
                break;
            case NIGHT:
                pool = closeStyle
                        ? new String[] {"이제 밤이 깊어가네.", "오늘도 고생 많았어.", "조용한 밤이야. 무슨 생각하고 있었어?"}
                        : new String[] {"이제 밤이 깊어가네요.", "오늘도 고생 많았어요.", "조용한 밤이에요. 무슨 생각하고 있었어요?"};
                break;
            case DAWN:
            default:
                pool = closeStyle
                        ? new String[] {"아직 깨어 있네.", "새벽에는 생각이 많아지지.", "잠이 오지 않아?"}
                        : new String[] {"아직 깨어 있네요.", "새벽에는 생각이 많아지죠.", "잠이 오지 않나요?"};
                break;
        }
        return pool[r.nextInt(pool.length)];
    }
}