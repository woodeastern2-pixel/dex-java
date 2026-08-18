package com.lumi.app.engine;

import com.lumi.app.data.LumiSettings;
import com.lumi.app.data.CharacterRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tiny multi-provider LLM client. Synchronous; call from a background thread.
 * Supports OpenAI, Anthropic, Google Gemini, and any OpenAI-compatible endpoint
 * (e.g. HyperCLOVA X v3, Together, Groq).
 */
public class LlmClient {

    public static class LlmException extends Exception {
        public LlmException(String msg) { super(msg); }
        public LlmException(String msg, Throwable t) { super(msg, t); }
    }

    private LumiSettings settings;
    private CharacterRepository repository;

    public LlmClient() {
    }

    public LlmClient(LumiSettings settings, CharacterRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    public String complete(LumiSettings settings,
                           String systemPrompt,
                           List<PromptBuilder.Turn> history) throws LlmException {
        return complete(settings, systemPrompt, history, 0.85, 1500);
    }

    public String complete(LumiSettings settings,
                           String systemPrompt,
                           List<PromptBuilder.Turn> history,
                           double temperature,
                           int maxTokens) throws LlmException {
        String provider = settings.getProvider();
        String apiKey = settings.getApiKey();
        String model = settings.getModel();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new LlmException("API 키가 설정되지 않았습니다.");
        }
        try {
            switch (provider) {
                case LumiSettings.PROVIDER_ANTHROPIC:
                    return callAnthropic(apiKey, model, systemPrompt, history, temperature, maxTokens);
                case LumiSettings.PROVIDER_GEMINI:
                    return callGemini(apiKey, model, systemPrompt, history, temperature, maxTokens);
                case LumiSettings.PROVIDER_OPENAI_COMPATIBLE: {
                    String url = settings.getBaseUrl();
                    if (url == null || url.isEmpty()) {
                        url = LumiSettings.defaultBaseUrlFor(provider);
                    }
                    return callOpenAiCompatible(url, apiKey, model, systemPrompt, history, temperature, maxTokens);
                }
                case LumiSettings.PROVIDER_OPENAI:
                default:
                    return callOpenAiCompatible(
                            "https://api.openai.com/v1/chat/completions",
                            apiKey, model, systemPrompt, history, temperature, maxTokens);
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("응답 처리 중 오류: " + e.getMessage(), e);
        }
    }

    public String completeOpenAi(String apiKey,
                                 String model,
                                 String systemPrompt,
                                 List<PromptBuilder.Turn> history,
                                 double temperature,
                                 int maxTokens) throws LlmException {
        String key = apiKey == null ? "" : apiKey.trim();
        String modelName = model == null || model.trim().isEmpty() ? "gpt-4o-mini" : model.trim();
        if (key.isEmpty()) {
            throw new LlmException("API 키가 설정되지 않았습니다.");
        }
        try {
            return callOpenAiCompatible(
                    "https://api.openai.com/v1/chat/completions",
                    key, modelName, systemPrompt, history, temperature, maxTokens);
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException("응답 처리 중 오류: " + exception.getMessage(), exception);
        }
    }

    // ----- OpenAI / OpenAI-compatible -----
    private String callOpenAiCompatible(String url, String apiKey, String model,
                                        String systemPrompt,
                                        List<PromptBuilder.Turn> history,
                                        double temperature,
                                        int maxTokens) throws Exception {
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        for (PromptBuilder.Turn t : history) {
            msgs.put(new JSONObject().put("role", t.role).put("content", t.content));
        }
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", msgs)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens);

        JSONObject resp = postJson(url, body, new String[][]{
                {"Authorization", "Bearer " + apiKey},
                {"Content-Type", "application/json"}
        });
        // OpenAI: choices[0].message.content
        // HyperCLOVA X v3 follows the same schema.
        JSONArray choices = resp.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg != null) {
                String content = msg.optString("content", "");
                if (!content.isEmpty()) return content.trim();
            }
        }
        // HyperCLOVA legacy fallback: result.message.content
        JSONObject result = resp.optJSONObject("result");
        if (result != null) {
            JSONObject m = result.optJSONObject("message");
            if (m != null) {
                String c = m.optString("content", "");
                if (!c.isEmpty()) return c.trim();
            }
        }
        throw new LlmException("응답이 비어 있습니다: " + resp.toString().substring(0, Math.min(200, resp.toString().length())));
    }

    // ----- Anthropic -----
    private String callAnthropic(String apiKey, String model,
                                 String systemPrompt,
                                 List<PromptBuilder.Turn> history,
                                 double temperature,
                                 int maxTokens) throws Exception {
        JSONArray msgs = new JSONArray();
        for (PromptBuilder.Turn t : history) {
            msgs.put(new JSONObject().put("role", t.role).put("content", t.content));
        }
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("system", systemPrompt)
                .put("max_tokens", maxTokens)
                .put("temperature", temperature)
                .put("messages", msgs);

        JSONObject resp = postJson("https://api.anthropic.com/v1/messages", body, new String[][]{
                {"x-api-key", apiKey},
                {"anthropic-version", "2023-06-01"},
                {"Content-Type", "application/json"}
        });
        JSONArray content = resp.optJSONArray("content");
        if (content != null && content.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.getJSONObject(i);
                if ("text".equals(part.optString("type"))) {
                    sb.append(part.optString("text", ""));
                }
            }
            String out = sb.toString().trim();
            if (!out.isEmpty()) return out;
        }
        throw new LlmException("응답이 비어 있습니다.");
    }

    // ----- Gemini -----
    private String callGemini(String apiKey, String model,
                              String systemPrompt,
                              List<PromptBuilder.Turn> history,
                              double temperature,
                              int maxTokens) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;
        JSONArray contents = new JSONArray();
        for (PromptBuilder.Turn t : history) {
            String role = "assistant".equals(t.role) ? "model" : "user";
            contents.put(new JSONObject()
                    .put("role", role)
                    .put("parts", new JSONArray().put(new JSONObject().put("text", t.content))));
        }
        JSONObject body = new JSONObject()
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("generationConfig", new JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxTokens));

        JSONObject resp = postJson(url, body, new String[][]{
                {"Content-Type", "application/json"}
        });
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            String finish = cand.optString("finishReason", "");
            JSONObject content = cand.optJSONObject("content");
            if (content != null) {
                JSONArray parts = content.optJSONArray("parts");
                if (parts != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        sb.append(parts.getJSONObject(i).optString("text", ""));
                    }
                    String out = sb.toString().trim();
                    if (!out.isEmpty()) return out;
                }
            }
            if ("SAFETY".equals(finish) || "RECITATION".equals(finish) || "BLOCKLIST".equals(finish)) {
                throw new LlmException("안전 필터에 막혔어요 (" + finish + "). 다른 표현으로 말해 보세요.");
            }
            if ("MAX_TOKENS".equals(finish)) {
                throw new LlmException("답이 너무 길어 잘렸어요. 다시 시도해 주세요.");
            }
        }
        throw new LlmException("응답이 비어 있습니다.");
    }

    // ----- HTTP helper -----
    private JSONObject postJson(String url, JSONObject body, String[][] headers) throws Exception {
        // Auto-retry once on 429 (rate limit) and 5xx, respecting Retry-After when present.
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                for (String[] h : headers) {
                    conn.setRequestProperty(h[0], h[1]);
                }
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String text = readAll(is);
                if (code >= 200 && code < 300) {
                    return new JSONObject(text);
                }
                if ((code == 429 || code >= 500) && attempt == 0) {
                    long wait = parseRetryAfterMs(conn.getHeaderField("Retry-After"));
                    if (wait <= 0) wait = 1500;
                    try { Thread.sleep(wait); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LlmException("중단되었습니다.");
                    }
                    continue; // retry
                }
                throw new LlmException(friendlyHttp(code, text));
            } catch (LlmException e) {
                throw e;
            } catch (IOException e) {
                last = new LlmException("네트워크 오류: " + e.getMessage(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        if (last != null) throw last;
        throw new LlmException("알 수 없는 오류");
    }

    private long parseRetryAfterMs(String header) {
        if (header == null || header.isEmpty()) return -1;
        try { return Long.parseLong(header.trim()) * 1000L; } catch (NumberFormatException ignore) {}
        return -1;
    }

    private String friendlyHttp(int code, String body) {
        String snippet = truncate(body, 200);
        switch (code) {
            case 401:
            case 403:
                return "API 키가 거부됐어요 (HTTP " + code + "). 키가 맞는지, 프로바이더 선택이 맞는지 확인해 주세요.";
            case 404:
                return "모델 또는 엔드포인트를 찾을 수 없어요 (HTTP 404). 모델 이름을 확인해 주세요. " + snippet;
            case 429:
                return "사용량 한도를 잠깐 넘었어요 (HTTP 429). 1~2분 뒤 다시 시도하거나, 더 가벼운 모델로 바꿔 보세요. " + snippet;
            case 500: case 502: case 503: case 504:
                return "서버가 일시적으로 응답하지 못했어요 (HTTP " + code + "). 잠시 뒤 다시 시도해 주세요.";
            default:
                return "HTTP " + code + ": " + snippet;
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    public String testConnection() throws LlmException {
        if (settings == null) {
            throw new LlmException("설정이 없습니다.");
        }
        if (!settings.isUseRemoteServer() || settings.getApiKey().isEmpty()) {
            throw new LlmException("API 키가 설정되지 않았습니다.");
        }
        try {
            List<PromptBuilder.Turn> testHistory = new java.util.ArrayList<>();
            testHistory.add(new PromptBuilder.Turn("user", "Hi"));
            String resp = complete(settings, "You are a helpful assistant.", testHistory);
            return resp;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("연결 실패: " + e.getMessage(), e);
        }
    }
}
