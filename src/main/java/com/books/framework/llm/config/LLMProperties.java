package com.books.framework.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {
    private String masterKey;
    private int dailyLimit = 50;
    private int perMinuteLimit = 10;
    private int defaultTimeoutMs = 10000;
    private String activeProvider;
}
