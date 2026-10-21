/**
 * Flag Generation Service
 * 
 * Generates cryptographically secure candidate solve flags in FLAG{...} format.
 */
const crypto = require('crypto');

/**
 * Creates a cryptographically random candidate flag string in FLAG{...} format.
 * Format: 38 characters total ('FLAG{' + 32 hex chars + '}').
 * 
 * @returns {string} 38-character flag string
 */
function createFlagString() {
    const raw = crypto.randomBytes(16).toString('hex');
    return `FLAG{${raw}}`;
}

module.exports = {
    createFlagString
};
