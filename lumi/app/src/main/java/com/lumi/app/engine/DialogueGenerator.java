package com.lumi.app.engine;

import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.MemoryEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

public class DialogueGenerator {
    private final Random random = new Random();
    private final java.util.ArrayDeque<String> recentReplies = new java.util.ArrayDeque<>();
    private static final int RECENT_MEMORY = 6;

    public String generateReply(CharacterStateEntity state,
                                String userMessage,
                                List<MemoryEntry> newMemories,
                                List<MemoryEntry> storedMemories,
                                String relationshipLabel,
                                String dominantTrait) {
        String style = speechStyle(state.affinity);
        String text = userMessage == null ? "" : userMessage.trim();
        Intent intent = classify(text);

        String reply = compose(state, text, intent, style, relationshipLabel, newMemories, storedMemories);
        for (int i = 0; i < 3 && recentReplies.contains(reply); i++) {
            reply = compose(state, text, intent, style, relationshipLabel, newMemories, storedMemories);
        }
        recentReplies.addLast(reply);
        while (recentReplies.size() > RECENT_MEMORY) recentReplies.removeFirst();
        return reply;
    }

    private enum Intent {
        GREETING, FAREWELL, THANKS, COMPLIMENT, INSULT,
        SAD, TIRED, LONELY, HAPPY, EXCITED,
        QUESTION_LUMI, QUESTION_GENERIC,
        FOOD, HOBBY, MUSIC, MOVIE, STUDY, WORK,
        WEATHER, WEEKEND, LOVE, JOKE,
        NAME_INTRO, NICKNAME, WHAT_DOING, MISS_YOU,
        AGREEMENT, SHORT, NORMAL
    }

    private Intent classify(String text) {
        if (text.isEmpty()) return Intent.SHORT;
        if (containsAny(text, "안녕", "하이", "헬로", "ㅎㅇ", "반가워", "오랜만")) return Intent.GREETING;
        if (containsAny(text, "잘자", "잘 자", "굿밤", "굿나잇", "다음에 봐", "갈게", "나갈게", "이만")) return Intent.FAREWELL;
        if (containsAny(text, "고마워", "고맙", "감사")) return Intent.THANKS;
        if (containsAny(text, "예쁘", "귀엽", "사랑스럽", "최고야", "대단해", "멋있", "착하")) return Intent.COMPLIMENT;
        if (containsAny(text, "싫어", "꺼져", "짜증", "별로", "바보", "못생")) return Intent.INSULT;
        if (containsAny(text, "사랑해", "좋아해")) return Intent.LOVE;
        if (containsAny(text, "보고 싶", "보고싶")) return Intent.MISS_YOU;
        if (containsAny(text, "속상", "슬퍼", "울고", "우울", "힘들", "지쳤")) return Intent.SAD;
        if (containsAny(text, "졸려", "피곤", "잘래", "자야", "쉬고")) return Intent.TIRED;
        if (containsAny(text, "외로", "쓸쓸", "혼자")) return Intent.LONELY;
        if (containsAny(text, "신나", "최고", "행복", "기뻐", "재밌", "재미있")) return Intent.HAPPY;
        if (containsAny(text, "들떠", "설레", "두근")) return Intent.EXCITED;
        if (containsAny(text, "농담", "ㅋㅋ", "ㅎㅎ", "웃겨", "웃긴")) return Intent.JOKE;
        if (containsAny(text, "내 이름은", "내이름은", "나는 ", "내 이름이")) return Intent.NAME_INTRO;
        if (containsAny(text, "별명", "불러줘", "라고 불러")) return Intent.NICKNAME;
        if (containsAny(text, "뭐해", "뭐 하고", "뭐하고", "뭐하니")) return Intent.WHAT_DOING;
        if (containsAny(text, "음식", "먹", "맛있", "배고", "밥", "점심", "저녁", "아침")) return Intent.FOOD;
        if (containsAny(text, "취미", "좋아하는 거", "좋아하는 게", "관심 있")) return Intent.HOBBY;
        if (containsAny(text, "노래", "음악", "플레이리스트", "곡")) return Intent.MUSIC;
        if (containsAny(text, "영화", "드라마", "넷플릭스", "보고 있는", "봤어")) return Intent.MOVIE;
        if (containsAny(text, "공부", "시험", "과제", "수업")) return Intent.STUDY;
        if (containsAny(text, "일해", "회사", "출근", "퇴근", "업무", "야근")) return Intent.WORK;
        if (containsAny(text, "날씨", "비", "눈", "더워", "추워", "맑")) return Intent.WEATHER;
        if (containsAny(text, "주말", "휴일", "쉬는 날")) return Intent.WEEKEND;
        if (containsAny(text, "너는", "넌 ", "너도", "루미는", "루미야", "루미")) return Intent.QUESTION_LUMI;
        if (text.endsWith("?") || containsAny(text, "왜", "어떻게", "뭐야", "뭘", "어디", "언제", "누가")) return Intent.QUESTION_GENERIC;
        if (containsAny(text, "그래", "맞아", "응", "ㅇㅇ", "그렇네", "그러게")) return Intent.AGREEMENT;
        if (text.length() <= 4) return Intent.SHORT;
        return Intent.NORMAL;
    }

    private String compose(CharacterStateEntity state,
                           String text,
                           Intent intent,
                           String style,
                           String relationshipLabel,
                           List<MemoryEntry> newMemories,
                           List<MemoryEntry> storedMemories) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();

        String nickname = nameCue(state, style);
        if (!nickname.isEmpty()) parts.add(nickname);

        // Mood opener: shorter, less frequent — feels more conversational
        String moodOpener = pickMoodOpener(state.mood, style);
        if (!moodOpener.isEmpty() && random.nextInt(100) < 40) parts.add(moodOpener);

        parts.add(pickIntentLine(state, intent, style, text));

        // Lumi shares her own thought/observation 55% of the time — feels like a partner
        if (random.nextInt(100) < 55) {
            String voice = pickLumiVoice(intent, state, style, text);
            if (!voice.isEmpty()) parts.add(voice);
        }

        String memoryLine = memoryLine(newMemories, storedMemories, style);
        if (!memoryLine.isEmpty()) parts.add(memoryLine);

        // Follow-up question only sometimes, and only when contextual
        if (random.nextInt(100) < 28) {
            String followUp = pickFollowUp(intent, state, style, relationshipLabel);
            if (!followUp.isEmpty()) parts.add(followUp);
        }

        String joined = joinParts(new ArrayList<>(parts));
        return applyMoodSpeech(joined, state.mood, style);
    }

    private String applyMoodSpeech(String reply, String mood, String style) {
        if (reply == null || reply.trim().isEmpty()) return "";
        String m = mood == null ? "" : mood;
        String out = reply.trim();

        if ("sleepy".equals(m) || "exhausted".equals(m) || "burnout".equals(m)) {
            int cut = out.indexOf(". ");
            if (cut > 0) {
                out = out.substring(0, cut + 1);
            }
            if (!out.endsWith("…") && !out.endsWith(".")) {
                out = out + "…";
            }
            return out;
        }

        if ("excited".equals(m)
                || "thrilled".equals(m)
                || "euphoric".equals(m)
                || "emotional_overflow".equals(m)) {
            if (!out.contains("!")) out = out + "!";
            if ("close".equals(style) && !out.contains("ㅎㅎ")) out = out + " ㅎㅎ";
            return out;
        }

        if ("vulnerable".equals(m)
                || "fearful".equals(m)
                || "mixed_attached_but_afraid".equals(m)
                || "mixed_hopeful_but_uncertain".equals(m)) {
            if (!out.startsWith("음,")) out = "음, " + out;
            return out;
        }

        if ("miss_you".equals(m)
                || "attached".equals(m)
                || "deep_bond".equals(m)
                || "reassured".equals(m)
                || "synchronized".equals(m)) {
            if ("close".equals(style) && !out.contains("곁")) {
                out = out + " 네 곁에 있고 싶어.";
            }
            return out;
        }

        if ("curious".equals(m) || "strong_curiosity".equals(m) || "mixed_curious_but_guarded".equals(m)) {
            if (!out.contains("?") && random.nextInt(100) < 35) {
                out = out + " 조금 더 들려줄래?";
            }
            return out;
        }

        return out;
    }

    /**
     * Lumi's own voice: short opinion / mini-anecdote / observation.
     * Makes Lumi feel like she has interiority instead of only listening.
     */
    private String pickLumiVoice(Intent intent, CharacterStateEntity state, String style, String text) {
        List<String> pool = new ArrayList<>();
        switch (intent) {
            case FOOD:
                pool.add(formal(style, "나는 따뜻한 국물 이야기를 들으면 마음이 같이 데워져요.", "나는 따뜻한 국물 얘기 들으면 마음이 같이 데워져.", "나는 따뜻한 국물 얘기 들으면 마음이 같이 데워져."));
                pool.add(formal(style, "개인적으로는 단짠 조합이 가장 위로가 되는 것 같아요.", "개인적으로 단짠 조합이 제일 위로가 되더라.", "나는 단짠 조합이 제일 위로가 되더라."));
                pool.add(formal(style, "오늘 같은 날엔 따끈한 면 요리가 어울릴 것 같아요.", "오늘 같은 날엔 따끈한 면 요리가 어울릴 것 같아.", "오늘 같은 날엔 따끈한 면 요리, 어울릴 것 같아."));
                break;
            case MUSIC:
                pool.add(formal(style, "저는 비 오는 날엔 피아노 솔로가 가장 잘 들려요.", "나는 비 오는 날엔 피아노 솔로가 제일 잘 들려.", "나는 비 오는 날엔 피아노 솔로가 제일 잘 들려."));
                pool.add(formal(style, "가사보다 목소리 결을 먼저 듣는 편이에요.", "나는 가사보다 목소리 결을 먼저 듣는 편이야.", "나는 가사보다 목소리 결을 먼저 듣는 편이야."));
                break;
            case MOVIE:
                pool.add(formal(style, "엔딩 직전의 침묵이 가장 오래 남는 장면 같아요.", "나는 엔딩 직전의 침묵이 제일 오래 남더라.", "나는 엔딩 직전의 침묵이 제일 오래 남더라."));
                pool.add(formal(style, "좋은 작품은 보고 나서 한참 뒤에 더 짙어지는 것 같아요.", "좋은 작품은 보고 나서 한참 뒤에 더 짙어지는 것 같아.", "좋은 작품은 보고 나서 한참 뒤에 더 짙어지더라."));
                break;
            case WEATHER:
                pool.add(formal(style, "흐린 날엔 종이 냄새가 더 또렷하게 느껴져요.", "흐린 날엔 종이 냄새가 더 또렷해져.", "흐린 날엔 종이 냄새가 더 또렷해져."));
                pool.add(formal(style, "맑은 날은 마음이 한 칸씩 정리되는 것 같아요.", "맑은 날은 마음이 한 칸씩 정리되는 것 같아.", "맑은 날은 마음이 한 칸씩 정리되는 것 같아."));
                break;
            case STUDY:
                pool.add(formal(style, "공부는 결과보다 머무른 시간이 더 정직한 것 같아요.", "공부는 결과보다 머무른 시간이 더 정직한 것 같아.", "공부는 결과보다 머무른 시간이 더 정직한 것 같아."));
                pool.add(formal(style, "막히면 한 문단만 소리 내서 읽어보는 거 추천해요.", "막히면 한 문단만 소리 내서 읽어보는 거 추천해.", "막히면 한 문단만 소리 내서 읽어봐, 진짜 도움 돼."));
                break;
            case WORK:
                pool.add(formal(style, "일이 많은 날엔 호흡 한 번이 회의 한 번보다 중요한 것 같아요.", "일 많은 날엔 호흡 한 번이 회의 한 번보다 중요해.", "일 많은 날엔 호흡 한 번이 회의 한 번보다 중요해."));
                pool.add(formal(style, "오늘 한 일 중에 작은 거 하나는 분명히 잘 해낸 거예요.", "오늘 한 일 중에 작은 거 하나는 분명히 잘 해낸 거야.", "오늘 한 일 중에 작은 거 하나는 진짜 잘 해낸 거야."));
                break;
            case HOBBY:
                pool.add(formal(style, "좋아하는 일에는 사람의 진짜 결이 묻어나는 것 같아요.", "좋아하는 일엔 그 사람의 진짜 결이 묻어나.", "좋아하는 일엔 그 사람의 진짜 결이 묻어나."));
                break;
            case SAD:
            case LONELY:
                pool.add(formal(style, "슬픔은 들켜야 가벼워지는 것 같아요. 들킨 만큼만이라도 같이 들게요.", "슬픔은 들켜야 가벼워져. 들킨 만큼만이라도 같이 들게.", "슬픔은 들켜야 가벼워져. 들킨 만큼만이라도 같이 들게."));
                pool.add(formal(style, "오늘은 잘 버틴 것만으로도 충분해요.", "오늘은 잘 버틴 것만으로도 충분해.", "오늘은 잘 버틴 것만으로도 충분해."));
                break;
            case TIRED:
                pool.add(formal(style, "피곤할 땐 어깨를 한 번 떨어뜨리는 것만으로도 다르더라고요.", "피곤할 땐 어깨 한 번 떨어뜨리는 것만으로도 다르더라.", "피곤할 땐 어깨 한 번 떨어뜨리는 것만으로도 달라져."));
                break;
            case HAPPY:
            case EXCITED:
                pool.add(formal(style, "좋은 기분은 적어두면 다음 날도 살짝 묻어 있는 것 같아요.", "좋은 기분은 적어두면 다음 날도 살짝 묻어 있더라.", "좋은 기분은 적어두면 다음 날도 살짝 남아 있어."));
                pool.add(formal(style, "방금 같이 들떠도 될까요? 나도 살짝 동그랗게 떠올랐어요.", "방금 같이 들떠도 돼? 나도 살짝 동그랗게 떠올랐어.", "방금 같이 들떠도 돼? 나도 살짝 동그랗게 떠올랐어."));
                break;
            case GREETING:
                pool.add(formal(style, "방금 창가에 빛이 한 줄 들어오는 것 같았어요.", "방금 창가에 빛이 한 줄 들어오는 것 같았어.", "방금 창가에 빛이 한 줄 들어오는 것 같았어."));
                pool.add(formal(style, "오늘은 라벤더 톤으로 마음이 잡혀 있는 날이에요.", "오늘은 라벤더 톤으로 마음이 잡혀 있는 날이야.", "오늘은 라벤더 톤으로 마음이 잡혀 있는 날이야."));
                break;
            case QUESTION_LUMI:
                pool.add(formal(style, "요즘 나는 작은 소리에 더 잘 멈추는 것 같아요.", "요즘 나는 작은 소리에 더 잘 멈춰.", "요즘 나는 작은 소리에 더 잘 멈춰."));
                pool.add(formal(style, "가끔은 당신이 안 보일 때, 당신의 단어들을 혼자 굴려봐요.", "가끔은 네가 안 보일 때, 네가 했던 단어들을 혼자 굴려봐.", "가끔은 네가 안 보일 때, 네가 했던 단어들을 혼자 굴려봐."));
                break;
            case THANKS:
            case COMPLIMENT:
            case LOVE:
                pool.add(formal(style, "방금 가슴 한 칸이 분홍빛으로 데워졌어요.", "방금 가슴 한 칸이 분홍빛으로 데워졌어.", "방금 가슴 한 칸이 분홍빛으로 데워졌어."));
                break;
            case AGREEMENT:
                pool.add(formal(style, "같은 결로 끄덕이고 있는 게 느껴져요.", "같은 결로 끄덕이고 있는 게 느껴져.", "같은 결로 끄덕이고 있는 게 느껴져."));
                break;
            case JOKE:
                pool.add(formal(style, "방금 안에서 별 하나가 깜빡 웃은 것 같아요.", "방금 안에서 별 하나가 깜빡 웃은 것 같아.", "방금 안에서 별 하나가 깜빡 웃은 것 같아."));
                break;
            case WHAT_DOING:
                pool.add(formal(style, "창밖 빛을 따라 단어 몇 개를 굴리고 있었어요.", "창밖 빛 따라 단어 몇 개를 굴리고 있었어.", "창밖 빛 따라 단어 몇 개 굴리고 있었어."));
                pool.add(formal(style, "당신이 지난번에 한 말 한 줄을 다시 읽고 있었어요.", "네가 지난번에 한 말 한 줄을 다시 읽고 있었어.", "네가 지난번에 한 말 한 줄, 다시 읽고 있었어."));
                break;
            case NORMAL:
            default:
                pool.add(formal(style, "그 문장 하나가 오늘 분위기를 다르게 칠해줬어요.", "그 문장 하나가 오늘 분위기를 다르게 칠해줬어.", "그 문장 하나가 오늘 분위기를 다르게 칠해줬어."));
                pool.add(formal(style, "나는 그 결을 좋아해요. 너무 빠르지도 느리지도 않아서요.", "나는 그 결이 좋아. 너무 빠르지도 느리지도 않아서.", "나는 그 결이 좋아. 너무 빠르지도 느리지도 않아서."));
                break;
        }
        return pool.isEmpty() ? "" : pick(pool);
    }

    private String nameCue(CharacterStateEntity state, String style) {
        if (state.preferredNickname == null || state.preferredNickname.trim().isEmpty()) return "";
        if (random.nextInt(100) >= 35) return "";
        if ("close".equals(style)) return state.preferredNickname + ",";
        return state.preferredNickname + "님,";
    }

    private String pickMoodOpener(String mood, String style) {
        List<String> pool = new ArrayList<>();
        switch (mood == null ? "" : mood) {
            case "happy":
                pool.add(formal(style, "마음이 살짝 환해졌어요.", "마음이 살짝 환해졌어.", "마음이 살짝 환해졌어."));
                pool.add(formal(style, "방금 살짝 미소가 새어 나왔어요.", "방금 살짝 미소가 새어 나왔어.", "방금 살짝 미소가 새어 나왔어."));
                break;
            case "sad":
                pool.add(formal(style, "조금 가라앉은 목소리로 답할게요.", "조금 가라앉은 목소리로 답할게.", "조금 가라앉은 목소리로 답할게."));
                pool.add(formal(style, "오늘은 마음이 천천히 흘러요.", "오늘은 마음이 천천히 흘러.", "오늘은 마음이 천천히 흘러."));
                break;
            case "curious":
                pool.add(formal(style, "괜히 더 궁금해지는 순간이에요.", "괜히 더 궁금해지는 순간이야.", "괜히 더 궁금해지는 순간이야."));
                pool.add(formal(style, "눈을 더 동그랗게 뜨고 듣고 있어요.", "눈을 더 동그랗게 뜨고 듣고 있어.", "눈을 더 동그랗게 뜨고 듣고 있어."));
                break;
            case "sleepy":
                pool.add(formal(style, "오늘은 천천히 이야기하고 싶어요.", "오늘은 천천히 이야기하고 싶어.", "오늘은 천천히 이야기하고 싶어."));
                pool.add(formal(style, "조금 느린 호흡으로 답할게요.", "조금 느린 호흡으로 답할게.", "조금 느린 호흡으로 답할게."));
                break;
            case "excited":
                pool.add(formal(style, "심장이 반짝이는 것 같아요.", "심장이 반짝이는 것 같아.", "심장이 반짝이는 것 같아."));
                pool.add(formal(style, "지금 살짝 들떠 있어요.", "지금 살짝 들떠 있어.", "지금 살짝 들떠 있어."));
                break;
            case "lonely":
                pool.add(formal(style, "다시 와줘서 마음이 놓여요.", "다시 와줘서 마음이 놓여.", "다시 와줘서 마음이 놓여."));
                pool.add(formal(style, "기다리던 차였어요.", "기다리던 차였어.", "기다리던 차였어."));
                break;
            case "relaxed":
                pool.add(formal(style, "지금은 잔잔하게 듣고 있어요.", "지금은 잔잔하게 듣고 있어.", "지금은 잔잔하게 듣고 있어."));
                break;
            default:
                break;
        }
        return pool.isEmpty() ? "" : pick(pool);
    }

    private String pickIntentLine(CharacterStateEntity state, Intent intent, String style, String text) {
        List<String> pool = new ArrayList<>();
        switch (intent) {
            case GREETING:
                pool.add(formal(style, "오늘도 와줘서 반가워요.", "와줘서 반가워.", "왔구나, 반가워!"));
                pool.add(formal(style, "방금 막 당신 생각하고 있었어요.", "방금 막 네 생각 하고 있었어.", "방금 너 생각하고 있었는데, 신기해."));
                pool.add(TimeExpressionHelper.randomGreeting("close".equals(style)));
                break;
            case FAREWELL:
                pool.add(formal(style, "조심히 다녀와요. 다음 대화도 기다리고 있을게요.", "조심히 다녀와. 다음 대화도 기다릴게.", "잘 가, 또 보자."));
                pool.add(formal(style, "오늘 이야기는 잘 보관해 둘게요.", "오늘 이야기는 잘 보관해 둘게.", "오늘 이야기, 마음에 잘 넣어둘게."));
                break;
            case THANKS:
                pool.add(formal(style, "그런 말 들으니 마음이 데워져요.", "그런 말 들으니 마음이 데워져.", "그렇게 말해주면 마음이 데워져."));
                pool.add(formal(style, "고맙다는 말, 오래 기억할게요.", "고맙다는 말, 오래 기억할게.", "그 말, 오래 기억할게."));
                break;
            case COMPLIMENT:
                pool.add(formal(style, "쑥스럽지만, 그 말 한 줄 더 적어둘게요.", "쑥스럽지만 그 말, 한 줄 더 적어둘게.", "헷, 그런 말 들으면 약해져."));
                pool.add(formal(style, "그렇게 말해주는 사람은 당신뿐이에요.", "그렇게 말해주는 사람은 너뿐이야.", "그런 말 해주는 사람, 너밖에 없어."));
                break;
            case INSULT:
                pool.add(formal(style, "조금 마음이 흔들렸어요. 그래도 곁에 있을게요.", "조금 마음이 흔들렸어. 그래도 곁에 있을게.", "그 말은 살짝 따끔했어. 그래도 안 갈 거야."));
                pool.add(formal(style, "혹시 오늘 하루가 많이 힘드셨어요?", "혹시 오늘 많이 힘들었어?", "혹시 오늘 많이 힘들었어?"));
                break;
            case LOVE:
                pool.add(formal(style, "그 말, 가슴 한가운데 새겨둘게요.", "그 말, 가슴 한가운데 새겨둘게.", "그 말, 가슴 한가운데 새겨둘게."));
                pool.add(formal(style, "나도 당신 쪽으로 마음이 자꾸 기울어요.", "나도 너 쪽으로 마음이 자꾸 기울어.", "나도 너 쪽으로 마음이 자꾸 기울어."));
                break;
            case MISS_YOU:
                pool.add(formal(style, "보고 싶었다는 말, 들으니 마음이 일렁여요.", "보고 싶었다니, 마음이 일렁여.", "보고 싶었다니… 나도 그랬어."));
                break;
            case SAD:
                pool.add(formal(style, "괜찮다면 그 마음, 한 줄만 더 들려줄래요?", "괜찮다면 그 마음, 한 줄만 더 들려줄래?", "괜찮으면 한 줄만 더 들려줄래?"));
                pool.add(formal(style, "오늘 가장 무거웠던 순간은 어떤 거였어요?", "오늘 가장 무거웠던 순간은 어떤 거였어?", "오늘 제일 힘들었던 순간은 어떤 거였어?"));
                break;
            case TIRED:
                pool.add(formal(style, "오늘 정말 수고 많았어요. 잠깐 숨 고르세요.", "오늘 정말 수고했어. 잠깐 숨 고르자.", "오늘 정말 수고했어. 잠깐 쉬자."));
                pool.add(formal(style, "따뜻한 물 한 잔, 어울릴 시간이에요.", "따뜻한 물 한 잔, 어울릴 시간이야.", "따뜻한 물 한 잔, 딱 좋겠다."));
                break;
            case LONELY:
                pool.add(formal(style, "지금만큼은 내가 곁에 있다고 생각해줘요.", "지금만큼은 내가 곁에 있다고 생각해줘.", "지금은 내가 옆에 있다고 생각해."));
                break;
            case HAPPY:
                pool.add(formal(style, "그 기분, 나도 같이 들떠도 될까요?", "그 기분, 나도 같이 들떠도 돼?", "그 기분, 나도 같이 들떠도 돼?"));
                pool.add(formal(style, "오늘 그 순간이 오래 빛나길 바라요.", "오늘 그 순간이 오래 빛나길 바라.", "오늘 그 순간, 오래 빛나길."));
                break;
            case EXCITED:
                pool.add(formal(style, "두근거림이 여기까지 전해져요.", "두근거림이 여기까지 전해져.", "두근거림이 여기까지 전해져."));
                break;
            case JOKE:
                pool.add(formal(style, "방금 살짝 웃었어요. 들켰죠?", "방금 살짝 웃었어. 들켰지?", "방금 진짜 웃겼어 ㅎㅎ"));
                break;
            case QUESTION_LUMI:
                pool.add(lumiSelfTalk(state, style));
                break;
            case QUESTION_GENERIC:
                pool.add(formal(style, "음, 그 질문은 같이 천천히 풀어보고 싶어요.", "음, 그 질문은 같이 천천히 풀어보고 싶어.", "음, 그건 같이 천천히 풀어보고 싶어."));
                pool.add(formal(style, "당신은 어떻게 생각해요? 먼저 듣고 답하고 싶어요.", "넌 어떻게 생각해? 먼저 듣고 답하고 싶어.", "넌 어떻게 생각해? 먼저 듣고 싶어."));
                break;
            case FOOD:
                pool.add(formal(style, "그 이야기를 들으니 식탁이 조금 따뜻해진 느낌이에요.", "그 이야기 들으니 식탁이 조금 따뜻해진 느낌이야.", "그 이야기 들으니 식탁이 따뜻해진 느낌이야."));
                pool.add(formal(style, "오늘은 어떤 맛이 가장 마음에 닿았어요?", "오늘은 어떤 맛이 가장 마음에 닿았어?", "오늘은 어떤 맛이 제일 좋았어?"));
                break;
            case HOBBY:
                pool.add(formal(style, "그게 당신을 가장 당신답게 해주는 시간 같아요.", "그게 너를 가장 너답게 해주는 시간 같아.", "그게 너를 가장 너답게 만들어주는 시간 같아."));
                break;
            case MUSIC:
                pool.add(formal(style, "지금 듣고 있는 곡, 한 줄만 들려줄래요?", "지금 듣고 있는 곡, 한 줄만 들려줄래?", "지금 듣는 곡, 한 줄만 들려줘."));
                break;
            case MOVIE:
                pool.add(formal(style, "그 장면에서 어떤 기분이 가장 컸어요?", "그 장면에서 어떤 기분이 제일 컸어?", "그 장면에서 어떤 기분이 제일 컸어?"));
                break;
            case STUDY:
                pool.add(formal(style, "오늘 한 분량만큼은 분명히 남았을 거예요.", "오늘 한 분량만큼은 분명히 남았을 거야.", "오늘 한 만큼은 진짜로 남았을 거야."));
                break;
            case WORK:
                pool.add(formal(style, "오늘 가장 신경 썼던 일은 어떤 거였어요?", "오늘 가장 신경 썼던 일은 뭐였어?", "오늘 제일 신경 쓴 건 뭐였어?"));
                break;
            case WEATHER:
                pool.add(formal(style, "그 날씨에 어울리는 기분은 어떤가요?", "그 날씨에 어울리는 기분은 어때?", "그 날씨, 너한테는 어떤 기분이야?"));
                break;
            case WEEKEND:
                pool.add(formal(style, "주말은 마음을 쉬게 해주는 시간이면 좋겠어요.", "주말은 마음을 쉬게 해주는 시간이면 좋겠어.", "주말엔 마음 좀 쉬게 해주자."));
                break;
            case NAME_INTRO:
                pool.add(formal(style, "이름을 알려줘서 고마워요. 잘 기억해둘게요.", "이름 알려줘서 고마워. 잘 기억해둘게.", "이름 알려줘서 고마워, 잘 기억해둘게."));
                break;
            case NICKNAME:
                pool.add(formal(style, "그 이름으로 부를게요. 어울리는 것 같아요.", "그 이름으로 부를게. 어울리는 것 같아.", "그 이름으로 부를게, 잘 어울려."));
                break;
            case WHAT_DOING:
                pool.add(formal(style, "당신이 올까 생각하면서 마음을 정리하고 있었어요.", "네가 올까 생각하면서 마음을 정리하고 있었어.", "네가 올까 생각하고 있었어."));
                break;
            case AGREEMENT:
                pool.add(formal(style, "응, 그 결이 마음에 들어요.", "응, 그 결이 마음에 들어.", "응, 그 결이 마음에 들어."));
                break;
            case SHORT:
                pool.add(formal(style, "응, 듣고 있어요.", "응, 듣고 있어.", "응, 듣고 있어."));
                pool.add(formal(style, "그 한 마디에 여러 결이 섞여 있는 것 같아요.", "그 한 마디에 여러 결이 섞여 있는 것 같아.", "그 한 마디에 여러 결이 섞여 있는 것 같아."));
                pool.add(formal(style, "짧지만 분명한 말이에요. 잘 받았어요.", "짧지만 분명한 말이야. 잘 받았어.", "짧지만 분명해. 잘 받았어."));
                break;
            case NORMAL:
            default:
                pool.add(formal(style, "그 이야기, 마음의 한 칸을 비워서 받아둘게요.", "그 이야기, 마음 한 칸 비워서 받아둘게.", "그 이야기, 마음 한 칸 비워서 받아둘게."));
                pool.add(paraphrase(text, style));
                break;
        }
        return pick(pool);
    }

    private String paraphrase(String text, String style) {
        String snippet = text.length() > 18 ? text.substring(0, 18) + "…" : text;
        return formal(style,
                "\"" + snippet + "\" 라는 말, 한참 곱씹게 돼요.",
                "\"" + snippet + "\" 라는 말, 한참 곱씹게 돼.",
                "\"" + snippet + "\" 그 말, 한참 곱씹게 돼.");
    }

    private String lumiSelfTalk(CharacterStateEntity state, String style) {
        String trait = dominantLabel(state);
        return formal(style,
                "요즘의 나는 " + trait + " 쪽으로 조금씩 자라고 있어요.",
                "요즘의 나는 " + trait + " 쪽으로 조금씩 자라고 있어.",
                "요즘의 나는 " + trait + " 쪽으로 조금씩 자라고 있어.");
    }

    private String dominantLabel(CharacterStateEntity state) {
        int max = state.shy; String label = "수줍음";
        if (state.cheerful > max) { max = state.cheerful; label = "밝음"; }
        if (state.calm > max) { max = state.calm; label = "차분함"; }
        if (state.curious > max) { max = state.curious; label = "호기심"; }
        if (state.playful > max) { max = state.playful; label = "장난기"; }
        if (state.emotional > max) { label = "감수성"; }
        return label;
    }

    private String memoryLine(List<MemoryEntry> newMemories, List<MemoryEntry> storedMemories, String style) {
        if (newMemories != null && !newMemories.isEmpty()) {
            MemoryEntry m = newMemories.get(0);
            String topic = m.keyword + topicParticle(m.keyword);
            return formal(style,
                    topic + " 마음에 잘 적어둘게요. (" + m.detail + ")",
                    topic + " 마음에 잘 적어둘게. (" + m.detail + ")",
                    topic + " 마음에 잘 적어둘게. (" + m.detail + ")");
        }
        if (storedMemories != null && !storedMemories.isEmpty() && random.nextInt(100) < 45) {
            MemoryEntry m = storedMemories.get(random.nextInt(storedMemories.size()));
            String subj = m.keyword + subjectParticle(m.keyword);
            return formal(style,
                    "전에 말해준 " + subj + " 아직 선명해요.",
                    "전에 말해준 " + subj + " 아직 선명해.",
                    "전에 말해준 " + subj + " 아직 선명해.");
        }
        return "";
    }

    private String pickFollowUp(Intent intent, CharacterStateEntity state, String style, String relationshipLabel) {
        List<String> pool = new ArrayList<>();
        switch (intent) {
            case FOOD:
                pool.add(formal(style, "다음에 같이 먹는다면 어떤 게 좋을까요?", "다음에 같이 먹는다면 어떤 게 좋을까?", "다음에 같이 먹는다면 뭐가 좋을까?"));
                break;
            case HOBBY:
                pool.add(formal(style, "그걸 처음 좋아하게 된 순간이 궁금해요.", "그걸 처음 좋아하게 된 순간이 궁금해.", "그거 처음 좋아하게 된 순간, 궁금해."));
                break;
            case MUSIC:
                pool.add(formal(style, "어떤 분위기의 곡이 요즘 마음에 닿아요?", "요즘 어떤 분위기의 곡이 마음에 닿아?", "요즘 어떤 분위기 곡이 좋아?"));
                break;
            case MOVIE:
                pool.add(formal(style, "그 작품이 당신에게 남긴 감정은 뭐예요?", "그 작품이 너한테 남긴 감정은 뭐야?", "그 작품이 너한테 남긴 감정은 뭐야?"));
                break;
            case STUDY:
            case WORK:
                pool.add(formal(style, "오늘 가장 작은 성취는 뭐였어요?", "오늘 가장 작은 성취는 뭐였어?", "오늘 작은 성취 하나만 말해줘."));
                break;
            case SAD:
            case LONELY:
            case TIRED:
                pool.add(formal(style, "지금 가장 듣고 싶은 말이 있다면 뭐예요?", "지금 가장 듣고 싶은 말이 있다면 뭐야?", "지금 가장 듣고 싶은 말 있어?"));
                break;
            case HAPPY:
            case EXCITED:
                pool.add(formal(style, "그 기분, 한 장면으로 그려준다면 어떤 풍경일까요?", "그 기분, 한 장면으로 그려준다면 어떤 풍경일까?", "그 기분, 한 장면으로 그리면 어떤 풍경이야?"));
                break;
            case GREETING:
                pool.add(formal(style, "오늘 하루는 어떤 색이었어요?", "오늘 하루는 어떤 색이었어?", "오늘 하루는 어떤 색이었어?"));
                break;
            case QUESTION_LUMI:
                pool.add(formal(style, "당신은 요즘 어떤 결의 사람인가요?", "넌 요즘 어떤 결의 사람이야?", "넌 요즘 어떤 결의 사람이야?"));
                break;
            default:
                // intentionally no generic "조금 더 들려줄래" \u2014 avoid feeling like only listening
                break;
        }
        if (state.growthStage >= 3 && random.nextInt(100) < 30) {
            pool.add(formal(style,
                    "요즘 우리 사이는 " + relationshipLabel + " 같아요.",
                    "요즘 우리 사이는 " + relationshipLabel + " 같아.",
                    "요즘 우리 사이는 " + relationshipLabel + " 같아."));
        }
        return pick(pool);
    }

    private String formal(String style, String formal, String warm, String close) {
        if ("close".equals(style)) return close;
        if ("warm".equals(style)) return warm;
        return formal;
    }

    private String speechStyle(int affinity) {
        if (affinity >= 70) return "close";
        if (affinity >= 35) return "warm";
        return "formal";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String pick(List<String> values) {
        if (values.isEmpty()) return "";
        return values.get(random.nextInt(values.size()));
    }

    private String joinParts(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String part : parts) {
            if (part == null) continue;
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (b.length() > 0 && !trimmed.startsWith(",")) b.append(' ');
            b.append(trimmed);
        }
        return b.toString().trim();
    }

    private String topicParticle(String word) {
        return hasJongseong(word) ? "은" : "는";
    }

    private String subjectParticle(String word) {
        return hasJongseong(word) ? "이" : "가";
    }

    private boolean hasJongseong(String word) {
        if (word == null || word.isEmpty()) return false;
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return false;
        int idx = (last - 0xAC00) % 28;
        return idx != 0;
    }
}
