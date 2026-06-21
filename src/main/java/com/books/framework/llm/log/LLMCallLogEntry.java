package com.books.framework.llm.log;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LLMCallLogEntry {
    private Long id;
    private String provider;
    private String modelName;
    private String requestPrompt;
    private String responseText;
    private String parsedResult;
    private int inputTokens;
    private int outputTokens;
    private int tokensUsed;
    private BigDecimal cost;
    private int responseTimeMs;
    private boolean success;
    private String errorMessage;
    private String callerInfo;
    private String idempotencyKey;
    private String metadataJson;
    private LocalDateTime createdAt;
}
