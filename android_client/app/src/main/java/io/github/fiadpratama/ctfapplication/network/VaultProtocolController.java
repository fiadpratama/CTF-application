package io.github.fiadpratama.ctfapplication.network;

import android.os.Handler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

import io.github.fiadpratama.ctfapplication.crypto.CryptoHelper;

/**
 * Asynchronous challenge protocol orchestrator.
 *
 * Manages background worker execution for Phase 1 encrypted handshakes and
 * Phase 3 flag verification requests, marshaling structured callbacks back
 * onto the UI thread.
 */
public class VaultProtocolController {

    /**
     * Deferred provider resolving the symmetric cipher key inside the worker thread.
     */
    public interface KeyProvider {
        String getKey();
    }

    /**
     * Main-thread callback interface for Stage 1 handshake execution.
     */
    public interface Stage1Callback {
        void onTransmitting();
        void onResult(int responseCode, String displayMessage);
        void onRateLimited(int responseCode, String displayMessage, int retryAfterSeconds);
        void onError(String errorCode);
    }

    /**
     * Main-thread callback interface for Stage 3 flag verification.
     */
    public interface FlagVerifyCallback {
        void onVerified(String debriefMessage);
        void onRejected(int responseCode, String displayMessage);
        void onRateLimited(int responseCode, String displayMessage, int retryAfterSeconds);
        void onError(String errorCode);
    }

    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public VaultProtocolController(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    /**
     * Terminates active protocol worker execution when the host Activity is destroyed.
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Executes the Phase 1 cryptographic handshake sequence on a background worker thread.
     *
     * @param keyProvider Provider resolving the symmetric encryption key on the worker thread
     * @param callback Main-thread callback receiver
     */
    public void executeStage1Handshake(final KeyProvider keyProvider, final Stage1Callback callback) {
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String e2eKey = keyProvider.getKey();
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String hash = CryptoHelper.computeHandshakeSignature(timestamp);

                    JSONObject payloadJson = new JSONObject();
                    payloadJson.put("timestamp", timestamp);
                    payloadJson.put("hash", hash);

                    byte[] keyBytes = e2eKey.getBytes(StandardCharsets.UTF_8);
                    JSONObject encryptedPayload = CryptoHelper.encryptPayload(payloadJson, keyBytes);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onTransmitting();
                        }
                    });

                    VaultApiClient.postJson(VaultApiClient.STAGE1_URL, encryptedPayload, new VaultApiClient.ApiCallback() {
                        @Override
                        public void onResponse(final int responseCode, final String body) {
                            final String displayMessage = VaultApiClient.extractDisplayMessage(body);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (responseCode == 429) {
                                        int retrySec = VaultApiClient.extractRetryAfterSeconds(body, 300);
                                        callback.onRateLimited(responseCode, displayMessage, retrySec);
                                    } else {
                                        callback.onResult(responseCode, displayMessage);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(final String errorCode) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError(errorCode);
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("UNEXPECTED");
                        }
                    });
                }
            }
        });
    }

    /**
     * Submits a candidate flag to the verification endpoint on a background worker thread.
     *
     * @param flag Candidate flag string
     * @param callback Main-thread callback receiver
     */
    public void verifyFlag(final String flag, final FlagVerifyCallback callback) {
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                try {
                    json.put("flag", flag);
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("UNEXPECTED");
                        }
                    });
                    return;
                }

                VaultApiClient.postJson(VaultApiClient.VERIFY_FLAG_URL, json, new VaultApiClient.ApiCallback() {
                    @Override
                    public void onResponse(final int responseCode, final String body) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (responseCode == 200) {
                                    try {
                                        JSONObject resJson = new JSONObject(body);
                                        callback.onVerified(resJson.getString("message"));
                                    } catch (Exception e) {
                                        callback.onError("UNEXPECTED");
                                    }
                                } else {
                                    String displayMessage = VaultApiClient.extractDisplayMessage(body);
                                    if (responseCode == 429) {
                                        int retrySec = VaultApiClient.extractRetryAfterSeconds(body, 300);
                                        callback.onRateLimited(responseCode, displayMessage, retrySec);
                                    } else {
                                        callback.onRejected(responseCode, displayMessage);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(final String errorCode) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(errorCode);
                            }
                        });
                    }
                });
            }
        });
    }
}
