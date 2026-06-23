package com.lumi.app.engine;

import com.lumi.app.model.CharacterStateEntity;

public class PersonalityEvolutionEngine {
    public void evolve(CharacterStateEntity state, String userMessage, String mood) {
        state.shy = clamp(state.shy - 1, 10, 90);
        if (containsAny(userMessage, "궁금", "왜", "어떻게", "질문")) {
            state.curious = clamp(state.curious + 2, 0, 100);
        }
        if (containsAny(userMessage, "ㅋㅋ", "ㅎㅎ", "장난", "재밌")) {
            state.playful = clamp(state.playful + 3, 0, 100);
        }
        if (containsAny(userMessage, "고마워", "좋아", "행복", "반가워")) {
            state.cheerful = clamp(state.cheerful + 2, 0, 100);
        }
        if (containsAny(userMessage, "괜찮아", "천천히", "쉬어", "편안")) {
            state.calm = clamp(state.calm + 2, 0, 100);
        }
        if ("sad".equals(mood)
            || "lonely".equals(mood)
            || "hurt".equals(mood)
            || "fearful".equals(mood)
            || "vulnerable".equals(mood)
            || "fear_of_abandonment".equals(mood)
            || "mixed_attached_but_afraid".equals(mood)
            || "mixed_hopeful_but_uncertain".equals(mood)
            || "mixed_tired_but_comforted".equals(mood)) {
            state.emotional = clamp(state.emotional + 3, 0, 100);
        }
        state.growthStage = resolveStage(state);
    }

    public int resolveStage(CharacterStateEntity state) {
        if (state.affinity >= 70 || state.totalInteractions >= 20) {
            return 3;
        }
        if (state.affinity >= 35 || state.totalInteractions >= 8) {
            return 2;
        }
        return 1;
    }

    public String dominantTrait(CharacterStateEntity state) {
        int max = state.shy;
        String trait = "수줍음";
        if (state.cheerful > max) {
            max = state.cheerful;
            trait = "밝음";
        }
        if (state.calm > max) {
            max = state.calm;
            trait = "차분함";
        }
        if (state.curious > max) {
            max = state.curious;
            trait = "호기심";
        }
        if (state.playful > max) {
            max = state.playful;
            trait = "장난기";
        }
        if (state.emotional > max) {
            trait = "감수성";
        }
        return trait;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}