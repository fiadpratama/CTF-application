/**
 * Application environment configuration and startup validation.
 *
 * Enforces required environment variables, secret formatting constraints,
 * and minimum client compatibility thresholds on process startup.
 */
const E2EE_KEY_STRING = process.env.E2EE_KEY;
const BACKDOOR_CODE = process.env.BACKDOOR_CODE;
const MULTIPLIER_STRING = process.env.SECRET_MULTIPLIER || '';
const SECRET_MULTIPLIER = Number(MULTIPLIER_STRING);
const JWT_SECRET = process.env.JWT_SECRET;
const UPSTASH_REDIS_REST_URL = (process.env.UPSTASH_REDIS_REST_URL || '').trim();
const UPSTASH_REDIS_REST_TOKEN = (process.env.UPSTASH_REDIS_REST_TOKEN || '').trim();
const MIN_VERSION_CODE = 8;
const MAX_SAFE_SECRET_MULTIPLIER = Math.floor(Number.MAX_SAFE_INTEGER / 9999);

const configErrors = [];
if (!E2EE_KEY_STRING || Buffer.byteLength(E2EE_KEY_STRING, 'utf8') !== 32) {
    configErrors.push('E2EE_KEY must be exactly 32 bytes');
}
if (!BACKDOOR_CODE) configErrors.push('BACKDOOR_CODE is required');
if (
    !/^\d+$/.test(MULTIPLIER_STRING) ||
    SECRET_MULTIPLIER < 1 ||
    !Number.isSafeInteger(SECRET_MULTIPLIER) ||
    SECRET_MULTIPLIER > MAX_SAFE_SECRET_MULTIPLIER
) {
    configErrors.push(`SECRET_MULTIPLIER must be a positive safe integer (<= ${MAX_SAFE_SECRET_MULTIPLIER})`);
}
if (!JWT_SECRET || JWT_SECRET.length < 32) {
    configErrors.push('JWT_SECRET must be at least 32 characters');
}
if (!UPSTASH_REDIS_REST_URL) {
    configErrors.push('UPSTASH_REDIS_REST_URL is required');
}
if (!UPSTASH_REDIS_REST_TOKEN) {
    configErrors.push('UPSTASH_REDIS_REST_TOKEN is required');
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
