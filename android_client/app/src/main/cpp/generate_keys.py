import os

XOR_KEY = [0x5A, 0x3C, 0x91, 0xE7]

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROPERTIES_FILE = os.path.join(SCRIPT_DIR, "native-secrets.properties")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "generated-keys.h")

def xor_transform(data: bytes) -> list:
    return [b ^ XOR_KEY[i % len(XOR_KEY)] ^ ((i * 7) & 0xFF) for i, b in enumerate(data)]

def rotate_left(byte_val: int, shift: int) -> int:
    shift = shift % 8
    return ((byte_val << shift) | (byte_val >> (8 - shift))) & 0xFF

def bit_rotate_transform(values: list) -> list:
    result = []
    for i, v in enumerate(values):
        shift = (i % 3) + 1
        result.append(rotate_left(v, shift))
    return result

def encrypt(plaintext: str) -> list:
    data = plaintext.encode('utf-8')
    xored = xor_transform(data)
    return bit_rotate_transform(xored)

def to_cpp_array(name: str, values: list) -> str:
    lines = [f"const unsigned char {name}[] = {{"]
    for i in range(0, len(values), 8):
        chunk = values[i:i+8]
        lines.append("    " + ", ".join(f"0x{v:02x}" for v in chunk) + ",")
    lines.append("};")
    return "\n".join(lines)

def read_secrets(path: str) -> dict:
    secrets = {}
    with open(path) as f:
        for line in f:
            if "=" in line and not line.strip().startswith("#"):
                key, val = line.strip().split("=", 1)
                secrets[key] = val
    return secrets

def rotate_right(byte_val: int, shift: int) -> int:
    shift = shift % 8
    return ((byte_val >> shift) | (byte_val << (8 - shift))) & 0xFF

def sanity_check(plaintext: str, encrypted: list):
    unrotated = [rotate_right(v, (i % 3) + 1) for i, v in enumerate(encrypted)]
    decoded = bytes([unrotated[i] ^ XOR_KEY[i % len(XOR_KEY)] ^ ((i * 7) & 0xFF) for i in range(len(unrotated))])
    return decoded.decode('utf-8') == plaintext

def main():
    if not os.path.exists(PROPERTIES_FILE):
        print(f"[ERROR] Target file not found: {PROPERTIES_FILE}")
        return

    secrets = read_secrets(PROPERTIES_FILE)

    e2e_encrypted = encrypt(secrets["e2e_key_plaintext"])
    backdoor_encrypted = encrypt(secrets["backdoor_plaintext"])

    assert sanity_check(secrets["e2e_key_plaintext"], e2e_encrypted), "Sanity check FAILED for E2E key!"
    assert sanity_check(secrets["backdoor_plaintext"], backdoor_encrypted), "Sanity check FAILED for backdoor!"

    output = "#pragma once\n\n"
    output += to_cpp_array("ENC_E2E_KEY", e2e_encrypted) + "\n\n"
    output += to_cpp_array("ENC_BACKDOOR", backdoor_encrypted) + "\n"

    with open(OUTPUT_FILE, "w") as f:
        f.write(output)

    print(f"[SUCCESS] {os.path.basename(OUTPUT_FILE)} generated successfully in {SCRIPT_DIR}.")
    print("[SUCCESS] Sanity check passed — symmetric encode/decode verified.")

if __name__ == "__main__":
    main()
