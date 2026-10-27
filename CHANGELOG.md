# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-10-25

Major architectural overhaul across both client and serverless backend, introducing distributed state management via Redis, atomic Lua scripting, stateless JWT session tokens, modernized Android build tooling, immersive UI debrief experience, and hardened native binary defenses.

### Added
- **Distributed State Architecture:** Migrated backend persistence to Upstash Redis, enabling stateless multi-instance scalability across Vercel serverless functions.
- **Atomic Lua Scripts:**
  - `RATE_LIMIT_LUA`: Dual fixed-window rate limiting (30 requests/60s sustained quota and 15 requests/10s burst detection) with progressive IP ban escalation (30 minutes $\rightarrow$ 1 hour $\rightarrow$ 3 hours) and 24-hour sliding violation retention.
  - `ISSUE_FLAG_LUA`: Idempotent Stage 2 flag generation and deterministic session binding.
  - `CLAIM_FLAG_LUA`: Atomic flag redemption enforcing a single solve-counter increment (`unused -> claimed:<solveNum>`) with a 300-second idempotent retry grace window to prevent race conditions and duplicate solve counts.
- **Stateless JWT Sessions:** Implemented HS256 signed session tokens carrying challenge parameters, version metadata, and single-use `jti` nonces.
- **Idempotent Flag Claim Grace Window:** Added a 5-minute (300-second) grace window on claimed flags, allowing participants to retry Stage 3 verification after transient network timeouts and receive their assigned solve number without re-incrementing the global solve counter.
- **Hall of Fame v2 & Cinematic Debrief:**
  - **Delayed Skip Button & Audio Feedback:** Added a skip action button (revealed after 15 seconds) on the victory debrief sequence paired with tactile terminal audio feedback (`ToneGenerator.TONE_PROP_BEEP, 50`), transition state locking (`isTransitioning`), and smooth 1-second audio/visual fade-out.
  - **Dual Gradient Overlays:** Added top (`bg_honor_fade_top`, 115dp with a 20% solid stop) and bottom (`bg_honor_fade_bottom`, 24dp) gradient fades layered above the scrolling debrief view and beneath interactive controls.
  - **Immersive Edge-to-Edge Experience:** Dynamically applies `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` and sticky immersive mode during debrief playback to hide system and navigation bars, with safe restoration upon exit.
  - **Lifecycle-Aware Timeline Synchronization:** Synchronized background music (`MediaPlayer`), scrolling credits (`ObjectAnimator`), and delayed transition timers across `onPause()` and `onResume()` using elapsed monotonic time (`SystemClock.elapsedRealtime()`).
  - **Session Persistence:** Added an anonymous global solve counter, retry cooldown display, and `FLAG_KEEP_SCREEN_ON` to prevent device sleep during playback.
- **Dynamic Display Cutout & IME WindowInsets:** Implemented Jetpack `ViewCompat.setOnApplyWindowInsetsListener` on `mainLayout` (combining `systemBars()`, `displayCutout()`, and `ime()` insets for camera clearance and automatic soft-keyboard layout resizing) and on `honorSkipButton` (dynamic `displayCutout()` margin clearance).
- **In-App Client Rate Limiting:** Added proactive client-side request throttling (`AppRateLimiter`: 10 requests/60s threshold with a 5-minute cooldown) to prevent rapid submission spamming.
- **Automated Native Key Generation:** Integrated `generateNativeKeys` Gradle task directly into the `preBuild` lifecycle to generate native C++ headers deterministically from local secrets.
- **Modernized Android Tooling:** Bundled Gradle Wrapper 8.5, updated Android Gradle Plugin to 8.2.2, targeted Android 14 (API 34), and standardized Java 17 toolchain.
- **Fail-Open Rate Limit Resilience:** Added graceful fail-open fallback in rate limiting to maintain Stage 1 availability during transient Redis connectivity faults (while Stage 2 flag issuance and Stage 3 redemption remain strictly fail-closed on Redis state).

### Changed
- **Client Architecture Modularization:** Decoupled `MainActivity` into domain-focused packages (`crypto`, `network`, `security`, `ui`), extracting protocol orchestration into `VaultProtocolController`, Hall of Fame debrief lifecycle into `HonorScreenManager`, window/cutout/IME management into `SystemUiHelper`, tactile audio into `SoundEffectsManager`, debounced alerts into `ToastHelper`, and client throttling into `AppRateLimiter`.
- **Codebase Documentation & Comment Standardization:** Refactored and standardized Javadoc, JSDoc, and C++ comments across both Android client (`android_client/`) and Node.js server (`server/`) modules to adhere to clean industry standards—focusing on architectural context, protocol security, and lifecycle constraints without noise or challenge spoilers.
- **Server Modular Architecture & Route Decomposition:** Refactored monolithic `server.js` into clean architectural layers under `server/src/` (`app.js`, `routes/`, `middleware/`, `services/`, `config.js`), modularized verification endpoints into dedicated per-stage routes (`stage1.js`, `stage2.js`, `verifyFlag.js`), centralized cryptographic/JWT operations in `services/crypto.js`, and isolated Redis Lua scripts inside `vaultState.js`.
- **Hall of Fame Layout & Spacing:** Standardized inter-section ASCII text spacing in `hallOfFame.js` with uniform double line breaks (`\n\n`), scaled title typography to 36sp, and enhanced section margins.
- **UI Design System Standardization:** Extracted launcher icon colors into shared semantic color tokens (`@color/ic_launcher_background`, `@color/ic_launcher_accent`), categorized color palettes in `colors.xml`, renamed the Stage 1 action button to "DECRYPT PAYLOAD", and refined challenge subtitle styling.
- **Terminal UI Thread Safety:** Refactored `TerminalLogger` to a thread-safe FIFO queue (`Queue<LogEntry>`) to prevent UI callback collisions and Activity context leaks.
- **Terminal Output Polish:** Standardized terminal diagnostic output formatting and extracted all user-facing log messages into `strings.xml` resources.
- **Network Safety & Exception Handling:** Enforced strict `try-finally` stream closing and timeout handling across all `VaultApiClient` network calls.
- **Unified Client Error Display:** Standardized HTTP error extraction to display server-provided error messages directly in the terminal interface.
- **Stage 1 Verification Semantics:** Separated parameter presence checks (HTTP 400 *Missing backdoor_code parameter*) from token validation (HTTP 401 *Invalid backdoor code*) for fair protocol deduction.
- **Native Symbol Table Hardening:** Refined native symbol identifiers in `libnative-crypto.so` and added active runtime cross-references to prevent aggressive linker dead-code stripping while preserving realistic `.rodata` layout.
- **Build Script Automation:** Hardened release build scripts with automated environment detection (JDK, Android SDK, Python) and validation checks.
- **Consolidated Git Configuration:** Unified repository ignore rules into a single root `.gitignore`.
- **Documentation & Bilingual Support:** Added bilingual (English/Indonesian) README, refined threat modeling in `SECURITY.md`, documented audio asset attribution, and synchronized architecture specifications.

### Fixed
- **Soft-Keyboard Layout Resize & Terminal Auto-Scroll:** Restored automatic upward shifting of the flag input and submit controls above the virtual keyboard by integrating `WindowInsetsCompat.Type.ime()` into `mainLayout`'s inset listener while preserving edge-to-edge rendering on `honorLayout`.
- **Hall of Fame Playback & Scroll State Reset:** Resolved brief visual flicker and credit continuation glitch on repeated Hall of Fame playback by introducing `resetHonorViews()` in `HonorScreenManager` to synchronously cancel active animators, reset scroll coordinates (`scrollTo(0, 0)`), and enforce zero alpha before display.
- **Initial Layout Inset Clearance:** Adjusted `activity_main.xml` base layout padding (`46dp`) ensuring balanced display clearance and camera cutout aesthetics across varied aspect ratios.

### Security
- Enforced single-use JWT session consumption via Redis atomic state binding.
- Added strict JSON request body schema validation and JWT claim verification (`iat`, `exp`, `jti`, `challenge`).
- Scoped rate limiting and version gate middlewares specifically to `/api/vault` endpoints prior to JSON body parsing.
- Added startup environment validation ensuring required secrets (`UPSTASH_REDIS_REST_URL`, `JWT_SECRET`, etc.) are configured before server boot.
- Upgraded `qs` dependency to resolve security vulnerability.
- Removed unused Android system permissions from `AndroidManifest.xml`.
- Rotated developer signing keystore and native cryptographic material.

### Deprecated
- **Releases v1.0.0 – v1.6.1 (`versionCode` 1 to 7):** Deprecated at the server level. Minimum supported version raised to `MIN_VERSION_CODE = 8`; all prior builds receive `HTTP 426 Upgrade Required`.

---

## [1.6.1] - 2026-08-29 [DEPRECATED]

Comprehensive security and code-quality audit cleanup ahead of the v2.0.0 backend migration.

### Security
- **Global Rate Limiting:** Relocated rate limiting middleware ahead of version gating, ensuring rejected version requests remain subject to IP rate limits.

### Changed
- **Terminal Output Sanitization:** Removed technical implementation details (library names, algorithm specifics) from terminal output to eliminate unintended reverse engineering spoilers.
- **Cosmetic Telemetry:** Reactivated environment detection code as informational terminal log lines without affecting cryptography or network flow.
- **Typing Animation Delay:** Accelerated terminal typewriter delay for improved responsiveness.
- **Documentation:** Updated README architecture section and synchronized incident history in `SECURITY.md`.
- **Version Bump:** Internal `versionCode` updated to 7.

---

## [1.6.0] - 2026-08-28 [DEPRECATED]

Added three defensive layers to the native cryptographic engine to raise the barrier against static and dynamic reverse engineering.

### Added
- **Timing Obfuscation:** Added variable-duration dummy arithmetic computation (`timing_noise`) prior to key derivation to disrupt timing-based side-channel analysis.
- **Secondary Encoding Layer:** Added position-dependent bit-rotation transform (`ROL`/`ROR`) on top of XOR masking in native C++.
- **Passive Anti-Tracing:** Added passive inspection of `/proc/self/status` (`TracerPid != 0`) to detect active debugging/tracing; silently corrupts key output on detection instead of terminating.
- **Key Generation Verification:** Added automated round-trip symmetry verification in the build key generator to prevent silent key corruption.

### Changed
- **Build Scripts:** Translated build and key generation scripts to English; removed deprecated build script.
- **Version Bump:** Internal `versionCode` updated to 6.

---

## [1.5.0] - 2026-08-23 [DEPRECATED]

Introduced custom application identity assets, minimum client version enforcement, and server-side hardening.

### Added
- **Application Identity:** Added custom adaptive launcher icon and splash screen icon assets.
- **Version Gating:** Client transmits `X-App-Version-Code` header; backend rejects outdated clients below minimum supported version (`HTTP 426 Upgrade Required`).
- **Security Policy:** Added `SECURITY.md` outlining secret isolation model and responsible vulnerability disclosure process.

### Changed
- **Minimum SDK:** Raised minimum supported Android version from API 24 to API 26 (`minSdk 26`).
- **Server Hardening:** Enforced structured JSON error responses, 10kb request payload size limit, malformed JSON middleware, and disabled `X-Powered-By` header.
- **Version Bump:** Internal `versionCode` updated to 5.

---

## [1.4.0] - 2026-08-21 [DEPRECATED]

Secret isolation from version control and release signing key rotation.

### Changed
- **Secret Isolation:** Extracted native encryption keys and backdoor authorization token from git; implemented local build-time generation.
- **Server Configuration:** Removed hardcoded fallback values for encryption key and backdoor token; server requires environment variables and refuses to start without them.
- **Key Rotation:** Rotated AES-256-GCM encryption key and backdoor authorization code.
- **Signing Key Rotation:** Rotated application signing keystore following exposure in earlier commit history.
- **Version Bump:** Internal `versionCode` updated to 4.

---

## [1.3.0] - 2026-08-19 [DEPRECATED]

Modularized client architecture, upgraded UI/UX responsiveness, and synchronized terminal event handling.

### Added
- **Splash Screen:** Integrated Android Splash Screen API (`SplashScreen.installSplashScreen()`).
- **Modular Package Structure:** Refactored Android client into distinct packages (`crypto`, `network`, `ui`, `util`).
- **Terminal Event Synchronization:** Implemented callback listeners to synchronize audio feedback and UI state transitions with terminal animations.

### Changed
- **UI Responsiveness:** Transitioned to dynamic XML layouts utilizing `layout_weight` and `fitsSystemWindows` for soft-keyboard handling.
- **State Management:** Added universal interaction locks and automatic soft-keyboard dismissal during network requests.
- **Error Codes:** Standardized UI error messages to concise technical indicators (`[!]`, `[504]`, `[500]`).
- **Version Bump:** Internal `versionCode` updated to 3.

---

## [1.2.0] - 2026-08-18 [DEPRECATED]

Updated core Android application identity and native JNI linkage.

### Changed
- **Package Identity:** Renamed Android package from `com.ctf.vault` to `io.github.fiadpratama.ctfapplication`.
- **JNI Synchronization:** Updated native C++ method signatures to match the new package namespace.
- **Network Security:** Disabled `android:usesCleartextTraffic` in `AndroidManifest.xml` to enforce HTTPS.
- **Code Cleanup:** Refactored `MainActivity` and extracted hardcoded strings into `strings.xml`.
- **Version Bump:** Internal `versionCode` updated to 3.

---

## [1.1.0] - 2026-08-16 [DEPRECATED]

Security hardening of flag lifecycle, native binary stripping, and infrastructure upgrades.

### Added
- **Single-Use Flags:** Flags are dynamically generated per-solve and permanently burned after one use.
- **License:** Added MIT License.

### Changed
- **Infrastructure Update:** Rebranded project to "CTF Application Core Infrastructure".
- **Binary Stripping:** Stripped debug symbols (`-s`) from native C++ release binary.
- **Secret Management:** Keystore credentials removed from version control into local configuration.
- **Key Rotation:** AES encryption keys and backdoor codes updated.
- **Dependency Update:** Bumped `body-parser` from 1.20.5 to 1.20.6.
- **Build Ignore:** Fixed `.gitignore` pattern for Android `build/` directories.
- **Version Bump:** Internal `versionCode` updated to 2.

---

## [1.0.0] - 2026-08-13 [DEPRECATED]

Initial release of the CTF Android application and serverless backend infrastructure.

### Added
- **Mobile CTF Client:** Android application featuring interactive hacker terminal interface and challenge stages.
- **Serverless Backend:** Express.js verification endpoints deployed on Vercel (`verify-stage-1`, `verify-stage-2`, `verify-stage-3`).
- **Hall of Fame:** Terminal ASCII victory sequence and solve confirmation.
- **Initial Build:** First downloadable release package (`The_Vault_CTF.apk`, `versionCode 1`).
