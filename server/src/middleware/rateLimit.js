/**
 * Rate Limiting & Abuse Prevention Middleware
 * 
 * Enforces per-IP request thresholds and temporary ban escalation using Redis.
 */
const redis = require('../services/redis');

const BAN_SECONDS = 1800;

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
        const banKey = `banned:${ip}`;
        const bannedUntil = await redis.get(banKey);
        if (bannedUntil) {
            const remaining = Math.ceil(Number(bannedUntil) - Date.now() / 1000);
            return { allowed: false, retryAfter: remaining > 0 ? remaining : 60 };
        }

        const key = `ratelimit:${ip}`;
        const count = await redis.incr(key);
        if (count === 1) {
            await redis.expire(key, 60);
        }

        if (count > 30) {
            const until = Math.ceil(Date.now() / 1000) + BAN_SECONDS;
            await redis.set(banKey, until, { ex: BAN_SECONDS });
            return { allowed: false, retryAfter: BAN_SECONDS };
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
