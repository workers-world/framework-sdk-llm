package com.books.framework.llm.config;

import com.books.framework.llm.LLMCallInterceptor;
import com.books.framework.llm.LLMService;
import com.books.framework.llm.LLMServiceImpl;
import com.books.framework.llm.cache.LLMResponseCache;
import com.books.framework.llm.controller.*;
import com.books.framework.llm.log.JdbcLLMCallLogger;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.llm.log.LLMMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import com.books.framework.llm.model.LLMBackendConfig;
import com.books.framework.llm.sanitizer.*;
import com.books.framework.utils.HanLPKeywordExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LLMProperties.class)
@ConditionalOnClass(org.springframework.ai.chat.model.ChatModel.class)
@ConditionalOnProperty(name = "book.llm.enable", havingValue = "true")
public class LLMAutoConfiguration {

    private final LlmHttpClientSupport httpClientSupport;

    public LLMAutoConfiguration(LlmHttpClientSupport httpClientSupport) {
        this.httpClientSupport = httpClientSupport;
    }

    @Bean
    @ConditionalOnMissingBean(LlmHttpClientSupport.class)
    public static LlmHttpClientSupport llmHttpClientSupport(ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        return new LlmHttpClientSupport(restClientBuilderProvider);
    }

    @Bean
    @ConditionalOnClass(OllamaChatModel.class)
    @ConditionalOnMissingBean(OllamaChatModelConfig.class)
    public OllamaChatModelConfig ollamaChatModelConfig(LlmHttpClientSupport httpClientSupport) {
        return new OllamaChatModelConfig(httpClientSupport);
    }

    // ===== 脱敏器 =====

    @Bean
    @ConditionalOnMissingBean(HanLPKeywordExtractor.class)
    public HanLPKeywordExtractor hanLPKeywordExtractor() {
        return new HanLPKeywordExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(PersonNameSanitizer.class)
    public PersonNameSanitizer personNameSanitizer(HanLPKeywordExtractor hanLPKeywordExtractor) {
        return new PersonNameSanitizer(hanLPKeywordExtractor);
    }

    @Bean
    @ConditionalOnMissingBean(PhoneNumberSanitizer.class)
    public PhoneNumberSanitizer phoneNumberSanitizer() { return new PhoneNumberSanitizer(); }

    @Bean
    @ConditionalOnMissingBean(BankCardSanitizer.class)
    public BankCardSanitizer bankCardSanitizer() { return new BankCardSanitizer(); }

    @Bean
    @ConditionalOnMissingBean(OrderIdSanitizer.class)
    public OrderIdSanitizer orderIdSanitizer() { return new OrderIdSanitizer(); }

    @Bean
    @ConditionalOnMissingBean(AddressSanitizer.class)
    public AddressSanitizer addressSanitizer() { return new AddressSanitizer(); }

    @Bean
    @ConditionalOnMissingBean(SanitizerRegistry.class)
    public SanitizerRegistry sanitizerRegistry() { return new SanitizerRegistry(); }

    // ===== ChatModel：从 DB 配置创建（建表+查询合并，确保表存在） =====

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(name = "llmChatModels")
    public Map<String, ChatModel> llmChatModels(JdbcTemplate jdbc,
                                                  List<ChatModelFactory> factories) {
        Map<String, ChatModel> map = new LinkedHashMap<>();
        List<LLMBackendConfig> configs = loadConfigs(jdbc);
        for (LLMBackendConfig cfg : configs) {
            if (!cfg.isEnabled()) continue;
            try {
                ChatModel model = createChatModel(cfg, factories);
                map.put(cfg.getProvider(), model);
                log.info("LLM ChatModel 创建成功: provider={}, model={}", cfg.getProvider(), cfg.getDefaultModel());
            } catch (Exception e) {
                log.error("LLM ChatModel 创建失败: provider={}, error={}", cfg.getProvider(), e.getMessage());
            }
        }
        return map;
    }

    // ===== ChatClient：用 Spring AI 内置 Advisor + SimpleLoggerAdvisor =====

    @Bean
    @ConditionalOnMissingBean(name = "llmChatClients")
    public Map<String, ChatClient> llmChatClients(
            @org.springframework.beans.factory.annotation.Qualifier("llmChatModels") Map<String, ChatModel> chatModels,
            ObjectProvider<ToolCallback> toolCallbacks) {
        List<ToolCallback> tools = toolCallbacks.stream().toList();
        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (Map.Entry<String, ChatModel> entry : chatModels.entrySet()) {
            ChatClient.Builder builder = ChatClient.builder(entry.getValue())
                    .defaultAdvisors(new SimpleLoggerAdvisor());
            if (!tools.isEmpty()) {
                builder.defaultTools(tools.toArray(new ToolCallback[0]));
            }
            map.put(entry.getKey(), builder.build());
        }
        return map;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public List<LLMBackendConfig> llmBackendConfigs(JdbcTemplate jdbc) {
        return loadConfigs(jdbc);
    }

    // ===== 日志 =====

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(LLMCallLogger.class)
    public JdbcLLMCallLogger llmCallLogger(JdbcTemplate jdbc) { return new JdbcLLMCallLogger(jdbc); }

    // ===== 本地缓存 =====

    @Bean
    @ConditionalOnMissingBean(LLMResponseCache.class)
    public LLMResponseCache llmResponseCache() {
        return new LLMResponseCache(1000, 1800); // 1000条 / 30分钟 TTL
    }

    // ===== 可观测性 =====

    @Bean
    @ConditionalOnMissingBean(LLMMetrics.class)
    public LLMMetrics llmMetrics(MeterRegistry meterRegistry) {
        return new LLMMetrics(meterRegistry);
    }

    // ===== 核心服务 =====

    @Bean
    @ConditionalOnMissingBean(LLMService.class)
    public LLMServiceImpl llmService(
            @Qualifier("llmChatClients") Map<String, ChatClient> chatClients,
            @Qualifier("llmBackendConfigs") List<LLMBackendConfig> configs,
                                      List<Sanitizer> sanitizers,
                                      ObjectProvider<LLMCallLogger> callLoggerProvider,
                                      LLMProperties properties,
                                      ObjectProvider<LLMMetrics> metricsProvider,
                                      ObjectProvider<LLMCallInterceptor> interceptors,
                                      ObjectProvider<LLMResponseCache> cacheProvider) {
        LLMCallLogger callLogger = callLoggerProvider.getIfAvailable();
        LLMMetrics metrics = metricsProvider.getIfAvailable();
        LLMResponseCache cache = cacheProvider.getIfAvailable();
        return new LLMServiceImpl(chatClients, configs, sanitizers, callLogger, properties, metrics, interceptors.stream().toList(), cache);
    }

    // ===== Controllers =====

    @Bean
    @ConditionalOnMissingBean(LLMExecuteController.class)
    public LLMExecuteController llmExecuteController(LLMService llmService) {
        return new LLMExecuteController(llmService);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(LLMConfigController.class)
    public LLMConfigController llmConfigController(JdbcTemplate jdbc,
                                                    LLMService llmService,
                                                    @org.springframework.beans.factory.annotation.Qualifier("llmChatModels") Map<String, ChatModel> chatModels,
                                                    @org.springframework.beans.factory.annotation.Qualifier("llmChatClients") Map<String, ChatClient> chatClients,
                                                    List<ChatModelFactory> factories,
                                                    LlmHttpClientSupport httpClientSupport) {
        return new LLMConfigController(jdbc, llmService, chatModels, chatClients, factories, httpClientSupport);
    }

    @Bean
    @ConditionalOnMissingBean(LLMLogController.class)
    public LLMLogController llmLogController(ObjectProvider<LLMCallLogger> callLoggerProvider) {
        return new LLMLogController(callLoggerProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LLMSanitizerController.class)
    public LLMSanitizerController llmSanitizerController(List<Sanitizer> sanitizers, SanitizerRegistry registry) {
        return new LLMSanitizerController(sanitizers, registry);
    }

    @Bean
    @ConditionalOnMissingBean(LLMHealthController.class)
    public LLMHealthController llmHealthController(ObjectProvider<LLMCallLogger> callLoggerProvider) {
        return new LLMHealthController(callLoggerProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LLMStatsController.class)
    public LLMStatsController llmStatsController(ObjectProvider<LLMCallLogger> callLoggerProvider) {
        return new LLMStatsController(callLoggerProvider.getIfAvailable());
    }

    // ===== 内部方法 =====

    private ChatModel createChatModel(LLMBackendConfig cfg, List<ChatModelFactory> factories) {
        String provider = cfg.getProvider();
        for (ChatModelFactory f : factories) {
            if (f.supports(provider)) {
                return f.create(cfg);
            }
        }
        // 默认 OpenAI 兼容
        String model = cfg.getDefaultModel();
        String baseUrl = cfg.getEndpoint();
        if (baseUrl == null || baseUrl.isEmpty()) {
            switch (provider.toLowerCase()) {
                case "qwen":   baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"; break;
                case "deepseek": baseUrl = "https://api.deepseek.com/v1"; break;
                case "cloudflare": baseUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai"; break;
                case "groq":   baseUrl = "https://api.groq.com/openai/v1"; break;
                case "together": baseUrl = "https://api.together.xyz/v1"; break;
                default:        baseUrl = "https://api.openai.com/v1";
            }
        }
        String apiKey = cfg.getApiKey() != null ? cfg.getApiKey() : "sk-placeholder";
        String gatewayApiKey = cfg.getGatewayApiKey();
        String defaultModel = model != null ? model.split(",")[0].trim() : "gpt-4o";

        RestClient.Builder restClientBuilder = httpClientSupport.outboundRestClientBuilder();
        // gateway 模式：用 gatewayApiKey 做 Bearer 认证，原始 apiKey 通过 X-API-Key 传递
        if (gatewayApiKey != null && !gatewayApiKey.isEmpty()) {
            restClientBuilder.defaultHeader("X-API-Key", apiKey);
            apiKey = gatewayApiKey;
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(defaultModel).build())
                .build();
    }

    private void ensureTables(JdbcTemplate jdbc) {
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
        log.info("LLM 表初始化完成（llm_backend_config、llm_call_logs）");
    }

    private List<LLMBackendConfig> loadConfigs(JdbcTemplate jdbc) {
        try {
            return jdbc.query(
                    "SELECT * FROM llm_backend_config WHERE is_enabled = 1",
                    new BackendConfigRowMapper());
        } catch (Exception e) {
            log.info("llm_backend_config 表不存在或未配置: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class BackendConfigRowMapper implements RowMapper<LLMBackendConfig> {
        @Override
        public LLMBackendConfig mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            LLMBackendConfig cfg = new LLMBackendConfig();
            cfg.setId(rs.getLong("id"));
            cfg.setProvider(rs.getString("provider"));
            cfg.setEndpoint(rs.getString("endpoint"));
            cfg.setApiKey(rs.getString("api_key"));
            cfg.setGatewayApiKey(rs.getString("gateway_api_key"));
            cfg.setDefaultModel(rs.getString("default_model"));
            cfg.setTimeoutMs(rs.getInt("timeout_ms"));
            cfg.setMaxRetries(rs.getInt("max_retries"));
            cfg.setRetryStrategy(rs.getString("retry_strategy"));
            cfg.setRetryOnCodes(rs.getString("retry_on_codes"));
            cfg.setPriority(rs.getInt("priority"));
            cfg.setMaxConcurrency(rs.getInt("max_concurrency"));
            cfg.setDailyLimit(rs.getInt("daily_limit"));
            cfg.setPerMinuteLimit(rs.getInt("per_minute_limit"));
            BigDecimal inputCost = rs.getBigDecimal("cost_per_1k_input");
            cfg.setCostPer1kInput(inputCost != null ? inputCost : BigDecimal.ZERO);
            BigDecimal outputCost = rs.getBigDecimal("cost_per_1k_output");
            cfg.setCostPer1kOutput(outputCost != null ? outputCost : BigDecimal.ZERO);
            cfg.setEnabled(rs.getInt("is_enabled") == 1);
            return cfg;
        }
    }
}
