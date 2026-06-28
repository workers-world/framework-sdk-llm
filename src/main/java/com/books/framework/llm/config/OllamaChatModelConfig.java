package com.books.framework.llm.config;

import com.books.framework.llm.model.LLMBackendConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

public class OllamaChatModelConfig implements ChatModelFactory {

    private final LlmHttpClientSupport httpClientSupport;

    public OllamaChatModelConfig(LlmHttpClientSupport httpClientSupport) {
        this.httpClientSupport = httpClientSupport;
    }

    @Override
    public boolean supports(String provider) {
        return "ollama".equalsIgnoreCase(provider);
    }

    @Override
    public ChatModel create(LLMBackendConfig cfg) {
        String baseUrl = cfg.getEndpoint() != null ? cfg.getEndpoint() : "http://localhost:11434";
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(httpClientSupport.outboundRestClientBuilder())
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model(cfg.getDefaultModel()).build())
                .build();
    }
}
