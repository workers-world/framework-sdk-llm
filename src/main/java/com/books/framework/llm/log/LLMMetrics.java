package com.books.framework.llm.log;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LLMMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> callCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public LLMMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess(String provider, String model, long elapsedMs, int inputTokens, int outputTokens) {
        counter(provider, model, "success").increment();
        timer(provider, model).record(elapsedMs, TimeUnit.MILLISECONDS);
        registry.counter("llm.tokens", "provider", provider, "type", "input").increment(inputTokens);
        registry.counter("llm.tokens", "provider", provider, "type", "output").increment(outputTokens);
    }

    public void recordFailure(String provider, String model) {
        counter(provider, model, "failure").increment();
    }

    private Counter counter(String provider, String model, String result) {
        String key = provider + "|" + model + "|" + result;
        return callCounters.computeIfAbsent(key, k ->
                Counter.builder("llm.calls")
                        .tag("provider", provider)
                        .tag("model", model != null ? model : "unknown")
                        .tag("result", result)
                        .register(registry));
    }

    private Timer timer(String provider, String model) {
        String key = provider + "|" + model;
        return latencyTimers.computeIfAbsent(key, k ->
                Timer.builder("llm.latency")
                        .tag("provider", provider)
                        .tag("model", model != null ? model : "unknown")
                        .register(registry));
    }
}
