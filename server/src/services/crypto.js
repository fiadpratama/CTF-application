/**
 * Cryptographic Helper Service
 * 
 * Provides end-to-end payload decryption using AES-256-GCM.
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
    try {
        const decipher = crypto.createDecipheriv('aes-256-gcm', E2EE_KEY, Buffer.from(ivBase64, 'base64'));
        decipher.setAuthTag(Buffer.from(authTagBase64, 'base64'));
        let decrypted = decipher.update(encryptedBase64, 'base64', 'utf8');
        decrypted += decipher.final('utf8');
        return JSON.parse(decrypted);
    } catch (decryptError) {
        return null;
    }
}

module.exports = {
    decryptPayload
};
