package com.ireumgil.engine;

import com.ireumgil.data.HanjaRepository;
import com.ireumgil.model.HanjaCharacter;
import com.ireumgil.ui.HandwritingCanvasView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandwritingCandidateMatcher {

    private static class Template {
        int stroke;
        float aspect;
        float length;

        Template(int stroke, float aspect, float length) {
            this.stroke = stroke;
            this.aspect = aspect;
            this.length = length;
        }
    }

    private static class Scored {
        HanjaCharacter c;
        float score;

        Scored(HanjaCharacter c, float score) {
            this.c = c;
            this.score = score;
        }
    }

    private final HanjaRepository repository;
    private final Map<String, Template> templates = new HashMap<>();

    public HandwritingCandidateMatcher(HanjaRepository repository) {
        this.repository = repository;
        seedTemplates();
    }

    public List<HanjaCharacter> match(HandwritingCanvasView.DrawingSignature signature, String readingHint, boolean surnameMode) {
        if (signature.strokeCount <= 0) {
            return new ArrayList<>();
        }

        List<HanjaCharacter> pool = surnameMode ? repository.getCommonSurnameCharacters() : repository.getAllAllowed();
        List<Scored> scored = new ArrayList<>();

        for (HanjaCharacter c : pool) {
            Template t = templates.get(c.character);
            if (t == null) {
                int fallbackStroke = c.strokeCount == null ? 10 : c.strokeCount;
                t = new Template(fallbackStroke, 1.0f, 3.5f);
            }
            float s = similarity(signature, t);
            if (readingHint != null && !readingHint.trim().isEmpty()) {
                if (c.reading != null && c.reading.startsWith(readingHint.trim())) {
                    s += 20f;
                }
            }
            if (surnameMode && c.reading != null && repository.isCommonSurnameReading(c.reading)) {
                s += 10f;
            }
            scored.add(new Scored(c, s));
        }

        Collections.sort(scored, (a, b) -> Float.compare(b.score, a.score));

        List<HanjaCharacter> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.c);
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    private float similarity(HandwritingCanvasView.DrawingSignature input, Template t) {
        float strokePenalty = Math.abs(input.strokeCount - t.stroke) * 12f;
        float aspectPenalty = Math.abs(input.aspectRatio - t.aspect) * 30f;
        float lengthPenalty = Math.abs(input.normalizedLength - t.length) * 8f;
        return 100f - strokePenalty - aspectPenalty - lengthPenalty;
    }

    private void seedTemplates() {
        templates.put("金", new Template(8, 1.0f, 5.1f));
        templates.put("李", new Template(7, 0.9f, 4.2f));
        templates.put("朴", new Template(6, 0.75f, 3.2f));
        templates.put("崔", new Template(11, 0.9f, 6.0f));
        templates.put("鄭", new Template(19, 1.1f, 8.4f));
        templates.put("趙", new Template(14, 1.1f, 6.7f));
        templates.put("尹", new Template(4, 1.0f, 2.7f));
        templates.put("張", new Template(11, 1.0f, 5.3f));
        templates.put("林", new Template(8, 1.1f, 4.4f));
        templates.put("韓", new Template(17, 1.0f, 8.1f));
        templates.put("吳", new Template(7, 1.0f, 3.8f));
        templates.put("徐", new Template(10, 1.0f, 4.7f));
        templates.put("申", new Template(5, 0.95f, 3.0f));
        templates.put("權", new Template(22, 1.0f, 9.2f));
        templates.put("黃", new Template(12, 1.1f, 6.0f));
        templates.put("安", new Template(6, 0.9f, 3.4f));
        templates.put("宋", new Template(7, 0.95f, 3.9f));
        templates.put("柳", new Template(9, 1.0f, 4.6f));
        templates.put("洪", new Template(9, 1.0f, 4.8f));
        templates.put("姜", new Template(9, 1.0f, 4.3f));
    }
}
