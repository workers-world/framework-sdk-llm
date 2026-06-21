package com.books.framework.llm.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LLMConfigUpsertX1 {
    private String provider;
    private String endpoint;
    private String apiKey;
    private String defaultModel;
    private Integer timeoutMs;
    private Integer maxRetries;
    private String retryStrategy;
    private String retryOnCodes;
    private Integer priority;
    private Integer maxConcurrency;
    private Integer dailyLimit;
    private Integer perMinuteLimit;
    private BigDecimal costPer1kInput;
    private BigDecimal costPer1kOutput;
}
