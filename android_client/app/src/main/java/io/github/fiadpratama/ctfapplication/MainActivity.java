package io.github.fiadpratama.ctfapplication;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.media.MediaPlayer;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.view.animation.LinearInterpolator;

import org.json.JSONObject;

import java.util.Random;

import io.github.fiadpratama.ctfapplication.crypto.CryptoHelper;
import io.github.fiadpratama.ctfapplication.network.VaultApiClient;
import io.github.fiadpratama.ctfapplication.ui.TerminalLogger;
import io.github.fiadpratama.ctfapplication.util.SecurityChecks;

public class MainActivity extends AppCompatActivity {

    // --- UI Dimension Constants (dp) ---
    private static final int PADDING_BOOT_H_DP = 6;
    private static final int PADDING_BOOT_TOP_DP = 12;
    private static final int PADDING_ROOT_H_DP = 18;
    private static final int PADDING_ROOT_TOP_DP = 30;
    private static final int PADDING_ROOT_BOTTOM_DP = 18;
    private static final int SUBTITLE_PADDING_TOP_DP = 6;
    private static final int SUBTITLE_PADDING_BOTTOM_DP = 24;
    private static final int BUTTON_HEIGHT_DP = 45;
    private static final int BUTTON_MARGIN_BOTTOM_DP = 24;
    private static final int TERMINAL_PADDING_DP = 9;
    private static final int INPUT_PADDING_DP = 6;
    private static final int INPUT_MARGIN_TOP_DP = 12;
    private static final int INPUT_MARGIN_BOTTOM_DP = 6;
    private static final int HONOR_PADDING_H_DP = 12;
    private static final int HONOR_PADDING_TOP_DP = 24;
    private static final int HONOR_SCROLL_PADDING_DP = 240;
    private static final int HONOR_TEXT_PADDING_TOP_DP = 18;

    // --- UI Components ---
    private TextView terminalLog;
    private ScrollView scrollView;
    private LinearLayout mainRootLayout;
    private LinearLayout bootLayout;
    private TextView bootText;
    private LinearLayout honorLayout;
    private TextView honorText;
    private TextView honorTitleText;
    private MediaPlayer mediaPlayer;
    private EditText flagInput;
    private Button submitFlagBtn;

    // --- State & Utilities ---
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private ToneGenerator toneGen;
    private TerminalLogger terminalLogger;

    // --- Native Library (JNI) ---
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
        super.onCreate(savedInstanceState);

        toneGen = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);

        LinearLayout appContainer = new LinearLayout(this);
        appContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        appContainer.setOrientation(LinearLayout.VERTICAL);
        appContainer.setBackgroundColor(Color.parseColor("#0a0a0a"));

        // --- Build screen layers in order ---
        setupBootScreen(appContainer);
        setupHonorScreen(appContainer);
        setupMainUI(appContainer);

        setContentView(appContainer);

        terminalLogger = new TerminalLogger(terminalLog, scrollView, handler);

        startBootSequence();
    }

    // ==========================================
    // UI UNIT CONVERSION
    // ==========================================
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // ==========================================
    // UI SETUP
    // ==========================================
    private void setupBootScreen(LinearLayout container) {
        bootLayout = new LinearLayout(this);
        bootLayout.setOrientation(LinearLayout.VERTICAL);
        bootLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        bootLayout.setBackgroundColor(Color.parseColor("#000000"));
        bootLayout.setPadding(
            dpToPx(PADDING_BOOT_H_DP), dpToPx(PADDING_BOOT_TOP_DP),
            dpToPx(PADDING_BOOT_H_DP), dpToPx(PADDING_BOOT_H_DP));

        bootText = new TextView(this);
        bootText.setTextColor(Color.parseColor("#00FF41"));
        bootText.setTextSize(14f);
        bootText.setTypeface(Typeface.MONOSPACE);
        bootLayout.addView(bootText);

        container.addView(bootLayout);
    }

    private void setupMainUI(LinearLayout container) {
        mainRootLayout = new LinearLayout(this);
        mainRootLayout.setOrientation(LinearLayout.VERTICAL);
        mainRootLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        mainRootLayout.setPadding(
            dpToPx(PADDING_ROOT_H_DP), dpToPx(PADDING_ROOT_TOP_DP),
            dpToPx(PADDING_ROOT_H_DP), dpToPx(PADDING_ROOT_BOTTOM_DP));
        mainRootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mainRootLayout.setVisibility(View.GONE);

        TextView mainTitleText = new TextView(this);
        mainTitleText.setText(getString(R.string.main_title));
        mainTitleText.setTextColor(Color.parseColor("#00FF41"));
        mainTitleText.setTextSize(32f);
        mainTitleText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mainTitleText.setGravity(Gravity.CENTER);
        mainTitleText.setLetterSpacing(0.2f);

        Animation anim = new AlphaAnimation(0.3f, 1.0f);
        anim.setDuration(1500);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        mainTitleText.startAnimation(anim);
        mainRootLayout.addView(mainTitleText);

        TextView subText = new TextView(this);
        subText.setText(getString(R.string.main_subtitle));
        subText.setTextColor(Color.parseColor("#008F11"));
        subText.setTextSize(12f);
        subText.setTypeface(Typeface.MONOSPACE);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(0, dpToPx(SUBTITLE_PADDING_TOP_DP), 0, dpToPx(SUBTITLE_PADDING_BOTTOM_DP));
        mainRootLayout.addView(subText);

        Button unlockButton = new Button(this);
        unlockButton.setText(getString(R.string.btn_initiate_decryption));
        unlockButton.setTextColor(Color.parseColor("#0a0a0a"));
        unlockButton.setTextSize(16f);
        unlockButton.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setShape(GradientDrawable.RECTANGLE);
        btnShape.setCornerRadius(10f);
        btnShape.setColor(Color.parseColor("#00FF41"));
        unlockButton.setBackground(btnShape);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BUTTON_HEIGHT_DP));
        btnParams.setMargins(0, 0, 0, dpToPx(BUTTON_MARGIN_BOTTOM_DP));
        unlockButton.setLayoutParams(btnParams);

        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                terminalLogger.log(getString(R.string.log_initiating_connection));
                unlockVault();
            }
        });
        mainRootLayout.addView(unlockButton);

        scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        GradientDrawable terminalShape = new GradientDrawable();
        terminalShape.setShape(GradientDrawable.RECTANGLE);
        terminalShape.setColor(Color.parseColor("#000000"));
        terminalShape.setStroke(2, Color.parseColor("#008F11"));
        terminalShape.setCornerRadius(15f);
        scrollView.setBackground(terminalShape);
        scrollView.setPadding(
            dpToPx(TERMINAL_PADDING_DP), dpToPx(TERMINAL_PADDING_DP),
            dpToPx(TERMINAL_PADDING_DP), dpToPx(TERMINAL_PADDING_DP));

        terminalLog = new TextView(this);
        terminalLog.setTextColor(Color.parseColor("#00FF41"));
        terminalLog.setTextSize(12f);
        terminalLog.setTypeface(Typeface.MONOSPACE);
        scrollView.addView(terminalLog);

        mainRootLayout.addView(scrollView);

        flagInput = new EditText(this);
        flagInput.setHint(getString(R.string.flag_input_hint));
        flagInput.setTextColor(Color.parseColor("#00FF41"));
        flagInput.setHintTextColor(Color.parseColor("#008F11"));
        flagInput.setTextSize(14f);
        flagInput.setTypeface(Typeface.MONOSPACE);

        GradientDrawable inputShape = new GradientDrawable();
        inputShape.setShape(GradientDrawable.RECTANGLE);
        inputShape.setStroke(2, Color.parseColor("#00FF41"));
        inputShape.setCornerRadius(10f);
        flagInput.setBackground(inputShape);
        flagInput.setPadding(
            dpToPx(INPUT_PADDING_DP), dpToPx(INPUT_PADDING_DP),
            dpToPx(INPUT_PADDING_DP), dpToPx(INPUT_PADDING_DP));

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, dpToPx(INPUT_MARGIN_TOP_DP), 0, dpToPx(INPUT_MARGIN_BOTTOM_DP));
        flagInput.setLayoutParams(inputParams);

        mainRootLayout.addView(flagInput);

        submitFlagBtn = new Button(this);
        submitFlagBtn.setText(getString(R.string.btn_submit_flag));
        submitFlagBtn.setTextColor(Color.parseColor("#0a0a0a"));
        submitFlagBtn.setTextSize(16f);
        submitFlagBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        GradientDrawable btnShape2 = new GradientDrawable();
        btnShape2.setShape(GradientDrawable.RECTANGLE);
        btnShape2.setCornerRadius(10f);
        btnShape2.setColor(Color.parseColor("#00FF41"));
        submitFlagBtn.setBackground(btnShape2);

        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BUTTON_HEIGHT_DP));
        submitFlagBtn.setLayoutParams(submitParams);

        submitFlagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                String flag = flagInput.getText().toString().trim();
                if (flag.isEmpty()) return;
                submitFlagBtn.setEnabled(false);
                terminalLogger.log(getString(R.string.log_verifying_flag));
                verifyFlag(flag);
            }
        });
        mainRootLayout.addView(submitFlagBtn);

        container.addView(mainRootLayout);
    }

    // ==========================================
    // BOOT SEQUENCE
    // ==========================================
    private void startBootSequence() {
        if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 300);

        java.util.List<String> bootLogs = new java.util.ArrayList<>();
        bootLogs.add("Initializing kernel... OK");
        bootLogs.add("Mounting /dev/block/bootdevice... OK");
        bootLogs.add("Checking Virtual Machine environment... " + (SecurityChecks.isEmulator() ? "EMULATOR DETECTED [BYPASSED]" : "CLEAN"));
        bootLogs.add("Checking Root privileges... " + (SecurityChecks.isRooted() ? "ROOTED DEVICE DETECTED [BYPASSED]" : "CLEAN"));
        bootLogs.add("Loading cryptography modules... OK");
        bootLogs.add("Establishing secure proxy... OK");
        bootLogs.add("WARNING: Hostile environment detected.");
        bootLogs.add("Enforcing military-grade lockdown...");
        bootLogs.add(">> PROTOCOL 0x5A_XOR INITIALIZED");
        bootLogs.add(">> NETWORK_FILTER_B64 ACTIVE");

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (final String log : bootLogs) {
                    try { Thread.sleep(400); } catch (Exception e) {}
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            bootText.append(log + "\n");
                        }
                    });
                }

                final String baseText = getString(R.string.log_booting_ui);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        bootText.append(baseText);
                    }
                });

                for (int cycle = 0; cycle < 3; cycle++) {
                    for (int dots = 1; dots <= 3; dots++) {
                        try { Thread.sleep(300); } catch (Exception e) {}
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                bootText.append(".");
                            }
                        });
                    }
                    try { Thread.sleep(300); } catch (Exception e) {}
                    if (cycle < 2) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                String current = bootText.getText().toString();
                                bootText.setText(current.substring(0, current.length() - 3));
                            }
                        });
                    }
                }

                try { Thread.sleep(500); } catch (Exception e) {}
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        bootText.append(" OK\n");
                    }
                });

                try { Thread.sleep(800); } catch (Exception e) {}
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        bootLayout.setVisibility(View.GONE);
                        mainRootLayout.setVisibility(View.VISIBLE);
                        terminalLogger.log(getString(R.string.log_system_idle));
                    }
                });
            }
        }).start();
    }

    // ==========================================
    // CORE LOGIC
    // ==========================================
    private void simulateHexDump() {
        for (int i = 0; i < 6; i++) {
            StringBuilder hexLine = new StringBuilder("[0x" + Integer.toHexString(0x7F000 + random.nextInt(0x1000)).toUpperCase() + "] : ");
            for (int j = 0; j < 8; j++) {
                hexLine.append(Integer.toHexString(random.nextInt(256)).toUpperCase() + " ");
            }
            hexLine.append("... (DECODING)");
            terminalLogger.log(hexLine.toString());
        }
    }

    private void unlockVault() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_generating_token)); }});
                    Thread.sleep(700);

                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String hash = CryptoHelper.computeTimestampHash(timestamp);

                    JSONObject payloadJson = new JSONObject();
                    payloadJson.put("timestamp", timestamp);
                    payloadJson.put("hash", hash);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_loading_native_lib)); }});
                    Thread.sleep(800);

                    handler.post(new Runnable() { @Override public void run() {
                        terminalLogger.log(getString(R.string.log_extracting_key));
                        simulateHexDump();
                    }});
                    Thread.sleep(2000);

                    byte[] keyBytes = getE2EKey().getBytes("UTF-8");

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_encrypting_payload)); }});
                    Thread.sleep(800);

                    JSONObject encryptedPayload = CryptoHelper.encryptPayload(payloadJson, keyBytes);

                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log(getString(R.string.log_transmitting)); }});
                    Thread.sleep(800);

                    VaultApiClient.postJson(VaultApiClient.STAGE1_URL, encryptedPayload, new VaultApiClient.ApiCallback() {
                        @Override
                        public void onResponse(final int responseCode, final String body) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    terminalLogger.log("> SERVER RESPONSE [" + responseCode + "]:\n> " + body);
                                    if (responseCode == 401) {
                                        if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                    }
                                    submitFlagBtn.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onError(final String message) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    terminalLogger.log("> FATAL ERROR: " + message);
                                    submitFlagBtn.setEnabled(true);
                                }
                            });
                        }
                    });

                } catch (Exception e) {
                    final String err = e.getMessage();
                    handler.post(new Runnable() { @Override public void run() { terminalLogger.log("> FATAL ERROR: " + err); submitFlagBtn.setEnabled(true); }});
                }
            }
        }).start();
    }

    // ==========================================
    // HONOR SCREEN
    // ==========================================
    private void setupHonorScreen(LinearLayout container) {
        honorLayout = new LinearLayout(this);
        honorLayout.setOrientation(LinearLayout.VERTICAL);
        honorLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        honorLayout.setBackgroundColor(Color.parseColor("#050505"));
        honorLayout.setPadding(
            dpToPx(HONOR_PADDING_H_DP), dpToPx(HONOR_PADDING_TOP_DP),
            dpToPx(HONOR_PADDING_H_DP), dpToPx(HONOR_PADDING_H_DP));
        honorLayout.setGravity(Gravity.CENTER);
        honorLayout.setVisibility(View.GONE);

        final ScrollView honorScroll = new ScrollView(this);
        honorScroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        honorScroll.setVerticalScrollBarEnabled(false);
        honorScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollContent.setGravity(Gravity.CENTER);
        scrollContent.setPadding(0, dpToPx(HONOR_SCROLL_PADDING_DP), 0, dpToPx(HONOR_SCROLL_PADDING_DP));

        honorTitleText = new TextView(this);
        honorTitleText.setText(getString(R.string.honor_title));
        honorTitleText.setTextColor(Color.parseColor("#FFD700"));
        honorTitleText.setTextSize(36f);
        honorTitleText.setTypeface(Typeface.SERIF, Typeface.BOLD);
        honorTitleText.setGravity(Gravity.CENTER);

        scrollContent.addView(honorTitleText);

        honorText = new TextView(this);
        honorText.setTextColor(Color.parseColor("#E0E0E0"));
        honorText.setTextSize(16f);
        honorText.setTypeface(Typeface.SERIF);
        honorText.setGravity(Gravity.CENTER);
        honorText.setPadding(0, dpToPx(HONOR_TEXT_PADDING_TOP_DP), 0, 0);
        honorText.setLineSpacing(1.5f, 1.5f);

        scrollContent.addView(honorText);
        honorScroll.addView(scrollContent);
        honorLayout.addView(honorScroll);
        container.addView(honorLayout);
    }

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
                                    } catch (Exception e) {}
                                } else {
                                    terminalLogger.log(getString(R.string.log_verification_failed));
                                    if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                    submitFlagBtn.setEnabled(true);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                terminalLogger.log("> ERROR: " + message);
                                submitFlagBtn.setEnabled(true);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void showHonorScreen(String message) {
        mainRootLayout.setVisibility(View.GONE);
        honorLayout.setVisibility(View.VISIBLE);
        honorText.setText(message);

        Animation bgAnim = new AlphaAnimation(0.0f, 1.0f);
        bgAnim.setDuration(5000);
        honorLayout.startAnimation(bgAnim);

        honorLayout.setAlpha(0.0f);
        honorLayout.animate().alpha(1.0f).setDuration(5000).start();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ScrollView scroll = (ScrollView) honorLayout.getChildAt(0);
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

                honorLayout.animate().alpha(0.0f).setDuration(5000).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaPlayer != null) {
                            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                            mediaPlayer.release();
                            mediaPlayer = null;
                        }
                        honorLayout.setVisibility(View.GONE);
                        honorLayout.setAlpha(1.0f);

                        terminalLogger.clear();

                        mainRootLayout.setVisibility(View.VISIBLE);
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
