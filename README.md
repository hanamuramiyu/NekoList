<div align="center">

<img src="logo.png" alt="monban">

# monban

Minecraft access control for standalone servers and Velocity networks.

[Modrinth](https://modrinth.com/plugin/monban) · [Documentation](https://monban.miyu.pw/) · [GitHub Releases](https://github.com/hanamuramiyu/monban/releases)

</div>

monban is a whitelist and access-control plugin with explicit `ONLINE` and `OFFLINE` player identities. It supports standalone Bukkit/Spigot and Paper/Folia servers, and can act as the access authority for a Velocity network with network-wide and scoped backend access.

## Supported platforms

| Platform | Deployment | Global whitelist | Scoped backend access | Hybrid auth selection |
| --- | --- | :---: | :---: | :---: |
| Bukkit / Spigot | Standalone | ✓ | — | — |
| Paper / Folia | Standalone | ✓ | — | — |
| Velocity | Network authority | ✓ | ✓ | ✓ |

The Paper/Folia artifact is a Paper plugin and is separate from the Bukkit/Spigot compatibility artifact. Hybrid authentication-flow selection belongs to Velocity; the Paper/Folia plugin does not implement `deployment.mode: VELOCITY`.

## Features

- Explicit `ONLINE` and `OFFLINE` identities instead of inferring trust from the presence of a UUID.
- One `/monban whitelist` administration model across supported platforms.
- Network-wide whitelist enforcement on Velocity.
- Positive `SERVER_GROUP` and `SERVER` access grants on Velocity.
- Per-backend `OPEN` and `GRANT_REQUIRED` policies.
- Optional Velocity hybrid `ONLINE` / `OFFLINE` authentication-flow selection.
- Native Folia-compatible scheduling at the Paper platform boundary.
- Strict, versioned YAML configuration and persistent access state.
- Fail-closed startup and access verification for security-sensitive initialization failures.

## Requirements

monban plugin bytecode targets Java 21. Server runtime requirements are platform/version-specific; in particular, Paper `26.1+` requires Java 25 to run.

## Install

1. Download the build for your platform from [Modrinth](https://modrinth.com/plugin/monban) or [GitHub Releases](https://github.com/hanamuramiyu/monban/releases).
2. Put the JAR in the platform's `plugins/` directory.
3. For standalone Bukkit/Spigot or Paper/Folia, set the native Minecraft whitelist to `white-list=false`.
4. Start the server once, configure `plugins/monban/config.yml`, then restart.
5. Manage the global whitelist with `/monban whitelist ...`; `enable` and `disable` can be used at runtime. Velocity additionally provides `/monban lookup ...`, `/monban access ...`, and `/monban status`.

Full setup, configuration, command, storage, and Velocity documentation is available at **[monban.miyu.pw](https://monban.miyu.pw/)**.

## Building

```bash
./gradlew clean build
```

## License

monban is licensed under the [Mozilla Public License 2.0](LICENSE).
