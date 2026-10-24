/**
 * Rate Limiting & Abuse Prevention Middleware
 * 
 * Enforces per-IP request thresholds, anomaly burst detection, and progressive
 * ban escalation (30 min -> 1 hour -> 3 hours) using an atomic Redis Lua script.
 */
const redis = require('../services/redis');

const WINDOW_SECONDS = 60;
const MAX_REQUESTS = 30;
const BURST_WINDOW_SECONDS = 10;
const MAX_BURST_REQUESTS = 10;
const VIOLATION_TTL_SECONDS = 86400; // 24 hours retention for repeat offenders

/**
 * Atomic Lua script for per-IP rate limiting, anomaly burst detection, and progressive ban escalation.
 *
 * KEYS: [1] ban record (banned:<ip>), [2] rate counter (ratelimit:<ip>),
 *       [3] violation counter (violations:<ip>), [4] burst counter (burst:<ip>)
 * ARGV: [1] current unix timestamp (s), [2] window TTL (s), [3] max requests,
 *       [4] burst window TTL (s), [5] max burst requests, [6] violation retention TTL (s)
 * Returns: { allowed (1|0), retryAfter (seconds) }
 */
const RATE_LIMIT_LUA = `
local now = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local maxRequests = tonumber(ARGV[3])
local burstWindowSeconds = tonumber(ARGV[4])
local maxBurstRequests = tonumber(ARGV[5])
local violationTtlSeconds = tonumber(ARGV[6])

-- Evaluate existing active ban
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

-- Track short-window burst rate for rapid spam detection
local burstCount = redis.call('INCR', KEYS[4])
if burstCount == 1 or redis.call('TTL', KEYS[4]) < 0 then
    redis.call('EXPIRE', KEYS[4], burstWindowSeconds)
end

-- Track standard sliding window request count
local count = redis.call('INCR', KEYS[2])
if count == 1 or redis.call('TTL', KEYS[2]) < 0 then
    redis.call('EXPIRE', KEYS[2], windowSeconds)
end

-- Enforce escalating ban upon quota breach or burst anomaly
local isViolation = false
if burstCount > maxBurstRequests or count > maxRequests then
    isViolation = true
end

if isViolation then
    local violations = redis.call('INCR', KEYS[3])
    redis.call('EXPIRE', KEYS[3], violationTtlSeconds)

    -- Tier 1: 30 min (1800s), Tier 2: 1 hour (3600s), Tier 3: 3 hours (10800s)
    local banSeconds = 1800
    if violations == 2 then
        banSeconds = 3600
    elseif violations >= 3 then
        banSeconds = 10800
    end

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
 * @returns {string} Formatted string (e.g., "3 hours", "1 hour", "30 min", or "45 s")
 */
function formatRetry(seconds) {
    if (seconds >= 3600) {
        const hours = Math.ceil(seconds / 3600);
        return hours === 1 ? '1 hour' : `${hours} hours`;
    }
    if (seconds >= 60) {
        return `${Math.ceil(seconds / 60)} min`;
    }
    return `${seconds} s`;
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
        const violationKey = `violations:${clientIp}`;
        const burstKey = `burst:${clientIp}`;
        const nowSeconds = Math.ceil(Date.now() / 1000);

        const result = await redis.eval(
            RATE_LIMIT_LUA,
            [banKey, rateKey, violationKey, burstKey],
            [
                String(nowSeconds),
                String(WINDOW_SECONDS),
                String(MAX_REQUESTS),
                String(BURST_WINDOW_SECONDS),
                String(MAX_BURST_REQUESTS),
                String(VIOLATION_TTL_SECONDS)
            ]
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
