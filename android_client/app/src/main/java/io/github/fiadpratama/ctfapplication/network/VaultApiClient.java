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
 * Vault REST API Client
 *
 * Handles HTTPS transport, request timeout management, and client version attribution
 * when communicating with the remote Vault serverless backend.
 */
public class VaultApiClient {

    private static final String BASE_URL = "https://server-umber-eta.vercel.app/api/vault";
    public static final String STAGE1_URL = BASE_URL + "/stage1";
    public static final String VERIFY_FLAG_URL = BASE_URL + "/verify-flag";

    /**
     * Asynchronous callback interface for HTTP responses.
     */
    public interface ApiCallback {
        void onResponse(int responseCode, String body);
        void onError(String message);
    }

    /**
     * Dispatches an asynchronous HTTP POST request with JSON payload.
     *
     * @param urlStr Target endpoint URL
     * @param body JSON body to transmit
     * @param callback Result callback handler
     */
    public static void postJson(String urlStr, JSONObject body, ApiCallback callback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-App-Version-Code", String.valueOf(BuildConfig.VERSION_CODE));
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

            callback.onResponse(responseCode, response.toString());
        } catch (UnknownHostException e) {
            callback.onError("NO_INTERNET");
        } catch (SocketTimeoutException e) {
            callback.onError("TIMEOUT");
        } catch (Exception e) {
            callback.onError("UNEXPECTED");
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
}
