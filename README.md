# framework-sdk-llm

Spring Boot library for LLM integration in the Books framework ecosystem: multi-provider `ChatClient` wiring, PII sanitization, rate limiting, circuit breaking, JDBC-backed configuration and call logging, and HTTP management APIs.

> **Open-source readiness (Linear [WW-33](https://linear.app)):** This repository remains **private** until upstream `framework-sdk` publishes stable Maven artifacts. Documentation and governance files in this repo unblock the remediation checklist; dependency coordinates are documented below.

## Features

- Spring AI–based chat execution (OpenAI-compatible APIs, Ollama, and pluggable `ChatModelFactory`)
- Optional JDBC storage for provider configs and call logs
- Built-in sanitizers (person name, phone, bank card, order id, address)
- REST controllers under `/api/llm/**` (see [Management APIs](#management-apis))
- Lightweight **SimpleLLM** mode when only a single `ChatClient` bean is needed

## Dependencies

| Artifact | Purpose |
|----------|---------|
| `com.books:framework-sdk-core` | Base message types, `@ApiName`, exceptions |
| Spring Boot Web, Validation, JDBC | HTTP APIs and persistence |
| Spring AI OpenAI + Ollama starters | Model clients |

### `framework-sdk-bom` / SNAPSHOT status

This project imports `com.books:framework-sdk-bom`. The current POM pins a **SNAPSHOT** version because no **stable, publicly verifiable** BOM release was available in this agent’s audit of org tags and accessible package metadata.

**Before public open source:**

1. Complete [framework-sdk](https://github.com/workers-world/framework-sdk) remediation and publish a **non-SNAPSHOT** `framework-sdk-bom`.
2. Update `framework-sdk.version` in `pom.xml` to that release.
3. Re-run CI and publish `framework-sdk-llm` to GitHub Packages.

Until then, builds require credentials to the organization Maven registry. Do not invent placeholder release versions.

## Installation

### Maven

```xml
<dependency>
    <groupId>com.books</groupId>
    <artifactId>framework-sdk-llm</artifactId>
    <version>1.1.0</version>
</dependency>
```

Import the BOM in your parent POM or align Spring Boot / Spring AI versions with this module’s `pom.xml`.

### Enable the module

```yaml
book:
  llm:
    enable: true
```

When `book.llm.enable=true` and Spring AI is on the classpath:

- **Full stack** (`LLMAutoConfiguration`): registers `LLMService`, JDBC tables (if `JdbcTemplate` is present), and `/api/llm/**` controllers.
- **SimpleLLM** (`SimpleLLMAutoConfiguration`): activates only if no `llmService` bean exists; exposes a single `ChatClient` from `book.llm.*` or the first row in `llm_backend_config`.

## Configuration

### Module switch

| Key | Default | Description |
|-----|---------|-------------|
| `book.llm.enable` | (unset / false) | Must be `true` to load LLM auto-configuration |

### SimpleLLM (`book.llm.*`)

Used by `SimpleLLMAutoConfiguration` when the full `LLMService` stack is not registered.

| Key | Default | Description |
|-----|---------|-------------|
| `book.llm.provider` | `openai` | Backend id (`openai`, `qwen`, `deepseek`, …) |
| `book.llm.endpoint` | (provider default) | OpenAI-compatible base URL |
| `book.llm.api-key` | — | Provider API key |
| `book.llm.model` | `gpt-4o` | Default model name |

### Full module (`llm.*`)

Bound to `LLMProperties` (`@ConfigurationProperties(prefix = "llm")`).

| Key | Default | Description |
|-----|---------|-------------|
| `llm.master-key` | — | Reserved for host-app or future use; **not** used as HTTP auth in this module |
| `llm.daily-limit` | `50` | Rate limit: max calls per day |
| `llm.per-minute-limit` | `10` | Rate limit: max calls per minute |
| `llm.default-timeout-ms` | `10000` | Default upstream timeout |
| `llm.active-provider` | — | Default provider id when a request omits `provider` |

Provider connection details for the full module are normally stored in **`llm_backend_config`** (see below), not only in YAML.

## Database tables

When a `JdbcTemplate` bean is available, the module creates (if missing):

### `llm_backend_config`

Stores per-provider endpoints, API keys, models, retry/concurrency, and cost metadata. Managed via `/api/llm/configs/**` and read at startup to build `ChatModel` beans.

Notable columns: `provider`, `endpoint`, `api_key`, `default_model`, `is_enabled`, plus framework `tech_*` audit fields.

### `llm_call_logs`

Stores each call’s prompt, response, tokens, cost, timing, success flag, idempotency key, and metadata. Populated when `JdbcLLMCallLogger` is active.

**Security:** Logs may contain sensitive user content. Restrict database and API access accordingly.

## Provider examples

### OpenAI-compatible (OpenAI, DashScope compatible mode, DeepSeek, etc.)

**YAML (SimpleLLM):**

```yaml
book:
  llm:
    enable: true
    provider: deepseek
    endpoint: https://api.deepseek.com/v1
    api-key: ${DEEPSEEK_API_KEY}
    model: deepseek-chat
```

**Database (full module):** insert into `llm_backend_config`:

```sql
INSERT INTO llm_backend_config (provider, endpoint, api_key, default_model, is_enabled)
VALUES ('deepseek', 'https://api.deepseek.com/v1', '${SECRET}', 'deepseek-chat', 1);
```

Built-in defaults when `endpoint` is empty (OpenAI-compatible path in `LLMAutoConfiguration`):

| `provider` | Default base URL |
|------------|------------------|
| `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `deepseek` | `https://api.deepseek.com/v1` |
| (other) | `https://api.openai.com/v1` |

### Ollama (local)

Register a row with `provider = ollama`. `OllamaChatModelConfig` uses `endpoint` (default `http://localhost:11434`) and `default_model` as the Ollama model tag.

```sql
INSERT INTO llm_backend_config (provider, endpoint, default_model, is_enabled)
VALUES ('ollama', 'http://localhost:11434', 'llama3.2', 1);
```

Execute requests with `provider` set to `ollama` in the `LLMExecuteX1` DTO (via `/api/llm/execute`).

See [docs/examples/application-llm.yml](docs/examples/application-llm.yml) for a copy-paste starter file.

## Management APIs

All endpoints use **POST** and the Books `BaseMessage` envelope unless your host app adds a global prefix.

| Path | Risk | Description |
|------|------|-------------|
| `/api/llm/execute`, `/api/llm/execute-batch` | **High** | Run models (cost + data egress) |
| `/api/llm/configs/**` | **Critical** | List/create/update/delete backend configs and API keys |
| `/api/llm/logs/**` | **High** | Read prompts and responses |
| `/api/llm/stats/**` | Medium | Usage statistics |
| `/api/llm/sanitizers/**` | Medium | Toggle PII sanitizers |
| `/api/llm/health/**` | Low | Health / reachability hints |

## Security

This JAR **does not register Spring Security**. Every `/api/llm/**` route is **unauthenticated by default**. The host application **must** enforce authentication and authorization.

Recommendations:

- Block or protect `/api/llm/**` on any internet-facing deployment.
- Encrypt or externalize `api_key` values; never log raw keys.
- Treat call logs as confidential data.

### Optional Spring Security (host app)

Add rules in your application’s security configuration (adjust paths if you use a context path or admin prefix):

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/llm/**").hasRole("LLM_ADMIN")
    .anyRequest().permitAll());
```

For execute-only vs admin separation, split matchers:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/llm/configs/**", "/api/llm/logs/**", "/api/llm/sanitizers/**")
        .hasRole("LLM_ADMIN")
    .requestMatchers("/api/llm/execute", "/api/llm/execute-batch")
        .hasAnyRole("LLM_USER", "LLM_ADMIN")
    .requestMatchers("/api/llm/health/**", "/api/llm/stats/**")
        .authenticated());
```

See [SECURITY.md](SECURITY.md) for vulnerability reporting and storage guidance.

## Building and releasing

```bash
mvn clean test
```

Tag releases with `v*` to trigger [.github/workflows/release.yml](.github/workflows/release.yml).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
