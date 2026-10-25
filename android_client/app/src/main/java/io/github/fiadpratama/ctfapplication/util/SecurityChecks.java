package io.github.fiadpratama.ctfapplication.util;

import java.io.File;

/**
 * Runtime environment integrity evaluator.
 *
 * Checks device characteristics and filesystem artifacts for virtualization (emulators)
 * or privileged execution (root) environments to support diagnostic telemetry.
 */
public class SecurityChecks {

    private static Boolean cachedCompromised = null;

    /**
     * Inspects build properties and hardware signatures for Android emulator artifacts.
     *
     * @return true if emulator characteristics are detected
     */
    public static boolean isEmulator() {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
            || android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.HARDWARE.contains("goldfish")
            || android.os.Build.HARDWARE.contains("ranchu")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.MANUFACTURER.contains("Genymotion")
            || android.os.Build.PRODUCT.contains("sdk_google")
            || android.os.Build.PRODUCT.contains("google_sdk")
            || android.os.Build.PRODUCT.contains("sdk")
            || android.os.Build.PRODUCT.contains("sdk_x86")
            || android.os.Build.PRODUCT.contains("vbox86p")
            || android.os.Build.PRODUCT.contains("emulator")
            || android.os.Build.PRODUCT.contains("simulator");
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

    /**
     * Generates a concise diagnostic status string for terminal output.
     *
     * @return Formatted status log string
     */
    public static String getEnvironmentSummary() {
        if (isCompromised()) {
            return "> [SYS] Environment: Non-standard runtime detected (Sandbox/Root).";
        }
        return "> [SYS] Environment: Standard hardware runtime verified.";
    }
}
