# Security Policy

## About This Project

CTF Application is an educational mobile security challenge designed to simulate real-world Android application assessments. Reverse engineering, static code inspection, dynamic binary instrumentation, and network protocol interception are the intended mechanics of this challenge, not vulnerabilities to report.

## Secret Management

This repository is fully open source. However, production cryptographic secrets are strictly isolated from version control:

- **Client Secrets:** Generated locally from a git-ignored configuration file (`native-secrets.properties`) and embedded into C++ headers automatically during the Gradle pre-build lifecycle.
- **Backend Secrets:** Injected via server environment variables for payload decryption, challenge verification, and session signing.

Reading the open-source code will not reveal live keys or credentials, but it outlines the challenge architecture. Participants seeking an authentic assessment experience are encouraged to approach the application as a black-box target.

## Architecture & State Integrity

- **Stateless Sessions:** Initial verification issues signed HS256 JWT tokens carrying challenge claims and unique nonces (`jti`).
- **Distributed State:** Ephemeral challenge states, atomic rate-limiting counters, and active flag registries are managed via Upstash Redis across serverless instances.
- **Idempotency & Abuse Prevention:** Stage 2 validation binds each session to a single unique flag via atomic Lua scripts, allowing retry resilience without multi-flag issuance. Flags have a strict 15-minute expiration window.
- **Version Gating:** The server enforces a minimum supported client version (`MIN_VERSION_CODE = 8`) to ensure participants interact with hardened binary builds.

## Incident History

This project previously underwent credential and keystore rotations to isolate legacy development secrets from git history. All pre-v2.0.0 credentials, signing keystores, and challenge tokens have been permanently retired. For detailed version milestones, see [CHANGELOG.md](CHANGELOG.md).

## Reporting a Vulnerability

Please report genuine infrastructure vulnerabilities that fall outside the intended challenge scope, such as:

- Flaws allowing unauthorized access, modification, or invalidation of other participants' sessions or flags.
- Bypass of the serverless rate limiter or Denial of Service (DoS) vectors against the Redis/Edge infrastructure.
- Inadvertent exposure of production server environment variables or signing private keys.

*Note: Reverse engineering the Android APK, bypassing client-side detection, decrypting client payloads, and manipulating client-controlled parameters are intended challenge objectives, not vulnerabilities.*

To report a vulnerability, please submit a private advisory through GitHub's **Security Advisories** feature on this repository.

## Supported Versions

| Version | Status | Server Compatibility |
| :--- | :--- | :--- |
| **v2.0.0+** (`versionCode >= 8`) | **Supported** | Active |
| **< v2.0.0** (`versionCode < 8`) | **Deprecated** | Rejected (`HTTP 426 Upgrade Required`) |
