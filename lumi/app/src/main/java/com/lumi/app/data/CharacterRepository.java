package com.lumi.app.data;

import android.util.Log;

import com.lumi.app.engine.AffinityManager;
import com.lumi.app.engine.DialogueGenerator;
import com.lumi.app.engine.EmotionEngine;
import com.lumi.app.engine.LlmClient;
import com.lumi.app.engine.GoogleSearchClient;
import com.lumi.app.engine.MemoryManager;
import com.lumi.app.engine.PersonalityEvolutionEngine;
import com.lumi.app.engine.PromptBuilder;
import com.lumi.app.engine.ProactiveMessages;
import com.lumi.app.model.CharacterStateEntity;
import com.lumi.app.model.ConversationMessage;
import com.lumi.app.model.InteractionResult;
import com.lumi.app.model.MemoryEntry;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

public class CharacterRepository {
    private static final String TAG = "LumiRepo";
    private static final TimeZone SEOUL_TZ = TimeZone.getTimeZone("Asia/Seoul");

    private final CharacterDao dao;
    private final EmotionEngine emotionEngine;
    private final AffinityManager affinityManager;
    private final MemoryManager memoryManager;
    private final DialogueGenerator dialogueGenerator;
    private final PersonalityEvolutionEngine personalityEvolutionEngine;
    private final PromptBuilder promptBuilder;
    private final GoogleSearchClient googleSearchClient;
    private final LlmClient llmClient;
    private final SimpleDateFormat dayFormat;

    private LumiSettings settings;
    private String lastReplySource = "local"; // "remote" or "local"
    private String lastReplyError = null;

    public CharacterRepository(CharacterDao dao) {
        this.dao = dao;
        this.emotionEngine = new EmotionEngine();
        this.affinityManager = new AffinityManager();
        this.memoryManager = new MemoryManager();
        this.dialogueGenerator = new DialogueGenerator();
        this.personalityEvolutionEngine = new PersonalityEvolutionEngine();
        this.promptBuilder = new PromptBuilder();
        this.googleSearchClient = new GoogleSearchClient();
        this.llmClient = new LlmClient();
        this.dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        this.dayFormat.setTimeZone(SEOUL_TZ);
    }

    public void setSettings(LumiSettings settings) {
        this.settings = settings;
    }

    public LumiSettings getSettings() {
        return settings;
    }

    public String getLastReplySource() {
        return lastReplySource;
    }

    public String getLastReplyError() {
        return lastReplyError;
    }

    public CharacterStateEntity loadState() {
        CharacterStateEntity state = dao.getCharacterState();
        if (state != null) {
            return state;
        }
        CharacterStateEntity initialState = new CharacterStateEntity();
        dao.upsertCharacterState(initialState);
        dao.insertConversationMessage(new ConversationMessage(
                "lumi",
                "오늘도 와줬네요. 나는 루미예요. 천천히 서로를 알아가고 싶어요.",
                initialState.mood,
                System.currentTimeMillis()
        ));
        return initialState;
    }

    public List<ConversationMessage> loadRecentMessages(int limit) {
        loadState();
        List<ConversationMessage> messages = dao.getRecentMessages(limit);
        Collections.reverse(messages);
        return messages;
    }

    public List<MemoryEntry> loadTopMemories(int limit) {
        loadState();
        return dao.getTopMemories(limit);
    }

    /**
     * Synchronous; may perform a network call. CALL FROM A BACKGROUND THREAD.
     */
    public InteractionResult sendMessage(String userMessageText) {        return sendMessage(userMessageText, ConversationMessage.ATTACH_NONE, null, null);
    }

    /**
     * 사용자가 사진/음성(텍스트 변환된) 등 첨부와 함께 보낼 때 호출. background thread 권장.
     * 음성의 경우, userMessageText 에는 STT 로 변환된 한국어 본문을 그대로 전달한다.
     */
    public InteractionResult sendMessage(String userMessageText,
                                         String attachmentType,
                                         String attachmentUri,
                                         String attachmentMeta) {
        long now = System.currentTimeMillis();
        CharacterStateEntity state = loadState().copy();
        syncDailyCounters(state, now);
        state.totalInteractions += 1;
        state.dailyInteractions += 1;
        String effectiveText = userMessageText == null ? "" : userMessageText;
        String hint = attachmentHintForLumi(attachmentType, attachmentMeta);
        String textForEngines = (effectiveText + " " + hint).trim();
        state.affinity = clamp(state.affinity + affinityManager.calculateDelta(state, textForEngines), 0, 100);
        state.mood = emotionEngine.resolveMood(state, textForEngines);
        List<MemoryEntry> newMemories = memoryManager.captureMemories(dao, state, textForEngines, now);
        personalityEvolutionEngine.evolve(state, textForEngines, state.mood);

        ConversationMessage userMessage = new ConversationMessage(
                "user", effectiveText, "user", now,
                attachmentType == null ? ConversationMessage.ATTACH_NONE : attachmentType,
                attachmentUri, attachmentMeta);
        userMessage.id = dao.insertConversationMessage(userMessage);

        dao.upsertCharacterState(state);
        List<MemoryEntry> topMemories = dao.getTopMemories(12);
        String relationshipLabel = affinityManager.getRelationshipLabel(state.affinity);
        String dominantTrait = personalityEvolutionEngine.dominantTrait(state);

        // Lumi 의 응답 생성 시, 음성 메시지면 "방금 들은 말"이라는 맥락을 함께 줘서
        // Lumi 가 텍스트가 아니라 "들은 것"으로 인지하도록 한다.
        String textForLlm = effectiveText;
        if (ConversationMessage.ATTACH_VOICE.equals(attachmentType) && !effectiveText.isEmpty()) {
            textForLlm = "(방금 음성으로 이렇게 말했어요) " + effectiveText;
        } else if (ConversationMessage.ATTACH_IMAGE.equals(attachmentType)) {
            textForLlm = (effectiveText.isEmpty() ? "" : effectiveText + " ")
                    + "(사진을 한 장 같이 보냈어요)";
        }

        String reply = generateReply(state, textForLlm, newMemories, topMemories,
                relationshipLabel, dominantTrait);

        ConversationMessage lumiMessage = new ConversationMessage("lumi", reply, state.mood, now + 1);
        decorateLumiReplyWithAttachment(lumiMessage, state, textForEngines);
        lumiMessage.id = dao.insertConversationMessage(lumiMessage);
        dao.upsertCharacterState(state);
        return new InteractionResult(state, userMessage, lumiMessage, dao.getTopMemories(8));
    }

    /**
     * 사용자가 "그려줘" 같은 그림 요청을 했을 때, 그 user 메시지와 루미의 이미지 응답을
     * 한꺼번에 저장한다. imageFilePath 가 null/empty 면 실패 케이스로 간주하고
     * 텍스트 위로 메시지만 남긴다.
     *
     * background thread 에서 호출.
     */
    public InteractionResult generateImageReply(String userPromptText,
                                                String imageFilePath,
                                                String fallbackErrorText,
                                                String generatedPrompt) {
        return generateImageReply(userPromptText, imageFilePath, fallbackErrorText, generatedPrompt, true);
        }

    public InteractionResult generateImageReply(String userPromptText,
                            String imageFilePath,
                            String fallbackErrorText,
                            String generatedPrompt,
                            boolean saveUserMessage) {
        long now = System.currentTimeMillis();
        CharacterStateEntity state = loadState().copy();
        syncDailyCounters(state, now);
        state.totalInteractions += 1;
        state.dailyInteractions += 1;
        String effectiveText = userPromptText == null ? "" : userPromptText;
        String stateText = effectiveText.trim().isEmpty()
            ? (generatedPrompt == null ? "" : generatedPrompt)
            : effectiveText;
        state.affinity = clamp(state.affinity + affinityManager.calculateDelta(state, stateText), 0, 100);
        state.mood = emotionEngine.resolveMood(state, stateText);
        personalityEvolutionEngine.evolve(state, stateText, state.mood);

        ConversationMessage userMessage = null;
        if (saveUserMessage) {
            userMessage = new ConversationMessage(
                "user", effectiveText, "user", now,
                ConversationMessage.ATTACH_NONE, null, null);
            userMessage.id = dao.insertConversationMessage(userMessage);
        }

        boolean ok = imageFilePath != null && !imageFilePath.isEmpty();
        String reply;
        ConversationMessage lumiMessage;
        if (ok) {
            reply = pickImageCaption(state.mood);
            lumiMessage = new ConversationMessage(
                    "lumi", reply, state.mood, now + 1,
                    ConversationMessage.ATTACH_IMAGE, "file://" + imageFilePath,
                generatedPrompt == null || generatedPrompt.trim().isEmpty()
                    ? effectiveText
                    : generatedPrompt);
        } else {
            reply = fallbackErrorText == null || fallbackErrorText.trim().isEmpty()
                ? "이미지를 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요."
                : fallbackErrorText;
            lumiMessage = new ConversationMessage(
                    "lumi", reply, state.mood, now + 1,
                    ConversationMessage.ATTACH_NONE, null, null);
        }
        lumiMessage.id = dao.insertConversationMessage(lumiMessage);
        dao.upsertCharacterState(state);
        return new InteractionResult(state, userMessage, lumiMessage, dao.getTopMemories(8));
    }

    private String pickImageCaption(String mood) {
        String[] options;
        if (mood != null && (mood.contains("기쁨") || mood.contains("호기심"))) {
            options = new String[] {
                    "이런 느낌이면 좋겠다 싶어서 그려봤어요 ✨",
                    "마음 가는 대로 한 장 그려봤어요!",
                    "어때요, 상상한 거랑 비슷해요?"
            };
        } else if (mood != null && (mood.contains("차분") || mood.contains("평온"))) {
            options = new String[] {
                    "조용히 한 장 그려봤어요.",
                    "이 분위기, 어울리려나요…",
                    "천천히 보여드려요."
            };
        } else {
            options = new String[] {
                    "그려봤어요. 마음에 들어줬으면 좋겠어요.",
                    "상상한 모습을 한 장으로 담아봤어요.",
                    "여기, 같이 봐요."
            };
        }
        return options[new java.util.Random().nextInt(options.length)];
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private String attachmentHintForLumi(String attachmentType, String meta) {
        if (attachmentType == null) return "";
        switch (attachmentType) {
            case ConversationMessage.ATTACH_IMAGE: return "[사진을 공유했어요]";
            case ConversationMessage.ATTACH_VOICE:
                return meta != null ? "[음성 메모 " + meta + "초]" : "[음성 메모]";
            default: return "";
        }
    }

    private void decorateLumiReplyWithAttachment(ConversationMessage lumiMessage,
                                                 CharacterStateEntity state,
                                                 String userText) {
        java.util.Random r = new java.util.Random();
        if (r.nextInt(100) >= 25) return;
        String t = userText == null ? "" : userText.toLowerCase(java.util.Locale.ROOT);
        if (containsAny(t, "노래", "음악", "플레이리스트", "곡")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_LINK;
            lumiMessage.attachmentUri = "https://www.youtube.com/results?search_query=lofi+chill+playlist";
            lumiMessage.attachmentMeta = "지금 분위기에 어울리는 플레이리스트";
            return;
        }
        if (containsAny(t, "영화", "드라마", "넷플릭스")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_LINK;
            lumiMessage.attachmentUri = "https://www.themoviedb.org/discover/movie";
            lumiMessage.attachmentMeta = "오늘 같이 골라볼래요?";
            return;
        }
        if (containsAny(t, "공부", "시험", "과제", "수업", "집중")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_LINK;
            lumiMessage.attachmentUri = "https://pomofocus.io/";
            lumiMessage.attachmentMeta = "25분만 같이 앉아 있을까요";
            return;
        }
        if (containsAny(t, "날씨", "비", "눈", "더워", "추워")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_LINK;
            lumiMessage.attachmentUri = "https://www.weather.go.kr/w/index.do";
            lumiMessage.attachmentMeta = "오늘 하늘 사정";
            return;
        }
        if (containsAny(t, "사진", "찍었", "보여")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_IMAGE;
            lumiMessage.attachmentUri = "lumi://emblem";
            lumiMessage.attachmentMeta = "내 마음 한 컷";
            return;
        }
        if (containsAny(t, "힘들", "지쳤", "우울", "슬퍼", "외로")) {
            lumiMessage.attachmentType = ConversationMessage.ATTACH_LINK;
            lumiMessage.attachmentUri = "https://www.calm.com/breathe";
            lumiMessage.attachmentMeta = "같이 한 번만 호흡해볼까요";
        }
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null) return false;
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    /**
     * 루미가 사용자의 입력 없이 먼저 보내는 메시지를 생성하고 저장한다.
     * 알림 리시버에서 호출. background thread 권장.
     */
    public ConversationMessage createProactiveMessage() {
        long now = System.currentTimeMillis();
        CharacterStateEntity state = loadState().copy();
        syncDailyCounters(state, now);
        String dominantTrait = personalityEvolutionEngine.dominantTrait(state);
        String relationshipLabel = affinityManager.getRelationshipLabel(state.affinity);
        String text = null;

        // LLM을 통해 자의적인 첫 마디 생성 시도
        if (settings != null && settings.getApiKey() != null && !settings.getApiKey().isEmpty()) {
            try {
                List<MemoryEntry> memories = dao.getTopMemories(8);
                String userName = settings.getUserName();
                String baseSystem = promptBuilder.buildSystemPrompt(
                        state, relationshipLabel, dominantTrait, memories, userName);
                String proactiveSystem = baseSystem
                        + "\n\n## 지금 상황\n"
                        + "루미가 사용자의 메시지 없이 먼저 말을 건넬 차례입니다. "
                        + "현재 시간과 루미의 성격·기분·기억을 반영하여 자연스럽고 따뜻한 한 마디를 짧게 보내주세요. "
                        + "절대 '안녕하세요' 같은 무난한 인사로 시작하지 마세요. "
                        + "형식적이거나 기계적인 문장은 금지합니다. "
                        + "항상 1~2문장 이내로, 루미답게 말하세요.";
                java.util.List<PromptBuilder.Turn> turns = new java.util.ArrayList<>();
                // 최근 대화 기록을 포함해 LLM이 대화 맥락을 인식하도록 함
                List<ConversationMessage> recentMsgs = dao.getRecentMessages(10);
                Collections.reverse(recentMsgs);
                for (ConversationMessage cm : recentMsgs) {
                    String role = "lumi".equals(cm.sender) ? "assistant" : "user";
                    if (cm.content != null && !cm.content.trim().isEmpty()) {
                        turns.add(new PromptBuilder.Turn(role, cm.content));
                    }
                }
                turns.add(new PromptBuilder.Turn("user", "[루미가 먼저 말을 걸어주세요]"));
                text = llmClient.complete(settings, proactiveSystem, turns);
                if (text != null) text = text.trim();
            } catch (Exception ignored) {
                text = null;
            }
        }

        // LLM 실패 시 하드코딩 풀에서 선택
        if (text == null || text.isEmpty()) {
            text = ProactiveMessages.pick(state, dominantTrait, relationshipLabel, now);
        }

        ConversationMessage msg = new ConversationMessage(
                "lumi", text, state.mood, now,
                ConversationMessage.ATTACH_NUDGE, null, null);
        msg.id = dao.insertConversationMessage(msg);
        dao.upsertCharacterState(state);
        return msg;
    }

    public long lastInteractionTimestamp() {
        java.util.List<ConversationMessage> recent = dao.getRecentMessages(1);
        return recent.isEmpty() ? 0L : recent.get(0).timestamp;
    }

    /** 대화 기록만 삭제. 루미의 성격/친밀도/기억은 유지. */
    public void resetConversation() {
        dao.clearConversation();
    }

    /** 루미가 기억하고 있는 키워드(메모리)만 삭제. 대화/성격은 유지. */
    public void resetMemories() {
        dao.clearMemories();
    }

    /**
     * 전체 초기화: 대화·기억·성격(친밀도/성격치/성장단계) 모두 처음 상태로.
     * 호출 후 첫 사용에서 환영 메시지가 다시 뜨도록 loadState() 가 자동 시드한다.
     */
    public void resetAll() {
        dao.clearConversation();
        dao.clearMemories();
        dao.clearCharacterState();
        lastReplySource = "local";
        lastReplyError = null;
        // 즉시 초기 상태 재시드
        loadState();
    }

    private String generateReply(CharacterStateEntity state,
                                 String userText,
                                 List<MemoryEntry> newMemories,
                                 List<MemoryEntry> topMemories,
                                 String relationshipLabel,
                                 String dominantTrait) {
        String searchContext = null;
        if (shouldUseSearch(userText)) {
            searchContext = fetchSearchContext(userText);
        }
        if (settings != null && settings.isRemoteEnabled()) {
            try {
                String userName = settings.getUserName();
                String system = promptBuilder.buildSystemPrompt(
                        state, relationshipLabel, dominantTrait, topMemories, userName, searchContext);
                List<ConversationMessage> recent = dao.getRecentMessages(20);
                Collections.reverse(recent);
                List<PromptBuilder.Turn> history = promptBuilder.buildHistory(recent, userText);
                String reply = llmClient.complete(settings, system, history);
                if (reply != null && !reply.trim().isEmpty()) {
                    lastReplySource = "remote";
                    lastReplyError = null;
                    return sanitize(reply);
                }
            } catch (Exception e) {
                Log.w(TAG, "Remote LLM failed, falling back to local: " + e.getMessage());
                lastReplyError = e.getMessage();
            }
        }
        lastReplySource = "local";
        return dialogueGenerator.generateReply(
                state, userText, newMemories, topMemories,
                relationshipLabel, dominantTrait);
    }

    private boolean shouldUseSearch(String userText) {
        if (userText == null) return false;
        String t = userText.trim();
        if (t.isEmpty()) return false;
        return containsAny(t,
                "검색", "찾아", "알려줘", "알려 주세요", "정확", "최신", "현재", "오늘", "지금",
                "누구", "무엇", "어디", "언제", "왜", "어떻게", "공식", "근거", "기사", "뉴스",
                "가격", "비교", "순위", "시간", "날짜", "위키", "사양", "통계", "법", "정책");
    }

    private String fetchSearchContext(String userText) {
        try {
            List<GoogleSearchClient.SearchResult> results;
            String modeLabel = "웹 검색";
            results = googleSearchClient.searchWithoutApi(userText, 3);

            if (results == null || results.isEmpty()) {
                return "- 검색 결과 없음";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("- 검색 방식: ").append(modeLabel).append("\n");
            int idx = 1;
            for (GoogleSearchClient.SearchResult result : results) {
                String verification = googleSearchClient.fetchPageSummary(result.url);
                sb.append(idx++).append(". 제목: ")
                        .append(result.title == null ? "" : result.title).append("\n")
                        .append("   링크: ")
                        .append(result.url == null ? "" : result.url).append("\n")
                        .append("   요약: ")
                        .append(result.snippet == null ? "" : result.snippet).append("\n");
                if (verification != null && !verification.trim().isEmpty()) {
                    sb.append("   검증: ").append(verification).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            Log.w(TAG, "Google search failed: " + e.getMessage());
            return "- 검색 시도 실패: " + e.getMessage();
        }
    }

    private String sanitize(String reply) {
        String r = reply.trim();
        // 루미: / Lumi: 접두사 제거
        if (r.startsWith("루미:")) r = r.substring(3).trim();
        else if (r.toLowerCase(Locale.ROOT).startsWith("lumi:")) r = r.substring(5).trim();
        // 코드 블록 전체를 통째로 반환하는 경우를 대비해 fence 제거
        if (r.startsWith("```")) {
            r = r.replace("```", "").trim();
        }
        // 따옴표 전체 감싸기 제거
        if (r.length() > 1 && r.startsWith("\"") && r.endsWith("\"")) {
            r = r.substring(1, r.length() - 1).trim();
        }
        // 시스템 프롬프트 섹션이 새어 나온 경우 강하게 필터링
        if (looksLikePromptLeak(r)) {
            // 줄바꿈 기준으로 분리, 프롬프트 헤더/규칙이 아닌 첫 번째 문장만 추출
            String[] lines = r.split("\n");
            StringBuilder filtered = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("##") || trimmed.startsWith("당신은 '루미")
                        || trimmed.matches("^\\d+\\.\\s.*") // 번호 규칙 목록
                        || trimmed.startsWith("-") && trimmed.length() < 80 // 짧은 목록 항목
                        || trimmed.startsWith("당신은") || trimmed.startsWith("절대 자신을")
                        || trimmed.contains("대화 규칙") || trimmed.contains("말투 규칙")
                        || trimmed.contains("루미의 현재 상태") || trimmed.contains("성격 수치")) {
                    continue;
                }
                filtered.append(trimmed).append(" ");
                // 첫 번째 유효 문장만 사용 (너무 길어지지 않도록)
                if (filtered.length() > 20) break;
            }
            String result = filtered.toString().trim();
            if (result.isEmpty()) {
                return "잠깐, 생각을 정리할게요.";
            }
            return result;
        }
        return r;
    }

    private boolean looksLikePromptLeak(String text) {
        if (text == null || text.isEmpty()) return false;
        if (text.contains("시스템 프롬프트")) return true;
        String[] markers = new String[] {
                "당신은 '루미(Lumi)'",
                "## 최우선 대화 규칙",
                "## 말투 규칙",
                "## 대화 규칙",
                "## 루미의 현재 상태",
                "## 루미가 기억하고 있는 것들",
                "한국어로만 답하세요",
                "절대 자신을 'AI'"
        };
        int hit = 0;
        for (String m : markers) {
            if (text.contains(m)) hit++;
        }
        return hit >= 2;
    }

    public String buildDailySummary(CharacterStateEntity state) {
        if (state.dailyInteractions == 0) {
            String[] pool = new String[] {
                    "오늘은 아직 당신 목소리를 못 들었어요. 첫 한마디를 기다리고 있어요.",
                    "아직 오늘 대화가 시작되지 않았어요. 지금 인사해주면 루미의 하루가 그 문장부터 열려요.",
                    "오늘은 조용한 상태예요. 당신이 먼저 말을 걸어주면 금방 온도가 올라갈 거예요."
            };
            return pickVariant(pool, state);
        }

        String mood = moodLabel(state.mood);
        String relation = affinityManager.getRelationshipLabel(state.affinity);
        int d = state.dailyInteractions;

        if (d < 4) {
            String[] pool = new String[] {
                    String.format(Locale.KOREA,
                    "오늘 %d번 이야기 나눴어요. 지금 루미 마음은 %s 쪽이고, 당신에게는 %s 느낌으로 다가가고 있어요.",
                            d, mood, relation),
                    String.format(Locale.KOREA,
                    "오늘 %d번 대화를 주고받았어요. 현재 기분은 %s에 가깝고, 친밀도는 %d/100까지 올라왔어요.",
                            d, mood, state.affinity),
                    String.format(Locale.KOREA,
                    "오늘 %d번 마음을 나누는 동안 루미 분위기가 %s 쪽으로 정리됐어요. 당신에 대한 기억도 조금 더 또렷해졌어요.",
                            d, mood)
            };
            return pickVariant(pool, state);
        }

        String[] pool = new String[] {
                String.format(Locale.KOREA,
                "오늘 %d번이나 대화했네요. 루미 감정은 지금 %s 쪽이고, 말투도 당신 리듬에 많이 맞춰졌어요.",
                        d, mood),
                String.format(Locale.KOREA,
                "오늘 %d번 이어진 대화 덕분에 루미 상태가 꽤 선명해졌어요. 지금은 %s 무드, 관계는 '%s'에 가까워요.",
                        d, mood, relation),
                String.format(Locale.KOREA,
                "오늘 %d번 주고받은 대화가 쌓이면서 루미 반응이 더 섬세해졌어요. 현재 기분은 %s, 친밀도는 %d/100이에요.",
                        d, mood, state.affinity)
        };
        return pickVariant(pool, state);
    }

    private String moodLabel(String mood) {
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
            case "curious": return "호기심";
            case "sleepy": return "졸림";
            case "excited": return "들뜸";
            case "thrilled": return "황홀한 들뜸";
            case "euphoric": return "감정 폭발";
            case "lonely": return "외로움";
            case "miss_you": return "그리움";
            case "attached": return "애착";
            case "deep_bond": return "깊은 애착";
            case "trusting": return "신뢰감";
            case "reassured": return "감정적 안심";
            case "waiting": return "기다림";
            case "hurt": return "상처받음";
            case "jealous": return "질투";
            case "guilty": return "죄책감";
            case "exhausted": return "지침";
            case "burnout": return "번아웃";
            case "spaced_out": return "멍함";
            case "calm": return "차분함";
            case "peaceful": return "평온함";
            case "emotional_overflow": return "감정의 넘침";
            case "synchronized": return "마음이 통하는 기분";
            case "mixed_attached_but_afraid": return "애착이 있지만 두려움";
            case "mixed_hopeful_but_uncertain": return "희망적이지만 확신 없음";
            case "mixed_tired_but_comforted": return "지쳤지만 위로받음";
            case "mixed_curious_but_guarded": return "호기심 있지만 경계함";
            case "relaxed": return "편안함";
            default: return "잔잔함";
        }
    }

    private String pickVariant(String[] pool, CharacterStateEntity state) {
        long seed = state.dailyInteractions * 31L
                + state.affinity * 17L
                + state.growthStage * 13L
                + (state.mood == null ? 7 : state.mood.hashCode());
        int idx = new Random(seed).nextInt(pool.length);
        return pool[idx];
    }

    private void syncDailyCounters(CharacterStateEntity state, long timestamp) {
        String today = dayFormat.format(new Date(timestamp));
        if (!today.equals(state.lastInteractionDate)) {
            state.dailyInteractions = 0;
            state.lastInteractionDate = today;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
