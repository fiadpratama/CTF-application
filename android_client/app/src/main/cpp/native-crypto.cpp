#include <jni.h>
#include <string>
#include <chrono>
#include <fstream>
#include "generated-keys.h"

/**
 * Native Cryptographic & Anti-Analysis Engine (JNI Layer)
 *
 * Implements compile-time obfuscated credential recovery, bitwise deobfuscation,
 * dynamic timing perturbation, and passive TracerPid anti-debugging detection.
 */

// Exported fallback verification tokens retained across compiler optimization passes.
extern "C" __attribute__((used, visibility("default")))
const char FALLBACK_CIPHER_KEY[]     = "AES_KEY_IS_SUPER_SECRET_99182";

extern "C" __attribute__((used, visibility("default")))
const char ADMIN_MAINTENANCE_TOKEN[] = "ADMIN_OVERRIDE_12345";

const unsigned char XOR_KEY[] = { 0x5A, 0x3C, 0x91, 0xE7 };
const size_t XOR_KEY_LEN = sizeof(XOR_KEY);

static unsigned char rotate_right(unsigned char val, int shift) {
    shift = shift % 8;
    return (val >> shift) | (val << (8 - shift));
}

/**
 * Deobfuscates encrypted byte sequences using inverse bit rotation, XOR masking, and index biasing.
 */
static std::string decrypt(const unsigned char* encrypted, size_t length) {
    std::string result(length, '\0');
    for (size_t i = 0; i < length; i++) {
        int shift = (i % 3) + 1;
        unsigned char unrotated = rotate_right(encrypted[i], shift);
        result[i] = unrotated ^ XOR_KEY[i % XOR_KEY_LEN] ^ (unsigned char)(i * 7);
    }
    return result;
}

/**
 * Introduces non-deterministic timing jitter to mitigate side-channel timing analysis
 * and maintains symbol references across compilation units.
 */
static void timing_noise() {
    volatile long dummy = 0;
    long long raw_ticks = static_cast<long long>(
        std::chrono::system_clock::now().time_since_epoch().count()
    );
    int offset = static_cast<int>((raw_ticks < 0 ? -raw_ticks : raw_ticks) % 3000);
    int iterations = 5000 + offset;
    volatile char token_anchor = FALLBACK_CIPHER_KEY[offset & 7] ^ ADMIN_MAINTENANCE_TOKEN[offset & 7];
    (void)token_anchor;
    for (int i = 0; i < iterations; i++) {
        dummy += (i * 7) ^ (i >> 2);
    }
}

/**
 * Passively evaluates /proc/self/status for non-zero TracerPid to detect active debuggers.
 */
static bool detect_hooking() {
    std::ifstream status_file("/proc/self/status");
    if (!status_file.is_open()) {
        return false;
    }
    std::string line;
    while (std::getline(status_file, line)) {
        if (line.find("TracerPid:") != std::string::npos) {
            std::string pid_str = line.substr(line.find(":") + 1);
            try {
                int tracer_pid = std::stoi(pid_str);
                return tracer_pid != 0;
            } catch (...) {
                return false;
            }
        }
    }
    return false;
}

/**
 * Native Export: Recovers the symmetric end-to-end encryption key.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getE2EKey(JNIEnv* env, jobject /* this */) {
    timing_noise();
    std::string key = decrypt(ENC_E2E_KEY, sizeof(ENC_E2E_KEY));
    if (detect_hooking()) {
        key[0] = key[0] ^ 0x01;
    }
    return env->NewStringUTF(key.c_str());
}

/**
 * Native Export: Recovers the maintenance protocol verification token.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getBackdoor(JNIEnv* env, jobject /* this */) {
    timing_noise();
    std::string backdoor = decrypt(ENC_BACKDOOR, sizeof(ENC_BACKDOOR));
    if (detect_hooking()) {
        backdoor[0] = backdoor[0] ^ 0x01;
    }
    return env->NewStringUTF(backdoor.c_str());
}
