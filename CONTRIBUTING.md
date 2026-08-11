# Contributing to monban

Thanks for taking the time to contribute.

## Before you start

- Search existing issues before opening a new one.
- Small fixes, tests, documentation improvements, and focused platform improvements are welcome.
- Discuss large behavior, storage-format, security, or architecture changes before implementing them.
- Keep pull requests focused on one coherent change.

## Development requirements

- JDK 21
- Gradle Wrapper from this repository
- A suitable Minecraft platform runtime when testing platform-specific behavior

Paper runtime requirements are version-specific. Paper `26.1+` requires Java 25 to run even though monban plugin bytecode targets Java 21.

## Building and testing

```bash
./gradlew clean build
git diff --check
```

Platform and security-sensitive changes should also be runtime-tested on the affected server/proxy implementation where practical.

## Pull requests

- Explain what changed and why.
- Add or update tests where appropriate.
- Update user-facing documentation when behavior changes.
- Preserve explicit `ONLINE` / `OFFLINE` identity semantics.
- Do not silently weaken fail-closed access behavior.
- Do not introduce unrelated refactors into the same pull request.

## Reporting bugs

Use the GitHub bug report form and include enough information to reproduce the problem: platform, versions, deployment mode, relevant configuration, logs, and reproduction steps.

Potential security vulnerabilities should follow [SECURITY.md](SECURITY.md) instead of being disclosed in a normal public issue.
