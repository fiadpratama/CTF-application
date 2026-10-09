# Security Policy

## About This Project

CTF Application is an educational mobile security challenge. It is designed to be reverse-engineered and analyzed as part of the intended learning experience — static analysis, dynamic instrumentation, and network protocol interception are the core mechanics of the challenge itself, not vulnerabilities to be reported.

## Secret Management

This repository is open source, including the native C++ layer and backend server code. Cryptographic secrets (the E2EE key, backdoor authorization token, session signing key, and stage-2 multiplier) are kept out of version control; see Incident History for earlier exposure and remediation. They are:

- Generated locally from a git-ignored configuration file (`native-secrets.properties`) for the Android client, via `generate_keys.py`.
- Injected via environment variables for the backend server (`E2EE_KEY`, `BACKDOOR_CODE`, `SECRET_MULTIPLIER`, `JWT_SECRET`).

Reading the source will not reveal these values, but it does reveal how the challenge works. Participants who prefer to discover the mechanics themselves may want to avoid reading the backend and native sources before attempting the challenge.

## Architecture Notes

Stage 1 session state is carried in signed, stateless JWTs, and each token can be redeemed only once. State that must be shared across serverless function instances — flag records, used session identifiers, and rate-limiting counters — is stored in Upstash Redis, provisioned through the Vercel Marketplace. Flags are short-lived and, after their first successful verification, remain valid only for a brief grace window.

## Incident History

This project has undergone credential rotation following past exposure of secrets in earlier commits (native encryption key, backdoor code, signing keystore, stage-2 multiplier). All affected values have been rotated and are no longer valid. See the release notes for version-specific details.

## Reporting a Vulnerability

Please report genuine security issues that fall outside the intended challenge mechanics, for example:

- A way to access, modify, or invalidate other participants' sessions or flags.
- A weakness in the backend's session handling, rate limiting, or storage that allows abuse of the infrastructure.
- Exposure of the session signing key or other server-only secrets.

Extracting secrets from the client, intercepting traffic, and manipulating client-supplied values are part of the intended challenge and are not vulnerabilities.

To report an issue, open a private security advisory via GitHub's "Report a vulnerability" feature on this repository, or contact the maintainer through their GitHub profile. Please do not open a public issue for security-sensitive findings.

This is a personal project maintained on a best-effort basis, so response times may vary. Automated or high-volume testing against the live backend is not permitted.

## Supported Versions

Only the latest release is actively maintained. The backend enforces a minimum client version and rejects older builds with HTTP 426; this minimum may be raised in the future when changes require it. Releases that predate credential rotations will not function against the production backend.
