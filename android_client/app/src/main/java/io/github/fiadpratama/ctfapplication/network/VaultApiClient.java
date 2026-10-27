package io.github.fiadpratama.ctfapplication.network;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import io.github.fiadpratama.ctfapplication.BuildConfig;

/**
 * HTTP REST transport client.
 *
 * Handles HTTPS request dispatching, socket timeout configuration, client version headers,
 * and structured error parsing when communicating with the backend.
 */
public class VaultApiClient {

    private static final String BASE_URL = "https://server-umber-eta.vercel.app/api/vault";
    public static final String STAGE1_URL = BASE_URL + "/stage1";
    public static final String VERIFY_FLAG_URL = BASE_URL + "/verify-flag";

    /**
     * Callback interface for handling HTTP response payloads and transport errors.
     */
    public interface ApiCallback {
        void onResponse(int responseCode, String body);
        void onError(String message);
    }

    /**
     * Executes a synchronous (blocking) HTTP POST request carrying a JSON payload
     * and dispatches the result through {@link ApiCallback} on the calling thread.
     * Must be invoked from a background worker thread.
     *
     * @param urlStr Target endpoint URL
     * @param body JSON body to transmit
     * @param callback Result callback handler
     */
    public static void postJson(String urlStr, JSONObject body, ApiCallback callback) {
        HttpURLConnection conn = null;
        try {
            if (body == null) {
                body = new JSONObject();
            }
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-App-Version-Code", String.valueOf(BuildConfig.VERSION_CODE));
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            byte[] jsonBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream stream = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            if (callback != null) {
                callback.onResponse(responseCode, response.toString());
            }
        } catch (UnknownHostException | java.net.ConnectException | java.net.NoRouteToHostException e) {
            if (callback != null) {
                callback.onError("NO_INTERNET");
            }
        } catch (SocketTimeoutException e) {
            if (callback != null) {
                callback.onError("TIMEOUT");
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onError("UNEXPECTED");
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Extracts human-readable display messages from server JSON responses.
     *
     * @param rawJsonBody Raw HTTP response body
     * @return Extracted message string or fallback identifier
     */
    public static String extractDisplayMessage(String rawJsonBody) {
        if (rawJsonBody == null || rawJsonBody.trim().isEmpty()) {
            return "NO_RESPONSE";
        }
        try {
            JSONObject json = new JSONObject(rawJsonBody);
            if (json.has("message")) return json.getString("message");
            if (json.has("error")) return json.getString("error");
            return "UNRECOGNIZED_RESPONSE";
        } catch (Exception e) {
            return "PARSE_FAILED";
        }
    }

    /**
     * Extracts cooldown duration in seconds from 429 rate limit responses.
     *
     * @param rawJsonBody Raw HTTP response body
     * @param defaultSeconds Fallback cooldown duration if extraction fails
     * @return Extracted cooldown seconds, or defaultSeconds
     */
    public static int extractRetryAfterSeconds(String rawJsonBody, int defaultSeconds) {
        if (rawJsonBody == null || rawJsonBody.trim().isEmpty()) {
            return defaultSeconds;
        }
        try {
            JSONObject json = new JSONObject(rawJsonBody);
            if (json.has("retry_after")) {
                int sec = json.getInt("retry_after");
                if (sec > 0) return sec;
            }
            if (json.has("retryAfter")) {
                int sec = json.getInt("retryAfter");
                if (sec > 0) return sec;
            }
        } catch (Exception ignored) {
        }
        return defaultSeconds;
    }
}
