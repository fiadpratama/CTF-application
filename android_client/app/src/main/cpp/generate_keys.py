"""Generate generated-keys.h from native-secrets.properties.

Executed automatically during the Gradle pre-build lifecycle (or standalone).
The properties file is git-ignored and must never be committed; see native-secrets.properties.example.
"""

import os
import sys

XOR_KEY = [0x5A, 0x3C, 0x91, 0xE7]
E2E_KEY_BYTES = 32  # AES-256 requires a 32-byte key

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROPERTIES_FILE = os.path.join(SCRIPT_DIR, "native-secrets.properties")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "generated-keys.h")


def fail(message: str) -> None:
    print(f"[ERROR] {message}")
    sys.exit(1)


def xor_transform(data: bytes) -> list[int]:
    return [b ^ XOR_KEY[i % len(XOR_KEY)] ^ ((i * 7) & 0xFF) for i, b in enumerate(data)]


def rotate_left(byte_val: int, shift: int) -> int:
    shift = shift % 8
    return ((byte_val << shift) | (byte_val >> (8 - shift))) & 0xFF


def rotate_right(byte_val: int, shift: int) -> int:
    shift = shift % 8
    return ((byte_val >> shift) | (byte_val << (8 - shift))) & 0xFF


def bit_rotate_transform(values: list[int]) -> list[int]:
    return [rotate_left(v, (i % 3) + 1) for i, v in enumerate(values)]


def encrypt(plaintext: str) -> list[int]:
    return bit_rotate_transform(xor_transform(plaintext.encode("utf-8")))


def sanity_check(plaintext: str, encrypted: list[int]) -> bool:
    unrotated = [rotate_right(v, (i % 3) + 1) for i, v in enumerate(encrypted)]
    decoded = bytes(unrotated[i] ^ XOR_KEY[i % len(XOR_KEY)] ^ ((i * 7) & 0xFF) for i in range(len(unrotated)))
    return decoded.decode("utf-8") == plaintext


def to_cpp_array(name: str, values: list[int]) -> str:
    lines = [f"const unsigned char {name}[] = {{"]
    for i in range(0, len(values), 8):
        chunk = values[i:i + 8]
        lines.append("    " + ", ".join(f"0x{v:02x}" for v in chunk) + ",")
    lines.append("};")
    return "\n".join(lines)


def read_secrets(path: str) -> dict[str, str]:
    secrets: dict[str, str] = {}
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, val = line.split("=", 1)
            secrets[key.strip()] = val.strip()
    return secrets


def require(secrets: dict[str, str], name: str) -> str:
    value = secrets.get(name, "")
    if not value:
        fail(f"Missing or empty property '{name}' in {os.path.basename(PROPERTIES_FILE)}.")
    return value


def main() -> None:
    if not os.path.exists(PROPERTIES_FILE):
        fail(
            f"Configuration file not found: {PROPERTIES_FILE}\n"
            "Copy native-secrets.properties.example to native-secrets.properties and fill in your values."
        )

    secrets = read_secrets(PROPERTIES_FILE)
    e2e_key = require(secrets, "e2e_key_plaintext")
    backdoor = require(secrets, "backdoor_plaintext")

    key_length = len(e2e_key.encode("utf-8"))
    if key_length != E2E_KEY_BYTES:
        fail(f"e2e_key_plaintext must be exactly {E2E_KEY_BYTES} bytes, got {key_length}.")

    e2e_encrypted = encrypt(e2e_key)
    backdoor_encrypted = encrypt(backdoor)

    if not sanity_check(e2e_key, e2e_encrypted):
        fail("Sanity check failed for the E2E key.")
    if not sanity_check(backdoor, backdoor_encrypted):
        fail("Sanity check failed for the backdoor value.")

    output = "#pragma once\n\n"
    output += to_cpp_array("ENC_E2E_KEY", e2e_encrypted) + "\n\n"
    output += to_cpp_array("ENC_BACKDOOR", backdoor_encrypted) + "\n"

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write(output)

    print(f"[SUCCESS] {os.path.basename(OUTPUT_FILE)} generated successfully in {SCRIPT_DIR}.")
    print("[SUCCESS] Sanity check passed - symmetric encode/decode verified.")


if __name__ == "__main__":
    main()
