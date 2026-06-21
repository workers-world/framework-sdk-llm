package com.books.framework.llm;

import com.books.framework.llm.model.LLMBackendConfig;
import com.books.framework.llm.model.LLMCallOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public interface LLMService {
    ChatResponse execute(Prompt prompt, LLMCallOptions options);
    Flux<ChatResponse> executeStream(Prompt prompt, LLMCallOptions options);
    Future<ChatResponse> executeAsync(Prompt prompt, LLMCallOptions options);
    List<ChatResponse> executeBatch(List<Prompt> prompts, LLMCallOptions options);
    ChatResponse getAsyncResult(String taskId);
    void refresh(Map<String, ChatClient> chatClients, List<LLMBackendConfig> configs);

    /** 执行调用并自动将响应 JSON 映射到目标类型 */
    default <T> T executeForType(Prompt prompt, LLMCallOptions options, Class<T> targetType) {
        ChatResponse response = execute(prompt, options);
        String content = getContent(response);
        if (content == null || content.isEmpty()) return null;
        try {
            return new ObjectMapper().readValue(content, targetType);
        } catch (Exception e) {
            throw new RuntimeException("结构化输出解析失败: " + e.getMessage(), e);
        }
    }

    private String getContent(ChatResponse resp) {
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) return "";
        var g = resp.getResults().get(0);
        return g != null && g.getOutput() != null ? g.getOutput().getText() : "";
    }
}
