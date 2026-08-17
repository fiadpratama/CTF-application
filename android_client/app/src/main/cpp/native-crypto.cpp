#include <jni.h>
#include <string>

// ==========================================
// NATIVE CORE ENGINE (JNI Layer)
// ==========================================

const char* DECOY_KEY      = "AES_KEY_IS_SUPER_SECRET_99182";
const char* DECOY_BACKDOOR = "ADMIN_OVERRIDE_12345";

const unsigned char XOR_KEY[] = { 0x5A, 0x3C, 0x91, 0xE7 };
const size_t XOR_KEY_LEN = sizeof(XOR_KEY);

const unsigned char ENC_E2E_KEY[] = {
    0x6c, 0x0f, 0xa8, 0xc5, 0x74, 0x29, 0x8f, 0xef,
    0x07, 0x64, 0xbe, 0xcb, 0x7d, 0x03, 0x9b, 0xe4,
    0x59, 0x6f, 0x86, 0x48, 0xaf, 0xca, 0x78, 0x7e,
    0xc1, 0xa4, 0x0d, 0x3e, 0xf6, 0x9c, 0x29, 0x5f
};

const unsigned char ENC_BACKDOOR[] = {
    0x0e, 0x0a, 0xcd, 0xc6, 0x0b, 0x2e, 0xe8, 0x83,
    0x3d, 0x53, 0x85, 0x9a, 0x5a, 0x57, 0xb0, 0xbe,
    0x66, 0x14, 0xcb
};

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
