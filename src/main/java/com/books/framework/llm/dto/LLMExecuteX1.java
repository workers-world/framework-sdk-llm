package com.books.framework.llm.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LLMExecuteX1 {
    private String systemPrompt;
    private String userMessage;
    private String responseFormat;
    private String jsonSchema;
    private String provider;
    private String model;
    private boolean skipSanitize;
    private List<String> enabledSanitizers;
    private String idempotencyKey;
    private int maxTokens;
    private double temperature;
    private Map<String, Object> metadata;

    public int getMaxTokens() {
        return maxTokens > 0 ? maxTokens : 256;
    }

    public double getTemperature() {
        return temperature > 0 ? temperature : 0.1;
    }
}
