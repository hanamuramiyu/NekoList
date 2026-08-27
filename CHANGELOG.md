# Changelog

All notable user-facing changes to monban will be documented in this file.

## 3.2.0 — 2026-08-28

Player groups and permissions update.

### Highlights

- Built-in player groups separate from Velocity `SERVER_GROUP` definitions.
- Group and direct player permissions with `NETWORK`, `SERVER_GROUP`, and `SERVER` scopes.
- Group ACL grants combined with the existing direct whitelist and scoped access grants.
- Effective access lookup showing groups, permissions, and their origins.
- Velocity administration commands for groups, assignments, group permissions, and direct permissions.
- Centralized state synchronization from Velocity to Bukkit and Paper backends.
- Local Bukkit/Paper permission attachments without requiring LuckPerms.
- Fail-closed backend access checks until the first verified synchronized state snapshot is received.
- Automatic `sync.yml` creation on Velocity when backend permissions are enabled.
- Correct ONLINE/OFFLINE identity detection on forwarded hybrid backend connections.
- Backend permission attachments are applied before command visibility is sent to players, with command-tree refresh after permission updates.

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
