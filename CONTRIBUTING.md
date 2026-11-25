# Contributing Guidelines

## Purpose and Scope

**CTF Application** is an educational mobile security challenge designed to simulate a multi-stage Android application security assessment. While community contributions that improve platform compatibility, stability, and documentation are welcome, all submissions must preserve the integrity of the challenge stages and adhere to strict anti-spoiler boundaries.

| Category | Examples | Contribution Policy |
| :--- | :--- | :--- |
| **In-Scope Contributions** | Android OS/device compatibility fixes, UI accessibility and lifecycle stability improvements, non-breaking backend hardening, and documentation or translation refinements (`README.md` / `README.id.md`). | **Accepted via Pull Request.** Must not alter the intended challenge solution path. |
| **Intended Challenge Mechanics** | Stage 1 UI payload discrepancy (`timestamp`/`hash` vs. `backdoor_code`), absence of a Stage 2 UI trigger, native JNI key obfuscation, decoy symbols, and runtime anti-tracing heuristics. | **Do not modify.** Pull requests that automate, bypass, or remove challenge puzzles will be closed. |
| **Infrastructure Vulnerabilities** | Server-side session forgery, unissued flag redemption, distributed state tampering, rate-limit bypasses, or production credential exposure. | **Do not open a public Issue or PR.** Report privately per [SECURITY.md](SECURITY.md). |

## Anti-Spoiler & Secret Isolation Policy

To keep the assessment fair for all participants, public issues, commit messages, and pull requests must follow these rules:

- **No Spoilers or Walkthroughs:** Do not post challenge solutions, decoded protocol parameters, reverse-engineered native values, or solver scripts in GitHub Issues, Pull Requests, or commit descriptions.
- **No Live Flags or Production Secrets:** Never include issued `FLAG{...}` strings, production cryptographic keys, or live session tokens recovered from the official release APK in any public submission.
- **Placeholder Credentials Only:** Always use the dummy 32-byte placeholder configuration (`native-secrets.properties.example` and `server/.env.example`) and mock local environment variables when building, testing, or demonstrating changes.

## Local Development & Standards

Before submitting a pull request, ensure your changes align with the project's architecture and toolchain requirements:

- **Android Client (`android_client/`):** Targets `compileSdk 36` / `targetSdk 36` (`minSdk 26`) using JDK 17, Android NDK `26.1.10909125`, CMake `3.22.1`, and `Python 3.9+` for pre-build native header generation. Native C++ changes must maintain 16 KB ELF page-size alignment across all four ABI targets (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). See [Building from Source](README.md#getting-started) in `README.md`.
- **Verification Backend (`server/`):** Requires `Node.js >= 20.0.0` and Express `^5.2.1`. Changes to middleware or route handlers must preserve existing HTTP status contracts (`200`, `400`, `401`, `403`, `410`, `413`, `426`, `429`, `503`), response headers, and atomic Redis Lua semantics.
- **Documentation Parity:** Any user-facing documentation change in [README.md](README.md) should be mirrored in the Indonesian translation ([README.id.md](README.id.md)), and notable functional changes should be recorded in [CHANGELOG.md](CHANGELOG.md).

## Submitting Issues & Pull Requests

1. **Bug Reports:** Open a GitHub Issue describing the affected component (`android_client/` or `server/`), environment details (Android API level, device/emulator architecture, or Node.js version), and reproduction steps using dummy configuration values.
2. **Pull Requests:** Fork the repository, create a descriptive topic branch, and keep changes focused on a single concern with clean, conventional commit messages.
3. **Verification:** Confirm that local Android release compilation (`gradlew assembleRelease`) and standalone backend startup succeed without warnings or secret leaks before requesting a review.
