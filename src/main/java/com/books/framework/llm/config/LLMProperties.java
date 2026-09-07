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
    /**
     * 是否把完整 prompt/响应交给 SimpleLoggerAdvisor 打进应用日志。
     * 默认 false:prompt 含交易对手、商品名等敏感内容,仅调试时显式开启。
     */
    private boolean logPrompts = false;
}
