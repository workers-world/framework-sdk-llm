package com.books.framework.llm.rate;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> openUntil = new ConcurrentHashMap<>();
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> halfOpenProbes = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long recoverySeconds;
    private final int halfOpenMaxRequests;

    public CircuitBreaker(int failureThreshold, long recoverySeconds) {
        this(failureThreshold, recoverySeconds, 3);
    }

    public CircuitBreaker(int failureThreshold, long recoverySeconds, int halfOpenMaxRequests) {
        this.failureThreshold = failureThreshold;
        this.recoverySeconds = recoverySeconds;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
    }

    public boolean allowsRequest(String provider) {
        State state = states.getOrDefault(provider, State.CLOSED);
        if (state == State.CLOSED) return true;

        if (state == State.OPEN) {
            Instant until = openUntil.get(provider);
            if (until != null && Instant.now().isAfter(until)) {
                states.put(provider, State.HALF_OPEN);
                halfOpenProbes.put(provider, new AtomicInteger(0));
                log.info("熔断器进入半开探测: {}, 允许{}个探测请求", provider, halfOpenMaxRequests);
                return true;
            }
            log.warn("熔断器开启中: {}, 恢复时间: {}", provider, until);
            return false;
        }

        // HALF_OPEN: 只放行有限探测请求
        AtomicInteger probes = halfOpenProbes.get(provider);
        if (probes != null && probes.incrementAndGet() <= halfOpenMaxRequests) {
            return true;
        }
        log.warn("熔断器半开探测已达上限: {}", provider);
        return false;
    }

    public void recordSuccess(String provider) {
        State state = states.getOrDefault(provider, State.CLOSED);
        if (state == State.HALF_OPEN) {
            states.put(provider, State.CLOSED);
            failureCounts.remove(provider);
            openUntil.remove(provider);
            halfOpenProbes.remove(provider);
            log.info("熔断器已关闭（探测成功）: {}", provider);
            return;
        }
        failureCounts.remove(provider);
        openUntil.remove(provider);
    }

    public void recordFailure(String provider) {
        State state = states.getOrDefault(provider, State.CLOSED);
        if (state == State.HALF_OPEN) {
            // 半开探测失败，重新熔断
            states.put(provider, State.OPEN);
            openUntil.put(provider, Instant.now().plusSeconds(recoverySeconds));
            halfOpenProbes.remove(provider);
            log.warn("熔断器半开探测失败，重新熔断: {}", provider);
            return;
        }
        int count = failureCounts.computeIfAbsent(provider, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= failureThreshold) {
            states.put(provider, State.OPEN);
            openUntil.put(provider, Instant.now().plusSeconds(recoverySeconds));
            log.warn("熔断器触发: {}, 连续失败{}次，{}秒后进入半开状态", provider, count, recoverySeconds);
        }
    }
}
