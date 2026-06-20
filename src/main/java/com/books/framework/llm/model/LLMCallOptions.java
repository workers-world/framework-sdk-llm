package com.books.framework.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LLMCallOptions {
    private String provider;
    private String model;
    private String idempotencyKey;
    private boolean skipSanitize;
    private List<String> enabledSanitizers;
    private Map<String, Object> metadata;
}
