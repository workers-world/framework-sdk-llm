package com.books.framework.llm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class LlmHttpClientSupportTest {

    @Test
    void shouldBuildOutboundRestClientBuilder() {
        ObjectProvider<RestClient.Builder> provider = new ObjectProvider<>() {
            @Override
            public RestClient.Builder getObject() {
                return RestClient.builder();
            }

            @Override
            public RestClient.Builder getObject(Object... args) {
                return getObject();
            }

            @Override
            public RestClient.Builder getIfAvailable() {
                return getObject();
            }

            @Override
            public RestClient.Builder getIfUnique() {
                return getObject();
            }
        };
        LlmHttpClientSupport support = new LlmHttpClientSupport(provider);

        RestClient client = support.outboundRestClientBuilder().build();

        assertThat(client).isNotNull();
    }
}
