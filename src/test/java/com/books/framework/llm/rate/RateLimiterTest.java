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

    @Test
    void providerCountersAreIndependent() {
        // 历史版本为单一全局计数器:A 后端用满配额后 B 后端也被限流
        RateLimiter rl = new RateLimiter(1, 100);
        assertTrue(rl.tryAcquire("p1", 5, 0, 0));
        assertFalse(rl.tryAcquire("p1", 5, 0, 0));
        assertTrue(rl.tryAcquire("p2", 5, 0, 0), "另一 provider 不应被 p1 的用量影响");
        assertEquals(1, rl.getDailyCount("p1"));
        assertEquals(1, rl.getDailyCount("p2"));
        assertEquals(2, rl.getDailyCount());
    }

    @Test
    void dailyWindowResetsOnCalendarDay() {
        // 历史版本为「距上次重置 24h」滚动窗口;现在是本地自然日切换即清零
        RateLimiter rl = new RateLimiter(1, 100);
        assertTrue(rl.tryAcquire("day", 5, 0, 0));
        assertFalse(rl.tryAcquire("day", 5, 0, 0));
        rl.forceDailyEpochDay(java.time.LocalDate.now().toEpochDay() + 1);
        assertTrue(rl.tryAcquire("day", 5, 0, 0), "自然日切换后配额应重置");
    }

    @Test
    void minuteLimitIsPerProvider() {
        RateLimiter rl = new RateLimiter(100, 1);
        assertTrue(rl.tryAcquire("m1", 5, 0, 0));
        assertFalse(rl.tryAcquire("m1", 5, 0, 0));
        assertTrue(rl.tryAcquire("m2", 5, 0, 0));
    }
}
