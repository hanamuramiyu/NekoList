# Velocity

This module is monban's network access-authority plugin for Velocity.

## Responsibilities

- create a Velocity-specific first-run configuration with `deployment.mode: VELOCITY`;
- resolve authenticated player identity after Velocity authentication;
- enforce the network whitelist before a player connects to a backend;
- enforce scoped backend access before server connection;
- support optional hybrid `ONLINE` / `OFFLINE` authentication-flow selection;
- fail closed when access verification cannot be completed safely.

Minecraft identity forwarding remains the responsibility of Velocity and the backend forwarding configuration. monban does not introduce a second forwarding secret or a separate backend synchronization channel.

## Java baseline

This module targets Java 21 bytecode, matching the rest of monban. Platform runtime requirements remain separate from the plugin bytecode target.
