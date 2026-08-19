# CTF Application

A hands-on Android reverse engineering and network security challenge covering static analysis, dynamic instrumentation, and protocol interception.

## Project Overview

This CTF simulates a mobile application security assessment scenario, testing skills in static analysis, dynamic instrumentation, and network protocol interception. The application implements a server-side verification architecture where the flag is only released after a full exploit chain is completed correctly.

## Architecture

- **Client (Android):** Java with a native C++ (JNI) component.
- **Security Engine:** Native constants are obfuscated at compile-time to resist casual static string analysis.
- **Backend (Serverless):** Node.js REST API deployed on the Vercel Edge Network.
- **Cryptography:** AES-256-GCM end-to-end encryption for client-server payload transmission.

## Tools & Prerequisites

Recommended tools for this challenge:

- **Decompilation & Static Analysis:** JADX, Apktool, or Ghidra.
- **Dynamic Instrumentation:** Frida or Objection.
- **Traffic Interception:** Burp Suite, OWASP ZAP, or Wireshark.

## Challenge Specifications

The objective is to extract a valid flag from the remote server. The assessment consists of three sequential stages.

### Phase 1: Cryptographic Extraction

The Android client transmits an encrypted payload to the backend. You will need to identify what the client requires to complete this exchange successfully.

*Note: Investigation of the compiled native layer (`libnative-crypto.so`) is required. Not everything you find there is what it appears to be.*

### Phase 2: Protocol Manipulation

Passing Phase 1 unlocks a server-side validation step involving a value you don't yet have.

### Phase 3: Final Verification

Submit your extracted flag through the Android application to complete the assessment.

## Getting Started

1. Download the compiled `.apk` from the repository releases.
2. Install it on an Android emulator or physical test device.
3. Set up your proxy and install interceptor certificates if using Burp Suite/ZAP.
4. Begin your analysis.

---

*Disclaimer: This repository is intended strictly for educational purposes, security research, and ethical hacking practice. Unauthorized automated attacks, including Denial of Service (DoS) or brute-forcing against the deployed backend infrastructure, are strictly prohibited.*
