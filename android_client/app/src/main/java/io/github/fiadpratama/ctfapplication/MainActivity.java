package io.github.fiadpratama.ctfapplication;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;

import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import io.github.fiadpratama.ctfapplication.crypto.CryptoHelper;
import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;
import io.github.fiadpratama.ctfapplication.network.VaultApiClient;
import io.github.fiadpratama.ctfapplication.ui.TerminalLogger;
import io.github.fiadpratama.ctfapplication.util.SecurityChecks;

/**
 * Main Activity
 *
 * Core coordinator for the Android CTF client application. Manages immersive UI presentation,
 * terminal console streaming, native cryptographic handshakes, and flag verification workflows.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGen;
    private TerminalLogger terminalLogger;
    private MediaPlayer mediaPlayer;
    private ObjectAnimator honorScrollAnimator;
    private ValueAnimator honorAudioAnimator;
    private boolean isHonorScreenActive = false;

    static {
        System.loadLibrary("native-crypto");
    }

    /**
     * Native interface: retrieves session encryption key.
     */
    public native String getE2EKey();

    /**
     * Native interface: retrieves maintenance protocol token.
     */
    public native String getBackdoor();

    // --- Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toneGen = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);

        // Enforce immersive fullscreen sticky mode
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);

        terminalLogger = new TerminalLogger(binding.terminalLogText, binding.terminalScrollView, handler);

        setupListeners();

        initTerminalDiagnostics();
    }

    /**
     * Executes initial environment security diagnostics once during application startup.
     */
    private void initTerminalDiagnostics() {
        terminalLogger.log(getString(R.string.log_sys_init));
        if (SecurityChecks.isCompromised()) {
            terminalLogger.log(getString(R.string.log_env_warning));
        } else {
            terminalLogger.log(getString(R.string.log_env_nominal));
        }
        terminalLogger.log(getString(R.string.log_sys_idle));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isHonorScreenActive && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * Evaluates whether the Activity is still active and safe for UI updates.
     *
     * @return true if Activity is neither finishing nor destroyed
     */
    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    // --- UI State & Input Handling ---

    /**
     * Toggles interactive state of action buttons to prevent concurrent request spam.
     *
     * @param isEnabled true to enable user interaction; false to lock
     */
    private void setButtonsState(boolean isEnabled) {
        if (!isActivityAlive()) return;
        binding.unlockButton.setEnabled(isEnabled);
        binding.submitFlagBtn.setEnabled(isEnabled);
    }

    /**
     * Dismisses the virtual software keyboard if currently active.
     */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // --- View Listeners ---

    /**
     * Initializes click and touch listeners for interactive views.
     */
    private void setupListeners() {
        // Intercept touch on honor scroll view to prevent user interference with automated animation
        binding.honorScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true; 
            }
        });

        binding.unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                setButtonsState(false);
                terminalLogger.log(getString(R.string.log_handshake_start));
                unlockVault();
            }
        });

        binding.submitFlagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                String flag = binding.flagInput.getText().toString().trim();
                if (flag.isEmpty()) return;
                setButtonsState(false);
                terminalLogger.log(getString(R.string.log_flag_submitting));
                verifyFlag(flag);
            }
        });
    }

    // --- Handshake & Vault Unlock Pipeline ---

    /**
     * Executes the Phase 1 cryptographic handshake sequence on a background thread.
     */
    private void unlockVault() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String hash = CryptoHelper.computeHandshakeSignature(timestamp);

                    JSONObject payloadJson = new JSONObject();
                    payloadJson.put("timestamp", timestamp);
                    payloadJson.put("hash", hash);

                    byte[] keyBytes = getE2EKey().getBytes(StandardCharsets.UTF_8);
                    JSONObject encryptedPayload = CryptoHelper.encryptPayload(payloadJson, keyBytes);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityAlive()) {
                                terminalLogger.log(getString(R.string.log_handshake_transmit));
                            }
                        }
                    });

                    VaultApiClient.postJson(VaultApiClient.STAGE1_URL, encryptedPayload, new VaultApiClient.ApiCallback() {
                        @Override
                        public void onResponse(final int responseCode, final String body) {
                            final String displayMessage = VaultApiClient.extractDisplayMessage(body);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityAlive()) return;
                                    terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
                                            if (!isActivityAlive()) return;
                                            if (responseCode != 200) {
                                                if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                            }
                                            setButtonsState(true);
                                        }
                                    });
                                }
                            });
                        }

                        @Override
                        public void onError(final String errorCode) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityAlive()) return;
                                    terminalLogger.log(translateErrorCode(errorCode), new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
                                            if (!isActivityAlive()) return;
                                            if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                            setButtonsState(true);
                                        }
                                    });
                                }
                            });
                        }
                    });

                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityAlive()) return;
                            terminalLogger.log(getString(R.string.err_unexpected), new TerminalLogger.OnCompleteListener() {
                                @Override
                                public void onComplete() {
                                    if (!isActivityAlive()) return;
                                    setButtonsState(true);
                                }
                            });
                        }
                    });
                }
            }
        }).start();
    }

    // --- Flag Verification Pipeline ---

    /**
     * Submits candidate flag to the remote server for validation and Hall of Fame unlocking.
     *
     * @param flag Submitted challenge flag
     */
    private void verifyFlag(final String flag) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                try {
                    json.put("flag", flag);
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityAlive()) return;
                            terminalLogger.log(getString(R.string.err_unexpected), new TerminalLogger.OnCompleteListener() {
                                @Override
                                public void onComplete() {
                                    if (!isActivityAlive()) return;
                                    setButtonsState(true);
                                }
                            });
                        }
                    });
                    return;
                }

                VaultApiClient.postJson(VaultApiClient.VERIFY_FLAG_URL, json, new VaultApiClient.ApiCallback() {
                    @Override
                    public void onResponse(final int responseCode, final String body) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityAlive()) return;
                                if (responseCode == 200) {
                                    try {
                                        JSONObject resJson = new JSONObject(body);
                                        showHonorScreen(resJson.getString("message"));
                                    } catch (Exception e) {
                                        terminalLogger.log(getString(R.string.err_unexpected), new TerminalLogger.OnCompleteListener() {
                                            @Override
                                            public void onComplete() {
                                                if (!isActivityAlive()) return;
                                                setButtonsState(true);
                                            }
                                        });
                                    }
                                } else {
                                    final String displayMessage = VaultApiClient.extractDisplayMessage(body);
                                    terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
                                            if (!isActivityAlive()) return;
                                            if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                            setButtonsState(true);
                                        }
                                    });
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(final String errorCode) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityAlive()) return;
                                terminalLogger.log(translateErrorCode(errorCode), new TerminalLogger.OnCompleteListener() {
                                    @Override
                                    public void onComplete() {
                                        if (!isActivityAlive()) return;
                                        if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                        setButtonsState(true);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        }).start();
    }

    // --- Network Error Translation ---

    /**
     * Maps internal error codes to localized string resources.
     *
     * @param code Machine-readable error code
     * @return User-facing error message
     */
    private String translateErrorCode(String code) {
        switch (code) {
            case "NO_INTERNET":
                return getString(R.string.err_no_internet);
            case "TIMEOUT":
                return getString(R.string.err_timeout);
            default:
                return getString(R.string.err_unexpected);
        }
    }

    // --- Honor Screen Presentation ---

    /**
     * Triggers the cinematic Hall of Fame screen with audio playback and auto-scrolling credits.
     *
     * @param message Structured debriefing message received from the server
     */
    private void showHonorScreen(String message) {
        if (!isActivityAlive()) return;
        isHonorScreenActive = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        binding.honorText.setText(message);

        binding.honorLayout.setAlpha(0.0f);
        binding.honorLayout.animate().alpha(1.0f).setDuration(5000).start();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isActivityAlive()) return;
                NestedScrollView scroll = binding.honorScroll;
                View content = scroll.getChildAt(0);
                int maxScroll = content.getHeight() - scroll.getHeight();
                if (maxScroll > 0) {
                    honorScrollAnimator = ObjectAnimator.ofInt(scroll, "scrollY", 0, maxScroll);
                    honorScrollAnimator.setDuration(82000);
                    honorScrollAnimator.setInterpolator(new LinearInterpolator());
                    honorScrollAnimator.start();
                }
            }
        }, 2000);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isActivityAlive()) return;
                if (mediaPlayer != null) {
                    honorAudioAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
                    honorAudioAnimator.setDuration(5000);
                    honorAudioAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            if (mediaPlayer != null) {
                                float vol = (float) animation.getAnimatedValue();
                                mediaPlayer.setVolume(vol, vol);
                            }
                        }
                    });
                    honorAudioAnimator.start();
                }

                binding.honorLayout.animate().alpha(0.0f).setDuration(5000).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityAlive()) return;
                        isHonorScreenActive = false;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        if (mediaPlayer != null) {
                            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                            mediaPlayer.release();
                            mediaPlayer = null;
                        }
                        binding.honorLayout.setVisibility(View.GONE);
                        binding.honorLayout.setAlpha(1.0f);

                        terminalLogger.clear();

                        binding.mainLayout.setVisibility(View.VISIBLE);
                        binding.flagInput.setText("");
                        setButtonsState(true);
                        terminalLogger.log(getString(R.string.log_system_rebooted));
                    }
                }).start();
            }
        }, 87000);

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bgm);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isHonorScreenActive = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (honorScrollAnimator != null) {
            honorScrollAnimator.cancel();
            honorScrollAnimator = null;
        }
        if (honorAudioAnimator != null) {
            honorAudioAnimator.cancel();
            honorAudioAnimator = null;
        }
        if (binding != null) {
            binding.honorLayout.animate().cancel();
        }
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
