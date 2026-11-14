package io.github.fiadpratama.ctfapplication.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import io.github.fiadpratama.ctfapplication.R;
import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;

/**
 * Cinematic Hall of Fame Presentation Controller
 *
 * Coordinates the full-screen debriefing sequence, background music playback,
 * smooth auto-scrolling credits, audio fade-out, 15-second skip button reveal,
 * Lifecycle-driven pause/resume synchronization, and single-path idempotent teardown.
 */
public class HonorScreenManager {

    /**
     * Callback triggered when the cinematic sequence finishes and default UI is restored.
     */
    public interface OnCinematicFinishedListener {
        void onFinished();
    }

    private enum State {
        IDLE,
        PENDING_START,
        PLAYING,
        PAUSED,
        FADING_OUT
    }

    private static final long SCROLL_START_DELAY_MS = 2000L;
    private static final long SCROLL_DURATION_MS = 82000L;
    private static final long TRANSITION_TOTAL_MS = 87000L;
    private static final long FADE_STANDARD_MS = 5000L;
    private static final long FADE_SKIP_MS = 1000L;
    private static final long SKIP_APPEAR_DELAY_MS = 15000L;
    private static final long SKIP_FADE_IN_MS = 600L;

    private final AppCompatActivity activity;
    private final Lifecycle lifecycle;
    private final ActivityMainBinding binding;
    private final Runnable onSkipAction;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private State state = State.IDLE;
    private State stateBeforePause = State.IDLE;
    private boolean isLifecycleResumed;
    private boolean isDestroyed = false;

    private String pendingDebriefMessage;
    private OnCinematicFinishedListener pendingOnFinished;

    private MediaPlayer mediaPlayer;
    private boolean isMediaPlayerPlaying = false;

    private ObjectAnimator layoutFadeAnimator;
    private ObjectAnimator skipFadeAnimator;
    private ObjectAnimator scrollAnimator;
    private ValueAnimator audioFadeAnimator;

    private long lastResumeRealtimeMs = 0L;
    private long remainingScrollDelayMs = 0L;
    private long remainingSkipDelayMs = 0L;
    private long remainingTransitionMs = 0L;

    private Runnable scrollRunnable;
    private Runnable transitionRunnable;
    private Runnable skipRevealRunnable;

    private final DefaultLifecycleObserver lifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onResume(@NonNull LifecycleOwner owner) {
            isLifecycleResumed = true;
            handleForegroundResume();
        }

        @Override
        public void onPause(@NonNull LifecycleOwner owner) {
            isLifecycleResumed = false;
            handleForegroundPause();
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            onDestroyCleanup();
        }
    };

    public HonorScreenManager(AppCompatActivity activity, ActivityMainBinding binding) {
        this(activity, binding, null);
    }

    public HonorScreenManager(final AppCompatActivity activity, ActivityMainBinding binding, Runnable onSkipAction) {
        this.activity = activity;
        this.lifecycle = activity != null ? activity.getLifecycle() : null;
        this.binding = binding;
        this.onSkipAction = onSkipAction;
        this.isLifecycleResumed = this.lifecycle != null
            && this.lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED);

        if (this.lifecycle != null) {
            this.lifecycle.addObserver(lifecycleObserver);
        }
        if (this.binding != null && this.activity != null) {
            SystemUiHelper.attachSkipButtonInsets(this.activity, this.binding.honorSkipButton);
        }
    }

    /**
     * Checks if the cinematic sequence is currently pending, playing, paused, or fading out.
     *
     * @return true if honor screen flow is active
     */
    public boolean isActive() {
        return state != State.IDLE;
    }

    private boolean isForegroundEligible() {
        return !isDestroyed
            && binding != null
            && lifecycle != null
            && isLifecycleResumed
            && lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED)
            && SystemUiHelper.isActivityAlive(activity);
    }

    /**
     * Launches the cinematic sequence when the Activity is actively resumed in foreground,
     * or defers playback into {@link State#PENDING_START} until {@code onResume} occurs.
     *
     * @param debriefMessage Structured message text received from server
     * @param onFinished Callback invoked when cinematic completes and UI is reset
     */
    public void start(String debriefMessage, final OnCinematicFinishedListener onFinished) {
        if (isDestroyed || binding == null || !SystemUiHelper.isActivityAlive(activity)) {
            return;
        }

        cleanupPresentation(false);

        pendingDebriefMessage = debriefMessage != null ? debriefMessage : "";
        pendingOnFinished = onFinished;

        binding.honorText.setText(pendingDebriefMessage);
        binding.honorLayout.setAlpha(0.0f);
        binding.honorSkipButton.setAlpha(0.0f);
        binding.honorSkipButton.setVisibility(View.GONE);
        binding.honorSkipButton.setEnabled(false);
        resetHonorScrollToTop();

        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        ViewCompat.requestApplyInsets(binding.honorSkipButton);

        if (!isForegroundEligible()) {
            state = State.PENDING_START;
            return;
        }

        beginForegroundPlayback();
    }

    /**
     * Begins playback once foreground lifecycle conditions are satisfied.
     */
    private void beginForegroundPlayback() {
        if (!isForegroundEligible()) {
            state = State.PENDING_START;
            return;
        }

        state = State.PLAYING;
        stateBeforePause = State.IDLE;
        lastResumeRealtimeMs = SystemClock.elapsedRealtime();
        remainingScrollDelayMs = SCROLL_START_DELAY_MS;
        remainingSkipDelayMs = SKIP_APPEAR_DELAY_MS;
        remainingTransitionMs = TRANSITION_TOTAL_MS;

        SystemUiHelper.setCinematicWindowMode(activity, true);
        SystemUiHelper.applyImmersiveMode(activity);

        binding.honorSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state != State.PLAYING || !isForegroundEligible()) {
                    return;
                }
                if (onSkipAction != null) {
                    onSkipAction.run();
                }
                startFadeOut(FADE_SKIP_MS);
            }
        });

        startLayoutFadeIn();
        scheduleTimeline();
        startAudioPlayback();
    }

    private void startLayoutFadeIn() {
        cancelAnimator(layoutFadeAnimator);
        layoutFadeAnimator = ObjectAnimator.ofFloat(binding.honorLayout, View.ALPHA, 0.0f, 1.0f);
        layoutFadeAnimator.setDuration(FADE_STANDARD_MS);
        layoutFadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        layoutFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (layoutFadeAnimator == animation) {
                    layoutFadeAnimator = null;
                }
            }
        });
        layoutFadeAnimator.start();
    }

    private void scheduleTimeline() {
        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                scrollRunnable = null;
                remainingScrollDelayMs = 0L;
                if (state != State.PLAYING || !isForegroundEligible()) {
                    return;
                }
                NestedScrollView scroll = binding.honorScroll;
                View content = scroll.getChildAt(0);
                if (content != null) {
                    int maxScroll = content.getHeight() - scroll.getHeight();
                    if (maxScroll > 0) {
                        cancelAnimator(scrollAnimator);
                        scrollAnimator = ObjectAnimator.ofInt(scroll, "scrollY", 0, maxScroll);
                        scrollAnimator.setDuration(SCROLL_DURATION_MS);
                        scrollAnimator.setInterpolator(new LinearInterpolator());
                        scrollAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (scrollAnimator == animation) {
                                    scrollAnimator = null;
                                }
                            }
                        });
                        scrollAnimator.start();
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
                if (state != State.PLAYING || !isForegroundEligible()) {
                    return;
                }
                binding.honorSkipButton.setVisibility(View.VISIBLE);
                binding.honorSkipButton.setAlpha(0.0f);
                binding.honorSkipButton.setEnabled(true);

                cancelAnimator(skipFadeAnimator);
                skipFadeAnimator = ObjectAnimator.ofFloat(binding.honorSkipButton, View.ALPHA, 0.0f, 1.0f);
                skipFadeAnimator.setDuration(SKIP_FADE_IN_MS);
                skipFadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                skipFadeAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (skipFadeAnimator == animation) {
                            skipFadeAnimator = null;
                        }
                    }
                });
                skipFadeAnimator.start();
            }
        };
        handler.postDelayed(skipRevealRunnable, remainingSkipDelayMs);

        transitionRunnable = new Runnable() {
            @Override
            public void run() {
                transitionRunnable = null;
                remainingTransitionMs = 0L;
                if (state != State.PLAYING || !isForegroundEligible()) {
                    return;
                }
                startFadeOut(FADE_STANDARD_MS);
            }
        };
        handler.postDelayed(transitionRunnable, remainingTransitionMs);
    }

    private void startAudioPlayback() {
        releaseAudioResources();
        try {
            final MediaPlayer player = MediaPlayer.create(activity, R.raw.bgm);
            if (player == null) {
                return;
            }
            mediaPlayer = player;
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (mediaPlayer == mp) {
                        releaseAudioResources();
                    }
                    return true;
                }
            });
            player.setLooping(true);
            setMediaPlayerVolumeSafe(player, 1.0f);
            if (mediaPlayer == player) {
                player.start();
                isMediaPlayerPlaying = true;
            }
        } catch (Exception ignored) {
            releaseAudioResources();
        }
    }

    /**
     * Cross-fades audio and layout opacity down smoothly, synchronized under Activity lifecycle,
     * and restores the terminal workspace when completed.
     *
     * @param fadeDurationMs Duration of fade-out animation in milliseconds
     */
    private void startFadeOut(long fadeDurationMs) {
        if (state != State.PLAYING || binding == null) {
            return;
        }
        state = State.FADING_OUT;
        binding.honorSkipButton.setEnabled(false);
        binding.honorSkipButton.setOnClickListener(null);

        cancelTimelineRunnables();
        cancelAnimator(scrollAnimator);
        scrollAnimator = null;

        float currentSkipAlpha = binding.honorSkipButton.getAlpha();
        cancelAnimator(skipFadeAnimator);
        skipFadeAnimator = null;
        if (binding.honorSkipButton.getVisibility() == View.VISIBLE && currentSkipAlpha > 0.0f) {
            skipFadeAnimator = ObjectAnimator.ofFloat(binding.honorSkipButton, View.ALPHA, currentSkipAlpha, 0.0f);
            skipFadeAnimator.setDuration(fadeDurationMs);
            skipFadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            skipFadeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (skipFadeAnimator == animation) {
                        skipFadeAnimator = null;
                    }
                }
            });
            skipFadeAnimator.start();
        }

        cancelAnimator(audioFadeAnimator);
        audioFadeAnimator = null;
        if (mediaPlayer != null) {
            final MediaPlayer ownedPlayer = mediaPlayer;
            audioFadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
            audioFadeAnimator.setDuration(fadeDurationMs);
            audioFadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (mediaPlayer == ownedPlayer) {
                        float vol = (float) animation.getAnimatedValue();
                        setMediaPlayerVolumeSafe(ownedPlayer, vol);
                    }
                }
            });
            audioFadeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (audioFadeAnimator == animation) {
                        audioFadeAnimator = null;
                    }
                }
            });
            audioFadeAnimator.start();
        }

        float currentLayoutAlpha = binding.honorLayout.getAlpha();
        cancelAnimator(layoutFadeAnimator);
        layoutFadeAnimator = ObjectAnimator.ofFloat(binding.honorLayout, View.ALPHA, currentLayoutAlpha, 0.0f);
        layoutFadeAnimator.setDuration(fadeDurationMs);
        layoutFadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        layoutFadeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean wasCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                wasCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (layoutFadeAnimator == animation) {
                    layoutFadeAnimator = null;
                }
                if (!wasCancelled && state == State.FADING_OUT && !isDestroyed) {
                    finishCinematicAndRestoreUi();
                }
            }
        });
        layoutFadeAnimator.start();
    }

    private void finishCinematicAndRestoreUi() {
        OnCinematicFinishedListener callback = pendingOnFinished;
        cleanupPresentation(true);
        if (callback != null && !isDestroyed && SystemUiHelper.isActivityAlive(activity)) {
            callback.onFinished();
        }
    }

    private void handleForegroundPause() {
        if (isDestroyed) {
            return;
        }
        if (state != State.PLAYING && state != State.FADING_OUT) {
            return;
        }

        stateBeforePause = state;
        state = State.PAUSED;

        if (mediaPlayer != null && isMediaPlayerPlaying) {
            try {
                mediaPlayer.pause();
                isMediaPlayerPlaying = false;
            } catch (IllegalStateException e) {
                releaseAudioResources();
            }
        }

        pauseAnimatorIfRunning(layoutFadeAnimator);
        pauseAnimatorIfRunning(skipFadeAnimator);
        pauseAnimatorIfRunning(scrollAnimator);
        pauseAnimatorIfRunning(audioFadeAnimator);

        if (stateBeforePause == State.PLAYING) {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - lastResumeRealtimeMs);
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

    private void handleForegroundResume() {
        if (!isForegroundEligible()) {
            return;
        }

        if (state == State.PENDING_START) {
            beginForegroundPlayback();
            return;
        }

        if (state != State.PAUSED) {
            if (state == State.PLAYING || state == State.FADING_OUT) {
                SystemUiHelper.applyImmersiveMode(activity);
            }
            return;
        }

        State resumedState = stateBeforePause;
        stateBeforePause = State.IDLE;
        state = resumedState;

        SystemUiHelper.applyImmersiveMode(activity);
        lastResumeRealtimeMs = SystemClock.elapsedRealtime();

        if (mediaPlayer != null && !isMediaPlayerPlaying) {
            try {
                mediaPlayer.start();
                isMediaPlayerPlaying = true;
            } catch (IllegalStateException e) {
                releaseAudioResources();
            }
        }

        resumeAnimatorIfPaused(layoutFadeAnimator);
        resumeAnimatorIfPaused(skipFadeAnimator);
        resumeAnimatorIfPaused(scrollAnimator);
        resumeAnimatorIfPaused(audioFadeAnimator);

        if (state == State.PLAYING) {
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

    /**
     * Releases all active resources when the host Activity is destroyed (idempotent).
     */
    public void onDestroy() {
        onDestroyCleanup();
    }

    private void onDestroyCleanup() {
        if (isDestroyed) {
            return;
        }
        isDestroyed = true;
        isLifecycleResumed = false;
        if (lifecycle != null) {
            lifecycle.removeObserver(lifecycleObserver);
        }
        cleanupPresentation(false);
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Single idempotent teardown path guaranteeing strict ordering across timers, animators,
     * audio ownership, window flags, and view restoration.
     *
     * @param restoreMainUi true to show main terminal UI; false when restarting or destroying
     */
    private void cleanupPresentation(boolean restoreMainUi) {
        state = State.IDLE;
        stateBeforePause = State.IDLE;
        pendingDebriefMessage = null;
        pendingOnFinished = null;

        cancelTimelineRunnables();

        cancelAnimator(layoutFadeAnimator);
        layoutFadeAnimator = null;
        cancelAnimator(skipFadeAnimator);
        skipFadeAnimator = null;
        cancelAnimator(scrollAnimator);
        scrollAnimator = null;

        releaseAudioResources();

        SystemUiHelper.setCinematicWindowMode(activity, false);

        if (binding != null) {
            binding.honorSkipButton.setOnClickListener(null);
            binding.honorSkipButton.setEnabled(false);
            binding.honorSkipButton.setVisibility(View.GONE);
            binding.honorSkipButton.setAlpha(0.0f);
            binding.honorLayout.setAlpha(0.0f);
            binding.honorText.setText("");
            resetHonorScrollToTop();

            if (restoreMainUi) {
                binding.honorLayout.setVisibility(View.GONE);
                binding.mainLayout.setVisibility(View.VISIBLE);
                binding.flagInput.setText("");
                if (SystemUiHelper.isActivityAlive(activity)) {
                    SystemUiHelper.applyImmersiveMode(activity);
                }
            }
        }
    }

    private void resetHonorScrollToTop() {
        if (binding == null) {
            return;
        }
        binding.honorScroll.stopNestedScroll();
        binding.honorScroll.scrollTo(0, 0);
        binding.honorScroll.setScrollY(0);
        binding.honorScroll.post(new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed && binding != null && SystemUiHelper.isActivityAlive(activity)) {
                    binding.honorScroll.scrollTo(0, 0);
                }
            }
        });
    }

    private void setMediaPlayerVolumeSafe(MediaPlayer player, float volume) {
        if (player == null || player != mediaPlayer) {
            return;
        }
        try {
            player.setVolume(volume, volume);
        } catch (IllegalStateException e) {
            releaseAudioResources();
        }
    }

    private void cancelTimelineRunnables() {
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
    }

    private void cancelAnimator(Animator animator) {
        if (animator == null) {
            return;
        }
        animator.removeAllListeners();
        if (animator instanceof ValueAnimator) {
            ((ValueAnimator) animator).removeAllUpdateListeners();
        }
        animator.cancel();
    }

    private void pauseAnimatorIfRunning(Animator animator) {
        if (animator != null && animator.isRunning()) {
            animator.pause();
        }
    }

    private void resumeAnimatorIfPaused(Animator animator) {
        if (animator != null && animator.isPaused()) {
            animator.resume();
        }
    }

    /**
     * Releases MediaPlayer safely by first tearing down the owning audio fade animator so no
     * animation frame can access a released or errored player instance.
     */
    private void releaseAudioResources() {
        cancelAnimator(audioFadeAnimator);
        audioFadeAnimator = null;

        isMediaPlayerPlaying = false;
        MediaPlayer player = mediaPlayer;
        mediaPlayer = null;
        if (player != null) {
            try {
                player.setOnErrorListener(null);
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
        }
    }
}
