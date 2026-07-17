package com.books.framework.llm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.chat.model.ChatModel")
public class LLMTableInitializer {

    private final JdbcTemplate jdbc;

    public LLMTableInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS llm_backend_config (
                    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider        VARCHAR(20)  NOT NULL,
                    endpoint        VARCHAR(255),
                    api_key         VARCHAR(512),
                    gateway_api_key VARCHAR(512),
                    default_model   VARCHAR(128),
                    timeout_ms      INT          DEFAULT 10000,
                    max_retries     INT          DEFAULT 2,
                    retry_strategy  VARCHAR(20)  DEFAULT 'FIXED',
                    retry_on_codes  VARCHAR(100) DEFAULT '429,500,502,503',
                    priority        INT          DEFAULT 0,
                    max_concurrency INT          DEFAULT 5,
                    daily_limit     INT          DEFAULT 0,
                    per_minute_limit INT          DEFAULT 0,
                    cost_per_1k_input  DECIMAL(10,6) DEFAULT 0,
                    cost_per_1k_output DECIMAL(10,6) DEFAULT 0,
                    is_enabled      TINYINT(1)   DEFAULT 1,
                    tech_version    INT          NOT NULL DEFAULT 1,
                    tech_business_id VARCHAR(100) NOT NULL DEFAULT '',
                    tech_create_time VARCHAR(14)  NOT NULL DEFAULT '',
                    tech_update_time VARCHAR(14)  NOT NULL DEFAULT '',
                    tech_trace_id   VARCHAR(32),
                    extend_01       VARCHAR(400),
                    extend_02       VARCHAR(400),
                    extend_03       VARCHAR(400),
                    extend_04       VARCHAR(400)
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS llm_call_logs (
                    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider        VARCHAR(20),
                    model_name      VARCHAR(128),
                    request_prompt  TEXT,
                    response_text   TEXT,
                    parsed_result   TEXT,
                    input_tokens    INT          DEFAULT 0,
                    output_tokens   INT          DEFAULT 0,
                    tokens_used     INT,
                    cost            DECIMAL(10,6),
                    response_time_ms INT,
                    success         TINYINT(1),
                    error_message   TEXT,
                    caller_info     VARCHAR(100),
                    idempotency_key VARCHAR(64),
                    metadata_json   TEXT,
                    tech_version    INT          NOT NULL DEFAULT 1,
                    tech_business_id VARCHAR(100) NOT NULL DEFAULT '',
                    tech_create_time VARCHAR(14)  NOT NULL DEFAULT '',
                    tech_update_time VARCHAR(14)  NOT NULL DEFAULT '',
                    tech_trace_id   VARCHAR(32),
                    extend_01       VARCHAR(400),
                    extend_02       VARCHAR(400),
                    extend_03       VARCHAR(400),
                    extend_04       VARCHAR(400)
                )""");
        log.info("LLM 表初始化完成：llm_backend_config、llm_call_logs");
    }
}
