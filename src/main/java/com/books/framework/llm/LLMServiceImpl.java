package com.books.framework.llm;

import com.books.framework.llm.config.LLMProperties;
import com.books.framework.llm.log.LLMCallLogEntry;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.llm.model.LLMBackendConfig;
import com.books.framework.llm.model.LLMCallOptions;
import com.books.framework.llm.rate.CircuitBreaker;
import com.books.framework.llm.rate.RateLimiter;
import com.books.framework.llm.sanitizer.Sanitizer;
import com.books.framework.llm.sanitizer.SanitizerChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class LLMServiceImpl implements LLMService {

    private final Map<String, ChatClient> chatClientMap;
    private final Map<String, LLMBackendConfig> configMap;
    private final SanitizerChain sanitizerChain;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final LLMCallLogger callLogger;
    private final LLMProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, Future<ChatResponse>> asyncTasks = new ConcurrentHashMap<>();

    public LLMServiceImpl(Map<String, ChatClient> chatClientMap,
                          List<LLMBackendConfig> configs,
                          List<Sanitizer> sanitizers,
                          LLMCallLogger callLogger,
                          LLMProperties properties) {
        this.chatClientMap = new ConcurrentHashMap<>(chatClientMap);
        this.configMap = new ConcurrentHashMap<>();
        for (LLMBackendConfig c : configs) {
            if (c.isEnabled()) this.configMap.put(c.getProvider(), c);
        }
        this.sanitizerChain = new SanitizerChain(sanitizers);
        this.callLogger = callLogger;
        this.properties = properties;
        this.rateLimiter = new RateLimiter(properties.getDailyLimit(), properties.getPerMinuteLimit());
        this.circuitBreaker = new CircuitBreaker(5, 60);
    }

    @Override
    public void refresh(Map<String, ChatClient> chatClients, List<LLMBackendConfig> configs) {
        this.chatClientMap.clear();
        this.chatClientMap.putAll(chatClients);
        this.configMap.clear();
        for (LLMBackendConfig c : configs) {
            if (c.isEnabled()) this.configMap.put(c.getProvider(), c);
        }
        log.info("LLM 配置已刷新: {} 个后端, {} 个启用", chatClients.size(), configMap.size());
    }

    @Override
    public ChatResponse execute(Prompt prompt, LLMCallOptions options) {
        String idempotencyKey = options != null ? options.getIdempotencyKey() : null;
        // 幂等检查
        if (idempotencyKey != null && !idempotencyKey.isEmpty() && callLogger != null) {
            LLMCallLogEntry cached = callLogger.findByIdempotencyKey(idempotencyKey);
            if (cached != null) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(cached.getResponseText()))));
            }
        }

        String provider = resolveProvider(options);
        ChatClient chatClient = chatClientMap.get(provider);
        if (chatClient == null) {
            log.error("未找到后端: {}" + provider);
            throw new RuntimeException("未找到后端: " + provider);
        }
        LLMBackendConfig config = configMap.get(provider);

        // 脱敏
        List<Message> sanitizedMessages = sanitizeMessages(prompt.getInstructions(), options);

        // 限流 + 熔断
        int maxConcurrency = config != null ? config.getMaxConcurrency() : 5;
        if (!rateLimiter.tryAcquire(provider, maxConcurrency)) {
            throw new RuntimeException("调用频率限制: " + provider);
        }
        if (!circuitBreaker.allowsRequest(provider)) {
            rateLimiter.release(provider);
            throw new RuntimeException("后端已熔断: " + provider);
        }

        long start = System.currentTimeMillis();
        String modelName = resolveModel(options, prompt, config);
        try {
            List<Message> messages = sanitizedMessages != null ? sanitizedMessages : prompt.getInstructions();
            Prompt callPrompt = buildCallPrompt(messages, prompt, modelName);

            ChatResponse chatResponse = chatClient.prompt(callPrompt).call().chatResponse();

            // 响应脱敏
            if (options == null || !options.isSkipSanitize()) {
                chatResponse = sanitizeResponse(chatResponse, options);
            }

            long elapsed = System.currentTimeMillis() - start;
            int tokens = 0;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                tokens = (int) chatResponse.getMetadata().getUsage().getTotalTokens();
            }
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getModel() != null) {
                modelName = chatResponse.getMetadata().getModel();
            }

            circuitBreaker.recordSuccess(provider);

            // DB 审计日志
            if (callLogger != null) {
                try {
                    BigDecimal cost = calcCost(tokens, config);
                    callLogger.log(LLMCallLogEntry.builder()
                            .provider(provider).modelName(modelName)
                            .requestPrompt(toPromptLog(prompt.getInstructions()))
                            .responseText(getContent(chatResponse))
                            .tokensUsed(tokens).cost(cost)
                            .responseTimeMs((int) elapsed).success(true)
                            .idempotencyKey(idempotencyKey)
                            .metadataJson(toJson(options != null ? options.getMetadata() : null))
                            .build());
                } catch (Exception e) {
                    log.error("LLM 日志写入失败", e);
                }
            }

            return chatResponse;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            circuitBreaker.recordFailure(provider);
            if (callLogger != null) {
                try {
                    callLogger.log(LLMCallLogEntry.builder()
                            .provider(provider).modelName(modelName)
                            .requestPrompt(toPromptLog(prompt.getInstructions()))
                            .success(false).errorMessage(e.getMessage())
                            .responseTimeMs((int) elapsed)
                            .idempotencyKey(idempotencyKey)
                            .build());
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("LLM 调用失败: " + provider + " - " + e.getMessage(), e);
        } finally {
            rateLimiter.release(provider);
        }
    }

    @Override
    public Future<ChatResponse> executeAsync(Prompt prompt, LLMCallOptions options) {
        String taskId = UUID.randomUUID().toString();
        Future<ChatResponse> future = asyncExecutor.submit(() -> execute(prompt, options));
        asyncTasks.put(taskId, future);
        return future;
    }

    @Override
    public List<ChatResponse> executeBatch(List<Prompt> prompts, LLMCallOptions options) {
        List<ChatResponse> responses = new ArrayList<>(prompts.size());
        for (Prompt p : prompts) {
            responses.add(execute(p, options));
        }
        return responses;
    }

    @Override
    public ChatResponse getAsyncResult(String taskId) {
        Future<ChatResponse> future = asyncTasks.get(taskId);
        if (future == null) return null;
        if (future.isDone()) {
            try { return future.get(); } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private String resolveProvider(LLMCallOptions options) {
        if (options != null && options.getProvider() != null && !options.getProvider().isEmpty())
            return options.getProvider();
        if (properties.getActiveProvider() != null && !properties.getActiveProvider().isEmpty())
            return properties.getActiveProvider();
        for (Map.Entry<String, LLMBackendConfig> e : configMap.entrySet()) {
            if (e.getValue().isEnabled()) return e.getKey();
        }
        return chatClientMap.isEmpty() ? "openai" : chatClientMap.keySet().iterator().next();
    }

    private String resolveModel(LLMCallOptions options, Prompt prompt, LLMBackendConfig config) {
        if (options != null && options.getModel() != null && !options.getModel().isEmpty()) {
            return options.getModel();
        }
        if (prompt.getOptions() != null) {
            String promptModel = prompt.getOptions().getModel();
            if (promptModel != null && !promptModel.isEmpty()) {
                return promptModel;
            }
        }
        if (config != null && config.getDefaultModel() != null && !config.getDefaultModel().isEmpty()) {
            return config.getDefaultModel().split(",")[0].trim();
        }
        return null;
    }

    private Prompt buildCallPrompt(List<Message> messages, Prompt prompt, String model) {
        OpenAiChatOptions runtimeOptions = null;
        if (prompt.getOptions() != null) {
            runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, OpenAiChatOptions.class);
        }
        ChatOptions callOptions = runtimeOptions;
        if (model != null && !model.isEmpty()) {
            callOptions = ModelOptionsUtils.merge(
                    OpenAiChatOptions.builder().model(model).build(),
                    runtimeOptions,
                    OpenAiChatOptions.class);
        }
        return new Prompt(messages, callOptions);
    }

    private List<Message> sanitizeMessages(List<Message> messages, LLMCallOptions options) {
        if (options != null && options.isSkipSanitize()) return null;
        if (messages == null || messages.isEmpty()) return null;

        List<String> enabled = options != null ? options.getEnabledSanitizers() : null;
        if (enabled == null || enabled.isEmpty()) {
            enabled = Arrays.asList("PersonNameSanitizer", "PhoneNumberSanitizer", "BankCardSanitizer");
        }

        List<Message> sanitized = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            String text = getMessageText(msg);
            if (text == null || text.isEmpty()) {
                sanitized.add(msg);
                continue;
            }
            String clean = sanitizerChain.sanitize(text, enabled);
            sanitized.add(rebuildMessage(msg, clean));
        }
        return sanitized;
    }

    private ChatResponse sanitizeResponse(ChatResponse response, LLMCallOptions options) {
        if (response == null || response.getResults() == null) return response;
        List<String> enabled = options != null ? options.getEnabledSanitizers() : null;
        List<Generation> gens = new ArrayList<>(response.getResults().size());
        for (Generation g : response.getResults()) {
            AssistantMessage msg = g.getOutput();
            if (msg == null || msg.getText() == null) {
                gens.add(g);
                continue;
            }
            String clean = sanitizerChain.sanitize(msg.getText(), enabled);
            gens.add(new Generation(new AssistantMessage(clean), g.getMetadata()));
        }
        return new ChatResponse(gens, response.getMetadata());
    }

    private String getMessageText(Message msg) {
        if (msg instanceof UserMessage) return ((UserMessage) msg).getText();
        if (msg instanceof SystemMessage) return ((SystemMessage) msg).getText();
        if (msg instanceof AssistantMessage) return ((AssistantMessage) msg).getText();
        return null;
    }

    private Message rebuildMessage(Message original, String newText) {
        if (original instanceof UserMessage) return new UserMessage(newText);
        if (original instanceof SystemMessage) return new SystemMessage(newText);
        if (original instanceof AssistantMessage) return new AssistantMessage(newText);
        return original;
    }

    private String getContent(ChatResponse resp) {
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) return "";
        Generation g = resp.getResults().get(0);
        return g != null && g.getOutput() != null ? g.getOutput().getText() : "";
    }

    private String toPromptLog(List<Message> messages) {
        if (messages == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m instanceof SystemMessage) sb.append("[System] ").append(((SystemMessage) m).getText()).append("\n");
            else if (m instanceof UserMessage) sb.append("[User] ").append(((UserMessage) m).getText()).append("\n");
        }
        return sb.toString();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { return null; }
    }

    private BigDecimal calcCost(int tokens, LLMBackendConfig config) {
        if (config == null || config.getCostPer1kOutput() == null) return BigDecimal.ZERO;
        return config.getCostPer1kOutput()
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }
}
