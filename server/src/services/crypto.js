/**
 * Cryptographic transport and session token service.
 * 
 * Implements AES-256-GCM authenticated payload decryption, constant-time
 * authorization token comparison, and HS256 JWT session signing/verification.
 */
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { E2EE_KEY, BACKDOOR_CODE, JWT_SECRET } = require('../config');

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

/**
 * Compares a candidate backdoor authorization token against the configured secret
 * using constant-time buffer evaluation.
 *
 * @param {string} candidateBackdoor - Candidate token string from decrypted payload
 * @returns {boolean} True if the candidate matches the configured backdoor code
 */
function verifyBackdoorCode(candidateBackdoor) {
    if (typeof candidateBackdoor !== 'string') {
        return false;
    }
    const candidateBuf = Buffer.from(candidateBackdoor, 'utf8');
    const expectedBuf = Buffer.from(BACKDOOR_CODE, 'utf8');
    return candidateBuf.length === expectedBuf.length && crypto.timingSafeEqual(candidateBuf, expectedBuf);
}

/**
 * Signs an ephemeral Stage 1 session JWT using HMAC-SHA256.
 *
 * @param {number} challenge - Randomly generated Stage 1 challenge integer
 * @param {string} jti - Unique 32-hex character session identifier
 * @returns {string} Signed JWT string valid for 10 minutes
 */
function signSessionToken(challenge, jti) {
    return jwt.sign(
        { challenge, jti },
        JWT_SECRET,
        { expiresIn: '10m', algorithm: 'HS256' }
    );
}

/**
 * Verifies an incoming Stage 2 session JWT signature and expiration window.
 *
 * @param {string} token - Raw JWT string from X-Session-Token header
 * @returns {object|null} Decoded JWT payload or null if signature/expiration is invalid
 */
function verifySessionToken(token) {
    try {
        return jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
    } catch {
        return null;
    }
}

module.exports = {
    decryptPayload,
    verifyBackdoorCode,
    signSessionToken,
    verifySessionToken
};
