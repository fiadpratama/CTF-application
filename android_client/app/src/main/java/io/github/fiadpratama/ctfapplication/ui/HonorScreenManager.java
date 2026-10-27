package io.github.fiadpratama.ctfapplication.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import io.github.fiadpratama.ctfapplication.R;
import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;

/**
 * Cinematic Hall of Fame Presentation Controller
 *
 * Coordinates the full-screen debriefing sequence, background music playback,
 * smooth auto-scrolling credits, audio fade-out, 15-second skip button reveal,
 * lifecycle-aware pause/resume synchronization, and return transitions.
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
    private boolean isTransitioning = false;
    private boolean isPaused = false;

    private long lastResumeRealtimeMs = 0L;
    private long remainingScrollDelayMs = 0L;
    private long remainingSkipDelayMs = 0L;
    private long remainingTransitionMs = 0L;

    private Runnable scrollRunnable;
    private Runnable transitionRunnable;
    private Runnable skipRevealRunnable;

    public HonorScreenManager(Activity activity, ActivityMainBinding binding) {
        this(activity, binding, null);
    }

    public HonorScreenManager(final Activity activity, ActivityMainBinding binding, Runnable onSkipAction) {
        this.activity = activity;
        this.binding = binding;
        this.onSkipAction = onSkipAction;

        if (this.binding != null && this.activity != null) {
            SystemUiHelper.attachSkipButtonInsets(this.activity, this.binding.honorSkipButton);
        }
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
     * Resets the visual, textual, and scroll state of the honor screen components
     * to a clean baseline to prevent flickering or resuming previous playback positions.
     */
    private void resetHonorViews() {
        if (binding == null) return;
        binding.honorLayout.animate().cancel();
        binding.honorSkipButton.animate().cancel();
        binding.honorLayout.setAlpha(0.0f);
        binding.honorSkipButton.setAlpha(0.0f);
        binding.honorSkipButton.setVisibility(View.GONE);
        binding.honorSkipButton.setEnabled(false);
        binding.honorText.setText("");
        binding.honorScroll.stopNestedScroll();
        binding.honorScroll.scrollTo(0, 0);
        binding.honorScroll.setScrollY(0);
        binding.honorScroll.post(new Runnable() {
            @Override
            public void run() {
                if (SystemUiHelper.isActivityAlive(activity) && binding != null) {
                    binding.honorScroll.scrollTo(0, 0);
                    binding.honorScroll.setScrollY(0);
                }
            }
        });
    }

    /**
     * Launches the full cinematic sequence: cross-fades layouts, starts BGM,
     * triggers auto-scroll, fades out audio, and restores default UI.
     *
     * @param debriefMessage Structured message text received from server
     * @param onFinished Callback invoked when cinematic completes and UI is reset
     */
    public void start(String debriefMessage, final OnCinematicFinishedListener onFinished) {
        if (!SystemUiHelper.isActivityAlive(activity)) return;

        cancelTimeline();
        if (audioFadeAnimator != null) {
            audioFadeAnimator.cancel();
            audioFadeAnimator = null;
        }
        releaseMediaPlayer();

        isActive = true;
        isTransitioning = false;
        isPaused = false;
        lastResumeRealtimeMs = SystemClock.elapsedRealtime();
        remainingScrollDelayMs = SCROLL_START_DELAY_MS;
        remainingSkipDelayMs = SKIP_APPEAR_DELAY_MS;
        remainingTransitionMs = TRANSITION_TOTAL_MS;

        SystemUiHelper.setCinematicWindowMode(activity, true);
        SystemUiHelper.applyImmersiveMode(activity);

        binding.honorLayout.animate().cancel();
        binding.honorSkipButton.animate().cancel();
        binding.honorLayout.setAlpha(0.0f);
        binding.honorSkipButton.setVisibility(View.GONE);
        binding.honorSkipButton.setAlpha(0.0f);
        binding.honorSkipButton.setEnabled(true);
        binding.honorText.setText(debriefMessage);

        binding.honorScroll.stopNestedScroll();
        binding.honorScroll.scrollTo(0, 0);
        binding.honorScroll.setScrollY(0);
        binding.honorScroll.post(new Runnable() {
            @Override
            public void run() {
                if (SystemUiHelper.isActivityAlive(activity) && binding != null) {
                    binding.honorScroll.scrollTo(0, 0);
                    binding.honorScroll.setScrollY(0);
                }
            }
        });

        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        ViewCompat.requestApplyInsets(binding.honorSkipButton);

        binding.honorLayout.animate().alpha(1.0f).setDuration(FADE_STANDARD_MS).start();

        binding.honorSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive || isTransitioning) return;
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
                scrollRunnable = null;
                remainingScrollDelayMs = 0L;
                if (!SystemUiHelper.isActivityAlive(activity) || !isActive || isTransitioning) return;
                NestedScrollView scroll = binding.honorScroll;
                View content = scroll.getChildAt(0);
                if (content != null) {
                    int maxScroll = content.getHeight() - scroll.getHeight();
                    if (maxScroll > 0) {
                        scrollAnimator = ObjectAnimator.ofInt(scroll, "scrollY", 0, maxScroll);
                        scrollAnimator.setDuration(SCROLL_DURATION_MS);
                        scrollAnimator.setInterpolator(new LinearInterpolator());
                        scrollAnimator.start();
                        if (isPaused) {
                            scrollAnimator.pause();
                        }
                    }
                }
            }
        };
        handler.postDelayed(scrollRunnable, remainingScrollDelayMs);

        skipRevealRunnable = new Runnable() {
            @Override
            public void run() {
                skipRevealRunnable = null;
                remainingSkipDelayMs = 0L;
                if (!SystemUiHelper.isActivityAlive(activity) || !isActive || isTransitioning) return;
                binding.honorSkipButton.setVisibility(View.VISIBLE);
                binding.honorSkipButton.setAlpha(0.0f);
                binding.honorSkipButton.animate().alpha(1.0f).setDuration(600).start();
            }
        };
        handler.postDelayed(skipRevealRunnable, remainingSkipDelayMs);

        transitionRunnable = new Runnable() {
            @Override
            public void run() {
                transitionRunnable = null;
                remainingTransitionMs = 0L;
                if (!SystemUiHelper.isActivityAlive(activity) || !isActive || isTransitioning) return;
                cancelTimeline();
                fadeAudioAndEnd(FADE_STANDARD_MS, onFinished);
            }
        };
        handler.postDelayed(transitionRunnable, remainingTransitionMs);

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
        remainingScrollDelayMs = 0L;
        remainingSkipDelayMs = 0L;
        remainingTransitionMs = 0L;
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
        if (isTransitioning) return;
        isTransitioning = true;
        binding.honorSkipButton.setEnabled(false);

        cancelTimeline();
        if (audioFadeAnimator != null) {
            audioFadeAnimator.cancel();
            audioFadeAnimator = null;
        }

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
                if (!SystemUiHelper.isActivityAlive(activity)) return;
                isActive = false;
                isTransitioning = false;
                isPaused = false;
                SystemUiHelper.setCinematicWindowMode(activity, false);
                releaseMediaPlayer();

                resetHonorViews();
                binding.honorLayout.setVisibility(View.GONE);

                binding.mainLayout.setVisibility(View.VISIBLE);
                binding.flagInput.setText("");
                SystemUiHelper.applyImmersiveMode(activity);

                if (onFinished != null) {
                    onFinished.onFinished();
                }
            }
        }).start();
    }

    /**
     * Suspends background music, scrolling animation, and timeline countdowns when the Activity pauses.
     */
    public void onPause() {
        if (!isActive || isPaused) return;
        isPaused = true;

        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - lastResumeRealtimeMs);

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        if (scrollAnimator != null && scrollAnimator.isStarted()) {
            scrollAnimator.pause();
        }
        if (audioFadeAnimator != null && audioFadeAnimator.isStarted()) {
            audioFadeAnimator.pause();
        }

        if (!isTransitioning) {
            if (scrollRunnable != null) {
                handler.removeCallbacks(scrollRunnable);
                remainingScrollDelayMs = Math.max(1L, remainingScrollDelayMs - elapsed);
            }
            if (skipRevealRunnable != null) {
                handler.removeCallbacks(skipRevealRunnable);
                remainingSkipDelayMs = Math.max(1L, remainingSkipDelayMs - elapsed);
            }
            if (transitionRunnable != null) {
                handler.removeCallbacks(transitionRunnable);
                remainingTransitionMs = Math.max(1L, remainingTransitionMs - elapsed);
            }
        }
    }

    /**
     * Resumes immersive mode, background music, scrolling animation, and remaining timeline countdowns.
     */
    public void onResume() {
        if (!isActive) return;
        SystemUiHelper.applyImmersiveMode(activity);

        if (!isPaused) return;
        isPaused = false;
        lastResumeRealtimeMs = SystemClock.elapsedRealtime();

        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        if (scrollAnimator != null && scrollAnimator.isPaused()) {
            scrollAnimator.resume();
        }
        if (audioFadeAnimator != null && audioFadeAnimator.isPaused()) {
            audioFadeAnimator.resume();
        }

        if (!isTransitioning) {
            if (scrollRunnable != null && remainingScrollDelayMs > 0L) {
                handler.postDelayed(scrollRunnable, remainingScrollDelayMs);
            }
            if (skipRevealRunnable != null && remainingSkipDelayMs > 0L) {
                handler.postDelayed(skipRevealRunnable, remainingSkipDelayMs);
            }
            if (transitionRunnable != null && remainingTransitionMs > 0L) {
                handler.postDelayed(transitionRunnable, remainingTransitionMs);
            }
        }
    }

    public void onDestroy() {
        isActive = false;
        isTransitioning = false;
        isPaused = false;
        SystemUiHelper.setCinematicWindowMode(activity, false);
        cancelTimeline();
        if (audioFadeAnimator != null) {
            audioFadeAnimator.cancel();
            audioFadeAnimator = null;
        }
        resetHonorViews();
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
}
