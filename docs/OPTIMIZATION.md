# LLM SDK 优化清单

> commit: `8c1a6d2` | 23 files | +912/-139 | 2026-06-21

## 功能优化

### 1. 流式调用 (Streaming SSE)

- `LLMService.executeStream()` — 返回 `Flux<ChatResponse>` 逐块推送
- `POST /api/llm/execute-stream` — SSE 端点，`text/event-stream`
- 支持逐块脱敏、完成/错误回调、300s 超时

### 2. 自动重试

- 消费 `llm_backend_config` 中已存在的 `max_retries` / `retry_strategy` / `retry_on_codes`
- 策略: `FIXED` (固定 1s) / `EXPONENTIAL` (1s, 2s, 4s, 8s...)
- 异常 message 匹配 HTTP 状态码判断是否可重试
- 重试期间保持并发槽位不释放

### 3. 熔断器 HALF_OPEN 状态

- 标准三态: `CLOSED → OPEN → HALF_OPEN → CLOSED`
- 半开状态最多放行 3 个探测请求 (可配置)
- 探测成功：关闭熔断；探测失败：重新熔断

### 4. Provider 故障转移

- `llm_backend_config.priority` 字段控制优先级
- 限流 / 熔断 / 重试耗尽 → 自动尝试下一个 provider
- 全部失败时抛出 "所有后端均失败" 异常

### 5. 批量并行化

- `executeBatch()` 从串行 for 改为 `CompletableFuture.supplyAsync()` 并行
- 受全局 `RateLimiter` 并发控制约束

### 6. Token 计数增强

- 新增 `input_tokens` / `output_tokens` 字段分别记录
- 成本公式: `input × cost_per_1k_input + output × cost_per_1k_output`
- 从 Spring AI Usage 取 `getPromptTokens()` / `getCompletionTokens()`

### 7. 拦截器链扩展

- `LLMCallInterceptor` 接口: `beforeCall()` / `afterCall()` / `onError()`
- 实现 `Ordered` 控制顺序，通过 Spring Bean 自动注册
- `beforeCall` 可短路返回（缓存命中等场景）

### 8. 限流器增强

- 分钟窗口切换从非原子 `set()` 改为 `compareAndSet()` CAS
- 日计数器 24h 懒自动重置（无需 `@Scheduled`）
- per-provider 日/分钟限制 (`daily_limit` / `per_minute_limit`)
- 全局限制为 fallback

### 9. 多模态支持

- `LLMExecuteX1.mediaUrls` — 图片 URL 列表
- 自动构建 `UserMessage(text, List<Media>)`
- 支持所有 OpenAI 视觉兼容模型（Cloudflare LLaVA、GPT-4V 等）

### 10. Function Calling / Tool Use

- `LLMAutoConfiguration` 自动收集 `ToolCallback` Bean
- `ChatClient.Builder.defaultTools()` 全局注册
- `LLMCallOptions.toolNames` / `LLMExecuteX1.toolNames` — 按请求过滤

### 11. 本地缓存

- Caffeine `LLMResponseCache`: 1000 条 / 30 分钟 TTL
- 缓存键: `SHA-256(provider | model | promptText)`
- 有幂等键时跳过缓存（幂等优先）
- `LLMResponseCache.invalidateAll()` 手动清空

### 12. 可观测性

- `LLMMetrics` — Micrometer 指标
  - `llm.calls` counter: provider / model / result(success|failure)
  - `llm.latency` timer: provider / model
  - `llm.tokens` counter: provider / type(input|output)

### 13. 结构化输出映射

- `LLMService.executeForType(Prompt, LLMCallOptions, Class<T>)` — 泛型默认方法
- JSON 响应自动反序列化为目标 POJO

## Schema 变更

| 表 | 变更 |
|----|------|
| `llm_backend_config.default_model` | VARCHAR(50) → VARCHAR(128) |
| `llm_backend_config` | + `priority` INT, `daily_limit` INT, `per_minute_limit` INT |
| `llm_call_logs.model_name` | VARCHAR(50) → VARCHAR(128) |
| `llm_call_logs` | + `input_tokens` INT, `output_tokens` INT |

## Provider 路由新增

`createChatModel()` switch 新增默认 baseUrl 映射:

| provider | baseUrl |
|----------|---------|
| `cloudflare` | `https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1` |
| `groq` | `https://api.groq.com/openai/v1` |
| `together` | `https://api.together.xyz/v1` |

## Bug 修复

| Bug | 修复 |
|-----|------|
| `PersonNameSanitizer.maskPersonNames()` 方法不存在 | 改用 `HanLPKeywordExtractor.extractGrouped()` |
| `LLMConfigController.refreshBackends()` RowMapper 丢失 `maxRetries`/`costPer1k` 等 10+ 字段 | 补全所有字段映射 |
| `RateLimiter.checkPerMinute()` 窗口切换非原子竞态 | CAS `compareAndSet` |
| Surefire 2.12.4 不支持 JUnit 5 | 升级到 3.2.5 |

## 测试覆盖

| 测试类 | 用例数 | 覆盖 |
|--------|--------|------|
| `RateLimiterTest` | 6 | 限流 / 释放 / per-provider / 日重置 |
| `CircuitBreakerTest` | 7 | 三态转换 / HALF_OPEN / 独立 provider |
| `SanitizerChainTest` | 6 | 排序 / 过滤 / 空值 |
| `LlmHttpClientSupportTest` | 1 | (已有) |
| **合计** | **20** | |

## 未实现（远期规划）

- 模块拆分 (core / spring / sanitizer 多模块)
