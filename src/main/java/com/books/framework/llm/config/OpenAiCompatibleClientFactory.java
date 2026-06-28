package com.books.framework.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleClientFactory {

    private OpenAiCompatibleClientFactory() {
    }

    public static OpenAIClient create(String baseUrl, String apiKey) {
        return create(baseUrl, apiKey, Map.of());
    }

    public static OpenAIClient create(String baseUrl, String apiKey, Map<String, String> extraHeaders) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .baseUrl(normalizeOpenAiBaseUrl(baseUrl))
                .apiKey(StringUtils.hasText(apiKey) ? apiKey.trim() : "sk-placeholder");
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            Map<String, Iterable<String>> headers = new HashMap<>();
            extraHeaders.forEach((k, v) -> headers.put(k, List.of(v)));
            builder.headers(headers);
        }
        return builder.build();
    }

    public static String normalizeOpenAiBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        if (!trimmed.endsWith("/v1")) {
            trimmed = trimmed + "/v1";
        }
        return trimmed;
    }
}
