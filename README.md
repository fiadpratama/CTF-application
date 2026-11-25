# CTF Application

English | [Bahasa Indonesia](README.id.md)

---

**CTF Application** is an end-to-end Android security assessment challenge. It is designed to evaluate practical skills in mobile reverse engineering, native binary analysis, cryptographic payload inspection, and client-server protocol reconstruction.

## System Overview

- **Mobile Client:** Android application (`Android 8.0+ / API 26+`, targeting `API 36` with 16 KB page-size support) combining a managed Java interface with a native C++ (JNI) cryptographic module.
- **Verification Backend:** Modular Node.js REST service enforcing client version compatibility, stateful session validation, and automated abuse mitigation.
- **Transport Security:** Authenticated symmetric encryption protecting request payloads over HTTPS.

## Prerequisites & Tooling

- **Target Environment:** Android 8.0+ (API 26+) emulator or physical test device.
- **Static Analysis:** Decompilers and disassemblers for inspecting managed bytecode and native shared libraries (e.g., JADX, Apktool, Ghidra).
- **Dynamic Analysis:** Runtime instrumentation tools for observing execution flow and memory state (e.g., Frida, Objection).
- **Network & Protocol Tooling:** HTTP interception proxy or custom scripting environment (e.g., Python, Burp Suite, OWASP ZAP) for inspecting headers, status codes, and encrypted payloads.

## Getting Started

1. **Download the Release APK:** Obtain the latest signed `.apk` from [GitHub Releases](https://github.com/fiadpratama/CTF-application/releases). Outdated client builds are rejected by the server with `HTTP 426 Upgrade Required`.
2. **Verify Package Integrity:** Confirm the APK signature and file checksum using the steps in [Release Verification](#release-verification).
3. **Conduct the Assessment:** Install the APK on your test environment and analyze the verification workflow from initial handshake to final flag claim.

> **Assessment Mode:** While this repository is open source for architectural reference, production cryptographic keys and authorization secrets are isolated from version control. For an authentic assessment experience, treat the release APK as a black-box target.

<details>
<summary>Building from Source (Optional Local Setup)</summary>

**Prerequisites:** JDK 17, Python 3.9+ (run automatically by Gradle `preBuild` via `generateNativeKeys`), Android SDK (`compileSdk 36`, `buildToolsVersion 36.0.0`, `NDK 26.1.10909125`, `CMake 3.22.1`), and the bundled Gradle Wrapper (`gradlew` / `gradlew.bat`).

To compile the Android client locally using dummy 32-byte placeholder secrets:

```bash
# 1. Create a local native secrets file from the 32-byte dummy template (POSIX / PowerShell)
cp android_client/app/src/main/cpp/native-secrets.properties.example android_client/app/src/main/cpp/native-secrets.properties
# Windows CMD: copy android_client\app\src\main\cpp\native-secrets.properties.example android_client\app\src\main\cpp\native-secrets.properties

# 2. Compile the release APK via Gradle Wrapper
cd android_client
./gradlew assembleRelease      # Linux / macOS
gradlew.bat assembleRelease    # Windows
```

- **Local Signing Behavior:** If `android_client/keystore.properties` is not present, Gradle automatically falls back to standard Android debug signing so local builds succeed out of the box. To sign with a custom keystore, create `android_client/keystore.properties` defining `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.
- **Pairing with a Local Server:** A locally built APK embeds dummy secrets from `native-secrets.properties` and targets `https://server-umber-eta.vercel.app/api/vault` in `VaultApiClient.java`. It will not authenticate against production unless you update `BASE_URL` and run `server/` (`Node.js >= 20.0.0`, e.g., copying `server/.env.example` to `server/.env.local` with matching `E2EE_KEY`, `BACKDOOR_CODE`, `SECRET_MULTIPLIER`, `JWT_SECRET`, and Upstash Redis credentials).
- **Output Path:** `android_client/app/build/outputs/apk/release/app-release.apk`
</details>

## Assessment Phases & Guidance

The challenge consists of three sequential stages that form a single investigative chain. Expand the guidance sections below only if you need structured direction.

### Phase 1: Handshake & Cryptographic Extraction

The client constructs and encrypts an initial handshake payload, which the server rejects under default UI execution. Identify the discrepancy between the default client request and the backend validation contract.

<details>
<summary>Hints for Phase 1</summary>

- **Orientation:** Trace the execution path from the primary UI action handler through the cryptographic helper to the native JNI boundary. Compare the methods exported by the native library against what the Java layer actually invokes.
- **Method:** Do not rely solely on plain strings visible in static binary dumps. Compare static disassembly with runtime return values, and account for runtime checks that may alter output when active tracing is detected.
- **Evidence:** Observe the HTTP status code and error message returned by the initial endpoint. The server distinguishes between a missing payload parameter and an invalid authorization value.
</details>

### Phase 2: Session & Protocol Reconstruction

Passing the initial handshake yields a dynamic challenge and session metadata. Reconstruct the expected protocol request to submit the computed answer and obtain a valid flag.

<details>
<summary>Hints for Phase 2</summary>

- **Orientation:** The Android UI does not provide a button for the second stage. Progression requires constructing and transmitting the next request externally using data returned in the Phase 1 response.
- **Method:** Inspect both the JSON body and the HTTP response headers returned by Phase 1. Decode the provided parameters, determine their mathematical relationship to the session challenge, and encrypt the resulting payload using the standard envelope structure.
- **Evidence:** The second endpoint requires an active session token header alongside the encrypted answer. Submitting a valid computation within the session lifetime returns a unique challenge flag.
</details>

### Phase 3: Flag Verification & Completion

Submit the issued flag to verify completion and trigger the Hall of Fame debriefing sequence.

<details>
<summary>Hints for Phase 3</summary>

- **Orientation:** Flags are generated dynamically per session and validated against server-side state rather than stored as static strings inside the client binary.
- **Method:** Submit the obtained flag through the application's verification input field or directly to the verification endpoint while the flag registration remains active.
- **Evidence:** Ensure all requests include the required client version header. A verified submission unlocks the Hall of Fame debriefing screen.
</details>

## Scope, Privacy, & Abuse Prevention

- **Rules of Engagement:** Testing must remain limited to binary analysis, protocol inspection, and controlled request crafting. Automated brute-force attacks, high-rate fuzzing, or denial-of-service testing against the live backend are strictly prohibited.
- **Data Privacy:** This application does not use user accounts or collect persistent personal profiles, aside from ephemeral IP address processing for rate limiting described below. Do not submit personal credentials or sensitive information in request payloads.
- **Rate Limiting & Telemetry:** Client IP addresses are processed ephemerally in distributed Redis state with automatic TTL expiration strictly to enforce rate limits and mitigate automated abuse. The mobile UI also enforces local request pacing to help avoid accidental server bans during normal use, while completed assessments are recorded solely through an anonymous global counter.

## Release Verification

Before installing the APK, you can independently verify both its **developer signature** (authenticity) and its **file checksum** (integrity):

1. **Developer Certificate Signature (Authenticity):**  
   All official release builds are signed with the same developer keystore (`CN=Fiad Pratama, OU=Software Engineering, O=Fiad Pratama, L=Palu, ST=Central Sulawesi, C=ID`). Verify the certificate SHA-256 digest using Android SDK `apksigner`:
   ```bash
   apksigner verify --print-certs app-release.apk
   ```
   - **Expected Signer Certificate SHA-256:**
     ```text
     11919c358daf9c9f56da77617284fb99cc402fc789242fe8c75c674a3c551a62
     ```

2. **APK File Checksum & 16 KB Alignment (Integrity):**  
   Compute the SHA-256 hash of the downloaded `.apk` file and compare it against the asset checksum listed on the [GitHub Releases](https://github.com/fiadpratama/CTF-application/releases) page (and optionally verify 16 KB page-size alignment for Android 15/16+ devices):
   ```bash
   sha256sum app-release.apk                    # Linux / macOS
   certutil -hashfile app-release.apk SHA256    # Windows
   zipalign -c -P 16 4 app-release.apk          # Optional 16 KB alignment check
   ```

## License & Asset Attribution

- **Source Code:** Released under the [MIT License](LICENSE). The MIT License applies to the project source code and does not cover third-party media assets.
- **Audio Asset:** The Hall of Fame debriefing score (`android_client/app/src/main/res/raw/bgm.ogg`) features an edited excerpt (2:55–4:55, with fades) of *"Signal to Noise"* by **Scott Buckley** ([www.scottbuckley.com.au](https://www.scottbuckley.com.au)), licensed under [Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

For version history and architectural changes, see [CHANGELOG.md](CHANGELOG.md). For contribution guidelines, infrastructure security boundaries, and vulnerability reporting, see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).
