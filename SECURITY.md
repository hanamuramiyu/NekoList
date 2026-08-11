# Security Policy

monban controls access to Minecraft servers and networks. Potential security issues should be handled carefully and should not be disclosed publicly before there is a reasonable opportunity to investigate and fix them.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting for this repository when it is available:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Include the affected monban version, platform and deployment, reproduction steps, impact, and any logs or proof-of-concept details needed to reproduce the issue.

If private vulnerability reporting is not available, do not publish exploit details in a normal issue. Open a minimal public issue stating that you need a private contact path for a potential security vulnerability, without including sensitive technical details.

## What counts as a security issue

Examples include:

- bypassing whitelist or scoped access enforcement;
- confusing `ONLINE` and `OFFLINE` identity provenance in a way that grants unintended access;
- fail-open behavior after initialization or verification failures;
- bypassing Velocity hybrid authentication decisions;
- bypassing backend admission or accessing a restricted backend without the required grant;
- unsafe handling of persisted access-control state that can produce unintended authorization.

Normal command UX problems, configuration questions, crashes without an access-control impact, and ordinary feature requests can use the regular GitHub issue templates.

## Disclosure

Please allow time for investigation, a fix, and release coordination before publishing vulnerability details. Security fixes will be documented publicly when it is safe to do so.
