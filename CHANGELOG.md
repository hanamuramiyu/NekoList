# Changelog

All notable user-facing changes to monban will be documented in this file.

## 3.1.0 — 2026-08-22

Administration & UX update.

### Highlights

- Shared Adventure presentation layer for whitelist, access, help, and status output.
- Interactive scoped-access pagination with filter-preserving navigation.
- Automatic ONLINE profile resolution through the official Mojang profile endpoint.
- Explicit UUID input remains available and bypasses profile lookup.
- Permission-aware `/monban` help on supported management commands.
- Runtime `/monban whitelist enable` and `/monban whitelist disable` on Bukkit/Spigot, Paper/Folia, and Velocity.
- Atomic persistence of runtime whitelist policy changes with runtime state updated only after a successful save.
- Velocity `/monban lookup <player>` with ONLINE and OFFLINE identity and whitelist status details.

## 3.0.0 — 2026-08-12

Major rewrite of monban with redesigned access control and restored Velocity support.

### Highlights

- Standalone whitelist support for Bukkit, Spigot, Paper, and Folia.
- Network-wide access control for Velocity.
- Explicit `ONLINE` and `OFFLINE` Minecraft identities.
- Unified `/monban whitelist` administration across supported platforms.
- `SERVER_GROUP` and `SERVER` access grants on Velocity.
- Backend access policies for Velocity networks.
- Velocity `/monban access` administration.
- Velocity `/monban status` operator summary.
- Optional hybrid `ONLINE` / `OFFLINE` authentication on Velocity.
- Fail-closed access enforcement and native whitelist conflict protection.
