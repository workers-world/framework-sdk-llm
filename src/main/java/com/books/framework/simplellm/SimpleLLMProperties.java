package com.books.framework.simplellm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "book.llm")
public class SimpleLLMProperties {

    private boolean enable;

    private String provider = "openai";

    private String endpoint;

    private String apiKey;

    private String model = "gpt-4o";
}
