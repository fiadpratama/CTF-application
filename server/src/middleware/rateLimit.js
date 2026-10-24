/**
 * Rate Limiting & Abuse Prevention Middleware
 * 
 * Enforces per-IP request thresholds and temporary ban escalation using
 * a single atomic Redis Lua script.
 */
const redis = require('../services/redis');

const WINDOW_SECONDS = 60;
const MAX_REQUESTS = 30;
const BAN_SECONDS = 1800;

/**
 * Atomic Lua script for per-IP rate limiting and temporary ban escalation.
 * Prevents orphaned keys without TTL when serverless invocations are interrupted.
 *
 * KEYS: [1] ban record (banned:<ip>), [2] rate counter (ratelimit:<ip>)
 * ARGV: [1] current unix timestamp (s), [2] window TTL (s), [3] max requests, [4] ban duration (s)
 * Returns: { allowed (1|0), retryAfter (seconds) }
 */
const RATE_LIMIT_LUA = `
local now = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local maxRequests = tonumber(ARGV[3])
local banSeconds = tonumber(ARGV[4])

local bannedUntil = redis.call('GET', KEYS[1])
if bannedUntil then
    local remaining = tonumber(bannedUntil) - now
    if remaining and remaining > 0 then
        return {0, math.ceil(remaining)}
    end
    local banTtl = redis.call('TTL', KEYS[1])
    if banTtl and banTtl > 0 then
        return {0, banTtl}
    end
    return {0, 60}
end

local count = redis.call('INCR', KEYS[2])
if count == 1 or redis.call('TTL', KEYS[2]) < 0 then
    redis.call('EXPIRE', KEYS[2], windowSeconds)
end

if count > maxRequests then
    local untilTs = now + banSeconds
    redis.call('SET', KEYS[1], tostring(untilTs), 'EX', banSeconds)
    return {0, banSeconds}
end

return {1, 0}
`;

/**
 * Formats seconds into a human-readable duration string.
 * 
 * @param {number} seconds - Duration in seconds
 * @returns {string} Formatted string (e.g., "30 min" or "45 s")
 */
function formatRetry(seconds) {
    return seconds >= 60 ? `${Math.ceil(seconds / 60)} min` : `${seconds} s`;
}

/**
 * Evaluates whether the request IP has exceeded rate limit thresholds.
 * 
 * @param {string} ip - Client IP address
 * @returns {Promise<{allowed: boolean, retryAfter?: number}>} Rate check decision
 */
async function checkRateLimit(ip) {
    try {
        const clientIp = ip || 'unknown';
        const banKey = `banned:${clientIp}`;
        const rateKey = `ratelimit:${clientIp}`;
        const nowSeconds = Math.ceil(Date.now() / 1000);

        const result = await redis.eval(
            RATE_LIMIT_LUA,
            [banKey, rateKey],
            [String(nowSeconds), String(WINDOW_SECONDS), String(MAX_REQUESTS), String(BAN_SECONDS)]
        );

        if (Array.isArray(result) && Number(result[0]) === 0) {
            const retryAfter = Number(result[1]) > 0 ? Number(result[1]) : 60;
            return { allowed: false, retryAfter };
        }
        return { allowed: true };
    } catch (redisError) {
        console.error("[RATE_LIMIT_ERROR]", redisError.message);
        return { allowed: true };
    }
}

/**
 * Express middleware to enforce request rate limits.
 */
async function rateLimitMiddleware(req, res, next) {
    const result = await checkRateLimit(req.ip);
    if (!result.allowed) {
        res.set('Retry-After', String(result.retryAfter));
        return res.status(429).json({
            status: 429,
            error: "Too Many Requests",
            message: `Rate limit exceeded, retry in ${formatRetry(result.retryAfter)}`,
            retry_after: result.retryAfter
        });
    }
    next();
}

module.exports = rateLimitMiddleware;
