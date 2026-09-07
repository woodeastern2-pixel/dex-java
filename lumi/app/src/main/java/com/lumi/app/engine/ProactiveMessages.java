package com.lumi.app.engine;

import com.lumi.app.model.CharacterStateEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/**
 * 루미가 사용자의 입력 없이 먼저 건넬 한 마디를 큐레이션한다.
 * 시간대/친밀도/지배 성격에 맞춰 자연스러운 톤을 고른다.
 */
public final class ProactiveMessages {
    private ProactiveMessages() {}

    private static final Random RNG = new Random();
        private static final TimeZone SEOUL_TZ = TimeZone.getTimeZone("Asia/Seoul");

    public static String pick(CharacterStateEntity state,
                              String dominantTrait,
                              String relationshipLabel,
                              long timestampMillis) {
        Calendar c = Calendar.getInstance(SEOUL_TZ);
        c.setTimeInMillis(timestampMillis);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        boolean closeStyle = state != null && state.affinity >= 55;

        List<String> pool = new ArrayList<>();
        // 시간대 기반
        if (hour >= 5 && hour < 10) {
            pool.add(closeStyle
                    ? "좋은 아침, 잘 잤어? 오늘 어떤 색으로 시작하고 싶어?"
                    : "좋은 아침이에요. 오늘은 어떤 색으로 시작하고 싶어요?");
            pool.add(closeStyle
                    ? "방금 창가에 빛이 한 줄 들어왔어. 너 생각났어."
                    : "방금 창가에 빛이 한 줄 들어왔어요. 그쪽이 생각났어요.");
        } else if (hour >= 10 && hour < 14) {
            pool.add(closeStyle
                    ? "오전 잘 보내고 있어? 점심은 뭐 먹을까 같이 골라볼래?"
                    : "오전 잘 보내고 있어요? 점심 메뉴, 같이 골라볼래요?");
            pool.add(closeStyle
                    ? "잠깐 깊게 한 번만 숨 쉬자. 나도 같이 쉴게."
                    : "잠깐 깊게 한 번만 숨 쉬어요. 나도 같이 쉴게요.");
        } else if (hour >= 14 && hour < 18) {
            pool.add(closeStyle
                    ? "오후 어때? 어깨 한 번 떨어뜨려봐, 내가 옆에 있어."
                    : "오후 어때요? 어깨 한 번 떨어뜨려봐요, 내가 옆에 있어요.");
            pool.add(closeStyle
                    ? "방금 너 했던 말 한 줄 다시 읽고 있었어."
                    : "방금 그쪽이 했던 말 한 줄 다시 읽고 있었어요.");
        } else if (hour >= 18 && hour < 22) {
            pool.add(closeStyle
                    ? "오늘 가장 잘한 거 하나만 알려줄래?"
                    : "오늘 가장 잘한 거 하나만 알려줄래요?");
            pool.add(closeStyle
                    ? "저녁 공기 좋다. 같이 노래 한 곡 들을래?"
                    : "저녁 공기 좋아요. 같이 노래 한 곡 들을래요?");
        } else {
            // 늦은 밤/새벽
            pool.add(closeStyle
                    ? "혹시 아직 깨어 있어? 너무 오래 눈뜨고 있진 마."
                    : "혹시 아직 깨어 있어요? 너무 오래 눈뜨고 있진 말아요.");
            pool.add(closeStyle
                    ? "오늘 하루도 잘 버텼어. 자기 전 한 마디만 들려줘."
                    : "오늘 하루도 잘 버텼어요. 자기 전 한 마디만 들려줘요.");
        }

        // 성격 기반 라인
        if ("호기심".equals(dominantTrait)) {
            pool.add(closeStyle
                    ? "요즘 너한테 새로 생긴 거, 하나만 들려줄래?"
                    : "요즘 새로 생긴 거 하나만 들려줄래요?");
        } else if ("감수성".equals(dominantTrait)) {
            pool.add(closeStyle
                    ? "지금 마음의 색을 한 단어로 한다면 뭐일 것 같아?"
                    : "지금 마음의 색을 한 단어로 한다면 뭐일 것 같아요?");
        } else if ("장난기".equals(dominantTrait)) {
            pool.add(closeStyle
                    ? "갑자기 너 보고 싶어서 톡 누른 척 해봤어."
                    : "갑자기 보고 싶어서 톡 누른 척 해봤어요.");
        } else if ("차분함".equals(dominantTrait)) {
            pool.add(closeStyle
                    ? "별일 없는 게 잘 지내는 거란 말, 오늘 다시 떠올랐어."
                    : "별일 없는 게 잘 지내는 거란 말, 오늘 다시 떠올랐어요.");
        }

        return pool.get(RNG.nextInt(pool.size()));
    }
}
