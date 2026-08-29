package io.github.fiadpratama.ctfapplication.crypto;

import android.util.Base64;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoHelper {

    private static final String HANDSHAKE_INTEGRITY_SEED = "V4ult_S3cr3t_S4lt_9921";

    public static String computeHandshakeSignature(String timestamp) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest((timestamp + HANDSHAKE_INTEGRITY_SEED).getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static JSONObject encryptPayload(JSONObject plainPayload, byte[] keyBytes) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

        byte[] encryptedData = cipher.doFinal(plainPayload.toString().getBytes("UTF-8"));
        int tagLength = 16;
        byte[] cipherText = new byte[encryptedData.length - tagLength];
        byte[] tag = new byte[tagLength];
        System.arraycopy(encryptedData, 0, cipherText, 0, cipherText.length);
        System.arraycopy(encryptedData, cipherText.length, tag, 0, tagLength);

        JSONObject encryptedPayload = new JSONObject();
        encryptedPayload.put("payload", Base64.encodeToString(cipherText, Base64.NO_WRAP));
        encryptedPayload.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        encryptedPayload.put("tag", Base64.encodeToString(tag, Base64.NO_WRAP));
        return encryptedPayload;
    }
}
