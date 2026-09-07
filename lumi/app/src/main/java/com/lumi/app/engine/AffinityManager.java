package com.lumi.app.engine;

import com.lumi.app.model.CharacterStateEntity;

public class AffinityManager {
    public int calculateDelta(CharacterStateEntity state, String userMessage) {
        int delta = 2;
        if (userMessage.length() >= 18) {
            delta += 1;
        }
        if (containsAny(userMessage, "고마워", "좋아", "반가워", "보고 싶", "괜찮아")) {
            delta += 2;
        }
        if (containsAny(userMessage, "싫어", "꺼져", "짜증", "별로")) {
            delta -= 4;
        }
        if (state.dailyInteractions >= 4) {
            delta += 1;
        }
        return clamp(delta, -5, 6);
    }

    public String getRelationshipLabel(int affinity) {
        if (affinity >= 70) {
            return "깊게 연결됨";
        }
        if (affinity >= 35) {
            return "마음이 열리는 중";
        }
        return "조심스럽게 알아가는 중";
    }

    public String getSpeechStyle(int affinity) {
        if (affinity >= 70) {
            return "close";
        }
        if (affinity >= 35) {
            return "warm";
        }
        return "formal";
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