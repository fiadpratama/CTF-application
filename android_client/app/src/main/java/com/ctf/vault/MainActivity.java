package com.ctf.vault;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
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
import android.widget.Toast;
import android.net.Uri;
import android.media.MediaPlayer;
import androidx.appcompat.app.AppCompatActivity;
import android.provider.Settings;
import android.content.Intent;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.content.res.AssetFileDescriptor;

import android.animation.ObjectAnimator;
import android.view.animation.LinearInterpolator;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final String API_URL = "https://server-umber-eta.vercel.app/api/vault/stage1";

    // --- UI Components ---
    private TextView terminalLog;
    private ScrollView scrollView;
    private LinearLayout mainRootLayout;
    private LinearLayout bootLayout;
    private TextView bootText;
    private LinearLayout honorLayout;
    private TextView honorText;
    private TextView titleText;
    private MediaPlayer mediaPlayer;
    private EditText flagInput;
    private Button submitFlagBtn;

    // --- State & Utilities ---
    private Handler handler = new Handler(Looper.getMainLooper());
    private Queue<Character> textQueue = new LinkedList<>();
    private boolean isTyping = false;
    private Random random = new Random();
    private ToneGenerator toneGen;

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
        startBootSequence();
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
        bootLayout.setPadding(20, 40, 20, 20);

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
        mainRootLayout.setPadding(60, 100, 60, 60);
        mainRootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mainRootLayout.setVisibility(View.GONE);

        TextView titleText = new TextView(this);
        titleText.setText("CTF APPLICATION");
        titleText.setTextColor(Color.parseColor("#00FF41"));
        titleText.setTextSize(32f);
        titleText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setLetterSpacing(0.2f);
        
        Animation anim = new AlphaAnimation(0.3f, 1.0f);
        anim.setDuration(1500);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        titleText.startAnimation(anim);
        mainRootLayout.addView(titleText);

        TextView subText = new TextView(this);
        subText.setText("SECURE MILITARY-GRADE ENCRYPTION\nPROCEED WITH CAUTION");
        subText.setTextColor(Color.parseColor("#008F11"));
        subText.setTextSize(12f);
        subText.setTypeface(Typeface.MONOSPACE);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(0, 20, 0, 80);
        mainRootLayout.addView(subText);

        Button unlockButton = new Button(this);
        unlockButton.setText("INITIATE DECRYPTION");
        unlockButton.setTextColor(Color.parseColor("#0a0a0a"));
        unlockButton.setTextSize(16f);
        unlockButton.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        
        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setShape(GradientDrawable.RECTANGLE);
        btnShape.setCornerRadius(10f);
        btnShape.setColor(Color.parseColor("#00FF41"));
        unlockButton.setBackground(btnShape);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        btnParams.setMargins(0, 0, 0, 80);
        unlockButton.setLayoutParams(btnParams);

        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                logToTerminal("> INITIATING SECURE CONNECTION...");
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
        scrollView.setPadding(30, 30, 30, 30);

        terminalLog = new TextView(this);
        terminalLog.setTextColor(Color.parseColor("#00FF41"));
        terminalLog.setTextSize(12f);
        terminalLog.setTypeface(Typeface.MONOSPACE);
        scrollView.addView(terminalLog);

        mainRootLayout.addView(scrollView);

        flagInput = new EditText(this);
        flagInput.setHint("ENTER FINAL FLAG HERE...");
        flagInput.setTextColor(Color.parseColor("#00FF41"));
        flagInput.setHintTextColor(Color.parseColor("#008F11"));
        flagInput.setTextSize(14f);
        flagInput.setTypeface(Typeface.MONOSPACE);
        
        GradientDrawable inputShape = new GradientDrawable();
        inputShape.setShape(GradientDrawable.RECTANGLE);
        inputShape.setStroke(2, Color.parseColor("#00FF41"));
        inputShape.setCornerRadius(10f);
        flagInput.setBackground(inputShape);
        flagInput.setPadding(20, 20, 20, 20);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, 40, 0, 20);
        flagInput.setLayoutParams(inputParams);
        
        mainRootLayout.addView(flagInput);

        submitFlagBtn = new Button(this);
        submitFlagBtn.setText("SUBMIT FLAG");
        submitFlagBtn.setTextColor(Color.parseColor("#0a0a0a"));
        submitFlagBtn.setTextSize(16f);
        submitFlagBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        
        GradientDrawable btnShape2 = new GradientDrawable();
        btnShape2.setShape(GradientDrawable.RECTANGLE);
        btnShape2.setCornerRadius(10f);
        btnShape2.setColor(Color.parseColor("#00FF41"));
        submitFlagBtn.setBackground(btnShape2);
        
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        submitFlagBtn.setLayoutParams(submitParams);
        
        submitFlagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                String flag = flagInput.getText().toString().trim();
                if(flag.isEmpty()) return;
                submitFlagBtn.setEnabled(false);
                logToTerminal("> VERIFYING FLAG WITH SERVER...");
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
        if(toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 300);

        java.util.List<String> bootLogs = new java.util.ArrayList<>();
        bootLogs.add("Initializing kernel... OK");
        bootLogs.add("Mounting /dev/block/bootdevice... OK");
        bootLogs.add("Checking Virtual Machine environment... " + (isEmulator() ? "EMULATOR DETECTED [BYPASSED]" : "CLEAN"));
        bootLogs.add("Checking Root privileges... " + (isRooted() ? "ROOTED DEVICE DETECTED [BYPASSED]" : "CLEAN"));
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
                
                final String baseText = "Booting Application UI";
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
                        logToTerminal("> SYSTEM IDLE\n> WAITING FOR COMMAND...");
                    }
                });
            }
        }).start();
    }

    // ==========================================
    // CORE LOGIC
    // ==========================================
    private void logToTerminal(final String message) {
        String fullMessage = message + "\n";
        for (char c : fullMessage.toCharArray()) {
            textQueue.add(c);
        }
        if (!isTyping) {
            typeNextCharacter();
        }
    }

    private void typeNextCharacter() {
        if (textQueue.isEmpty()) {
            isTyping = false;
            return;
        }
        isTyping = true;
        char c = textQueue.poll();
        terminalLog.append(String.valueOf(c));
        
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });

        int randomDelay;
        if (random.nextFloat() > 0.95f) {
            randomDelay = random.nextInt(50) + 20;
        } else {
            randomDelay = random.nextInt(10) + 2;
        }
        
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                typeNextCharacter();
            }
        }, randomDelay);
    }

    private void simulateHexDump() {
        for(int i = 0; i < 6; i++) {
            StringBuilder hexLine = new StringBuilder("[0x" + Integer.toHexString(0x7F000 + random.nextInt(0x1000)).toUpperCase() + "] : ");
            for(int j = 0; j < 8; j++) {
                hexLine.append(Integer.toHexString(random.nextInt(256)).toUpperCase() + " ");
            }
            hexLine.append("... (DECODING)");
            logToTerminal(hexLine.toString());
        }
    }

    private void unlockVault() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> GENERATING TIME-SYNC TOKEN..."); }});
                    Thread.sleep(700);

                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hashBytes = digest.digest((timestamp + "V4ult_S3cr3t_S4lt_9921").getBytes("UTF-8"));
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hashBytes) {
                        String hex = Integer.toHexString(0xff & b);
                        if(hex.length() == 1) hexString.append('0');
                        hexString.append(hex);
                    }
                    JSONObject payloadJson = new JSONObject();
                    payloadJson.put("timestamp", timestamp);
                    payloadJson.put("hash", hexString.toString());
                    
                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> LOAD JNI NATIVE LIBRARY [libnative-crypto.so]..."); }});
                    Thread.sleep(800);
                    
                    handler.post(new Runnable() { @Override public void run() { 
                        logToTerminal("> EXTRACTING AES-GCM KEY FROM NATIVE ENCLAVE..."); 
                        simulateHexDump();
                    }});
                    Thread.sleep(2000);

                    byte[] keyBytes = getE2EKey().getBytes("UTF-8");
                    
                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> ENCRYPTING PAYLOAD (AES-256-GCM)..."); }});
                    Thread.sleep(800);

                    byte[] iv = new byte[12];
                    new SecureRandom().nextBytes(iv);
                    
                    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                    
                    byte[] encryptedData = cipher.doFinal(payloadJson.toString().getBytes("UTF-8"));
                    JSONObject encryptedPayload = new JSONObject();
                    int tagLength = 16;
                    byte[] cipherText = new byte[encryptedData.length - tagLength];
                    byte[] tag = new byte[tagLength];
                    System.arraycopy(encryptedData, 0, cipherText, 0, cipherText.length);
                    System.arraycopy(encryptedData, cipherText.length, tag, 0, tagLength);
                    
                    encryptedPayload.put("payload", Base64.encodeToString(cipherText, Base64.NO_WRAP));
                    encryptedPayload.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
                    encryptedPayload.put("tag", Base64.encodeToString(tag, Base64.NO_WRAP));

                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> TRANSMITTING TO VAULT SERVER..."); }});
                    Thread.sleep(800);

                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    os.write(encryptedPayload.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    final int responseCode = conn.getResponseCode();
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    final String serverResponse = response.toString();
                    
                    handler.post(new Runnable() { 
                        @Override 
                        public void run() { 
                            logToTerminal("> SERVER RESPONSE [" + responseCode + "]:\n> " + serverResponse); 
                            if (responseCode == 401) {
                                if(toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                            }
                            submitFlagBtn.setEnabled(true); 
                        }
                    });

                } catch (Exception e) {
                    final String err = e.getMessage();
                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> FATAL ERROR: " + err); submitFlagBtn.setEnabled(true); }});
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
        honorLayout.setPadding(40, 80, 40, 40);
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
        scrollContent.setPadding(0, 800, 0, 800);

        titleText = new TextView(this);
        titleText.setText("HALL OF FAME");
        titleText.setTextColor(Color.parseColor("#FFD700"));
        titleText.setTextSize(36f);
        titleText.setTypeface(Typeface.SERIF, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        
        scrollContent.addView(titleText);

        honorText = new TextView(this);
        honorText.setTextColor(Color.parseColor("#E0E0E0"));
        honorText.setTextSize(16f);
        honorText.setTypeface(Typeface.SERIF);
        honorText.setGravity(Gravity.CENTER);
        honorText.setPadding(0, 60, 0, 0);
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
                try {
                    URL url = new URL("https://server-umber-eta.vercel.app/api/vault/verify-flag");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    JSONObject json = new JSONObject();
                    json.put("flag", flag);

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    final int responseCode = conn.getResponseCode();
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    final String serverResponse = response.toString();
                    
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (responseCode == 200) {
                                try {
                                    JSONObject resJson = new JSONObject(serverResponse);
                                    showHonorScreen(resJson.getString("message"));
                                } catch(Exception e){}
                            } else {
                                logToTerminal("> VERIFICATION FAILED: INVALID FLAG.");
                                if(toneGen != null) toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500);
                                submitFlagBtn.setEnabled(true);
                            }
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    handler.post(new Runnable() { @Override public void run() { logToTerminal("> ERROR: " + err); submitFlagBtn.setEnabled(true); }});
                }
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
                        
                        textQueue.clear();
                        isTyping = false;
                        terminalLog.setText("");
                        
                        mainRootLayout.setVisibility(View.VISIBLE);
                        logToTerminal("> SYSTEM REBOOTED.\n> WAITING FOR COMMAND...");
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

    // ==========================================
    // SECURITY CHECKS
    // ==========================================
    private boolean isEmulator() {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
            || android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.HARDWARE.contains("goldfish")
            || android.os.Build.HARDWARE.contains("ranchu")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.MANUFACTURER.contains("Genymotion")
            || android.os.Build.PRODUCT.contains("sdk_google")
            || android.os.Build.PRODUCT.contains("google_sdk")
            || android.os.Build.PRODUCT.contains("sdk")
            || android.os.Build.PRODUCT.contains("sdk_x86")
            || android.os.Build.PRODUCT.contains("vbox86p")
            || android.os.Build.PRODUCT.contains("emulator")
            || android.os.Build.PRODUCT.contains("simulator");
    }

    private boolean isRooted() {
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su" };
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }
}
