# Security Policy

## Purpose and Scope

**CTF Application** is an educational mobile security challenge designed to simulate an Android application security assessment. Because the application is intentionally built to be analyzed, it is important to distinguish between **intended challenge mechanics** and **actual infrastructure vulnerabilities**.

| Category | Examples | Policy Action |
| :--- | :--- | :--- |
| **Intended Challenge Mechanics** | Decompiling the APK, recovering obfuscated native values, bypassing client-side runtime checks, decrypting request payloads, and crafting protocol requests to obtain a flag. | **Expected participant activity.** Do not report as security vulnerabilities. |
| **Infrastructure Boundaries** | Bypassing server-side session signing without completing Phase 1, forging unissued flags, tampering with shared backend state, bypassing distributed rate limits, or exposing production server secrets. | **Reportable security issue.** Submit privately via [Reporting a Vulnerability](#reporting-a-vulnerability). |

## Security Controls & Isolation

Although the repository source code is publicly available for educational reference, live operational credentials and state boundaries are isolated from version control:

- **Secret Isolation:** Client cryptographic material is generated locally at build time from git-ignored configuration files (`native-secrets.properties`), while backend decryption keys, session signing secrets, and database credentials are injected exclusively through server environment variables.
- **Session & Flag Integrity:** Challenge sessions and issued flags are cryptographically signed or registered in distributed server state with strict expiration windows and atomic single-use validation.
- **Abuse Mitigation:** The backend enforces request payload size limits, schema validation, client version gating, and distributed IP rate limiting with progressive cooldowns to protect service availability.
- **Credential Lifecycle:** Legacy development keys, signing keystores, and retired challenge tokens from pre-v2.0.0 releases have been permanently rotated and invalidated. See [CHANGELOG.md](CHANGELOG.md) for historical release milestones.

## Supported Versions

Only the current release series is supported by the verification backend. Outdated client versions are automatically rejected at the API gateway.

| Version | Status | Backend Compatibility |
| :--- | :--- | :--- |
| **v2.0.0+** (`versionCode >= 8`) | **Supported** | Active |
| **< v2.0.0** (`versionCode < 8`) | **Deprecated** | Rejected (`HTTP 426 Upgrade Required`) |

## Reporting a Vulnerability

If you discover a genuine vulnerability that crosses the **Infrastructure Boundaries** defined above—such as unauthorized cross-session access, server-side secret exposure, or a denial-of-service vector against the backend infrastructure—please report it responsibly:

1. Submit a private report through the **Security Advisories** tab on this GitHub repository.
2. Include a clear technical description, reproduction steps, and the affected component (`server/` or build pipeline).
3. Do not publicly disclose the issue, execute destructive tests against live infrastructure, or publish active production credentials/tokens.
