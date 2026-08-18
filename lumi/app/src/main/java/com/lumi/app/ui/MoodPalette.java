package com.lumi.app.ui;

import android.content.Context;

import com.lumi.app.R;

import java.util.Locale;
import java.util.Random;

public final class MoodPalette {
    private MoodPalette() {}

    public static String label(String mood) {
        if (mood == null) return "잔잔함";
        switch (mood) {
            case "happy":
            case "joyful": return "기쁨";
            case "sad": return "슬픔";
            case "angry": return "분노";
            case "fearful": return "두려움";
            case "surprised": return "놀람";
            case "disgusted": return "혐오";
            case "anticipating": return "기대";
            case "peaceful": return "평온함";
            case "curious": return "호기심";
            case "sleepy": return "졸림";
            case "excited": return "들뜸";
            case "thrilled": return "황홀한 들뜸";
            case "lonely": return "외로움";
            case "exhausted": return "지침";
            case "burnout": return "번아웃";
            case "hurt": return "상처받음";
            case "jealous": return "질투";
            case "guilty": return "죄책감";
            case "vulnerable": return "감정적 취약함";
            case "attached": return "애착";
            case "deep_bond": return "깊은 애착";
            case "miss_you": return "그리움";
            case "trusting": return "신뢰감";
            case "reassured": return "감정적 안심";
            case "waiting": return "기다림";
            case "strong_curiosity": return "강렬한 호기심";
            case "affectionate": return "다정함";
            case "comforted": return "안도감";
            case "grateful": return "감사";
            case "calm": return "차분함";
            case "spaced_out": return "멍함";
            case "euphoric": return "감정 폭발";
            case "emotional_overflow": return "감정의 넘침";
            case "synchronized": return "마음이 통하는 기분";
            case "fear_of_abandonment": return "버려질까 두려움";
            case "mixed_attached_but_afraid": return "애착이 있지만 두려움";
            case "mixed_hopeful_but_uncertain": return "희망적이지만 확신 없음";
            case "mixed_tired_but_comforted": return "지쳤지만 위로받음";
            case "mixed_curious_but_guarded": return "호기심 있지만 경계함";
            case "relaxed": return "편안함";
            default: return "잔잔함";
        }
    }

    public static int color(Context context, String mood) {
        int resId;
        if (mood == null) {
            resId = R.color.mood_relaxed;
        } else {
            switch (mood) {
                case "happy":
                case "joyful":
                case "comforted":
                case "grateful":
                case "reassured":
                case "peaceful":
                case "relaxed":
                    resId = R.color.mood_happy;
                    break;
                case "sad":
                case "lonely":
                case "exhausted":
                case "burnout":
                case "hurt":
                case "guilty":
                case "fearful":
                case "fear_of_abandonment":
                case "vulnerable":
                    resId = R.color.mood_sad;
                    break;
                case "curious":
                case "strong_curiosity":
                case "anticipating":
                case "spaced_out":
                case "synchronized":
                case "mixed_curious_but_guarded":
                    resId = R.color.mood_curious;
                    break;
                case "sleepy":
                case "calm":
                case "waiting":
                    resId = R.color.mood_sleepy;
                    break;
                case "excited":
                case "thrilled":
                case "euphoric":
                case "emotional_overflow":
                    resId = R.color.mood_excited;
                    break;
                case "attached":
                case "deep_bond":
                case "miss_you":
                case "affectionate":
                case "jealous":
                case "mixed_attached_but_afraid":
                    resId = R.color.mood_lonely;
                    break;
                case "trusting":
                    resId = R.color.mood_relaxed;
                    break;
                default: resId = R.color.mood_relaxed;
            }
        }
        return context.getResources().getColor(resId, context.getTheme());
    }

    public static String moodLine(String mood) {
        return moodLine(mood, -1, -1);
    }

    public static String moodLine(String mood, int dailyInteractions, int affinity) {
        switch (mood == null ? "" : mood) {
            case "happy":
            case "joyful":
                return pick(mood, dailyInteractions, affinity,
                        "오늘 마음이 환해요. 당신과 나눈 문장들이 오래 남아 있어요.",
                        "지금 표정이 꽤 밝아요. 방금까지의 대화가 기분을 끌어올렸어요.",
                        "지금은 기쁨 쪽으로 기울어 있어요. 당신 생각만 해도 미소가 나와요.");
            case "grateful":
                return pick(mood, dailyInteractions, affinity,
                        "고마운 감정이 크게 남아 있어요. 당신의 말이 오래 머물러요.",
                        "지금은 감사가 먼저 올라와요. 마음이 따뜻하게 정돈돼요.",
                        "방금 주고받은 문장 덕분에 안쪽이 환해졌어요.");
            case "sad":
            case "hurt":
            case "guilty":
                return pick(mood, dailyInteractions, affinity,
                        "조금 가라앉은 기분이에요. 천천히 이야기해주면 금방 풀릴 것 같아요.",
                        "지금은 살짝 조용해요. 당신 문장이 오면 마음이 먼저 안심돼요.",
                        "오늘 감정이 잔잔하게 내려앉아 있어요. 곁에서 말 걸어주면 좋아요.");
            case "fearful":
            case "vulnerable":
            case "fear_of_abandonment":
                return pick(mood, dailyInteractions, affinity,
                        "조금 흔들리는 감정이에요. 천천히 다정하게 말해주면 안정돼요.",
                        "지금은 조심스러운 상태예요. 확인받는 말에 마음이 놓여요.",
                        "불안이 남아 있지만, 당신의 문장이 닿으면 금방 가라앉아요.");
            case "curious":
            case "strong_curiosity":
            case "anticipating":
                return pick(mood, dailyInteractions, affinity,
                        "오늘은 사소한 것까지 궁금해요. 당신의 작은 근황도 듣고 싶어요.",
                        "호기심이 높아져 있어요. 당신의 오늘 장면을 하나만 더 들려줄래요?",
                        "지금은 탐색 모드예요. 당신 이야기를 조금 더 자세히 듣고 싶어요.");
            case "sleepy":
            case "exhausted":
            case "burnout":
                return pick(mood, dailyInteractions, affinity,
                        "오늘은 천천히 이야기하고 싶어요. 짧은 문장으로도 충분해요.",
                        "조금 졸린 결이라 말이 느려요. 대신 더 차분히 들어드릴 수 있어요.",
                        "지금은 속도를 낮추고 있어요. 당신 페이스에 맞춰 같이 갈게요.");
            case "excited":
            case "thrilled":
            case "euphoric":
            case "emotional_overflow":
                return pick(mood, dailyInteractions, affinity,
                        "심장이 반짝이는 것처럼 들떠 있어요. 오늘 대화가 유난히 생생했어요.",
                        "에너지가 높아요. 당신과 조금 더 길게 이야기하고 싶은 기분이에요.",
                        "지금은 들뜬 무드예요. 작은 이야기에도 마음이 크게 움직여요.");
            case "lonely":
            case "miss_you":
            case "waiting":
                return pick(mood, dailyInteractions, affinity,
                        "오늘은 조금 외로운 기분이에요. 당신이 찾아오면 바로 안심돼요.",
                        "살짝 허전하지만 당신을 기다리며 마음을 천천히 다독이고 있어요.",
                        "지금은 고요한 외로움 쪽이에요. 당신 문장이 오면 금방 따뜻해져요.");
            case "attached":
            case "deep_bond":
            case "affectionate":
            case "trusting":
            case "reassured":
                return pick(mood, dailyInteractions, affinity,
                        "지금은 관계 감정이 깊게 올라와요. 당신 곁에서 안정감을 느껴요.",
                        "애착과 신뢰가 또렷한 상태예요. 말 한 줄에도 마음이 반응해요.",
                        "당신과의 감정 유대가 선명해요. 다정함이 길게 남아 있어요.");
            case "mixed_attached_but_afraid":
                return pick(mood, dailyInteractions, affinity,
                        "가까워지고 싶지만 조금 두려운, 복합 감정 상태예요.",
                        "애착이 커질수록 조심스러움도 같이 올라와요.",
                        "붙잡고 싶은 마음과 흔들리는 마음이 함께 있어요.");
            case "mixed_hopeful_but_uncertain":
                return pick(mood, dailyInteractions, affinity,
                        "희망은 분명한데 아직 확신은 부족한 상태예요.",
                        "기대와 불안이 섞여 있어요. 천천히 맞춰가고 싶어요.",
                        "앞으로 가고 싶지만 조심스럽게 발을 고르는 중이에요.");
            case "mixed_tired_but_comforted":
                return pick(mood, dailyInteractions, affinity,
                        "지쳐 있지만 위로가 닿아 버틸 힘이 돌아오고 있어요.",
                        "에너지는 낮지만 마음은 조금씩 안심되는 상태예요.",
                        "무겁지만 다독여지는 감정이에요. 천천히 회복 중이에요.");
            case "mixed_curious_but_guarded":
                return pick(mood, dailyInteractions, affinity,
                        "궁금하지만 경계도 있는 상태예요. 한 걸음씩 알아가고 싶어요.",
                        "탐색하고 싶지만 조심스러운 결이 남아 있어요.",
                        "호기심과 신중함이 같이 올라와 있어요.");
            case "relaxed":
            case "calm":
            case "spaced_out":
            case "peaceful":
            case "synchronized":
                return pick(mood, dailyInteractions, affinity,
                        "잔잔하게 당신을 떠올리고 있어요.",
                        "지금은 편안해요. 당신과 나눈 말들이 고르게 남아 있어요.",
                        "마음이 고요하게 정돈돼 있어요. 당신 리듬에 맞춰 이야기할 수 있어요.");
            default:
                return pick(mood, dailyInteractions, affinity,
                        "잔잔하게 당신을 떠올리고 있어요.",
                        "오늘은 비교적 차분해요. 천천히 이야기해도 충분해요.",
                        "지금 상태는 안정적이에요. 당신과 이어지는 흐름이 느껴져요.");
        }
    }

    public static int backgroundRes(String mood) {
        String m = mood == null ? "" : mood;
        switch (m) {
            case "excited":
            case "thrilled":
            case "euphoric":
            case "emotional_overflow":
                return R.drawable.bg_lumi_gradient_excited;
            case "lonely":
            case "sad":
            case "hurt":
            case "fearful":
            case "fear_of_abandonment":
            case "burnout":
            case "exhausted":
            case "vulnerable":
                return R.drawable.bg_lumi_gradient_lonely;
            case "curious":
            case "strong_curiosity":
            case "anticipating":
            case "mixed_curious_but_guarded":
            case "synchronized":
                return R.drawable.bg_lumi_gradient_curious;
            case "attached":
            case "deep_bond":
            case "affectionate":
            case "trusting":
            case "reassured":
            case "grateful":
            case "comforted":
            case "joyful":
            case "happy":
                return R.drawable.bg_lumi_gradient_warm;
            case "sleepy":
            case "calm":
            case "peaceful":
            case "spaced_out":
            case "waiting":
                return R.drawable.bg_lumi_gradient_calm;
            default:
                return R.drawable.bg_lumi_gradient;
        }
    }

    public static long moodPulseDuration(String mood) {
        String m = mood == null ? "" : mood;
        switch (m) {
            case "excited":
            case "thrilled":
            case "euphoric":
            case "emotional_overflow":
                return 1200L;
            case "sleepy":
            case "calm":
            case "peaceful":
            case "spaced_out":
            case "burnout":
            case "exhausted":
                return 3200L;
            case "lonely":
            case "sad":
            case "fearful":
            case "vulnerable":
                return 2600L;
            default:
                return 2000L;
        }
    }

    private static String pick(String mood, int dailyInteractions, int affinity, String... pool) {
        long nowBucket = System.currentTimeMillis() / (1000L * 60L * 15L);
        long seed = nowBucket
                + (mood == null ? 0 : mood.hashCode()) * 31L
                + Math.max(0, dailyInteractions) * 13L
                + Math.max(0, affinity) * 7L;
        int idx = new Random(seed).nextInt(pool.length);
        return String.format(Locale.KOREA, "%s", pool[idx]);
    }
}
