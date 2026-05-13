package com.books.framework.llm.rate;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CircuitBreaker {

    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> openUntil = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long recoverySeconds;

    public CircuitBreaker(int failureThreshold, long recoverySeconds) {
        this.failureThreshold = failureThreshold;
        this.recoverySeconds = recoverySeconds;
    }

    public boolean allowsRequest(String provider) {
        Instant until = openUntil.get(provider);
        if (until == null) return true;
        if (Instant.now().isAfter(until)) {
            openUntil.remove(provider);
            failureCounts.remove(provider);
            log.info("熔断器恢复: {}", provider);
            return true;
        }
        log.warn("熔断器开启中: {}, 恢复时间: {}", provider, until);
        return false;
    }

    public void recordSuccess(String provider) {
        failureCounts.remove(provider);
        openUntil.remove(provider);
    }

    public void recordFailure(String provider) {
        int count = failureCounts.computeIfAbsent(provider, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= failureThreshold) {
            openUntil.put(provider, Instant.now().plusSeconds(recoverySeconds));
            log.warn("熔断器触发: {}, 连续失败{}次，恢复时间{}秒后", provider, count, recoverySeconds);
        }
    }
}
