package com.books.framework.llm.rate;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用限流器。
 *
 * <p>计数语义(1.4 起):</p>
 * <ul>
 *   <li><b>按 provider 独立计数</b>——日/分钟配额均为 provider 维度,多后端不再互相吃配额
 *       (历史版本为单一全局计数器,N 个后端共享同一配额,且 fallback 链会重复计数)。</li>
 *   <li><b>自然日窗口</b>——日计数在本地时区自然日切换时清零
 *       (历史版本为「距上次重置 24h」的滚动窗口,跨夜调用会把配额摊薄到两个自然日)。</li>
 * </ul>
 *
 * <p>已知取舍:三个检查(日/分钟/并发)与计数之间仍存在 check-then-act 窗口,突发流量下
 * 可能轻微超发;对每分钟个位数的个人应用量级无实际影响,如需严格配额应改用持久化计数。</p>
 */
@Slf4j
public class RateLimiter {

    private final int globalDailyLimit;
    private final int globalPerMinuteLimit;
    private final Map<String, Integer> providerDailyLimits = new ConcurrentHashMap<>();
    private final Map<String, Integer> providerPerMinuteLimits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderCounters> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> concurrencyMap = new ConcurrentHashMap<>();

    public RateLimiter(int globalDailyLimit, int globalPerMinuteLimit) {
        this.globalDailyLimit = globalDailyLimit;
        this.globalPerMinuteLimit = globalPerMinuteLimit;
    }

    public void setProviderLimits(String provider, int dailyLimit, int perMinuteLimit) {
        if (dailyLimit > 0) providerDailyLimits.put(provider, dailyLimit);
        if (perMinuteLimit > 0) providerPerMinuteLimits.put(provider, perMinuteLimit);
    }

    public boolean tryAcquire(String provider, int maxConcurrency, int providerDailyLimit, int providerPerMinuteLimit) {
        Integer configuredDaily = providerDailyLimits.get(provider);
        Integer configuredPerMinute = providerPerMinuteLimits.get(provider);
        int effectiveDaily = providerDailyLimit > 0 ? providerDailyLimit
                : configuredDaily != null ? configuredDaily : globalDailyLimit;
        int effectivePerMinute = providerPerMinuteLimit > 0 ? providerPerMinuteLimit
                : configuredPerMinute != null ? configuredPerMinute : globalPerMinuteLimit;

        ProviderCounters c = counters.computeIfAbsent(provider, k -> new ProviderCounters());
        if (!checkDaily(c, effectiveDaily)) return false;
        if (!checkPerMinute(c, effectivePerMinute)) return false;
        if (!checkConcurrency(provider, maxConcurrency)) return false;
        c.dailyCount.incrementAndGet();
        c.minuteCount.incrementAndGet();
        concurrencyMap.computeIfAbsent(provider, k -> new AtomicInteger(0)).incrementAndGet();
        return true;
    }

    public void release(String provider) {
        AtomicInteger c = concurrencyMap.get(provider);
        if (c != null) {
            c.decrementAndGet();
        }
    }

    /** 当前并发占用数(诊断/测试用) */
    public int concurrencyOf(String provider) {
        AtomicInteger c = concurrencyMap.get(provider);
        return c != null ? c.get() : 0;
    }

    public int getDailyCount(String provider) {
        ProviderCounters c = counters.get(provider);
        return c != null ? c.dailyCount.get() : 0;
    }

    public int getDailyCount() {
        return counters.values().stream().mapToInt(v -> v.dailyCount.get()).sum();
    }

    public int getMinuteCount() {
        return counters.values().stream().mapToInt(v -> v.minuteCount.get()).sum();
    }

    public void resetDaily() {
        counters.values().forEach(c -> c.dailyCount.set(0));
        log.info("日调用计数器已重置");
    }

    /** 供测试模拟自然日切换:把所有 provider 的日计数窗口拨到指定 epochDay */
    void forceDailyEpochDay(long epochDay) {
        counters.values().forEach(c -> {
            c.dailyEpochDay.set(epochDay);
            c.dailyCount.set(0);
        });
    }

    private boolean checkDaily(ProviderCounters c, int limit) {
        c.rollDailyIfNeeded();
        if (c.dailyCount.get() >= limit) {
            log.warn("日调用量已达上限: {}/{}", c.dailyCount.get(), limit);
            return false;
        }
        return true;
    }

    private boolean checkPerMinute(ProviderCounters c, int limit) {
        c.rollMinuteIfNeeded();
        if (c.minuteCount.get() >= limit) {
            log.warn("每分钟调用量已达上限: {}/{}", c.minuteCount.get(), limit);
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

    private static final class ProviderCounters {
        final AtomicInteger dailyCount = new AtomicInteger(0);
        final AtomicInteger minuteCount = new AtomicInteger(0);
        final AtomicLong minuteWindowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong dailyEpochDay = new AtomicLong(LocalDate.now().toEpochDay());

        void rollDailyIfNeeded() {
            long today = LocalDate.now().toEpochDay();
            long prev = dailyEpochDay.get();
            if (prev != today && dailyEpochDay.compareAndSet(prev, today)) {
                dailyCount.set(0);
            }
        }

        void rollMinuteIfNeeded() {
            long now = System.currentTimeMillis();
            long start = minuteWindowStart.get();
            if (now - start >= 60_000 && minuteWindowStart.compareAndSet(start, now)) {
                minuteCount.set(0);
            }
        }
    }
}
