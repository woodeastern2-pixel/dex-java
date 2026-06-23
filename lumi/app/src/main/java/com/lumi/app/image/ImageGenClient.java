package com.lumi.app.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.lumi.app.data.LumiSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 유료 이미지 생성 API(OpenAI / fal.ai / OpenAI 호환) 전용 클라이언트.
 * 무료 이미지 생성 서비스는 지원하지 않는다.
 */
public class ImageGenClient {

    private static final String OPENAI_IMAGES_URL = "https://api.openai.com/v1/images/generations";
    private static final String FAL_RUN_URL = "https://fal.run";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 28 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 64 * 1024;
    private static final String OPENAI_IMAGES_PATH = "/images/generations";
    private static final int BLACK_CHANNEL_MAX = 6;
    private static final double BLACK_PIXEL_RATIO = 0.995;
    private static final Set<String> NANOBANANA2_MODEL_ALIASES = new HashSet<>(Arrays.asList(
            "nanobanana2",
            "nano-banana-2",
            "nanobanana-2",
            "nano_banana_2",
            "나노바나나2"
    ));
    private static final String NANOBANANA2_CANONICAL_MODEL = "nanobanana-2";

    private final Context appContext;
    private final LumiSettings settings;

    public ImageGenClient(Context context, LumiSettings settings) {
        this.appContext = context.getApplicationContext();
        this.settings = settings;
    }

    /**
     * 프롬프트로부터 이미지를 생성해 앱 캐시에 저장한 뒤 절대 파일 경로를 돌려준다.
     *
     * @param prompt 사용자가 그려달라고 한 한국어/영문 프롬프트.
     * @return 저장된 파일의 절대 경로.
     * @throws IOException 네트워크/IO 오류 또는 응답이 이미지가 아닐 때.
     */
    public String generateAndSave(String prompt, String userFacingProvider, String apiKey, String model, String baseUrl)
            throws IOException {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IOException("empty prompt");
        }
        String provider = userFacingProvider == null ? "" : userFacingProvider.trim();
        String key = apiKey == null ? "" : apiKey.trim();
        String modelName = normalizeImageModelName(model);
        String endpoint = baseUrl == null ? "" : baseUrl.trim();
        byte[] bytes;
        if (key.isEmpty()) {
            throw new IOException("이미지 생성 API 키가 비어 있어요");
        }
        if (LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            if (modelName.isEmpty()) {
                modelName = "gpt-image-1";
            }
            if (endpoint.isEmpty()) {
                throw new IOException("이미지 엔드포인트 URL이 필요해요");
            }
            endpoint = normalizeImageEndpoint(endpoint);
            if (isGeminiGenerateContentEndpoint(endpoint)) {
                bytes = fetchGeminiStyle(prompt.trim(), key, endpoint);
            } else {
                bytes = fetchOpenAiStyle(prompt.trim(), key, modelName, endpoint);
            }
        } else if (LumiSettings.IMAGE_GEN_PROVIDER_FAL.equals(provider)) {
            if (modelName.isEmpty()) {
                modelName = "fal-ai/flux/dev";
            }
            endpoint = normalizeFalEndpoint(endpoint, modelName);
            bytes = fetchFalStyle(prompt.trim(), key, endpoint);
        } else {
            if (modelName.isEmpty()) {
                modelName = "gpt-image-1";
            }
            bytes = fetchOpenAiStyle(prompt.trim(), key, modelName, OPENAI_IMAGES_URL);
        }
        return writeToCache(bytes);
    }

    /**
     * 이미지 API 연결/인증 설정을 가볍게 점검한다.
     */
    public String testConnection(String userFacingProvider, String apiKey, String model, String baseUrl)
            throws IOException {
        String provider = userFacingProvider == null ? "" : userFacingProvider.trim();
        String key = apiKey == null ? "" : apiKey.trim();
        String modelName = normalizeImageModelName(model);
        String endpoint = baseUrl == null ? "" : baseUrl.trim();

        if (key.isEmpty()) {
            throw new IOException("이미지 생성 API 키가 비어 있어요");
        }
        byte[] bytes;
        if (LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            if (modelName.isEmpty()) {
                modelName = "gpt-image-1";
            }
            if (endpoint.isEmpty()) {
                throw new IOException("이미지 엔드포인트 URL이 필요해요");
            }
            endpoint = normalizeImageEndpoint(endpoint);
            if (isGeminiGenerateContentEndpoint(endpoint)) {
                bytes = fetchGeminiStyle("simple circle icon", key, endpoint);
            } else {
                bytes = fetchOpenAiStyle("simple circle icon", key, modelName, endpoint);
            }
        } else if (LumiSettings.IMAGE_GEN_PROVIDER_FAL.equals(provider)) {
            if (modelName.isEmpty()) {
                modelName = "fal-ai/flux/dev";
            }
            endpoint = normalizeFalEndpoint(endpoint, modelName);
            bytes = fetchFalStyle("simple circle icon", key, endpoint);
        } else {
            if (modelName.isEmpty()) {
                modelName = "gpt-image-1";
            }
            bytes = fetchOpenAiStyle("simple circle icon", key, modelName, OPENAI_IMAGES_URL);
        }
        return "ok(" + bytes.length + " bytes)";
    }

    private byte[] fetchFalStyle(String prompt,
                                 String apiKey,
                                 String endpoint) throws IOException {
        boolean nsfwAllowed = settings != null && settings.isImageGenNsfwEnabled();
        try {
            return fetchFalStyleOnce(prompt, apiKey, endpoint, true, nsfwAllowed);
        } catch (ImageApiException firstError) {
            if (firstError.isBadRequestLike()) {
                return fetchFalStyleOnce(prompt, apiKey, endpoint, false, nsfwAllowed);
            }
            throw firstError;
        }
    }

    private byte[] fetchFalStyleOnce(String prompt,
                                     String apiKey,
                                     String endpoint,
                                     boolean includeSafetyChecker,
                                     boolean nsfwAllowed) throws IOException {
        JSONObject body;
        try {
            body = new JSONObject()
                    .put("prompt", prompt)
                    .put("num_images", 1);
            if (includeSafetyChecker) {
                body.put("enable_safety_checker", !nsfwAllowed);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("JSON 오류: " + e.getMessage(), e);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Key " + apiKey);
            conn.setRequestProperty("User-Agent", "Lumi/1.0 (Android)");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = safeReadString(conn.getErrorStream());
                if (isFalUserLocked(code, err)) {
                    throw new IOException("FAL 계정이 잠겨 있어요. fal.ai 대시보드에서 계정/결제 상태를 확인하거나 새 API 키를 발급해 주세요. 원문: USER IS LOCKED");
                }
                throw new ImageApiException(code, err);
            }
            String contentType = conn.getContentType();
            try (InputStream in = conn.getInputStream()) {
                byte[] responseBytes = readLimitedBytes(in, MAX_JSON_BYTES, "이미지 응답이 너무 커요");
                if (isImageResponse(contentType, responseBytes)) {
                    if (responseBytes.length > MAX_BYTES) throw new IOException("이미지가 너무 커요");
                    return rejectBlackFalPlaceholder(responseBytes, nsfwAllowed);
                }
                String json = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!isLikelyJson(contentType, json)) {
                    throw new IOException("FAL 응답 형식이 올바르지 않아요"
                            + formatContentType(contentType)
                            + previewResponse(json));
                }
                try {
                    return rejectBlackFalPlaceholder(parseFalJson(json), nsfwAllowed);
                } catch (org.json.JSONException e) {
                    throw new IOException("FAL JSON 파싱 오류: " + e.getMessage(), e);
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] fetchOpenAiStyle(String prompt,
                                    String apiKey,
                                    String model,
                                    String endpoint) throws IOException {
        boolean nsfwAllowed = settings != null && settings.isImageGenNsfwEnabled();
        boolean includeResponseFormat = shouldIncludeResponseFormat(model);
        try {
            return fetchOpenAiStyleOnce(prompt, apiKey, model, endpoint, includeResponseFormat, nsfwAllowed);
        } catch (ImageApiException firstError) {
            if (firstError.isBadRequestLike() && (includeResponseFormat || nsfwAllowed)) {
                return fetchOpenAiStyleOnce(prompt, apiKey, model, endpoint, false, false);
            }
            throw firstError;
        }
    }

    private byte[] fetchOpenAiStyleOnce(String prompt,
                                        String apiKey,
                                        String model,
                                        String endpoint,
                                        boolean includeResponseFormat,
                                        boolean includeModeration) throws IOException {
        JSONObject body = null;
        try {
            body = new JSONObject()
                .put("model", model)
                .put("prompt", prompt)
                .put("size", "1024x1024");
        if (includeResponseFormat) {
            body.put("response_format", "b64_json");
        }
        if (includeModeration) {
            // 일부 OpenAI 호환 서버가 허용하는 옵션. OpenAI 공식 서버는 무시할 수 있음.
            body.put("moderation", "low");
        }
        } catch (org.json.JSONException e) {
            throw new IOException("JSON 오류: " + e.getMessage(), e);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("User-Agent", "Lumi/1.0 (Android)");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = safeReadString(conn.getErrorStream());
                throw new ImageApiException(code, err);
            }
            String contentType = conn.getContentType();
            try (InputStream in = conn.getInputStream()) {
                byte[] responseBytes = readLimitedBytes(in, MAX_JSON_BYTES, "이미지 응답이 너무 커요");
                if (isImageResponse(contentType, responseBytes)) {
                    if (responseBytes.length > MAX_BYTES) throw new IOException("이미지가 너무 커요");
                    return responseBytes;
                }
                String json = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!isLikelyJson(contentType, json)) {
                    throw new IOException("이미지 서버 응답 형식이 올바르지 않아요"
                            + formatContentType(contentType)
                            + previewResponse(json));
                }
                try {
                    return parseImageJson(json, endpoint);
                } catch (org.json.JSONException e) {
                    throw new IOException("JSON 파싱 오류: " + e.getMessage(), e);
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] fetchGeminiStyle(String prompt,
                                    String apiKey,
                                    String endpoint) throws IOException {
        JSONObject body;
        try {
            body = new JSONObject()
                    .put("contents", new JSONArray().put(
                            new JSONObject().put("parts", new JSONArray().put(
                                    new JSONObject().put("text", prompt)
                            ))
                    ))
                    .put("generationConfig", new JSONObject()
                            .put("responseModalities", new JSONArray().put("IMAGE"))
                    );
        } catch (org.json.JSONException e) {
            throw new IOException("JSON 오류: " + e.getMessage(), e);
        }

        HttpURLConnection conn = null;
        try {
            String finalUrl = appendGeminiApiKey(endpoint, apiKey);
            conn = (HttpURLConnection) new URL(finalUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setRequestProperty("User-Agent", "Lumi/1.0 (Android)");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = safeReadString(conn.getErrorStream());
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new IOException("Gemini API 인증 실패(" + code + "). API 키 권한/결제/Generative Language API 활성화를 확인해 주세요."
                            + (err.isEmpty() ? "" : " 원문: " + err));
                }
                throw new ImageApiException(code, err);
            }
            String contentType = conn.getContentType();
            try (InputStream in = conn.getInputStream()) {
                byte[] responseBytes = readLimitedBytes(in, MAX_JSON_BYTES, "이미지 응답이 너무 커요");
                if (isImageResponse(contentType, responseBytes)) {
                    if (responseBytes.length > MAX_BYTES) throw new IOException("이미지가 너무 커요");
                    return responseBytes;
                }
                String json = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!isLikelyJson(contentType, json)) {
                    throw new IOException("Gemini 응답 형식이 올바르지 않아요"
                            + formatContentType(contentType)
                            + previewResponse(json));
                }
                try {
                    return parseGeminiImageJson(json);
                } catch (org.json.JSONException e) {
                    throw new IOException("Gemini JSON 파싱 오류: " + e.getMessage(), e);
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] parseGeminiImageJson(String json) throws IOException, org.json.JSONException {
        JSONObject root = new JSONObject(json == null ? "" : json.trim());
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates != null) {
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject candidate = candidates.optJSONObject(i);
                if (candidate == null) continue;
                JSONObject content = candidate.optJSONObject("content");
                if (content == null) continue;
                JSONArray parts = content.optJSONArray("parts");
                if (parts == null) continue;
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.optJSONObject(j);
                    if (part == null) continue;
                    JSONObject inlineData = part.optJSONObject("inlineData");
                    if (inlineData == null) continue;
                    String b64 = inlineData.optString("data", "");
                    if (!b64.isEmpty()) {
                        return decodeBase64Image(b64);
                    }
                }
            }
        }
        throw new IOException("Gemini 응답에 이미지 데이터가 없어요. candidates[].content.parts[].inlineData.data가 필요해요.");
    }

    private boolean isGeminiGenerateContentEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) return false;
        try {
            URL url = new URL(endpoint.trim());
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.US);
            String path = url.getPath() == null ? "" : url.getPath().toLowerCase(Locale.US);
            return host.contains("generativelanguage.googleapis.com")
                    && path.contains(":generatecontent");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String appendGeminiApiKey(String endpoint, String apiKey) throws IOException {
        String trimmedEndpoint = endpoint == null ? "" : endpoint.trim();
        if (trimmedEndpoint.isEmpty()) {
            throw new IOException("Gemini 엔드포인트 URL이 비어 있어요");
        }
        if (trimmedEndpoint.contains("key=")) {
            return trimmedEndpoint;
        }
        String encodedKey;
        try {
            encodedKey = URLEncoder.encode(apiKey, "UTF-8");
        } catch (Throwable t) {
            encodedKey = apiKey;
        }
        return trimmedEndpoint + (trimmedEndpoint.contains("?") ? "&" : "?") + "key=" + encodedKey;
    }

    private boolean isFalUserLocked(int statusCode, String responseBody) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.US);
        return statusCode == HttpURLConnection.HTTP_FORBIDDEN
                && body.contains("user is locked");
    }

    private boolean shouldIncludeResponseFormat(String model) {
        String normalized = model == null ? "" : model.trim().toLowerCase(Locale.US);
        return !normalized.startsWith("gpt-image-")
                && !NANOBANANA2_CANONICAL_MODEL.equals(normalized);
    }

    private String normalizeImageModelName(String model) {
        String normalized = model == null ? "" : model.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String lowered = normalized.toLowerCase(Locale.US).replace(" ", "");
        if (NANOBANANA2_MODEL_ALIASES.contains(lowered)) {
            return NANOBANANA2_CANONICAL_MODEL;
        }
        return normalized;
    }

    private String normalizeImageEndpoint(String endpoint) throws IOException {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isEmpty()) {
            throw new IOException("이미지 엔드포인트 URL이 필요해요");
        }
        try {
            new URL(normalized);
        } catch (Throwable t) {
            throw new IOException("이미지 엔드포인트 URL이 올바르지 않아요");
        }

        String withoutTrailingSlash = normalized;
        while (withoutTrailingSlash.endsWith("/")) {
            withoutTrailingSlash = withoutTrailingSlash.substring(0, withoutTrailingSlash.length() - 1);
        }
        String lowerWithoutTrailingSlash = withoutTrailingSlash.toLowerCase(Locale.US);
        if (lowerWithoutTrailingSlash.endsWith(OPENAI_IMAGES_PATH)
                || lowerWithoutTrailingSlash.contains(OPENAI_IMAGES_PATH + "?")) {
            return withoutTrailingSlash;
        }
        if (lowerWithoutTrailingSlash.endsWith("/v1")
                || lowerWithoutTrailingSlash.endsWith("/v1beta")
                || lowerWithoutTrailingSlash.endsWith("/openai")) {
            return withoutTrailingSlash + OPENAI_IMAGES_PATH;
        }
        try {
            String path = new URL(withoutTrailingSlash).getPath();
            if (path != null && path.length() > 1) {
                return withoutTrailingSlash;
            }
        } catch (Throwable ignored) {}
        return withoutTrailingSlash + "/v1" + OPENAI_IMAGES_PATH;
    }

    private String normalizeFalEndpoint(String endpoint, String model) throws IOException {
        String modelPath = model == null ? "" : model.trim();
        while (modelPath.startsWith("/")) {
            modelPath = modelPath.substring(1);
        }
        if (modelPath.isEmpty()) {
            throw new IOException("FAL 모델 ID가 필요해요");
        }
        if (modelPath.startsWith("http://") || modelPath.startsWith("https://")) {
            try {
                new URL(modelPath);
                return trimTrailingSlashes(modelPath);
            } catch (Throwable t) {
                throw new IOException("FAL 모델 URL이 올바르지 않아요");
            }
        }

        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isEmpty()) {
            normalized = FAL_RUN_URL;
        }
        try {
            new URL(normalized);
        } catch (Throwable t) {
            throw new IOException("FAL API URL이 올바르지 않아요");
        }

        String withoutTrailingSlash = trimTrailingSlashes(normalized);
        String lowerEndpoint = withoutTrailingSlash.toLowerCase(Locale.US);
        String lowerModelPath = modelPath.toLowerCase(Locale.US);
        if (lowerEndpoint.endsWith("/" + lowerModelPath)) {
            return withoutTrailingSlash;
        }
        return withoutTrailingSlash + "/" + modelPath;
    }

    private String trimTrailingSlashes(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static class ImageApiException extends IOException {
        private final int statusCode;

        ImageApiException(int statusCode, String responseBody) {
            super("이미지 서버 응답 " + statusCode
                    + (responseBody == null || responseBody.trim().isEmpty()
                    ? ""
                    : ": " + responseBody.trim()));
            this.statusCode = statusCode;
        }

        boolean isBadRequestLike() {
            return statusCode == HttpURLConnection.HTTP_BAD_REQUEST || statusCode == 422;
        }
    }

    private byte[] parseImageJson(String json, String responseEndpoint) throws IOException, org.json.JSONException {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("이미지 응답이 비어 있어요");
        }
        byte[] bytes;
        if (trimmed.startsWith("[")) {
            bytes = parseImageArray(new JSONArray(trimmed), responseEndpoint);
            if (bytes != null) {
                return bytes;
            }
            throw new IOException("이미지 데이터가 없어요");
        }

        JSONObject root = new JSONObject(trimmed);
        bytes = parseImageObject(root, responseEndpoint);
        if (bytes != null) {
            return bytes;
        }
        throw new IOException("이미지 데이터가 없어요. 서버 응답에는 data[0].url, image_url, images[0].url, output URL 또는 b64_json 중 하나가 필요해요.");
    }

    private byte[] parseImageObject(JSONObject object, String responseEndpoint) throws IOException, org.json.JSONException {
        if (object == null) {
            return null;
        }
        String b64 = firstNonEmpty(
                object.optString("b64_json", ""),
                object.optString("base64", ""),
                object.optString("image_base64", ""));
        if (!b64.isEmpty()) {
            return decodeBase64Image(b64);
        }

        String imageRef = firstNonEmpty(
                object.optString("url", ""),
                object.optString("image_url", ""),
                object.optString("file_url", ""),
                object.optString("download_url", ""),
                object.optString("path", ""),
                object.optString("image_path", ""),
                object.optString("filename", ""));
        if (!imageRef.isEmpty()) {
            return fetchImageReference(imageRef, responseEndpoint);
        }

        byte[] bytes = parseImageValue(object.opt("image"), responseEndpoint);
        if (bytes != null) return bytes;
        bytes = parseImageValue(object.opt("output"), responseEndpoint);
        if (bytes != null) return bytes;
        bytes = parseImageValue(object.opt("result"), responseEndpoint);
        if (bytes != null) return bytes;
        bytes = parseImageValue(object.opt("data"), responseEndpoint);
        if (bytes != null) return bytes;
        bytes = parseImageValue(object.opt("images"), responseEndpoint);
        if (bytes != null) return bytes;
        return parseImageValue(object.opt("artifacts"), responseEndpoint);
    }

    private byte[] parseImageArray(JSONArray array, String responseEndpoint) throws IOException, org.json.JSONException {
        if (array == null || array.length() == 0) {
            return null;
        }
        for (int i = 0; i < array.length(); i++) {
            byte[] bytes = parseImageValue(array.opt(i), responseEndpoint);
            if (bytes != null) {
                return bytes;
            }
        }
        return null;
    }

    private byte[] parseImageValue(Object value, String responseEndpoint) throws IOException, org.json.JSONException {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            return parseImageObject((JSONObject) value, responseEndpoint);
        }
        if (value instanceof JSONArray) {
            return parseImageArray((JSONArray) value, responseEndpoint);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            if (looksLikeBase64Image(text)) {
                return decodeBase64Image(text);
            }
            if (looksLikeImageReference(text)) {
                return fetchImageReference(text, responseEndpoint);
            }
        }
        return null;
    }

    private boolean looksLikeBase64Image(String value) {
        String text = value == null ? "" : value.trim();
        return text.startsWith("data:image/")
                || (text.length() > 256 && text.matches("^[A-Za-z0-9+/=\\r\\n]+$"));
    }

    private boolean looksLikeImageReference(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return text.startsWith("http://")
                || text.startsWith("https://")
                || text.startsWith("/")
                || text.startsWith("outputs/")
                || text.startsWith("output/")
                || text.startsWith("static/")
                || text.startsWith("files/")
                || text.contains(".png")
                || text.contains(".jpg")
                || text.contains(".jpeg")
                || text.contains(".webp")
                || text.contains(".gif");
    }

    private byte[] fetchImageReference(String reference, String responseEndpoint) throws IOException {
        String normalized = reference == null ? "" : reference.trim();
        if (normalized.startsWith("data:image/")) {
            return decodeBase64Image(normalized);
        }
        return downloadBytes(resolveImageUrl(normalized, responseEndpoint));
    }

    private String resolveImageUrl(String reference, String responseEndpoint) throws IOException {
        String normalized = reference == null ? "" : reference.trim().replace(" ", "%20");
        if (normalized.isEmpty()) {
            throw new IOException("이미지 URL이 비어 있어요");
        }
        try {
            URL endpointUrl = null;
            if (responseEndpoint != null && !responseEndpoint.trim().isEmpty()) {
                endpointUrl = new URL(responseEndpoint.trim());
            }
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                URL imageUrl = new URL(normalized);
                if (endpointUrl != null
                        && isLoopbackHost(imageUrl.getHost())
                        && !isLoopbackHost(endpointUrl.getHost())) {
                    return new URL(endpointUrl.getProtocol(), endpointUrl.getHost(), endpointUrl.getPort(), imageUrl.getFile()).toString();
                }
                return imageUrl.toString();
            }
            if (endpointUrl == null) {
                throw new IOException("이미지 URL이 절대 URL이 아니에요: " + normalized);
            }
            String path = normalized.startsWith("/") ? normalized : "/" + normalized;
            return new URL(endpointUrl.getProtocol(), endpointUrl.getHost(), endpointUrl.getPort(), path).toString();
        } catch (MalformedURLException e) {
            throw new IOException("이미지 URL이 올바르지 않아요: " + normalized, e);
        }
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.US);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("127.");
    }

    private byte[] parseFalJson(String json) throws IOException, org.json.JSONException {
        JSONObject root = new JSONObject(json);
        if (hasTrueValue(root.optJSONArray("has_nsfw_concepts"))) {
            throw new IOException("FAL이 안전 필터 결과로 이미지를 차단했어요. 설정에서 '민감한 이미지 허용'을 켜거나 프롬프트를 덜 민감하게 바꿔 주세요.");
        }

        JSONArray images = root.optJSONArray("images");
        byte[] imageBytes = parseFalImageArray(images);
        if (imageBytes != null) {
            return imageBytes;
        }

        JSONObject image = root.optJSONObject("image");
        imageBytes = parseFalImageObject(image);
        if (imageBytes != null) {
            return imageBytes;
        }

        String imageUrl = firstNonEmpty(
                root.optString("image_url", ""),
                root.optString("url", ""));
        if (!imageUrl.isEmpty()) {
            return downloadBytes(imageUrl);
        }

        JSONArray data = root.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject first = data.optJSONObject(0);
            imageBytes = parseFalImageObject(first);
            if (imageBytes != null) {
                return imageBytes;
            }
        }

        throw new IOException("FAL 이미지 응답이 비어 있어요");
    }

    private boolean hasTrueValue(JSONArray array) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) {
            if (array.optBoolean(i, false)) return true;
        }
        return false;
    }

    private byte[] rejectBlackFalPlaceholder(byte[] bytes, boolean nsfwAllowed) throws IOException {
        if (!isMostlyBlackImage(bytes)) {
            return bytes;
        }
        if (nsfwAllowed) {
            throw new IOException("FAL이 검은 이미지를 반환했어요. 모델을 fal-ai/flux/schnell 등으로 바꾸거나 프롬프트를 더 구체적으로 바꿔 주세요.");
        }
        throw new IOException("FAL이 안전 필터 결과로 검은 이미지를 반환했어요. 설정에서 '민감한 이미지 허용'을 켜거나 프롬프트를 덜 민감하게 바꿔 주세요.");
    }

    private boolean isMostlyBlackImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, 96);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) return false;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int total = width * height;
            if (total == 0) return false;
            int black = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int alpha = (pixel >>> 24) & 0xff;
                    if (alpha < 16) continue;
                    int red = (pixel >>> 16) & 0xff;
                    int green = (pixel >>> 8) & 0xff;
                    int blue = pixel & 0xff;
                    if (red <= BLACK_CHANNEL_MAX
                            && green <= BLACK_CHANNEL_MAX
                            && blue <= BLACK_CHANNEL_MAX) {
                        black++;
                    }
                }
            }
            return black / (double) total >= BLACK_PIXEL_RATIO;
        } finally {
            bitmap.recycle();
        }
    }

    private int sampleSizeFor(int width, int height, int targetMaxSide) {
        int sampleSize = 1;
        int maxSide = Math.max(width, height);
        while (maxSide / sampleSize > targetMaxSide) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private byte[] parseFalImageArray(JSONArray images) throws IOException, org.json.JSONException {
        if (images == null || images.length() == 0) {
            return null;
        }
        JSONObject first = images.optJSONObject(0);
        return parseFalImageObject(first);
    }

    private byte[] parseFalImageObject(JSONObject image) throws IOException, org.json.JSONException {
        if (image == null) {
            return null;
        }
        String url = image.optString("url", "");
        if (!url.isEmpty()) {
            return downloadBytes(url);
        }
        String b64 = firstNonEmpty(
                image.optString("b64_json", ""),
                image.optString("base64", ""),
                image.optString("content", ""));
        if (!b64.isEmpty()) {
            return decodeBase64Image(b64);
        }
        return null;
    }

    private byte[] decodeBase64Image(String b64) throws IOException {
        String data = b64 == null ? "" : b64.trim();
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma >= 0) {
            data = data.substring(comma + 1);
        }
        byte[] decoded;
        try {
            decoded = Base64.decode(data, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new IOException("이미지 데이터가 올바르지 않아요", e);
        }
        if (decoded.length > MAX_BYTES) throw new IOException("이미지가 너무 커요");
        return decoded;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "image/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("이미지 다운로드 실패: " + code);
            }
            String contentType = conn.getContentType();
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = readLimitedBytes(in, MAX_BYTES, "이미지가 너무 커요");
                if (!isImageResponse(contentType, bytes)) {
                    throw new IOException("이미지 다운로드 응답이 이미지가 아니에요"
                            + formatContentType(contentType)
                            + previewResponse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
                }
                return bytes;
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String safeReadString(InputStream in) throws IOException {
        return safeReadString(in, MAX_ERROR_BYTES);
    }

    private String safeReadString(InputStream in, int maxBytes) throws IOException {
        if (in == null) return "";
        return new String(readLimitedBytes(in, maxBytes, "응답이 너무 커요"),
                java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private byte[] readLimitedBytes(InputStream in, int maxBytes, String tooLargeMessage) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > maxBytes) {
                throw new IOException(tooLargeMessage);
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private boolean isLikelyJson(String contentType, String text) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        String trimmed = text == null ? "" : text.trim();
        return normalizedContentType.contains("json")
                || trimmed.startsWith("{")
                || trimmed.startsWith("[");
    }

    private boolean isImageResponse(String contentType, byte[] bytes) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        return normalizedContentType.startsWith("image/") || hasImageMagic(bytes);
    }

    private boolean hasImageMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        boolean png = bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47;
        boolean jpeg = (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8;
        boolean gif = bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46;
        boolean webp = bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50;
        return png || jpeg || gif || webp;
    }

    private String formatContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) return "";
        return " (Content-Type: " + contentType.trim() + ")";
    }

    private String previewResponse(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String normalized = text.trim().replace('\n', ' ').replace('\r', ' ');
        int max = Math.min(normalized.length(), 160);
        return ": " + normalized.substring(0, max);
    }

    private String writeToCache(byte[] bytes) throws IOException {
        if (!hasImageMagic(bytes)) {
            throw new IOException("이미지 데이터가 올바르지 않아요");
        }
        File dir = new File(appContext.getCacheDir(), "lumi_images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("이미지 캐시 폴더를 만들 수 없어요");
        }
        // 너무 오래된 캐시는 미리 정리 (7일)
        pruneOld(dir, 7L * 24 * 60 * 60 * 1000);
        File f = new File(dir, "img_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }
        return f.getAbsolutePath();
    }

    private void pruneOld(File dir, long maxAgeMs) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - maxAgeMs;
            for (File f : files) {
                if (f.isFile() && f.lastModified() < cutoff) {
                    // stale cache best-effort cleanup
                    f.delete();
                }
            }
        } catch (Throwable ignored) {}
    }
}
