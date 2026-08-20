import os

XOR_KEY = [0x5A, 0x3C, 0x91, 0xE7]

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROPERTIES_FILE = os.path.join(SCRIPT_DIR, "native-secrets.properties")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "generated-keys.h")

def encrypt(plaintext: str) -> list:
    data = plaintext.encode('utf-8')
    return [b ^ XOR_KEY[i % len(XOR_KEY)] ^ ((i * 7) & 0xFF) for i, b in enumerate(data)]

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

def main():
    if not os.path.exists(PROPERTIES_FILE):
        print(f"[ERROR] File tidak ditemukan: {PROPERTIES_FILE}")
        return

    secrets = read_secrets(PROPERTIES_FILE)

    output = "#pragma once\n\n"
    output += to_cpp_array("ENC_E2E_KEY", encrypt(secrets["e2e_key_plaintext"])) + "\n\n"
    output += to_cpp_array("ENC_BACKDOOR", encrypt(secrets["backdoor_plaintext"])) + "\n"

    with open(OUTPUT_FILE, "w") as f:
        f.write(output)

    print(f"[SUCCESS] {os.path.basename(OUTPUT_FILE)} berhasil dibuat di {SCRIPT_DIR}.")

if __name__ == "__main__":
    main()
