# CTF Application

An advanced Android Reverse Engineering and Network Penetration challenge designed to evaluate mobile application security protocols.

## Project Overview

This CTF is a multi-layered cybersecurity assessment framework testing proficiency in static analysis, dynamic instrumentation, and network protocol interception. Unlike conventional client-side CTFs, this application implements a zero-trust server-side verification architecture.

## Architecture

- **Client (Android):** Developed using Java and Native C++ (JNI).
- **Security Engine:** Features compile-time XOR encryption for sensitive constants and memory obfuscation to deter static string analysis.
- **Backend (Serverless):** Node.js REST API deployed on the Vercel Edge Network.
- **Cryptography:** Implements AES-256-GCM End-to-End Encryption (E2EE) for secure payload transmission.

## Tools & Prerequisites

To successfully tackle this challenge, players are recommended to prepare the following tools:

- **Decompilation & Static Analysis:** JADX, Apktool, or Ghidra.
- **Dynamic Instrumentation:** Frida or Objection.
- **Traffic Interception:** Burp Suite, OWASP ZAP, or Wireshark.

## Challenge Specifications

The primary objective is to extract the verified flag string from the remote server. The assessment is divided into three sequential security layers.

### Phase 1: Cryptographic Extraction (Reverse Engineering)

The Android client transmits an encrypted payload to the backend server. The objective is to extract the AES-256-GCM Key and the internal authorization token (Backdoor Code) embedded within the application binary.

*Note: Source code analysis requires investigation of the compiled Native layer (`libnative-crypto.so`). Decoy variables are present.*

### Phase 2: Protocol Manipulation (Traffic Interception)

Upon bypassing the first layer, the server initiates a mathematical validation challenge.

*Note: The required multiplier for the calculation is omitted from the JSON response body. Review the HTTP response headers for hidden parameters.*

### Phase 3: Final Verification

Submit the extracted flag into the Android application interface to validate the completion of the assessment.

## Getting Started

1. Download the compiled `.apk` file from the repository releases.
2. Install the application on an Android emulator or a physical test device.
3. Configure your system proxy and install interceptor certificates (if using Burp Suite/ZAP).
4. Start analyzing the application.

---

*Disclaimer: This repository is intended strictly for educational purposes, security research, and ethical hacking practice. Unauthorized automated attacks, including Denial of Service (DoS) or brute-forcing against the deployed backend infrastructure, are strictly prohibited.*
