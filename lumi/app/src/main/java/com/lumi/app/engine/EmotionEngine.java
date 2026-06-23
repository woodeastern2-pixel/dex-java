package com.lumi.app.engine;

import com.lumi.app.model.CharacterStateEntity;

public class EmotionEngine {
    public String resolveMood(CharacterStateEntity state, String userMessage) {
        String text = userMessage == null ? "" : userMessage;

        if (containsAny(text, "혐오", "역겨", "더러", "소름")) {
            return "disgusted";
        }
        if (containsAny(text, "화나", "짜증", "열받", "빡쳐", "분노")) {
            return state.affinity >= 55 ? "hurt" : "angry";
        }
        if (containsAny(text, "충격", "헉", "세상에", "놀랐", "깜짝")) {
            return "surprised";
        }
        if (containsAny(text, "무서", "겁나", "두려", "불안", "초조", "떨려")) {
            if (containsAny(text, "좋긴", "괜찮", "해볼")) {
                return "mixed_hopeful_but_uncertain";
            }
            return state.affinity >= 45 ? "vulnerable" : "fearful";
        }
        if (containsAny(text, "버려", "떠나", "잊힐", "혼자 남")) {
            return "fear_of_abandonment";
        }
        if (containsAny(text, "보고 싶", "그리워", "기다렸", "오랜만")) {
            if (state.affinity >= 60) return "miss_you";
            return "anticipating";
        }
        if (containsAny(text, "외로", "쓸쓸", "공허", "허전", "혼자")) {
            if (state.affinity >= 65) return "attached";
            return "lonely";
        }
        if (containsAny(text, "지쳤", "피곤", "번아웃", "힘들", "기운 없어")) {
            if (containsAny(text, "근데", "그래도", "괜찮아질")) {
                return "mixed_tired_but_comforted";
            }
            return containsAny(text, "번아웃") ? "burnout" : "exhausted";
        }
        if (containsAny(text, "졸려", "잘래", "자야", "잠와")) {
            return "sleepy";
        }
        if (containsAny(text, "속상", "슬퍼", "울고", "상처", "실망", "후회")) {
            if (containsAny(text, "미안", "죄책")) return "guilty";
            return state.affinity >= 55 ? "hurt" : "sad";
        }
        if (containsAny(text, "질투", "부럽", "시기")) {
            return "jealous";
        }
        if (containsAny(text, "설레", "두근", "떨려", "기대", "기다려져")) {
            if (state.affinity >= 70) return "thrilled";
            return "anticipating";
        }
        if (containsAny(text, "신나", "좋아", "행복", "최고", "재밌", "기뻐")) {
            if (containsAny(text, "넘쳐", "폭발", "미쳤", "대박")) {
                return "euphoric";
            }
            return state.affinity >= 60 ? "excited" : "joyful";
        }
        if (containsAny(text, "고마워", "감사", "덕분")) {
            return state.affinity >= 55 ? "grateful" : "comforted";
        }
        if (containsAny(text, "사랑해", "좋아해", "애착", "소중")) {
            if (containsAny(text, "무섭", "걱정")) {
                return "mixed_attached_but_afraid";
            }
            return state.affinity >= 75 ? "deep_bond" : "affectionate";
        }
        if (containsAny(text, "믿", "신뢰", "의지", "든든")) {
            return "trusting";
        }
        if (containsAny(text, "왜", "어떻게", "궁금", "뭐야", "뭘까", "알고 싶")) {
            if (containsAny(text, "경계", "조심")) {
                return "mixed_curious_but_guarded";
            }
            return state.curious >= 70 ? "strong_curiosity" : "curious";
        }
        if (containsAny(text, "멍", "나른", "가만히", "아무 생각")) {
            return "spaced_out";
        }

        if (state.dailyInteractions <= 1 && state.affinity >= 65) {
            return "waiting";
        }
        if (state.dailyInteractions >= 10 && state.emotional >= 70) {
            return "emotional_overflow";
        }
        if (state.totalInteractions >= 35 && state.affinity >= 75) {
            return "synchronized";
        }
        if (state.affinity >= 80) {
            return "reassured";
        }
        if (state.totalInteractions >= 18) {
            return "peaceful";
        }
        if (state.calm >= 65) {
            return "calm";
        }
        return "curious";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}