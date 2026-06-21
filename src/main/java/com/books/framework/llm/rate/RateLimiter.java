package com.books.framework.llm.rate;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class RateLimiter {

    private final int globalDailyLimit;
    private final int globalPerMinuteLimit;
    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicInteger minuteCount = new AtomicInteger(0);
    private final AtomicLong minuteWindowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastDailyReset = new AtomicLong(System.currentTimeMillis());
    private final Map<String, AtomicInteger> concurrencyMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> providerDailyLimits = new ConcurrentHashMap<>();
    private final Map<String, Integer> providerPerMinuteLimits = new ConcurrentHashMap<>();

    public RateLimiter(int globalDailyLimit, int globalPerMinuteLimit) {
        this.globalDailyLimit = globalDailyLimit;
        this.globalPerMinuteLimit = globalPerMinuteLimit;
    }

    public void setProviderLimits(String provider, int dailyLimit, int perMinuteLimit) {
        if (dailyLimit > 0) providerDailyLimits.put(provider, dailyLimit);
        if (perMinuteLimit > 0) providerPerMinuteLimits.put(provider, perMinuteLimit);
    }

    public boolean tryAcquire(String provider, int maxConcurrency, int providerDailyLimit, int providerPerMinuteLimit) {
        int effectiveDaily = providerDailyLimit > 0 ? providerDailyLimit : globalDailyLimit;
        int effectivePerMinute = providerPerMinuteLimit > 0 ? providerPerMinuteLimit : globalPerMinuteLimit;

        if (!checkDaily(effectiveDaily)) return false;
        if (!checkPerMinute(effectivePerMinute)) return false;
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

    private boolean checkDaily(int limit) {
        long now = System.currentTimeMillis();
        long lastReset = lastDailyReset.get();
        if (now - lastReset > 86400000) {
            if (lastDailyReset.compareAndSet(lastReset, now)) {
                dailyCount.set(0);
                log.info("日调用计数器已自动重置");
            }
        }
        if (dailyCount.get() >= limit) {
            log.warn("日调用量已达上限: {}/{}", dailyCount.get(), limit);
            return false;
        }
        return true;
    }

    private boolean checkPerMinute(int limit) {
        long now = System.currentTimeMillis();
        long currentWindow = minuteWindowStart.get();
        if (now - currentWindow > 60000) {
            if (minuteWindowStart.compareAndSet(currentWindow, now)) {
                minuteCount.set(0);
                return true;
            }
            // CAS 失败说明其他线程已切换窗口，重试读取新窗口
            long newWindow = minuteWindowStart.get();
            if (now - newWindow > 60000) {
                return minuteCount.get() < limit;
            }
            return minuteCount.get() < limit;
        }
        if (minuteCount.get() >= limit) {
            log.warn("每分钟调用量已达上限: {}/{}", minuteCount.get(), limit);
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
        log.info("日调用计数器已重置");
    }
}
