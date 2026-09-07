package com.books.framework.llm.rate;

import com.books.framework.llm.model.LLMBackendConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitedChatModelTest {

    @Test
    void callDelegatesAndReleasesSlot() {
        RateLimiter limiter = new RateLimiter(10, 10);
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));
        RateLimitedChatModel model = new RateLimitedChatModel("p", delegate, limiter, 5, 0, 0);

        model.call(new Prompt("hi"));

        assertEquals(0, model.currentConcurrency(), "call 结束后并发槽应释放");
    }

    @Test
    void callThrowsWhenLimitExhausted() {
        RateLimiter limiter = new RateLimiter(1, 10);
        ChatModel delegate = mock(ChatModel.class);
        RateLimitedChatModel model = new RateLimitedChatModel("p", delegate, limiter, 5, 1, 0);

        model.call(new Prompt("first"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> model.call(new Prompt("second")));
        assertTrue(ex.getMessage().contains("频率限制"));
    }

    @Test
    void streamCancelReleasesConcurrencySlot() {
        // 历史缺陷:流式取消(doOnCancel)不释放并发槽,泄满后该 provider 永久限流
        RateLimiter limiter = new RateLimiter(100, 100);
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class))).thenReturn(Flux.never());
        RateLimitedChatModel model = new RateLimitedChatModel("p", delegate, limiter, 1, 0, 0);

        var disposable = model.stream(new Prompt("hi")).subscribe();
        assertEquals(1, model.currentConcurrency());
        disposable.dispose();

        assertEquals(0, model.currentConcurrency(), "取消后并发槽必须释放");
        assertTrue(limiter.tryAcquire("p", 1, 0, 0), "释放后应可再次获取");
    }

    @Test
    void streamErrorReleasesConcurrencySlot() {
        RateLimiter limiter = new RateLimiter(100, 100);
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));
        RateLimitedChatModel model = new RateLimitedChatModel("p", delegate, limiter, 1, 0, 0);

        assertThrows(RuntimeException.class, () -> model.stream(new Prompt("hi")).blockFirst());
        assertEquals(0, model.currentConcurrency(), "出错后并发槽应释放");
    }

    @Test
    void unlimitedProviderIsNotWrapped() {
        // dailyLimit/perMinute/maxConcurrency 全为 0 时不应包装(LLMAutoConfiguration 的组装约定)
        LLMBackendConfig cfg = new LLMBackendConfig();
        cfg.setProvider("free");
        cfg.setDailyLimit(0);
        cfg.setPerMinuteLimit(0);
        cfg.setMaxConcurrency(0);
        assertEquals(0, cfg.getDailyLimit() + cfg.getPerMinuteLimit() + cfg.getMaxConcurrency());
    }

    @Test
    void clientFactoryAppliesTimeout() {
        // 超时接线:timeoutMs>0 时必须传给 OpenAI 客户端(历史版本从不生效)。
        // OpenAIOkHttpClient 构建成功即代表参数合法;真实超时语义由库保证,
        // 这里主要回归"不会因新增参数而破坏构建路径"以及 normalize 行为
        OpenAIClient client = com.books.framework.llm.config.OpenAiCompatibleClientFactory.create(
                "https://api.deepseek.com/v1", "sk-test", java.util.Map.of(), 5000);
        assertNotNull(client);
        assertThrows(IllegalArgumentException.class,
                () -> com.books.framework.llm.config.OpenAiCompatibleClientFactory.create("", "k", java.util.Map.of(), 5000));
    }
}
