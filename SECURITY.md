# Security Policy

## Scope

This repository (`framework-sdk-llm`) is a Spring Boot auto-configuration library for LLM integration. When enabled, it exposes **management and execution HTTP APIs** under `/api/llm/**`. The library **does not ship authentication or authorization** for those endpoints.

## Host application responsibilities

Integrators **must** protect `/api/llm/**` in the host application before exposing the service on any untrusted network:

- Authenticate callers (session, JWT, mTLS, API gateway, etc.).
- Authorize who may **execute** models, **read/write backend configs** (including API keys), **read call logs**, and **change sanitizer settings**.
- Prefer placing management APIs on an internal network or admin-only route prefix.

See [README.md](README.md#security) for endpoint inventory and a Spring Security configuration sketch.

## Sensitive data

- **Provider API keys** may be stored in the `llm_backend_config.api_key` column or in `book.llm.api-key` configuration. Treat these as secrets.
- **Call logs** (`llm_call_logs`) may contain full prompts and model responses, including personal or regulated data. Restrict log API access and define retention policies.
- The `llm.master-key` property exists for future or custom use; it is **not** enforced by this module as an HTTP auth mechanism.

### API key storage recommendations

- Do not commit keys to source control or store them in plain text in shared config repos.
- Prefer a secrets manager or encrypted configuration (e.g. Spring Cloud Config with encryption, vault integration, or database column encryption at the application layer).
- Rotate keys after exposure; use least-privilege provider tokens where supported.

## Default security posture

- No Spring Security filter chain is registered by this artifact.
- Config list APIs mask API keys in responses, but **upsert/update endpoints accept full keys** and **execute endpoints can trigger paid upstream usage**.

## Reporting a vulnerability

If you believe you have found a security issue in this repository:

1. **Do not** open a public GitHub issue with exploit details.
2. Email the maintainers via the contact path listed on the [workers-world](https://github.com/workers-world) organization profile, or use GitHub **Private vulnerability reporting** if enabled for this repository.
3. Include: affected version, component (API, JDBC logging, sanitizer, etc.), reproduction steps, and impact assessment.

We aim to acknowledge reports within a reasonable timeframe and will coordinate disclosure after a fix or mitigation is available.
