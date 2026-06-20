package com.books.framework.llm.controller;

import com.books.framework.annotation.ApiName;
import com.books.framework.constants.ErrorCodeEnum;
import com.books.framework.exception.AppException;
import com.books.framework.llm.LLMService;
import com.books.framework.llm.config.ChatModelFactory;
import com.books.framework.llm.dto.*;
import com.books.framework.llm.model.LLMBackendConfig;
import com.books.framework.message.BaseMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/llm/configs")
public class LLMConfigController {

    private final JdbcTemplate jdbc;
    private final LLMService llmService;
    private final Map<String, ChatModel> chatModels;
    private final Map<String, ChatClient> chatClients;
    private final List<ChatModelFactory> factories;

    public LLMConfigController(JdbcTemplate jdbc,
                               LLMService llmService,
                               Map<String, ChatModel> chatModels,
                               Map<String, ChatClient> chatClients,
                               List<ChatModelFactory> factories) {
        this.jdbc = jdbc;
        this.llmService = llmService;
        this.chatModels = chatModels;
        this.chatClients = chatClients;
        this.factories = factories != null ? factories : Collections.emptyList();
    }

    @ApiName(name = "llmListConfigs", output = {Map.class}, description = "列出所有后端配置")
    @PostMapping("/list")
    public BaseMessage list(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        try {
            List<Map<String, Object>> list = jdbc.queryForList(
                    "SELECT id, provider, endpoint, api_key, default_model, timeout_ms, max_retries, " +
                    "retry_strategy, max_concurrency, is_enabled, tech_create_time, tech_update_time " +
                    "FROM llm_backend_config ORDER BY id");
            for (Map<String, Object> row : list) {
                row.put("apiKey", maskApiKey((String) row.remove("api_key")));
            }
            BaseMessage response = request.newResponse();
            response.setMessage("查询成功");
            response.put("list", list);
            return response;
        } catch (Exception e) {
            BaseMessage response = request.newResponse();
            response.setMessage("查询成功");
            response.put("list", Collections.emptyList());
            return response;
        }
    }

    @ApiName(name = "llmGetConfig", input = {LLMConfigIdX1.class}, description = "查询后端配置详情")
    @PostMapping("/get")
    public BaseMessage getById(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        LLMConfigIdX1 x1 = request.getDto(LLMConfigIdX1.class);
        if (x1 == null || x1.getId() == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "id 不能为空");
        }
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM llm_backend_config WHERE id = ?", x1.getId());
            row.put("api_key", "****");
            BaseMessage response = request.newResponse();
            response.setMessage("查询成功");
            row.forEach((k, v) -> response.put(k, v));
            return response;
        } catch (Exception e) {
            throw new AppException(ErrorCodeEnum.DATA_NOT_FOUND, "后端配置不存在");
        }
    }

    @ApiName(name = "llmCreateConfig", input = {LLMConfigUpsertX1.class}, description = "新增后端配置")
    @PostMapping("/create")
    public BaseMessage create(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        LLMConfigUpsertX1 x1 = request.getDto(LLMConfigUpsertX1.class);
        if (x1 == null || x1.getProvider() == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "provider 不能为空");
        }
        jdbc.update(
                "INSERT INTO llm_backend_config (provider, endpoint, api_key, default_model, " +
                "timeout_ms, max_retries, retry_strategy, retry_on_codes, max_concurrency, " +
                "cost_per_1k_input, cost_per_1k_output, is_enabled) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                x1.getProvider(), x1.getEndpoint(), x1.getApiKey(), x1.getDefaultModel(),
                x1.getTimeoutMs() != null ? x1.getTimeoutMs() : 10000,
                x1.getMaxRetries() != null ? x1.getMaxRetries() : 2,
                x1.getRetryStrategy() != null ? x1.getRetryStrategy() : "FIXED",
                x1.getRetryOnCodes() != null ? x1.getRetryOnCodes() : "429,500,502,503",
                x1.getMaxConcurrency() != null ? x1.getMaxConcurrency() : 5,
                x1.getCostPer1kInput() != null ? x1.getCostPer1kInput() : BigDecimal.ZERO,
                x1.getCostPer1kOutput() != null ? x1.getCostPer1kOutput() : BigDecimal.ZERO,
                1);
        refreshBackends();
        BaseMessage response = request.newResponse();
        response.setMessage("创建成功");
        return response;
    }

    @ApiName(name = "llmUpdateConfig", input = {LLMConfigUpsertX1.class, LLMConfigIdX1.class}, description = "编辑后端配置")
    @PostMapping("/update")
    public BaseMessage update(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        LLMConfigIdX1 idX1 = request.getDto(LLMConfigIdX1.class);
        if (idX1 == null || idX1.getId() == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "id 不能为空");
        }
        LLMConfigUpsertX1 x1 = request.getDto(LLMConfigUpsertX1.class);
        if (x1 == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求体缺少 LLMConfigUpsertX1");
        }
        jdbc.update(
                "UPDATE llm_backend_config SET endpoint=?, api_key=COALESCE(?, api_key), " +
                "default_model=?, timeout_ms=?, max_retries=?, retry_strategy=?, " +
                "retry_on_codes=?, max_concurrency=?, cost_per_1k_input=?, cost_per_1k_output=? " +
                "WHERE id=?",
                x1.getEndpoint(), x1.getApiKey(), x1.getDefaultModel(),
                x1.getTimeoutMs(), x1.getMaxRetries(),
                x1.getRetryStrategy(), x1.getRetryOnCodes(), x1.getMaxConcurrency(),
                x1.getCostPer1kInput(), x1.getCostPer1kOutput(), idX1.getId());
        refreshBackends();
        BaseMessage response = request.newResponse();
        response.setMessage("更新成功");
        return response;
    }

    @ApiName(name = "llmDeleteConfig", input = {LLMConfigIdX1.class}, description = "删除后端配置")
    @PostMapping("/delete")
    public BaseMessage delete(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        LLMConfigIdX1 x1 = request.getDto(LLMConfigIdX1.class);
        if (x1 == null || x1.getId() == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "id 不能为空");
        }
        jdbc.update("DELETE FROM llm_backend_config WHERE id = ?", x1.getId());
        refreshBackends();
        BaseMessage response = request.newResponse();
        response.setMessage("删除成功");
        return response;
    }

    @ApiName(name = "llmToggleConfig", input = {LLMConfigIdX1.class, LLMConfigEnableX1.class}, description = "启用/禁用后端")
    @PostMapping("/enable")
    public BaseMessage enable(@RequestBody(required = false) BaseMessage request) {
        if (request == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "请求报文不能为空");
        }
        LLMConfigIdX1 idX1 = request.getDto(LLMConfigIdX1.class);
        if (idX1 == null || idX1.getId() == null) {
            throw new AppException(ErrorCodeEnum.PARAM_ERROR, "id 不能为空");
        }
        LLMConfigEnableX1 enableX1 = request.getDto(LLMConfigEnableX1.class);
        boolean enabled = enableX1 != null && Boolean.TRUE.equals(enableX1.getIsEnabled());
        jdbc.update("UPDATE llm_backend_config SET is_enabled = ? WHERE id = ?", enabled ? 1 : 0, idX1.getId());
        refreshBackends();
        BaseMessage response = request.newResponse();
        response.setMessage(enabled ? "已启用" : "已禁用");
        return response;
    }

    private void refreshBackends() {
        try {
            List<LLMBackendConfig> configs = jdbc.query(
                    "SELECT * FROM llm_backend_config WHERE is_enabled = 1",
                    (rs, rowNum) -> {
                        LLMBackendConfig cfg = new LLMBackendConfig();
                        cfg.setProvider(rs.getString("provider"));
                        cfg.setEndpoint(rs.getString("endpoint"));
                        cfg.setApiKey(rs.getString("api_key"));
                        cfg.setDefaultModel(rs.getString("default_model"));
                        cfg.setEnabled(true);
                        return cfg;
                    });
            Map<String, ChatModel> newModels = new ConcurrentHashMap<>();
            Map<String, ChatClient> newClients = new ConcurrentHashMap<>();
            for (LLMBackendConfig cfg : configs) {
                String provider = cfg.getProvider();
                ChatModel model = chatModels.get(provider);
                if (model == null) {
                    model = createChatModel(cfg);
                }
                newModels.put(provider, model);
                newClients.put(provider, ChatClient.builder(model).defaultAdvisors(new SimpleLoggerAdvisor()).build());
            }
            chatModels.clear();
            chatModels.putAll(newModels);
            chatClients.clear();
            chatClients.putAll(newClients);
            llmService.refresh(newClients, configs);
        } catch (Exception e) {
            // refresh fail silently — 下次重启会重新加载
        }
    }

    private ChatModel createChatModel(LLMBackendConfig cfg) {
        String provider = cfg.getProvider();
        for (ChatModelFactory f : factories) {
            if (f.supports(provider)) return f.create(cfg);
        }
        String baseUrl = cfg.getEndpoint();
        if (baseUrl == null || baseUrl.isEmpty()) {
            switch (provider.toLowerCase()) {
                case "qwen":   baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"; break;
                case "deepseek": baseUrl = "https://api.deepseek.com/v1"; break;
                default:        baseUrl = "https://api.openai.com/v1";
            }
        }
        String apiKey = cfg.getApiKey() != null ? cfg.getApiKey() : "sk-placeholder";
        String defaultModel = cfg.getDefaultModel() != null ? cfg.getDefaultModel().split(",")[0].trim() : "gpt-4o";
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(defaultModel).build())
                .build();
    }

    private static String maskApiKey(String key) {
        if (key == null || key.isEmpty()) return "(未设置)";
        if (key.length() <= 8) return key.substring(0, 2) + "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
