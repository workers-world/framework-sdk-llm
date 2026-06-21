package com.books.framework.llm.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LLMBackendConfig {
    private Long id;
    private String provider;
    private String endpoint;
    private String apiKey;
    private String defaultModel;
    private int timeoutMs;
    private int maxRetries;
    private String retryStrategy;
    private String retryOnCodes;
    private int priority;
    private int maxConcurrency;
    private int dailyLimit;
    private int perMinuteLimit;
    private BigDecimal costPer1kInput;
    private BigDecimal costPer1kOutput;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
