# Changelog

All notable user-facing changes to monban will be documented in this file.

<!-- Replace YYYY-MM-DD with the release date when v3.0.0 is published. -->

## 3.0.0 — YYYY-MM-DD

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
