# CTF Application

English | [Bahasa Indonesia](README.id.md)

---

A hands-on mobile security challenge simulating an Android application security assessment. Test your capabilities in reverse engineering, dynamic runtime inspection, cryptographic analysis, and network protocol manipulation.

## Architecture

- **Client (Android):** Java with a native C++ (JNI) engine (Android 8.0+ / API 26+, targeting API 34), hardened with compile-time symbol obfuscation, execution timing noise, and passive tracing detection.
- **Backend (Serverless):** Modular Node.js REST API deployed on Vercel, backed by Upstash Redis for distributed state, atomic Lua verification pipelines, stateless JWT session tokens, and version gating.
- **Cryptography:** AES-256-GCM payload encryption protecting client-server communication.

## Tools & Prerequisites

- **Static Analysis:** JADX, Apktool, or Ghidra.
- **Dynamic Instrumentation:** Frida or Objection.
- **Traffic Interception:** Burp Suite or OWASP ZAP *(note: user-installed CA certificates require network security configuration or runtime bypass on modern Android)*.

## Challenge Specifications

The objective is to compromise the verification pipeline and claim the flag from the remote server across three sequential phases.

### Phase 1: Cryptographic Extraction

The client transmits an encrypted envelope to the backend. Determine what the server expects to validate this initial handshake.

<details>
<summary>💡 Hints for Phase 1</summary>

- **Orientation:** Look beneath the managed runtime. The application's true core speaks a lower-level tongue where boundaries blur.
- **Method:** What is written directly on the binary's surface is not always what the engine executes. Runtime memory remembers what static eyes mistake.
- **Evidence:** The gatekeeper does not simply open or shut; listen carefully to how it rejects you. An empty pocket produces a different echo than a forged key.
</details>

### Phase 2: Protocol Manipulation

Passing the first gate grants access to a server-side evaluation requiring data not present in the default flow.

<details>
<summary>💡 Hints for Phase 2</summary>

- **Orientation:** Passing the first trial is not just a doorway, but a message. Never discard the envelope that brought you through.
- **Method:** The authority grants a signed seal alongside an unspoken condition. Inspect the metadata surrounding the grant—the answer bridges what is given and what is demanded.
- **Evidence:** Time is not static, and the balance requires transformation. Solve the relationship before the authority's patience expires.
</details>

### Phase 3: Final Verification

Submit the claimed flag to complete the assessment and unlock the Hall of Fame.

<details>
<summary>💡 Hints for Phase 3</summary>

- **Orientation:** The prize cannot be shared or hoarded; it recognizes only the session that earned it.
- **Method:** You may present your tribute through the standard interface, or speak directly to the final endpoint.
- **Evidence:** The gatekeeper only respects travelers carrying modern credentials. Ensure your transmission proclaims the current era.
</details>

## Getting Started

1. Download the latest `.apk` from [Releases](../../releases). Outdated builds are rejected with `HTTP 426`.
2. Install on an emulator or physical test device (Android 8.0+).
3. Set up your interception proxy and begin reverse engineering.

For detailed release notes and migration history, refer to [CHANGELOG.md](CHANGELOG.md).

### Building from Source (Optional)

```bash
# 1. Setup local native secrets configuration
cp android_client/app/src/main/cpp/native-secrets.properties.example android_client/app/src/main/cpp/native-secrets.properties

# 2. Build Release APK using Gradle Wrapper
cd android_client
./gradlew assembleRelease      # Linux / macOS
gradlew.bat assembleRelease    # Windows
```
The output APK will be placed in `android_client/app/build/outputs/apk/release/app-release.apk`.

## Privacy & Abuse Prevention

This challenge does not collect personal data.
- **Rate Limiting:** Client IP addresses are stored ephemerally in Redis with automatic expiry (up to 30 minutes for abusive clients) strictly for brute-force mitigation.
- **Solve Counter:** An anonymous global solve counter tracks completed challenges without storing user identifiers.
- **Traffic:** Only challenge payloads are evaluated. Do not transmit personal credentials.

## Release Verification

Official release APKs are signed with a dedicated developer keystore:

- **Signer SHA-256:** `11919c358daf9c9f56da77617284fb99cc402fc789242fe8c75c674a3c551a62`
- Verify using `apksigner`:
  ```bash
  apksigner verify --print-certs app-release.apk
  ```
- Compare the file SHA-256 digest against the checksum published in the corresponding [GitHub Release](../../releases).

---

*Disclaimer: This repository is intended strictly for educational security research and ethical hacking practice. Automated DoS or brute-force attacks against the live infrastructure are strictly prohibited.*
