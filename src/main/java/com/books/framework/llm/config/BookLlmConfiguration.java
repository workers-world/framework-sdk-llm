package com.books.framework.llm.config;

import com.books.framework.simplellm.SimpleLLMProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimpleLLMProperties.class)
public class BookLlmConfiguration {
}
