package com.books.framework.llm;

import com.books.framework.llm.model.LLMCallOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.Future;

public interface LLMService {
    ChatResponse execute(Prompt prompt, LLMCallOptions options);
    Future<ChatResponse> executeAsync(Prompt prompt, LLMCallOptions options);
    List<ChatResponse> executeBatch(List<Prompt> prompts, LLMCallOptions options);
    ChatResponse getAsyncResult(String taskId);
}
