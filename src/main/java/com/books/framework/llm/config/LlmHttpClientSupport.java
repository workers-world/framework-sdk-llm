package com.books.framework.llm.config;

import com.books.framework.logging.OutboundHttpLogInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class LlmHttpClientSupport {

    private static final java.time.Duration CONNECT_TIMEOUT = java.time.Duration.ofSeconds(10);
    private static final java.time.Duration READ_TIMEOUT = java.time.Duration.ofSeconds(60);

    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    public LlmHttpClientSupport(ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        this.restClientBuilderProvider = restClientBuilderProvider;
    }

    public RestClient.Builder outboundRestClientBuilder() {
        RestClient.Builder source = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        // 历史版本不设超时(JDK 默认无限),Worker 网关无响应时调用线程永久悬挂;
        // 且无条件 BufferingFactory 会在日志关闭时仍全量缓冲响应体
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return source.clone()
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .requestInterceptor(new OutboundHttpLogInterceptor());
    }
}
