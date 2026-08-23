# Security Policy

## About This Project

CTF Application is an educational mobile security challenge. It is designed to be reverse-engineered and analyzed as part of the intended learning experience — static analysis, dynamic instrumentation, and network protocol interception are the core mechanics of the challenge itself, not vulnerabilities to be reported.

## Secret Management

This repository is fully open source, including the native C++ layer and backend server code. Cryptographic secrets (the E2EE key, backdoor authorization token, and stage-2 multiplier) are never committed to version control. They are:

- Generated locally from a git-ignored configuration file (`native-secrets.properties`) for the Android client, via `generate_keys.py`.
- Injected via environment variables for the backend server (`E2EE_KEY`, `BACKDOOR_CODE`, `SECRET_MULTIPLIER`).

Reading the full source is encouraged and will not spoil the challenge — only the runtime secret values are protected.

## Incident History

This project has undergone credential rotation following past exposure of secrets in earlier commits (native encryption key, backdoor code, signing keystore, stage-2 multiplier). All affected values have been rotated and are no longer valid. See the CHANGELOG or release notes for version-specific details.

## Reporting a Vulnerability

If you discover a genuine security issue outside the intended challenge mechanics (e.g. a flaw in the E2EE implementation, a way to bypass version gating without a valid client, or a vulnerability unrelated to the reverse-engineering challenge itself), please open a private security advisory via GitHub's "Report a vulnerability" feature on this repository, or contact the maintainer directly through their GitHub profile.

Please do not open a public issue for security-sensitive findings.

## Supported Versions

Only the latest release is actively maintained. Older versions may use rotated credentials and will not function against the production backend.
