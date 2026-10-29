package io.github.fiadpratama.ctfapplication.security;

import android.os.Build;
import java.io.File;

/**
 * Runtime environment integrity evaluator.
 *
 * Checks device characteristics and filesystem artifacts for virtualization (emulators)
 * or privileged execution (root) environments to support diagnostic telemetry.
 */
public final class SecurityChecks {

    private static Boolean cachedCompromised = null;

    private SecurityChecks() {
        // Utility class
    }

    /**
     * Inspects build properties and hardware signatures for Android emulator artifacts.
     *
     * @return true if emulator characteristics are detected
     */
    public static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.PRODUCT.contains("sdk_google")
            || Build.PRODUCT.contains("google_sdk")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("sdk_x86")
            || Build.PRODUCT.contains("vbox86p")
            || Build.PRODUCT.contains("emulator")
            || Build.PRODUCT.contains("simulator");
    }

    /**
     * Checks standard filesystem locations for superuser binaries.
     *
     * @return true if an active root binary is identified
     */
    public static boolean isRooted() {
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    /**
     * Evaluates environment integrity with memoization to minimize filesystem overhead.
     *
     * @return true if running under an emulator or rooted environment
     */
    public static synchronized boolean isCompromised() {
        if (cachedCompromised == null) {
            cachedCompromised = isEmulator() || isRooted();
        }
        return cachedCompromised;
    }
}
