/**
 * Flag Generation Service
 * 
 * Generates cryptographically secure single-use solve flags with ephemeral TTL.
 */
const crypto = require('crypto');
const redis = require('./redis');

/**
 * Generates an ephemeral challenge completion flag and registers it in Redis.
 * 
 * @returns {Promise<string>} Generated flag string in FLAG{...} format
 */
function generateSolveFlag() {
    const raw = crypto.randomBytes(16).toString('hex');
    const flag = `FLAG{${raw}}`;
    return redis.set(`flag:${flag}`, "unused", { ex: 900 }).then(() => flag);
}

module.exports = {
    generateSolveFlag
};
