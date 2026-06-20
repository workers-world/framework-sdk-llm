package com.books.framework.llm.config;

import com.books.framework.logging.OutboundHttpLogInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class LlmHttpClientSupport {

    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    public LlmHttpClientSupport(ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        this.restClientBuilderProvider = restClientBuilderProvider;
    }

    public RestClient.Builder outboundRestClientBuilder() {
        RestClient.Builder source = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        return source.clone()
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .requestInterceptor(new OutboundHttpLogInterceptor());
    }
}
