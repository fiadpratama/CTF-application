package io.github.fiadpratama.ctfapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import io.github.fiadpratama.ctfapplication.databinding.ActivityMainBinding;
import io.github.fiadpratama.ctfapplication.network.AppRateLimiter;
import io.github.fiadpratama.ctfapplication.network.VaultProtocolController;
import io.github.fiadpratama.ctfapplication.security.SecurityChecks;
import io.github.fiadpratama.ctfapplication.ui.HonorScreenManager;
import io.github.fiadpratama.ctfapplication.ui.SoundEffectsManager;
import io.github.fiadpratama.ctfapplication.ui.SystemUiHelper;
import io.github.fiadpratama.ctfapplication.ui.TerminalLogger;
import io.github.fiadpratama.ctfapplication.ui.ToastHelper;

/**
 * Main application controller.
 *
 * Coordinates Activity lifecycle, JNI native symbol bindings, UI event listeners,
 * and delegation to specialized protocol, terminal, and debriefing controllers.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SoundEffectsManager soundEffects;
    private TerminalLogger terminalLogger;
    private HonorScreenManager honorScreenManager;
    private VaultProtocolController protocolController;
    private final ToastHelper toastHelper = new ToastHelper();
    private final AppRateLimiter appRateLimiter = new AppRateLimiter();
    private boolean isRequestRunning = false;

    static {
        System.loadLibrary("native-crypto");
    }

    /**
     * Retrieves the symmetric cipher key resolved by the native cryptographic engine.
     *
     * @return 256-bit symmetric encryption key string
     */
    public native String getE2EKey();

    /**
     * Retrieves the maintenance protocol token resolved by the native engine.
     *
     * @return Handshake authorization token string
     */
    public native String getBackdoor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        soundEffects = new SoundEffectsManager();
        protocolController = new VaultProtocolController(handler);

        SystemUiHelper.applyImmersiveMode(this);
        SystemUiHelper.attachMainLayoutInsets(this, binding.mainLayout, binding.terminalScrollView);

        terminalLogger = new TerminalLogger(binding.terminalLogText, binding.terminalScrollView, handler);
        honorScreenManager = new HonorScreenManager(this, binding, new Runnable() {
            @Override
            public void run() {
                soundEffects.playButtonBeep();
            }
        });

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
        if (honorScreenManager != null) {
            honorScreenManager.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SystemUiHelper.applyImmersiveMode(this);
        if (honorScreenManager != null) {
            honorScreenManager.onResume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SystemUiHelper.applyImmersiveMode(this);
        }
    }

    /**
     * Displays a debounced toast alert and plays an auditory alert tone if shown.
     *
     * @param message Text to display
     * @param isLongAlert true for standard error tone (500ms); false for short warning tone (200ms)
     */
    private void showThrottledAlert(String message, boolean isLongAlert) {
        if (!SystemUiHelper.isActivityAlive(this)) return;
        if (toastHelper.showThrottled(this, message) && soundEffects != null) {
            if (isLongAlert) {
                soundEffects.playErrorAlert();
            } else {
                soundEffects.playShortAlert();
            }
        }
    }

    /**
     * Toggles interactive state of action buttons to prevent concurrent request spam.
     *
     * @param isEnabled true to enable user interaction; false to lock
     */
    private void setButtonsState(boolean isEnabled) {
        if (!SystemUiHelper.isActivityAlive(this)) return;
        binding.unlockButton.setEnabled(isEnabled);
        binding.submitFlagBtn.setEnabled(isEnabled);
    }

    /**
     * Evaluates client-side rate limits prior to dispatching an outbound network request.
     *
     * @return true if request is permitted; false if throttled or under active cooldown
     */
    private boolean checkClientRateLimit() {
        if (appRateLimiter.isBanned()) {
            String dur = ToastHelper.formatDuration(appRateLimiter.getRemainingBanSeconds());
            showThrottledAlert(String.format(getString(R.string.toast_rate_limited), dur), false);
            return false;
        }
        if (isRequestRunning) {
            return false;
        }
        if (!appRateLimiter.recordAndCheck()) {
            String dur = ToastHelper.formatDuration(appRateLimiter.getRemainingBanSeconds());
            showThrottledAlert(String.format(getString(R.string.toast_rate_limited), dur), true);
            terminalLogger.log(getString(R.string.log_rate_limited));
            return false;
        }
        return true;
    }

    /**
     * Initializes click and touch listeners for interactive views.
     */
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
                SystemUiHelper.hideKeyboard(MainActivity.this);
                if (!checkClientRateLimit()) {
                    return;
                }

                isRequestRunning = true;
                setButtonsState(false);
                soundEffects.playButtonBeep();
                terminalLogger.log(getString(R.string.log_handshake_start));
                unlockVault();
            }
        });

        binding.submitFlagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SystemUiHelper.hideKeyboard(MainActivity.this);
                if (appRateLimiter.isBanned()) {
                    String dur = ToastHelper.formatDuration(appRateLimiter.getRemainingBanSeconds());
                    showThrottledAlert(String.format(getString(R.string.toast_rate_limited), dur), false);
                    return;
                }
                if (isRequestRunning) {
                    return;
                }

                String flag = binding.flagInput.getText().toString().trim();
                if (flag.isEmpty()) {
                    showThrottledAlert(getString(R.string.toast_empty_flag), false);
                    return;
                }

                if (!checkClientRateLimit()) {
                    return;
                }

                isRequestRunning = true;
                setButtonsState(false);
                soundEffects.playButtonBeep();
                terminalLogger.log(getString(R.string.log_flag_submitting));
                verifyFlag(flag);
            }
        });
    }

    /**
     * Dispatches the Phase 1 cryptographic handshake sequence via {@link VaultProtocolController}.
     */
    private void unlockVault() {
        protocolController.executeStage1Handshake(getE2EKey(), new VaultProtocolController.Stage1Callback() {
            @Override
            public void onTransmitting() {
                if (SystemUiHelper.isActivityAlive(MainActivity.this)) {
                    terminalLogger.log(getString(R.string.log_handshake_transmit));
                }
            }

            @Override
            public void onResult(final int responseCode, String displayMessage) {
                if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
                    @Override
                    public void onComplete() {
                        if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                        if (responseCode != 200) {
                            soundEffects.playErrorAlert();
                        }
                        isRequestRunning = false;
                        setButtonsState(true);
                    }
                });
            }

            @Override
            public void onRateLimited(int responseCode, String displayMessage, final int retryAfterSeconds) {
                handleRateLimitedResponse(responseCode, displayMessage, retryAfterSeconds);
            }

            @Override
            public void onError(String errorCode) {
                handleTransportError(errorCode);
            }
        });
    }

    /**
     * Dispatches candidate flag verification via {@link VaultProtocolController}.
     *
     * @param flag Submitted challenge flag
     */
    private void verifyFlag(String flag) {
        protocolController.verifyFlag(flag, new VaultProtocolController.FlagVerifyCallback() {
            @Override
            public void onVerified(String debriefMessage) {
                if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                isRequestRunning = false;
                honorScreenManager.start(debriefMessage, new HonorScreenManager.OnCinematicFinishedListener() {
                    @Override
                    public void onFinished() {
                        terminalLogger.clear();
                        setButtonsState(true);
                        terminalLogger.log(getString(R.string.log_system_rebooted));
                    }
                });
            }

            @Override
            public void onRejected(int responseCode, String displayMessage) {
                if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
                    @Override
                    public void onComplete() {
                        if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                        soundEffects.playErrorAlert();
                        isRequestRunning = false;
                        setButtonsState(true);
                    }
                });
            }

            @Override
            public void onRateLimited(int responseCode, String displayMessage, int retryAfterSeconds) {
                handleRateLimitedResponse(responseCode, displayMessage, retryAfterSeconds);
            }

            @Override
            public void onError(String errorCode) {
                handleTransportError(errorCode);
            }
        });
    }

    /**
     * Handles HTTP 429 rate-limited responses uniformly across stages.
     */
    private void handleRateLimitedResponse(int responseCode, String displayMessage, final int retryAfterSeconds) {
        if (!SystemUiHelper.isActivityAlive(this)) return;
        terminalLogger.log("> [" + responseCode + "] " + displayMessage, new TerminalLogger.OnCompleteListener() {
            @Override
            public void onComplete() {
                if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                appRateLimiter.applyServerBan(retryAfterSeconds);
                String formattedDur = ToastHelper.formatDuration(retryAfterSeconds);
                showThrottledAlert(String.format(getString(R.string.toast_rate_limited), formattedDur), true);
                isRequestRunning = false;
                setButtonsState(true);
            }
        });
    }

    /**
     * Handles network transport or unexpected protocol exceptions uniformly.
     */
    private void handleTransportError(final String errorCode) {
        if (!SystemUiHelper.isActivityAlive(this)) return;
        terminalLogger.log(translateErrorCode(errorCode), new TerminalLogger.OnCompleteListener() {
            @Override
            public void onComplete() {
                if (!SystemUiHelper.isActivityAlive(MainActivity.this)) return;
                if (!"UNEXPECTED".equals(errorCode)) {
                    soundEffects.playErrorAlert();
                }
                isRequestRunning = false;
                setButtonsState(true);
            }
        });
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (honorScreenManager != null) {
            honorScreenManager.onDestroy();
        }
        if (toastHelper != null) {
            toastHelper.dismiss();
        }
        if (soundEffects != null) {
            soundEffects.release();
            soundEffects = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
