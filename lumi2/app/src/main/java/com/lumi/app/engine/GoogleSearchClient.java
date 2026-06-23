package com.lumi.app.engine;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleSearchClient {
    public static class SearchResult {
        public final String title;
        public final String url;
        public final String snippet;

        public SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }

    public List<SearchResult> search(String apiKey, String cx, String query, int limit) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Google Search API 키가 비어 있습니다.");
        }
        if (cx == null || cx.trim().isEmpty()) {
            throw new IOException("Google Search Engine ID(cx)가 비어 있습니다.");
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        int size = Math.max(1, Math.min(limit, 5));
        String url = "https://www.googleapis.com/customsearch/v1"
                + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
                + "&cx=" + URLEncoder.encode(cx, StandardCharsets.UTF_8.name())
                + "&hl=ko"
                + "&safe=active"
                + "&num=" + size
                + "&q=" + encoded;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code;
        String text;
        try {
            code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            text = readAll(is);
        } finally {
            conn.disconnect();
        }

        if (code < 200 || code >= 300) {
            throw new IOException("검색 API 오류 (HTTP " + code + "): " + trim(text, 220));
        }

        return parseResults(text, size);
    }

    /**
     * Search without any search engine API key by parsing DuckDuckGo HTML results.
     */
    public List<SearchResult> searchWithoutApi(String query, int limit) throws IOException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        int size = Math.max(1, Math.min(limit, 5));
        String url = "https://html.duckduckgo.com/html/?kl=kr-ko&kp=1&q=" + encoded;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Lumi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");

        int code;
        String html;
        try {
            code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            html = readAll(is);
        } finally {
            conn.disconnect();
        }

        if (code < 200 || code >= 300) {
            throw new IOException("비API 검색 실패 (HTTP " + code + "): " + trim(html, 220));
        }

        return parseDuckDuckGoHtml(html, size);
    }

    /**
     * Fetches destination page title/description for a lightweight verification signal.
     */
    public String fetchPageSummary(String pageUrl) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) return "";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(pageUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Lumi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return "";
            String html = readAll(conn.getInputStream());
            if (html.isEmpty()) return "";

            String title = matchFirst(html, "<title[^>]*>(.*?)</title>");
            String desc = matchFirst(html,
                    "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"'](.*?)[\"'][^>]*>");
            if (desc.isEmpty()) {
                desc = matchFirst(html,
                        "<meta[^>]*content=[\"'](.*?)[\"'][^>]*name=[\"']description[\"'][^>]*>");
            }
            String combined = (clean(title) + " " + clean(desc)).trim();
            return trim(combined, 220);
        } catch (Exception ignored) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private List<SearchResult> parseResults(String body, int limit) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        if (body == null || body.isEmpty()) return results;

        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("items")) return results;
            org.json.JSONArray items = root.getJSONArray("items");
            for (int i = 0; i < items.length() && results.size() < limit; i++) {
                JSONObject it = items.getJSONObject(i);
                String title = it.optString("title", "").trim();
                String link = it.optString("link", "").trim();
                String snippet = it.optString("snippet", "").trim();
                if (title.isEmpty() && snippet.isEmpty()) continue;
                results.add(new SearchResult(title, link, snippet));
            }
            return results;
        } catch (Exception e) {
            throw new IOException("검색 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private List<SearchResult> parseDuckDuckGoHtml(String html, int limit) {
        List<SearchResult> results = new ArrayList<>();
        if (html == null || html.isEmpty()) return results;

        Pattern anchorPattern = Pattern.compile("<a\\b[^>]*class=[\"'][^\"']*result__a[^\"']*[\"'][^>]*>.*?</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern snippetPattern = Pattern.compile("<(?:a|div)[^>]*class=[\"'][^\"']*result__snippet[^\"']*[\"'][^>]*>(.*?)</(?:a|div)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher anchorMatcher = anchorPattern.matcher(html);
        while (anchorMatcher.find() && results.size() < limit) {
            String anchorHtml = anchorMatcher.group(0);
            String rawUrl = matchFirst(anchorHtml, "href=[\"'](.*?)[\"']");
            String title = clean(anchorHtml);
            String url = decodeDuckDuckGoRedirect(rawUrl);

            int blockEnd = Math.min(html.length(), anchorMatcher.end() + 2000);
            Matcher snippetMatcher = snippetPattern.matcher(html.substring(anchorMatcher.end(), blockEnd));
            String snippet = snippetMatcher.find() ? clean(snippetMatcher.group(1)) : "";

            if (title.isEmpty() && snippet.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        if (!results.isEmpty()) return results;

        Pattern itemPattern = Pattern.compile("<div[^>]*class=\"result__body\"[^>]*>(.*?)</div>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern titlePattern = Pattern.compile("<a[^>]*class=\"result__a\"[^>]*href=\"(.*?)\"[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern legacySnippetPattern = Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher itemMatcher = itemPattern.matcher(html);
        while (itemMatcher.find() && results.size() < limit) {
            String block = itemMatcher.group(1);
            Matcher titleMatcher = titlePattern.matcher(block);
            if (!titleMatcher.find()) continue;

            String rawUrl = titleMatcher.group(1);
            String title = clean(titleMatcher.group(2));
            String url = decodeDuckDuckGoRedirect(rawUrl);

            String snippet = "";
            Matcher snMatcher = legacySnippetPattern.matcher(block);
            if (snMatcher.find()) snippet = clean(snMatcher.group(1));

            if (title.isEmpty() && snippet.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    private String decodeDuckDuckGoRedirect(String url) {
        if (url == null) return "";
        String out = htmlDecode(url);
        int idx = out.indexOf("uddg=");
        if (idx >= 0) {
            String encoded = out.substring(idx + 5);
            int amp = encoded.indexOf('&');
            if (amp >= 0) encoded = encoded.substring(0, amp);
            try {
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                return encoded;
            }
        }
        return out;
    }

    private String matchFirst(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    private String clean(String text) {
        if (text == null) return "";
        String out = text.replaceAll("<[^>]+>", " ");
        out = htmlDecode(out).replaceAll("\\s+", " ").trim();
        return out;
    }

    private String htmlDecode(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}