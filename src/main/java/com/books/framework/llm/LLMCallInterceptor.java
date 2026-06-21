package com.books.framework.llm;

import com.books.framework.llm.model.LLMCallOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

/** 可在 execute 前后插入自定义逻辑。getOrder 越小越先执行 */
public interface LLMCallInterceptor extends Ordered {

    /** 调用前。返回 null 表示继续；返回非 null 则短路直接返回该响应 */
    default ChatResponse beforeCall(Prompt prompt, LLMCallOptions options) { return null; }

    /** 调用成功后。可修改/替换响应 */
    default ChatResponse afterCall(Prompt prompt, LLMCallOptions options, ChatResponse response, long elapsedMs) { return response; }

    /** 调用失败时 */
    default void onError(Prompt prompt, LLMCallOptions options, Exception e) {}

    @Override
    default int getOrder() { return 0; }
}
