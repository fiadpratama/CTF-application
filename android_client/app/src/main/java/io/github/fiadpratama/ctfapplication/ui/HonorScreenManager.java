package io.github.fiadpratama.ctfapplication.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import androidx.core.widget.NestedScrollView;

import io.github.fiadpratama.ctfapplication.R;
import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;

/**
 * Cinematic Hall of Fame Presentation Controller
 *
 * Coordinates the full-screen debriefing sequence, background music playback,
 * smooth auto-scrolling credits, audio fade-out, 15-second skip button reveal,
 * and return transitions.
 */
public class HonorScreenManager {

    /**
     * Callback triggered when the cinematic sequence finishes and default UI is restored.
     */
    public interface OnCinematicFinishedListener {
        void onFinished();
    }

    private static final long SCROLL_START_DELAY_MS = 2000L;
    private static final long SCROLL_DURATION_MS = 82000L;
    private static final long TRANSITION_TOTAL_MS = 87000L;
    private static final long FADE_STANDARD_MS = 5000L;
    private static final long FADE_SKIP_MS = 1000L;
    private static final long SKIP_APPEAR_DELAY_MS = 15000L;

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final Runnable onSkipAction;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private ObjectAnimator scrollAnimator;
    private ValueAnimator audioFadeAnimator;
    private boolean isActive = false;

    private Runnable scrollRunnable;
    private Runnable transitionRunnable;
    private Runnable skipRevealRunnable;

    public HonorScreenManager(Activity activity, ActivityMainBinding binding) {
        this(activity, binding, null);
    }

    public HonorScreenManager(Activity activity, ActivityMainBinding binding, Runnable onSkipAction) {
        this.activity = activity;
        this.binding = binding;
        this.onSkipAction = onSkipAction;
    }

    /**
     * Checks if the cinematic sequence is currently running.
     *
     * @return true if honor screen is visible and active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Launches the full cinematic sequence: cross-fades layouts, starts BGM,
     * triggers auto-scroll, fades out audio, and restores default UI.
     *
     * @param debriefMessage Structured message text received from server
     * @param onFinished Callback invoked when cinematic completes and UI is reset
     */
    public void start(String debriefMessage, final OnCinematicFinishedListener onFinished) {
        if (!isAlive()) return;

        isActive = true;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        applyImmersiveMode();

        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        binding.honorText.setText(debriefMessage);

        binding.honorLayout.setAlpha(0.0f);
        binding.honorLayout.animate().alpha(1.0f).setDuration(FADE_STANDARD_MS).start();

        binding.honorSkipButton.setVisibility(View.GONE);
        binding.honorSkipButton.setAlpha(0.0f);
        binding.honorSkipButton.setEnabled(true);
        binding.honorSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive) return;
                if (onSkipAction != null) {
                    onSkipAction.run();
                }
                binding.honorSkipButton.setEnabled(false);
                cancelTimeline();
                fadeAudioAndEnd(FADE_SKIP_MS, onFinished);
            }
        });

        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                NestedScrollView scroll = binding.honorScroll;
                View content = scroll.getChildAt(0);
                if (content != null) {
                    int maxScroll = content.getHeight() - scroll.getHeight();
                    if (maxScroll > 0) {
                        scrollAnimator = ObjectAnimator.ofInt(scroll, "scrollY", 0, maxScroll);
                        scrollAnimator.setDuration(SCROLL_DURATION_MS);
                        scrollAnimator.setInterpolator(new LinearInterpolator());
                        scrollAnimator.start();
                    }
                }
            }
        };
        handler.postDelayed(scrollRunnable, SCROLL_START_DELAY_MS);

        skipRevealRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlive() || !isActive) return;
                binding.honorSkipButton.setVisibility(View.VISIBLE);
                binding.honorSkipButton.setAlpha(0.0f);
                binding.honorSkipButton.animate().alpha(1.0f).setDuration(600).start();
            }
        };
        handler.postDelayed(skipRevealRunnable, SKIP_APPEAR_DELAY_MS);

        transitionRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                fadeAudioAndEnd(FADE_STANDARD_MS, onFinished);
            }
        };
        handler.postDelayed(transitionRunnable, TRANSITION_TOTAL_MS);

        try {
            mediaPlayer = MediaPlayer.create(activity, R.raw.bgm);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancels active timeline runnables and auto-scrolling animation.
     */
    private void cancelTimeline() {
        if (scrollRunnable != null) {
            handler.removeCallbacks(scrollRunnable);
            scrollRunnable = null;
        }
        if (transitionRunnable != null) {
            handler.removeCallbacks(transitionRunnable);
            transitionRunnable = null;
        }
        if (skipRevealRunnable != null) {
            handler.removeCallbacks(skipRevealRunnable);
            skipRevealRunnable = null;
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    /**
     * Fades audio volume down and smoothly cross-fades back to the main layout.
     *
     * @param fadeDurationMs Duration of fade-out animation in milliseconds
     * @param onFinished Callback invoked when layout restoration completes
     */
    private void fadeAudioAndEnd(long fadeDurationMs, final OnCinematicFinishedListener onFinished) {
        if (mediaPlayer != null) {
            audioFadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
            audioFadeAnimator.setDuration(fadeDurationMs);
            audioFadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (mediaPlayer != null) {
                        float vol = (float) animation.getAnimatedValue();
                        mediaPlayer.setVolume(vol, vol);
                    }
                }
            });
            audioFadeAnimator.start();
        }

        binding.honorSkipButton.animate().alpha(0.0f).setDuration(fadeDurationMs).start();

        binding.honorLayout.animate().alpha(0.0f).setDuration(fadeDurationMs).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                isActive = false;
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                releaseMediaPlayer();

                binding.honorSkipButton.setVisibility(View.GONE);
                binding.honorLayout.setVisibility(View.GONE);
                binding.honorLayout.setAlpha(1.0f);
                binding.mainLayout.setVisibility(View.VISIBLE);
                binding.flagInput.setText("");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                }
                applyImmersiveMode();

                if (onFinished != null) {
                    onFinished.onFinished();
                }
            }
        }).start();
    }

    public void onPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void onResume() {
        if (isActive) {
            applyImmersiveMode();
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        }
    }

    /**
     * Enforces sticky immersive full-screen display hiding system status and navigation bars.
     */
    private void applyImmersiveMode() {
        if (!isAlive()) return;
        activity.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void onDestroy() {
        isActive = false;
        if (activity != null && !activity.isDestroyed()) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
        }
        cancelTimeline();
        if (audioFadeAnimator != null) {
            audioFadeAnimator.cancel();
            audioFadeAnimator = null;
        }
        if (binding != null) {
            binding.honorSkipButton.animate().cancel();
            binding.honorLayout.animate().cancel();
        }
        releaseMediaPlayer();
        handler.removeCallbacksAndMessages(null);
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private boolean isAlive() {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
