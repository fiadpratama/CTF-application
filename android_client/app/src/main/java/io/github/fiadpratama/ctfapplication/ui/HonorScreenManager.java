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
 * smooth auto-scrolling credits, audio fade-out, and return transitions.
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
    private static final long FADE_DURATION_MS = 5000L;

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private ObjectAnimator scrollAnimator;
    private ValueAnimator audioFadeAnimator;
    private boolean isActive = false;

    public HonorScreenManager(Activity activity, ActivityMainBinding binding) {
        this.activity = activity;
        this.binding = binding;
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

        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        binding.honorText.setText(debriefMessage);

        binding.honorLayout.setAlpha(0.0f);
        binding.honorLayout.animate().alpha(1.0f).setDuration(FADE_DURATION_MS).start();

        handler.postDelayed(new Runnable() {
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
        }, SCROLL_START_DELAY_MS);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                fadeAudioAndEnd(onFinished);
            }
        }, TRANSITION_TOTAL_MS);

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
     * Fades audio volume down over 5s and smoothly cross-fades back to the main layout.
     */
    private void fadeAudioAndEnd(final OnCinematicFinishedListener onFinished) {
        if (mediaPlayer != null) {
            audioFadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
            audioFadeAnimator.setDuration(FADE_DURATION_MS);
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

        binding.honorLayout.animate().alpha(0.0f).setDuration(FADE_DURATION_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                isActive = false;
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                releaseMediaPlayer();

                binding.honorLayout.setVisibility(View.GONE);
                binding.honorLayout.setAlpha(1.0f);
                binding.mainLayout.setVisibility(View.VISIBLE);
                binding.flagInput.setText("");

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
        if (isActive && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void onDestroy() {
        isActive = false;
        if (activity != null && !activity.isDestroyed()) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        if (audioFadeAnimator != null) {
            audioFadeAnimator.cancel();
            audioFadeAnimator = null;
        }
        if (binding != null) {
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
