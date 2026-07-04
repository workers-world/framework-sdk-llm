package com.books.framework.llm.config;

import com.books.framework.llm.model.LLMBackendConfig;
import org.springframework.ai.image.ImageModel;

public interface ImageModelFactory {
    boolean supports(String provider);
    ImageModel create(LLMBackendConfig cfg);
}
