package com.books.framework.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "book.llm")
public class BookLlmProperties {
    private boolean enable;
}
