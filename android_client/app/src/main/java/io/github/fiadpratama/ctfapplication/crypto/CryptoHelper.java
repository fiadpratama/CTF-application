package io.github.fiadpratama.ctfapplication.crypto;

import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic helper for secure transport operations.
 *
 * Encapsulates symmetric payload encryption (AES-256-GCM) with authenticated tags
 * and cryptographic hashing (SHA-256) used during challenge handshakes.
 */
public class CryptoHelper {

    private static final String HANDSHAKE_INTEGRITY_SEED = "V4ult_S3cr3t_S4lt_9921";

    /**
     * Computes a SHA-256 integrity signature for the initial handshake timestamp.
     *
     * @param timestamp Client timestamp string
     * @return Hex-encoded SHA-256 digest
     * @throws Exception if hashing algorithm or encoding fails
     */
    public static String computeHandshakeSignature(String timestamp) throws Exception {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest((timestamp + HANDSHAKE_INTEGRITY_SEED).getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Encrypts a JSON payload using AES-256-GCM with a randomized 12-byte IV.
     *
     * @param plainPayload Raw JSON payload to encrypt
     * @param keyBytes 256-bit (32-byte) symmetric encryption key
     * @return Encrypted JSON containing Base64-encoded payload, iv, and authentication tag
     * @throws Exception if encryption fails
     */
    public static JSONObject encryptPayload(JSONObject plainPayload, byte[] keyBytes) throws Exception {
        if (plainPayload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("Key must be a non-null 32-byte array (AES-256)");
        }

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

        byte[] encryptedData = cipher.doFinal(plainPayload.toString().getBytes(StandardCharsets.UTF_8));
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
