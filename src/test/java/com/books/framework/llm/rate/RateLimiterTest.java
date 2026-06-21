package com.books.framework.llm.rate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter(5, 3);

    @Test
    void allowsUnderLimit() {
        assertTrue(limiter.tryAcquire("test", 5, 0, 0));
        assertTrue(limiter.tryAcquire("test", 5, 0, 0));
    }

    @Test
    void blocksOverDailyLimit() {
        RateLimiter strict = new RateLimiter(2, 100);
        assertTrue(strict.tryAcquire("a", 5, 0, 0));
        assertTrue(strict.tryAcquire("a", 5, 0, 0));
        assertFalse(strict.tryAcquire("a", 5, 0, 0));
    }

    @Test
    void blocksOverMinuteLimit() {
        RateLimiter strict = new RateLimiter(100, 2);
        assertTrue(strict.tryAcquire("b", 5, 0, 0));
        assertTrue(strict.tryAcquire("b", 5, 0, 0));
        assertFalse(strict.tryAcquire("b", 5, 0, 0));
    }

    @Test
    void releaseFreesConcurrencySlot() {
        // 使用大 daily/min 限制避免被全局限流干扰
        RateLimiter rl = new RateLimiter(100, 100);
        for (int i = 0; i < 5; i++) assertTrue(rl.tryAcquire("c", 5, 0, 0));
        assertFalse(rl.tryAcquire("c", 5, 0, 0));
        rl.release("c");
        assertTrue(rl.tryAcquire("c", 5, 0, 0));
    }

    @Test
    void providerDailyLimitOverridesGlobal() {
        // global daily=100, provider daily=1
        RateLimiter rl = new RateLimiter(100, 100);
        assertTrue(rl.tryAcquire("d", 10, 1, 0));
        assertFalse(rl.tryAcquire("d", 10, 1, 0));
    }

    @Test
    void resetDailyClearsCounter() {
        RateLimiter rl = new RateLimiter(1, 100);
        assertTrue(rl.tryAcquire("e", 5, 0, 0));
        assertFalse(rl.tryAcquire("e", 5, 0, 0));
        rl.resetDaily();
        assertTrue(rl.tryAcquire("e", 5, 0, 0));
    }
}
