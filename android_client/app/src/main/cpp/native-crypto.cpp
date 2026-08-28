#include <jni.h>
#include <string>
#include <chrono>
#include <fstream>
#include "generated-keys.h"

// ==========================================
// NATIVE CORE ENGINE (JNI Layer)
// ==========================================

const char* DECOY_KEY      = "AES_KEY_IS_SUPER_SECRET_99182";
const char* DECOY_BACKDOOR = "ADMIN_OVERRIDE_12345";

const unsigned char XOR_KEY[] = { 0x5A, 0x3C, 0x91, 0xE7 };
const size_t XOR_KEY_LEN = sizeof(XOR_KEY);

static unsigned char rotate_right(unsigned char val, int shift) {
    shift = shift % 8;
    return (val >> shift) | (val << (8 - shift));
}

static std::string decrypt(const unsigned char* encrypted, size_t length) {
    std::string result(length, '\0');
    for (size_t i = 0; i < length; i++) {
        int shift = (i % 3) + 1;
        unsigned char unrotated = rotate_right(encrypted[i], shift);
        result[i] = unrotated ^ XOR_KEY[i % XOR_KEY_LEN] ^ (unsigned char)(i * 7);
    }
    return result;
}

static void timing_noise() {
    volatile long dummy = 0;
    long seed = std::chrono::system_clock::now().time_since_epoch().count();
    int iterations = 5000 + static_cast<int>(seed % 3000);
    for (int i = 0; i < iterations; i++) {
        dummy += (i * 7) ^ (i >> 2);
    }
}

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

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getE2EKey(JNIEnv* env, jobject /* this */) {
    timing_noise();
    std::string key = decrypt(ENC_E2E_KEY, sizeof(ENC_E2E_KEY));
    if (detect_hooking()) {
        key[0] = key[0] ^ 0x01;
    }
    return env->NewStringUTF(key.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getBackdoor(JNIEnv* env, jobject /* this */) {
    timing_noise();
    std::string backdoor = decrypt(ENC_BACKDOOR, sizeof(ENC_BACKDOOR));
    if (detect_hooking()) {
        backdoor[0] = backdoor[0] ^ 0x01;
    }
    return env->NewStringUTF(backdoor.c_str());
}
