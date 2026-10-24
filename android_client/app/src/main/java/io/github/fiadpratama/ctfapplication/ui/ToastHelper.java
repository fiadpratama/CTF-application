package io.github.fiadpratama.ctfapplication.ui;

import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;
import java.util.Locale;

/**
 * UI Feedback & Notification Helper
 *
 * Manages toast notifications with debounce throttling and active-instance cancellation
 * to prevent OS notification queue build-up and screen spamming.
 */
public class ToastHelper {

    private static final long THROTTLE_INTERVAL_MS = 1500L;

    private Toast activeToast;
    private long lastToastTimestamp = 0;

    /**
     * Displays a short Toast notification with rate-limiting protection.
     * If called consecutively within the throttle window, the call is ignored to avoid UI spam.
     *
     * @param context Application or Activity context
     * @param message Text to display
     * @return true if the toast was displayed; false if throttled
     */
    public boolean showThrottled(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastToastTimestamp < THROTTLE_INTERVAL_MS) {
            return false;
        }

        if (activeToast != null) {
            activeToast.cancel();
        }

        lastToastTimestamp = now;
        activeToast = Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT);
        activeToast.show();
        return true;
    }

    /**
     * Formats seconds into human-readable duration strings (e.g. "5 min", "4 min 35 s", "45 s").
     *
     * @param seconds Total seconds
     * @return Formatted localized string
     */
    public static String formatDuration(long seconds) {
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            if (secs == 0) {
                return String.format(Locale.getDefault(), "%d min", mins);
            }
            return String.format(Locale.getDefault(), "%d min %02d s", mins, secs);
        }
        return String.format(Locale.getDefault(), "%d s", Math.max(0, seconds));
    }

    /**
     * Cancels any active toast and releases references to prevent leaks.
     */
    public void dismiss() {
        if (activeToast != null) {
            activeToast.cancel();
            activeToast = null;
        }
    }
}
