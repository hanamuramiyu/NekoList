# Paper / Folia

This module provides monban's native integration for Paper and Folia servers.

## Packaging

- The artifact is packaged as a Paper plugin using `paper-plugin.yml`.
- It is intentionally Paper/Folia-only and is separate from the Bukkit/Spigot compatibility artifact.
- Bukkit/Spigot servers must use `monban-bukkit-spigot` instead.
- The plugin descriptor declares `folia-supported: true`.

## Folia compatibility

Paper/Folia platform code must remain safe under Folia's regionized execution model:

- do not assume a single global main thread for world or entity work;
- run world and location work in the correct region context;
- run entity work in the correct entity context;
- use the global scheduler for global work;
- use the async scheduler for tick-independent asynchronous work.

Declaring `folia-supported: true` only allows Folia to load the plugin; the implementation must still obey the platform's region and threading rules.

## Deployment

The Paper/Folia plugin supports `deployment.mode: STANDALONE`. It can run on backend servers behind Velocity using the server's normal Velocity forwarding configuration, but network-wide monban access control is handled by `monban-velocity`.

Configuring `deployment.mode: VELOCITY` on the Paper/Folia plugin is rejected.

When the server is a backend in a Velocity network, monban can apply synchronized player-group and direct permissions locally. Install the Velocity monban build as the network authority, install this build on the backend, enable `backend-permissions` in `config.yml`, set the backend name, and copy the generated `sync.yml` from Velocity. In centralized mode local whitelist administration is disabled.

For a backend registered in Velocity as `lobby`, use:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
```

Use the same `plugins/monban/sync.yml` contents on Velocity and the backend. `server-groups.yml` is created automatically. If `SERVER_GROUP` permissions are used, configure the same server-to-group mapping there as on Velocity. `state-revision` is a Velocity file and is not needed on the backend.

Backend permissions can be checked with Chunky:

```text
monban group moderator permission add server lobby chunky.command.start
monban group moderator permission add server lobby chunky.command.cancel
```

After assigning the group to a player and reconnecting, `/chunky ` should show the permitted subcommands and `/chunky start` should execute.

Whitelist administration uses Paper/Folia schedulers directly at the platform boundary; there is no generic scheduler abstraction.
