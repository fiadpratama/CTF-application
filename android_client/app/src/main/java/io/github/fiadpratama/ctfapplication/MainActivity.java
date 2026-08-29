package io.github.fiadpratama.ctfapplication;

import android.animation.ObjectAnimator;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;

import org.json.JSONObject;

import io.github.fiadpratama.ctfapplication.crypto.CryptoHelper;
import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;
import io.github.fiadpratama.ctfapplication.network.VaultApiClient;
import io.github.fiadpratama.ctfapplication.ui.TerminalLogger;
import io.github.fiadpratama.ctfapplication.util.SecurityChecks;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGen;
    private TerminalLogger terminalLogger;
    private MediaPlayer mediaPlayer;

    static {
        System.loadLibrary("native-crypto");
    }

    public native String getE2EKey();
    public native String getBackdoor();

    // ==========================================
    // LIFECYCLE
    // ==========================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toneGen = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);

        terminalLogger = new TerminalLogger(binding.terminalLogText, binding.terminalScrollView, handler);

        setupListeners();

        terminalLogger.log(getString(R.string.log_system_idle));
    }

    // ==========================================
    // MANAJEMEN STATE & KEYBOARD
    // ==========================================
    private void setButtonsState(boolean isEnabled) {
        binding.unlockButton.setEnabled(isEnabled);
        binding.submitFlagBtn.setEnabled(isEnabled);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // ==========================================
    // UI LISTENERS
    // ==========================================
    private void setupListeners() {
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
                terminalLogger.log(getString(R.string.log_initiating_connection));
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
                terminalLogger.log(getString(R.string.log_verifying_flag));
                verifyFlag(flag);
            }
        });
    }

    // ==========================================
    // CORE LOGIC — STAGE 1
    // ==========================================
    private void unlockVault() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(SecurityChecks.getEnvironmentSummary()); }});
                    Thread.sleep(300);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_generating_token)); }});
                    Thread.sleep(400);

                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String hash = CryptoHelper.computeHandshakeSignature(timestamp);

                    JSONObject payloadJson = new JSONObject();
                    payloadJson.put("timestamp", timestamp);
                    payloadJson.put("hash", hash);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_loading_native_lib)); }});
                    Thread.sleep(400);

                    byte[] keyBytes = getE2EKey().getBytes("UTF-8");

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_extracting_key)); }});
                    Thread.sleep(400);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_encrypting_payload)); }});
                    Thread.sleep(400);

                    JSONObject encryptedPayload = CryptoHelper.encryptPayload(payloadJson, keyBytes);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_transmitting)); }});

                    VaultApiClient.postJson(VaultApiClient.STAGE1_URL, encryptedPayload, new VaultApiClient.ApiCallback() {
                        @Override
                        public void onResponse(final int responseCode, final String body) {
                            final String displayMessage = VaultApiClient.extractDisplayMessage(body);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
                                            if (responseCode == 401) {
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
                                    terminalLogger.log(translateErrorCode(errorCode), new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
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
                            terminalLogger.log(getString(R.string.err_unexpected), new TerminalLogger.OnCompleteListener() {
                                @Override
                                public void onComplete() {
                                    setButtonsState(true);
                                }
                            });
                        }
                    });
                }
            }
        }).start();
    }

    // ==========================================
    // CORE LOGIC — FLAG VERIFICATION
    // ==========================================
    private void verifyFlag(final String flag) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                try {
                    json.put("flag", flag);
                } catch (Exception e) {
                    return;
                }

                VaultApiClient.postJson(VaultApiClient.VERIFY_FLAG_URL, json, new VaultApiClient.ApiCallback() {
                    @Override
                    public void onResponse(final int responseCode, final String body) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (responseCode == 200) {
                                    try {
                                        JSONObject resJson = new JSONObject(body);
                                        showHonorScreen(resJson.getString("message"));
                                    } catch (Exception e) {
                                        terminalLogger.log(getString(R.string.err_unexpected), new TerminalLogger.OnCompleteListener() {
                                            @Override
                                            public void onComplete() {
                                                setButtonsState(true);
                                            }
                                        });
                                    }
                                } else {
                                    terminalLogger.log(getString(R.string.log_verification_failed), new TerminalLogger.OnCompleteListener() {
                                        @Override
                                        public void onComplete() {
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
                                terminalLogger.log(translateErrorCode(errorCode), new TerminalLogger.OnCompleteListener() {
                                    @Override
                                    public void onComplete() {
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

    // ==========================================
    // ERROR CODE TRANSLATION
    // ==========================================
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

    // ==========================================
    // HONOR SCREEN
    // ==========================================
    private void showHonorScreen(String message) {
        binding.mainLayout.setVisibility(View.GONE);
        binding.honorLayout.setVisibility(View.VISIBLE);
        binding.honorText.setText(message);

        binding.honorLayout.setAlpha(0.0f);
        binding.honorLayout.animate().alpha(1.0f).setDuration(5000).start();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                NestedScrollView scroll = binding.honorScroll;
                View content = scroll.getChildAt(0);
                int maxScroll = content.getHeight() - scroll.getHeight();
                if (maxScroll > 0) {
                    ObjectAnimator animator = ObjectAnimator.ofInt(scroll, "scrollY", 0, maxScroll);
                    animator.setDuration(82000);
                    animator.setInterpolator(new LinearInterpolator());
                    animator.start();
                }
            }
        }, 2000);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    android.animation.ValueAnimator fadeAudio = android.animation.ValueAnimator.ofFloat(1.0f, 0.0f);
                    fadeAudio.setDuration(5000);
                    fadeAudio.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                            if (mediaPlayer != null) {
                                float vol = (float) animation.getAnimatedValue();
                                mediaPlayer.setVolume(vol, vol);
                            }
                        }
                    });
                    fadeAudio.start();
                }

                binding.honorLayout.animate().alpha(0.0f).setDuration(5000).withEndAction(new Runnable() {
                    @Override
                    public void run() {
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
