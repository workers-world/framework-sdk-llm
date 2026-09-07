package com.books.framework.llm.rate;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 限流装饰 ChatModel:包装按 provider 配置的 {@link ChatModel},在模型层强制执行
 * 日/分钟/并发配额。
 *
 * <p>存在意义:限流必须挂在模型层而非 {@code LLMService} 内——消费方普遍绕过 LLMService
 * 直连 {@code ChatClient},只有模型层拦截才能覆盖所有调用路径。</p>
 *
 * <p>语义:超限抛 {@link RuntimeException}(与 LLMService 历史行为一致,调用方的
 * fallback 链会捕获并转移下一个后端);{@link #call} 在 finally 释放并发槽,
 * {@link #stream} 通过 {@code doFinally} 在 complete/error/<b>cancel</b> 时释放——
 * 流式客户端中途断开不再泄漏并发槽(历史缺陷)。</p>
 */
public class RateLimitedChatModel implements ChatModel {

    private final String provider;
    private final ChatModel delegate;
    private final RateLimiter rateLimiter;
    private final int maxConcurrency;
    private final int dailyLimit;
    private final int perMinuteLimit;

    public RateLimitedChatModel(String provider, ChatModel delegate, RateLimiter rateLimiter,
                                int maxConcurrency, int dailyLimit, int perMinuteLimit) {
        this.provider = provider;
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.maxConcurrency = maxConcurrency;
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (!rateLimiter.tryAcquire(provider, maxConcurrency, dailyLimit, perMinuteLimit)) {
            throw new RuntimeException("LLM 调用频率限制: " + provider);
        }
        try {
            return delegate.call(prompt);
        } finally {
            rateLimiter.release(provider);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        if (!rateLimiter.tryAcquire(provider, maxConcurrency, dailyLimit, perMinuteLimit)) {
            throw new RuntimeException("LLM 调用频率限制: " + provider);
        }
        return delegate.stream(prompt)
                .doFinally(signal -> rateLimiter.release(provider));
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    /** 供诊断/测试观察并发槽占用 */
    public int currentConcurrency() {
        return rateLimiter.concurrencyOf(provider);
    }
}
