package com.books.framework.llm.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LLMResponseZ1 {
    private String content;
    private String parsedContent;
    private boolean success;
    private String errorMessage;
    private int tokensUsed;
    private long responseTimeMs;
    private String modelName;
    private String provider;
    private BigDecimal cost;
    private boolean fromCache;
}
