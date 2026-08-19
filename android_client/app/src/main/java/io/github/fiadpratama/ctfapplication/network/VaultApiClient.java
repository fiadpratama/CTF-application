package io.github.fiadpratama.ctfapplication.network;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

public class VaultApiClient {

    private static final String BASE_URL = "https://server-umber-eta.vercel.app/api/vault";
    public static final String STAGE1_URL = BASE_URL + "/stage1";
    public static final String VERIFY_FLAG_URL = BASE_URL + "/verify-flag";

    public interface ApiCallback {
        void onResponse(int responseCode, String body);
        void onError(String message);
    }

    public static void postJson(String urlStr, JSONObject body, ApiCallback callback) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();
            conn.disconnect();

            callback.onResponse(responseCode, response.toString());
        } catch (UnknownHostException e) {
            callback.onError("NO_INTERNET");
        } catch (SocketTimeoutException e) {
            callback.onError("TIMEOUT");
        } catch (Exception e) {
            callback.onError("UNEXPECTED");
        }
    }

    public static String extractDisplayMessage(String rawJsonBody) {
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
