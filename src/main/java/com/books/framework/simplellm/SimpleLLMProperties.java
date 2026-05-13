package com.books.framework.simplellm;

import lombok.Data;

@Data
public class SimpleLLMProperties {
    /** 后端标识：openai / qwen / deepseek */
    private String provider = "openai";
    /** API 地址，留空用默认 */
    private String endpoint;
    /** API Key */
    private String apiKey;
    /** 默认模型 */
    private String model = "gpt-4o";
}
