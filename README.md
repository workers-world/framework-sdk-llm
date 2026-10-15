# Framework SDK — LLM

面向 Spring Boot 的多 Provider LLM 调用模块：限流/熔断、PII 脱敏、响应缓存、JDBC 调用日志、管理 API。依赖 [framework-sdk-core](https://github.com/workers-world/framework-sdk)（`BaseMessage`、`@ApiName`、HTTP 客户端等）。

- **groupId / artifactId**: `com.books:framework-sdk-llm`
- **当前版本**: `1.3.2-SNAPSHOT`
- **要求**: JDK 17+，Spring Boot 4.x，Spring AI 2.x

## 依赖

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.books</groupId>
      <artifactId>framework-sdk-bom</artifactId>
      <version>1.6.1-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.books</groupId>
    <artifactId>framework-sdk-llm</artifactId>
    <version>1.3.2-SNAPSHOT</version>
  </dependency>
</dependencies>
```

GitHub Packages（需可读 `framework-sdk` 与本仓）：

```xml
<repository>
  <id>github-framework-sdk-llm</id>
  <url>https://maven.pkg.github.com/workers-world/framework-sdk-llm</url>
</repository>
<repository>
  <id>github-framework-sdk</id>
  <url>https://maven.pkg.github.com/workers-world/framework-sdk</url>
</repository>
```

## 能力概览

| 能力 | 说明 |
|------|------|
| 多 Provider | OpenAI 兼容端点、Ollama、可配置网关等 |
| 限流 / 熔断 | 日限额、分钟限额、失败熔断 |
| 脱敏 | 姓名/手机/银行卡等链路，调用前处理 prompt |
| 缓存 | Caffeine 响应缓存（可关） |
| 调用日志 | JDBC 表记录 token / 耗时 / 成本 |
| 管理 API | `/api/llm/*`（执行、配置、日志、健康等） |
| 轻量模式 | `book.llm.*`（`SimpleLLMProperties`）创建单个 `ChatClient` |

## 主要配置

完整 LLM 模块（前缀 `llm`）：

```yaml
llm:
  daily-limit: 50
  per-minute-limit: 10
  default-timeout-ms: 10000
  active-provider: openai
  log-prompts: false   # 默认关闭，避免日志泄露 PII
```

后端明细通常存 DB 表 `llm_backend_config`（启动时可自动建表），也可通过管理 API 维护。API Key 勿写入仓库；用环境变量或密钥管理系统注入。

轻量模式（前缀 `book.llm`）：

```yaml
book:
  llm:
    enable: true
    provider: openai
    endpoint: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o
```

## 构建

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn verify
```

## CI

使用 [workers-world/java-actions](https://github.com/workers-world/java-actions) 的 `java-maven-sdk-ci`（pin `actions/v0.5.0`）。verify 会拉取 `framework-sdk` Packages。仓内需配置 Secret：`GITHUB_PACKAGES_TOKEN`、`GHA_TOKEN`。
