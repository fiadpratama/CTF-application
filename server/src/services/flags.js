/**
 * Cryptographic flag generation service.
 * 
 * Generates high-entropy candidate challenge flags in standard FLAG{...} format.
 */
const crypto = require('crypto');

/**
 * Creates a cryptographically random candidate flag string in FLAG{<32-hex>} format.
 * Total length is 38 characters.
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
