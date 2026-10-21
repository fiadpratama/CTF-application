/**
 * Flag Generation Service
 * 
 * Generates cryptographically secure single-use solve flags with ephemeral TTL.
 */
const crypto = require('crypto');
const redis = require('./redis');

/**
 * Creates a cryptographically random candidate flag string in FLAG{...} format.
 * 
 * @returns {string} 38-character flag string
 */
function createFlagString() {
    const raw = crypto.randomBytes(16).toString('hex');
    return `FLAG{${raw}}`;
}

/**
 * Generates an ephemeral challenge completion flag and registers it in Redis.
 * 
 * @returns {Promise<string>} Generated flag string in FLAG{...} format
 */
function generateSolveFlag() {
    const flag = createFlagString();
    return redis.set(`flag:${flag}`, "unused", { ex: 900 }).then(() => flag);
}

module.exports = {
    createFlagString,
    generateSolveFlag
};
