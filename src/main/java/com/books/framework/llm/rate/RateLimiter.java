package com.books.framework.llm.rate;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class RateLimiter {

    private final int dailyLimit;
    private final int perMinuteLimit;
    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicInteger minuteCount = new AtomicInteger(0);
    private final AtomicLong minuteWindowStart = new AtomicLong(System.currentTimeMillis());
    private final Map<String, AtomicInteger> concurrencyMap = new ConcurrentHashMap<>();

    public RateLimiter(int dailyLimit, int perMinuteLimit) {
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    public boolean tryAcquire(String provider, int maxConcurrency) {
        if (!checkDaily()) return false;
        if (!checkPerMinute()) return false;
        if (!checkConcurrency(provider, maxConcurrency)) return false;
        dailyCount.incrementAndGet();
        minuteCount.incrementAndGet();
        concurrencyMap.computeIfAbsent(provider, k -> new AtomicInteger(0)).incrementAndGet();
        return true;
    }

    public void release(String provider) {
        AtomicInteger c = concurrencyMap.get(provider);
        if (c != null) {
            c.decrementAndGet();
        }
    }

    private boolean checkDaily() {
        if (dailyCount.get() >= dailyLimit) {
            log.warn("日调用量已达上限: {}/{}", dailyCount.get(), dailyLimit);
            return false;
        }
        return true;
    }

    private boolean checkPerMinute() {
        long now = System.currentTimeMillis();
        long window = minuteWindowStart.get();
        if (now - window > 60000) {
            minuteWindowStart.set(now);
            minuteCount.set(0);
            return true;
        }
        if (minuteCount.get() >= perMinuteLimit) {
            log.warn("每分钟调用量已达上限: {}/{}", minuteCount.get(), perMinuteLimit);
            return false;
        }
        return true;
    }

    private boolean checkConcurrency(String provider, int maxConcurrency) {
        if (maxConcurrency <= 0) return true;
        AtomicInteger c = concurrencyMap.get(provider);
        if (c != null && c.get() >= maxConcurrency) {
            log.warn("{} 并发数已达上限: {}/{}", provider, c.get(), maxConcurrency);
            return false;
        }
        return true;
    }

    public int getDailyCount() { return dailyCount.get(); }
    public int getMinuteCount() { return minuteCount.get(); }

    public void resetDaily() {
        dailyCount.set(0);
    }
}
