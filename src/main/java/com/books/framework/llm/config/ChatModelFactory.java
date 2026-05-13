package com.books.framework.llm.config;

import com.books.framework.llm.model.LLMBackendConfig;
import org.springframework.ai.chat.model.ChatModel;

public interface ChatModelFactory {
    boolean supports(String provider);
    ChatModel create(LLMBackendConfig cfg);
}
