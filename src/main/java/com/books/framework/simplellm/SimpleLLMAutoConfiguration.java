package com.books.framework.simplellm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(name = "book.llm.enable", havingValue = "true")
@ConditionalOnMissingBean(name = "llmService")
public class SimpleLLMAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "book.llm")
    public SimpleLLMProperties simpleLLMProperties() {
        return new SimpleLLMProperties();
    }

    @Bean
    public ChatClient chatClient(SimpleLLMProperties props, ObjectProvider<JdbcTemplate> jdbcProvider) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc != null) {
            ensureTables(jdbc);
        }
        BackendConfig cfg = loadBackendConfig(jdbc, props);
        if (cfg == null) {
            log.warn("未配置 LLM 后端，ChatClient 将不可用。请在 llm_backend_config 表或 book.llm 配置中设置。");
            return null;
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(cfg.endpoint)
                .apiKey(cfg.apiKey)
                .build();
        ChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(cfg.model).build())
                .build();
        log.info("SimpleLLM ChatClient 创建完成: provider={}, model={}", cfg.provider, cfg.model);
        return ChatClient.create(model);
    }

    private void ensureTables(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS llm_backend_config (
                    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider        VARCHAR(20)  NOT NULL,
                    endpoint        VARCHAR(255),
                    api_key         VARCHAR(512),
                    default_model   VARCHAR(50),
                    timeout_ms      INT          DEFAULT 10000,
                    max_retries     INT          DEFAULT 2,
                    retry_strategy  VARCHAR(20)  DEFAULT 'FIXED',
                    retry_on_codes  VARCHAR(100) DEFAULT '429,500,502,503',
                    max_concurrency INT          DEFAULT 5,
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
                    model_name      VARCHAR(50),
                    request_prompt  TEXT,
                    response_text   TEXT,
                    parsed_result   TEXT,
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
        log.info("SimpleLLM 表初始化完成");
    }

    private BackendConfig loadBackendConfig(JdbcTemplate jdbc, SimpleLLMProperties props) {
        // 优先读 book.llm 配置
        if (props.getApiKey() != null && !props.getApiKey().isEmpty()) {
            return new BackendConfig(props.getProvider(), props.getEndpoint(),
                    props.getApiKey(), props.getModel());
        }
        // JdbcTemplate 不可用时跳过 DB 读取
        if (jdbc == null) return null;
        // 否则从 llm_backend_config 表读第一个启用的
        try {
            List<BackendConfig> list = jdbc.query(
                    "SELECT provider, endpoint, api_key, default_model FROM llm_backend_config WHERE is_enabled = 1 LIMIT 1",
                    (rs, rowNum) -> new BackendConfig(
                            rs.getString("provider"),
                            rs.getString("endpoint"),
                            rs.getString("api_key"),
                            rs.getString("default_model")));
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private record BackendConfig(String provider, String endpoint, String apiKey, String model) {}
}
