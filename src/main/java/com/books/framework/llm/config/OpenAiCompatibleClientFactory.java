package com.books.framework.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleClientFactory {

    private OpenAiCompatibleClientFactory() {
    }

    public static OpenAIClient create(String baseUrl, String apiKey) {
        return create(baseUrl, apiKey, Map.of(), 0);
    }

    public static OpenAIClient create(String baseUrl, String apiKey, Map<String, String> extraHeaders) {
        return create(baseUrl, apiKey, extraHeaders, 0);
    }

    /**
     * @param timeoutMs 总请求超时(毫秒);&lt;=0 时使用 openai-java 库默认值。
     *                  历史版本从不调用 timeout(),llm.default-timeout-ms 与 DB timeout_ms
     *                  均为死配置,请求挂起时会悬挂到库默认(约 10 分钟)。
     */
    public static OpenAIClient create(String baseUrl, String apiKey, Map<String, String> extraHeaders, long timeoutMs) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .baseUrl(normalizeOpenAiBaseUrl(baseUrl))
                .apiKey(StringUtils.hasText(apiKey) ? apiKey.trim() : "sk-placeholder");
        if (timeoutMs > 0) {
            builder.timeout(Duration.ofMillis(timeoutMs));
        }
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
