package io.github.fiadpratama.ctfapplication.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

/**
 * Window, system bar, display cutout, and soft-keyboard (IME) management helper.
 *
 * Centralizes immersive fullscreen enforcement, lifecycle safety checks, and
 * dynamic WindowInsets padding/margin adjustments across main and debrief layouts.
 */
public final class SystemUiHelper {

    private SystemUiHelper() {
        // Utility class
    }

    /**
     * Evaluates whether the host Activity is active and safe for UI operations.
     *
     * @param activity Target Activity instance
     * @return true if non-null, not finishing, and not destroyed
     */
    public static boolean isActivityAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    /**
     * Enforces sticky immersive full-screen display hiding system status and navigation bars.
     *
     * @param activity Target Activity instance
     */
    public static void applyImmersiveMode(Activity activity) {
        if (!isActivityAlive(activity)) return;
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            activity.getWindow(), activity.getWindow().getDecorView());
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    /**
     * Dismisses the virtual software keyboard if active and restores immersive mode.
     *
     * @param activity Target Activity instance
     */
    public static void hideKeyboard(Activity activity) {
        if (!isActivityAlive(activity)) return;
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
        applyImmersiveMode(activity);
    }

    /**
     * Configures screen wake-lock and display cutout rendering mode for the cinematic debrief.
     *
     * @param activity Target Activity instance
     * @param active true to enable keep-screen-on and short-edges cutout mode; false to restore defaults
     */
    @SuppressWarnings("deprecation")
    public static void setCinematicWindowMode(Activity activity, boolean active) {
        if (activity == null || activity.isDestroyed()) return;
        if (active) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Build.VERSION.SDK_INT < 35) {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Build.VERSION.SDK_INT < 35) {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
        }
    }

    /**
     * Attaches dynamic system bar, display cutout, and IME (soft-keyboard) insets handling
     * to the primary terminal layout.
     *
     * @param activity Host Activity instance
     * @param mainLayout Root terminal container view
     * @param terminalScrollView Scrollable terminal output view
     */
    public static void attachMainLayoutInsets(
            final Activity activity,
            final View mainLayout,
            final NestedScrollView terminalScrollView) {
        if (activity == null || mainLayout == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                float density = activity.getResources().getDisplayMetrics().density;
                int basePadding = (int) (18 * density);
                int topPadding = Math.max(systemBars.top + (int) (24 * density), (int) (52 * density));
                int bottomPadding = Math.max(ime.bottom, systemBars.bottom) + basePadding;
                v.setPadding(basePadding, topPadding, basePadding, bottomPadding);

                if (ime.bottom > 0 && terminalScrollView != null) {
                    terminalScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityAlive(activity)) {
                                terminalScrollView.fullScroll(View.FOCUS_DOWN);
                            }
                        }
                    });
                }
                return insets;
            }
        });
    }

    /**
     * Attaches dynamic display cutout margin clearance to the Hall of Fame skip button.
     *
     * @param activity Host Activity instance
     * @param skipButton Skip button view
     */
    public static void attachSkipButtonInsets(final Activity activity, final View skipButton) {
        if (activity == null || skipButton == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(skipButton, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                float density = activity.getResources().getDisplayMetrics().density;
                int baseMargin = (int) (16 * density);
                int topMargin = Math.max(baseMargin, cutout.top + (int) (8 * density));
                int endMargin = Math.max(baseMargin, cutout.right + baseMargin);
                ViewGroup.LayoutParams params = v.getLayoutParams();
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
                    lp.topMargin = topMargin;
                    lp.setMarginEnd(endMargin);
                    v.setLayoutParams(lp);
                }
                return insets;
            }
        });
    }
}
