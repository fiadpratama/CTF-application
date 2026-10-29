package io.github.fiadpratama.ctfapplication.ui;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Tactile and diagnostic audio feedback controller.
 *
 * Encapsulates Android {@link ToneGenerator} lifecycle management to emit
 * terminal button beeps and error alert tones safely across hardware profiles.
 */
public class SoundEffectsManager {

    private ToneGenerator toneGenerator;

    public SoundEffectsManager() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);
        } catch (Exception ignored) {
            toneGenerator = null;
        }
    }

    /**
     * Emits a brief tactile beep tone for interactive button presses.
     */
    public void playButtonBeep() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 50);
    }

    /**
     * Emits a short alert tone for validation warnings or throttled actions.
     */
    public void playShortAlert() {
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200);
    }

    /**
     * Emits a standard error alert tone for failed network or protocol operations.
     */
    public void playErrorAlert() {
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
    }

    /**
     * Plays a specific ToneGenerator tone identifier for the given duration.
     *
     * @param toneType ToneGenerator constant (0 to skip playback)
     * @param durationMs Duration in milliseconds
     */
    public void playTone(int toneType, int durationMs) {
        if (toneGenerator != null && toneType != 0) {
            try {
                toneGenerator.startTone(toneType, durationMs);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Releases underlying audio resources when the host Activity is destroyed.
     */
    public void release() {
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {
            }
            toneGenerator = null;
        }
    }
}
