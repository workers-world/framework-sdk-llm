package com.books.framework.llm.config;

import com.books.framework.llm.model.LLMBackendConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(OllamaChatModel.class)
public class OllamaChatModelConfig implements ChatModelFactory {

    @Override
    public boolean supports(String provider) {
        return "ollama".equalsIgnoreCase(provider);
    }

    @Override
    public ChatModel create(LLMBackendConfig cfg) {
        String baseUrl = cfg.getEndpoint() != null ? cfg.getEndpoint() : "http://localhost:11434";
        OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaChatOptions.builder().model(cfg.getDefaultModel()).build())
                .build();
    }
}
