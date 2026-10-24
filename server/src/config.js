/**
 * Application Configuration & Environment Validation
 * 
 * Validates required secrets and operational parameters during startup.
 * Throws a critical error if any configuration constraints are violated.
 */
const E2EE_KEY_STRING = process.env.E2EE_KEY;
const BACKDOOR_CODE = process.env.BACKDOOR_CODE;
const MULTIPLIER_STRING = process.env.SECRET_MULTIPLIER || '';
const SECRET_MULTIPLIER = Number(MULTIPLIER_STRING);
const JWT_SECRET = process.env.JWT_SECRET;
const MIN_VERSION_CODE = 5;

// Startup integrity validation
const configErrors = [];
if (!E2EE_KEY_STRING || Buffer.byteLength(E2EE_KEY_STRING, 'utf8') !== 32) {
    configErrors.push('E2EE_KEY must be exactly 32 bytes');
}
if (!BACKDOOR_CODE) configErrors.push('BACKDOOR_CODE is required');
if (!/^\d+$/.test(MULTIPLIER_STRING) || SECRET_MULTIPLIER < 1 || !Number.isSafeInteger(SECRET_MULTIPLIER)) {
    configErrors.push('SECRET_MULTIPLIER must be a positive safe integer');
}
if (!JWT_SECRET || JWT_SECRET.length < 32) {
    configErrors.push('JWT_SECRET must be at least 32 characters');
}
if (configErrors.length > 0) {
    throw new Error(`[CRITICAL] Invalid configuration: ${configErrors.join('; ')}`);
}

const E2EE_KEY = Buffer.from(E2EE_KEY_STRING);

module.exports = {
    E2EE_KEY,
    BACKDOOR_CODE,
    SECRET_MULTIPLIER,
    JWT_SECRET,
    MIN_VERSION_CODE
};
