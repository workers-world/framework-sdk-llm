package com.books.framework.llm;

import com.books.framework.llm.config.LLMProperties;
import com.books.framework.llm.cache.LLMResponseCache;
import com.books.framework.llm.log.LLMCallLogEntry;
import com.books.framework.llm.log.LLMCallLogger;
import com.books.framework.llm.log.LLMMetrics;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

import reactor.core.publisher.Flux;

@Slf4j
public class LLMServiceImpl implements LLMService {

    private final Map<String, ChatClient> chatClientMap;
    private final Map<String, LLMBackendConfig> configMap;
    private final SanitizerChain sanitizerChain;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final LLMCallLogger callLogger;
    private final LLMProperties properties;
    private final LLMMetrics metrics;
    private final List<LLMCallInterceptor> interceptors;
    private final LLMResponseCache cache;
    private final ObjectProvider<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, Future<ChatResponse>> asyncTasks = new ConcurrentHashMap<>();

    public LLMServiceImpl(Map<String, ChatClient> chatClientMap,
                          List<LLMBackendConfig> configs,
                          List<Sanitizer> sanitizers,
                          LLMCallLogger callLogger,
                          LLMProperties properties,
                          LLMMetrics metrics,
                          List<LLMCallInterceptor> interceptors,
                          LLMResponseCache cache,
                          ObjectProvider<ToolCallback> toolCallbacks) {
        this.chatClientMap = new ConcurrentHashMap<>(chatClientMap);
        this.configMap = new ConcurrentHashMap<>();
        for (LLMBackendConfig c : configs) {
            if (c.isEnabled()) this.configMap.put(c.getProvider(), c);
        }
        this.sanitizerChain = new SanitizerChain(sanitizers);
        this.callLogger = callLogger;
        this.properties = properties;
        this.metrics = metrics;
        this.interceptors = interceptors != null ? interceptors.stream().sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder())).toList() : List.of();
        this.cache = cache;
        this.toolCallbacks = toolCallbacks;
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

        // 本地缓存检查
        if (cache != null && (options == null || options.getIdempotencyKey() == null || options.getIdempotencyKey().isEmpty())) {
            String cacheProvider = resolveProvider(options);
            String cacheModel = resolveModel(options, prompt, configMap.get(cacheProvider));
            String promptText = toPromptLog(prompt.getInstructions());
            ChatResponse cached = cache.get(cacheProvider, cacheModel, promptText);
            if (cached != null) return cached;
        }

        // 脱敏（只做一次）
        List<Message> sanitizedMessages = sanitizeMessages(prompt.getInstructions(), options);

        String primaryProvider = resolveProvider(options);
        boolean explicitProvider = options != null
                && options.getProvider() != null
                && !options.getProvider().isEmpty();
        List<String> providers = buildFallbackChain(primaryProvider, !explicitProvider);

        long start = System.currentTimeMillis();
        Exception lastException = null;
        StringBuilder triedProviders = new StringBuilder();
        List<String> failureDetails = new ArrayList<>();

        for (String provider : providers) {
            ChatClient chatClient = chatClientMap.get(provider);
            if (chatClient == null) {
                log.warn("后端不存在，跳过: {}", provider);
                continue;
            }
            LLMBackendConfig config = configMap.get(provider);

            // 限流 + 熔断
            int maxConcurrency = config != null ? config.getMaxConcurrency() : 5;
            int pDailyLimit = config != null ? config.getDailyLimit() : 0;
            int pPerMinuteLimit = config != null ? config.getPerMinuteLimit() : 0;
            if (!rateLimiter.tryAcquire(provider, maxConcurrency, pDailyLimit, pPerMinuteLimit)) {
                log.warn("{} 限流，尝试故障转移", provider);
                continue;
            }
            if (!circuitBreaker.allowsRequest(provider)) {
                rateLimiter.release(provider);
                log.warn("{} 熔断中，尝试故障转移", provider);
                continue;
            }

            try {
                ChatResponse result = callProvider(provider, chatClient, config, prompt, options,
                        sanitizedMessages, idempotencyKey, start);
                return result;
            } catch (Exception e) {
                lastException = e;
                triedProviders.append(provider).append(',');
                failureDetails.add(provider + ": " + e.getMessage());
                log.warn("{} 调用失败{}: {}", provider, explicitProvider ? "" : "，尝试故障转移", e.getMessage());
                // rateLimiter.release done in callProvider finally
            }
        }
        String detail = failureDetails.isEmpty()
                ? (lastException != null ? lastException.getMessage() : "")
                : String.join("; ", failureDetails);
        String errorMessage = "所有后端均失败 [" + triedProviders + "] - " + detail;
        log.error("{}", errorMessage, lastException);

        throw new RuntimeException("所有后端均失败 [" + triedProviders + "] - "
                + detail, lastException);
    }

    private ChatResponse callProvider(String provider, ChatClient chatClient, LLMBackendConfig config,
                                       Prompt prompt, LLMCallOptions options, List<Message> sanitizedMessages,
                                       String idempotencyKey, long start) {
        String modelName = resolveModel(options, prompt, config);
        int maxRetries = config != null ? config.getMaxRetries() : 0;
        String retryStrategy = config != null ? config.getRetryStrategy() : "FIXED";
        String retryOnCodes = config != null ? config.getRetryOnCodes() : "";
        Exception lastException = null;

        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    List<Message> messages = sanitizedMessages != null ? sanitizedMessages : prompt.getInstructions();
                    Prompt callPrompt = buildCallPrompt(messages, prompt, modelName);

                    ChatClient.ChatClientRequestSpec spec = chatClient.prompt(callPrompt);
                    ToolCallback[] tools = resolveTools(options);
                    if (tools.length > 0) {
                        spec = spec.toolCallbacks(java.util.Arrays.asList(tools));
                    }

                    // 拦截器 beforeCall
                    ChatResponse intercepted;
                    for (LLMCallInterceptor interceptor : interceptors) {
                        intercepted = interceptor.beforeCall(prompt, options);
                        if (intercepted != null) return intercepted;
                    }

                    ChatResponse chatResponse = spec.call().chatResponse();

                    // 拦截器 afterCall
                    long afterElapsed = System.currentTimeMillis() - start;
                    for (LLMCallInterceptor interceptor : interceptors) {
                        chatResponse = interceptor.afterCall(prompt, options, chatResponse, afterElapsed);
                    }

                    // 响应脱敏
                    if (options == null || !options.isSkipSanitize()) {
                        chatResponse = sanitizeResponse(chatResponse, options);
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    int tokens = 0;
                    int inputTokens = 0;
                    int outputTokens = 0;
                    if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                        var usage = chatResponse.getMetadata().getUsage();
                        tokens = (int) usage.getTotalTokens();
                        inputTokens = (int) usage.getPromptTokens();
                        outputTokens = (int) usage.getCompletionTokens();
                    }
                    if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getModel() != null) {
                        modelName = chatResponse.getMetadata().getModel();
                    }

                    circuitBreaker.recordSuccess(provider);
                    if (metrics != null) metrics.recordSuccess(provider, modelName, elapsed, inputTokens, outputTokens);
                    if (cache != null && (options == null || options.getIdempotencyKey() == null || options.getIdempotencyKey().isEmpty())) {
                        cache.put(provider, modelName, toPromptLog(prompt.getInstructions()), chatResponse);
                    }

                    // DB 审计日志
                    if (callLogger != null) {
                        try {
                            BigDecimal cost = calcCost(inputTokens, outputTokens, config);
                            callLogger.log(LLMCallLogEntry.builder()
                                    .provider(provider).modelName(modelName)
                                    .requestPrompt(toPromptLog(prompt.getInstructions()))
                                    .responseText(getContent(chatResponse))
                                    .inputTokens(inputTokens).outputTokens(outputTokens)
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
                    lastException = e;
                    if (attempt < maxRetries && isRetryable(e, retryOnCodes)) {
                        long delay = calcRetryDelay(attempt, retryStrategy);
                        log.warn("LLM 重试 {}/{} provider={} delay={}ms: {}",
                                attempt + 1, maxRetries, provider, delay, e.getMessage());
                        try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                    break;
                }
            }

            // 所有重试已用尽
            long elapsed = System.currentTimeMillis() - start;
            for (LLMCallInterceptor interceptor : interceptors) {
                interceptor.onError(prompt, options, lastException);
            }
            circuitBreaker.recordFailure(provider);
            if (metrics != null) metrics.recordFailure(provider, modelName);
            if (callLogger != null) {
                try {
                    callLogger.log(LLMCallLogEntry.builder()
                            .provider(provider).modelName(modelName)
                            .requestPrompt(toPromptLog(prompt.getInstructions()))
                            .success(false).errorMessage(lastException != null ? lastException.getMessage() : null)
                            .responseTimeMs((int) elapsed)
                            .idempotencyKey(idempotencyKey)
                            .build());
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("LLM 调用失败: " + provider + " (model=" + modelName + ") - "
                    + (lastException != null ? lastException.getMessage() : ""), lastException);
        } finally {
            rateLimiter.release(provider);
        }
    }

    @Override
    public Flux<ChatResponse> executeStream(Prompt prompt, LLMCallOptions options) {
        String provider = resolveProvider(options);
        ChatClient chatClient = chatClientMap.get(provider);
        if (chatClient == null) {
            log.error("未找到后端: {}", provider);
            throw new RuntimeException("未找到后端: " + provider);
        }
        LLMBackendConfig config = configMap.get(provider);

        // 脱敏输入
        List<Message> sanitizedMessages = sanitizeMessages(prompt.getInstructions(), options);
        List<Message> messages = sanitizedMessages != null ? sanitizedMessages : prompt.getInstructions();

        // 限流 + 熔断
        int maxConcurrency = config != null ? config.getMaxConcurrency() : 5;
        int pDailyLimit = config != null ? config.getDailyLimit() : 0;
        int pPerMinuteLimit = config != null ? config.getPerMinuteLimit() : 0;
        if (!rateLimiter.tryAcquire(provider, maxConcurrency, pDailyLimit, pPerMinuteLimit)) {
            throw new RuntimeException("调用频率限制: " + provider);
        }
        if (!circuitBreaker.allowsRequest(provider)) {
            rateLimiter.release(provider);
            throw new RuntimeException("后端已熔断: " + provider);
        }

        String modelName = resolveModel(options, prompt, config);
        Prompt callPrompt = buildCallPrompt(messages, prompt, modelName);
        long start = System.currentTimeMillis();
        String idempotencyKey = options != null ? options.getIdempotencyKey() : null;

        ChatClient.ChatClientRequestSpec streamSpec = chatClient.prompt(callPrompt);
        ToolCallback[] tools = resolveTools(options);
        if (tools.length > 0) {
            streamSpec = streamSpec.toolCallbacks(java.util.Arrays.asList(tools));
        }
        return streamSpec.stream().chatResponse()
                .map(chunk -> {
                    if (options == null || !options.isSkipSanitize()) {
                        return sanitizeResponse(chunk, options);
                    }
                    return chunk;
                })
                .doOnComplete(() -> {
                    rateLimiter.release(provider);
                    circuitBreaker.recordSuccess(provider);
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("LLM 流式调用完成: provider={}, model={}, elapsed={}ms", provider, modelName, elapsed);
                })
                .doOnError(e -> {
                    rateLimiter.release(provider);
                    circuitBreaker.recordFailure(provider);
                    long elapsed = System.currentTimeMillis() - start;
                    log.error("LLM 流式调用失败: provider={}, elapsed={}ms, error={}", provider, elapsed, e.getMessage());
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
                });
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
        List<CompletableFuture<ChatResponse>> futures = new ArrayList<>(prompts.size());
        for (Prompt p : prompts) {
            futures.add(CompletableFuture.supplyAsync(() -> execute(p, options), asyncExecutor));
        }
        List<ChatResponse> responses = new ArrayList<>(prompts.size());
        for (CompletableFuture<ChatResponse> f : futures) {
            responses.add(f.join());
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

    private ToolCallback[] resolveTools(LLMCallOptions options) {
        if (options == null || options.getToolNames() == null || options.getToolNames().isEmpty()) {
            return new ToolCallback[0];
        }
        Set<String> names = new HashSet<>(options.getToolNames());
        return toolCallbacks.stream()
                .filter(t -> names.contains(t.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
    }

    private List<String> buildFallbackChain(String primaryProvider, boolean allowFallback) {
        if (!allowFallback) {
            return primaryProvider != null && !primaryProvider.isEmpty()
                    ? List.of(primaryProvider) : List.of();
        }
        // 按 priority 排序所有启用后端，主 provider 放最前
        List<Map.Entry<String, LLMBackendConfig>> sorted = new ArrayList<>(configMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getValue().getPriority(), b.getValue().getPriority()));

        List<String> result = new ArrayList<>();
        // 先把主 provider 放第一个
        if (primaryProvider != null && configMap.containsKey(primaryProvider)) {
            result.add(primaryProvider);
        }
        // 然后按 priority 追加其他 enable 的 provider
        for (Map.Entry<String, LLMBackendConfig> e : sorted) {
            if (!e.getKey().equals(primaryProvider) && e.getValue().isEnabled()) {
                result.add(e.getKey());
            }
        }
        return result;
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
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (prompt.getOptions() instanceof OpenAiChatOptions openAiOpts) {
            if (openAiOpts.getTemperature() != null) {
                builder.temperature(openAiOpts.getTemperature());
            }
            if (openAiOpts.getMaxTokens() != null) {
                builder.maxTokens(openAiOpts.getMaxTokens());
            }
            if (openAiOpts.getResponseFormat() != null) {
                builder.responseFormat(openAiOpts.getResponseFormat());
            }
            if (openAiOpts.getModel() != null && !openAiOpts.getModel().isEmpty()) {
                builder.model(openAiOpts.getModel());
            }
        }
        if (model != null && !model.isEmpty()) {
            builder.model(model);
        }
        return new Prompt(messages, builder.build());
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

    private boolean isRetryable(Exception e, String retryOnCodes) {
        if (retryOnCodes == null || retryOnCodes.isEmpty()) return false;
        String[] codes = retryOnCodes.split(",");
        String msg = e.getMessage();
        if (msg == null) return false;
        for (String code : codes) {
            if (msg.contains(code.trim())) return true;
        }
        return false;
    }

    private long calcRetryDelay(int attempt, String strategy) {
        long base = 1000L;
        if ("EXPONENTIAL".equalsIgnoreCase(strategy)) {
            return base * (1L << attempt); // 1s, 2s, 4s, 8s...
        }
        return base; // FIXED: constant 1s
    }

    private BigDecimal calcCost(int inputTokens, int outputTokens, LLMBackendConfig config) {
        if (config == null) return BigDecimal.ZERO;
        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;
        if (config.getCostPer1kInput() != null) {
            inputCost = config.getCostPer1kInput()
                    .multiply(BigDecimal.valueOf(inputTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }
        if (config.getCostPer1kOutput() != null) {
            outputCost = config.getCostPer1kOutput()
                    .multiply(BigDecimal.valueOf(outputTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }
        return inputCost.add(outputCost);
    }
}
