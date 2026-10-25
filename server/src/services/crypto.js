/**
 * Cryptographic transport helper service.
 * 
 * Implements AES-256-GCM authenticated payload decryption with strict validation
 * of initialization vectors and authentication tags.
 */
const crypto = require('crypto');
const { E2EE_KEY } = require('../config');

/**
 * Decrypts an encrypted payload using AES-256-GCM.
 * 
 * @param {string} encryptedBase64 - Base64-encoded encrypted payload
 * @param {string} ivBase64 - Base64-encoded initialization vector (12 bytes)
 * @param {string} authTagBase64 - Base64-encoded authentication tag (16 bytes)
 * @returns {object|null} Decrypted JSON object or null if decryption fails
 */
function decryptPayload(encryptedBase64, ivBase64, authTagBase64) {
    if (typeof encryptedBase64 !== 'string' || typeof ivBase64 !== 'string' || typeof authTagBase64 !== 'string') {
        return null;
    }
    try {
        const iv = Buffer.from(ivBase64, 'base64');
        const authTag = Buffer.from(authTagBase64, 'base64');
        if (iv.length !== 12 || authTag.length !== 16) {
            return null;
        }
        const decipher = crypto.createDecipheriv('aes-256-gcm', E2EE_KEY, iv);
        decipher.setAuthTag(authTag);
        let decrypted = decipher.update(encryptedBase64, 'base64', 'utf8');
        decrypted += decipher.final('utf8');
        const parsed = JSON.parse(decrypted);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            return null;
        }
        return parsed;
    } catch {
        return null;
    }
}

module.exports = {
    decryptPayload
};
