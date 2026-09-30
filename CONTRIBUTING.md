# Contributing

Thank you for your interest in improving `framework-sdk-llm`.

## Before you start

- This module depends on [`framework-sdk-core`](https://github.com/workers-world/framework-sdk) via `framework-sdk-bom`. Until a **stable, publicly resolvable** BOM is published, local builds may require access to the organization Maven registry and SNAPSHOT artifacts. See [README.md](README.md#dependencies).
- Do **not** open pull requests that change repository visibility to public; release and dependency publishing are coordinated separately (see Linear **WW-33** and related framework-sdk remediation work).

## Development setup

Requirements:

- JDK 17+
- Maven 3.9+
- Access to `com.books:framework-sdk-core` (GitHub Packages or internal mirror)

```bash
git clone https://github.com/workers-world/framework-sdk-llm.git
cd framework-sdk-llm
mvn test
```

Configure Maven credentials for GitHub Packages if required by your environment (`settings.xml` server id matching the repository distribution configuration).

## Pull request guidelines

1. Fork or branch from `dev_00_01_00` (or the current default integration branch).
2. Keep changes focused; avoid unrelated refactors.
3. Update [README.md](README.md) when adding configuration keys, endpoints, or database schema changes.
4. Update [SECURITY.md](SECURITY.md) when changing authentication boundaries or sensitive data handling.
5. Run `mvn test` before submitting.
6. Use clear commit messages (English or 中文 is fine; be descriptive).

## Code style

- Match existing patterns: Lombok where already used, Spring Boot 3.x conventions, package `com.books.framework.llm`.
- Prefer extending `ChatModelFactory` for new providers rather than duplicating OpenAI client wiring.

## Security-related changes

Changes that touch `/api/llm/**`, logging of prompts, or API key persistence require explicit notes in the PR description and an update to SECURITY.md.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
