# Velocity

This module is monban's network access-authority plugin for Velocity.

## Responsibilities

- create a Velocity-specific first-run configuration with `deployment.mode: VELOCITY`;
- resolve authenticated player identity after Velocity authentication;
- enforce the network whitelist before a player connects to a backend;
- enforce scoped backend access before server connection;
- manage player groups, group ACL, and group or direct player permissions;
- provide proxy permissions from effective monban player state;
- distribute signed access and permission state to configured Bukkit/Paper backends;
- support optional hybrid `ONLINE` / `OFFLINE` authentication-flow selection;
- fail closed when access verification cannot be completed safely.

Minecraft identity forwarding remains the responsibility of Velocity and the backend forwarding configuration. The monban state channel is separate from identity forwarding and uses its own shared HMAC secret configured in `sync.yml`.

Backends that need to apply permissions must also install the matching monban Bukkit or Paper build. Enable `backend-permissions` in the backend's `config.yml` and set its `server-name` to the exact registered-server name from Velocity.

When `backend-permissions.enabled` is true, this plugin creates `plugins/monban/sync.yml` automatically. Copy that file to each enabled backend. Keep whitelist, group, and permission administration on Velocity; centralized backends do not provide a second local administration path.

## Java baseline

This module targets Java 21 bytecode, matching the rest of monban. Platform runtime requirements remain separate from the plugin bytecode target.
