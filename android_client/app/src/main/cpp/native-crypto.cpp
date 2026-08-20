#include <jni.h>
#include <string>
#include "generated_keys.h"

// ==========================================
// NATIVE CORE ENGINE (JNI Layer)
// ==========================================

const char* DECOY_KEY      = "AES_KEY_IS_SUPER_SECRET_99182";
const char* DECOY_BACKDOOR = "ADMIN_OVERRIDE_12345";

const unsigned char XOR_KEY[] = { 0x5A, 0x3C, 0x91, 0xE7 };
const size_t XOR_KEY_LEN = sizeof(XOR_KEY);

static std::string decrypt(const unsigned char* encrypted, size_t length) {
    std::string result(length, '\0');
    for (size_t i = 0; i < length; i++) {
        result[i] = encrypted[i] ^ XOR_KEY[i % XOR_KEY_LEN] ^ (unsigned char)(i * 7);
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getE2EKey(JNIEnv* env, jobject /* this */) {
    std::string key = decrypt(ENC_E2E_KEY, sizeof(ENC_E2E_KEY));
    return env->NewStringUTF(key.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_fiadpratama_ctfapplication_MainActivity_getBackdoor(JNIEnv* env, jobject /* this */) {
    std::string backdoor = decrypt(ENC_BACKDOOR, sizeof(ENC_BACKDOOR));
    return env->NewStringUTF(backdoor.c_str());
}
