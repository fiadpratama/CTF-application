/**
 * Distributed vault state and atomic transaction service.
 * 
 * Coordinates flag issuance, session binding, and atomic solve redemption (single
 * solve-counter increment with a 5-minute idempotent retry grace window) using
 * Redis Lua scripts to eliminate race conditions and duplicate solve counts.
 */
const redis = require('./redis');
const { createFlagString } = require('./flags');

/**
 * Atomic Lua script for Stage 2 flag issuance with rollback protection.
 * KEYS: [1] session token (jti:<id>), [2] flag record (flag:<str>)
 * ARGV: [1] candidate flag, [2] session TTL (seconds), [3] flag TTL (seconds)
 * Returns: candidateFlag on success, existing flag if idempotent replay, or negative error code
 */
const ISSUE_FLAG_LUA = `
local jtiTtl = tonumber(ARGV[2])
local flagTtl = tonumber(ARGV[3])
if not jtiTtl or jtiTtl <= 0 or not flagTtl or flagTtl <= 0 then
    return -4
end

local candidateFlag = ARGV[1]
if not candidateFlag or #candidateFlag ~= 38 or not string.match(candidateFlag, '^FLAG%{[0-9a-f]+%}$') then
    return -4
end

local existing = redis.call('GET', KEYS[1])
if existing then
    if #existing == 38 and string.match(existing, '^FLAG%{[0-9a-f]+%}$') then
        return existing
    else
        return -2
    end
end

if redis.call('EXISTS', KEYS[2]) == 1 then
    return -6
end

local setFlagOk = redis.call('SET', KEYS[2], 'unused', 'NX', 'EX', flagTtl)
if not setFlagOk then
    return -6
end

local ok, setJtiResult = pcall(redis.call, 'SET', KEYS[1], candidateFlag, 'EX', jtiTtl)
if not ok or not setJtiResult then
    pcall(redis.call, 'DEL', KEYS[2])
    return -5
end

return candidateFlag
`;

/**
 * Atomic Lua script to redeem a solve flag (single solve-counter increment from 'unused'
 * to 'claimed:<solveNum>' with a 300-second idempotent retry grace window) and write rollback.
 * KEYS: [1] flag record (flag:<str>), [2] global solves counter (stats:solves)
 * Returns: solveNumber on initial claim, existing solveNumber on idempotent retry, or negative error code
 */
const CLAIM_FLAG_LUA = `
local status = redis.call('GET', KEYS[1])
if not status then
    return -1
end
if status == 'unused' then
    local solveNum = redis.call('INCR', KEYS[2])
    local ok, setOk = pcall(redis.call, 'SET', KEYS[1], 'claimed:' .. tostring(solveNum), 'EX', 300)
    if not ok or not setOk then
        pcall(redis.call, 'DECR', KEYS[2])
        return -2
    end
    return solveNum
end
if string.match(status, '^claimed:[1-9]%d*$') then
    local num = tonumber(string.sub(status, 9))
    if num and num >= 1 and num <= 9007199254740991 and num == math.floor(num) then
        return num
    end
end
return -2
`;

/**
 * Validates whether a flag status represents a valid lifecycle state.
 * Accepts 'unused' or 'claimed:<num>' where <num> is a positive safe integer.
 *
 * @param {*} status Raw status value from Redis
 * @returns {boolean} True if status is valid
 */
function isValidFlagStatus(status) {
    if (status === 'unused') return true;
    if (typeof status !== 'string') return false;
    const match = /^claimed:([1-9]\d*)$/.exec(status);
    if (!match) return false;
    return Number.isSafeInteger(Number(match[1]));
}

/**
 * Issues an ephemeral challenge flag bound to a verified session token atomically.
 *
 * @param {string} jti - Unique 32-hex character session identifier
 * @returns {Promise<{ status: number, flag?: string, error?: string, message?: string }>}
 */
async function issueSessionFlag(jti) {
    const usedKey = `jti:${jti}`;

    let candidateFlag = createFlagString();
    let flagKey = `flag:${candidateFlag}`;
    let result = await redis.eval(ISSUE_FLAG_LUA, [usedKey, flagKey], [candidateFlag, '660', '900']);

    // Retry once if candidate flag randomly collided with existing key
    if (result === -6) {
        candidateFlag = createFlagString();
        flagKey = `flag:${candidateFlag}`;
        result = await redis.eval(ISSUE_FLAG_LUA, [usedKey, flagKey], [candidateFlag, '660', '900']);
    }

    if (typeof result === 'string' && result.length === 38 && /^FLAG\{[0-9a-f]{32}\}$/.test(result)) {
        if (result === candidateFlag) {
            return { status: 200, flag: result };
        }

        const status = await redis.get(`flag:${result}`);
        if (isValidFlagStatus(status)) {
            return { status: 200, flag: result };
        }

        if (!status) {
            return { status: 410, error: "Gone", message: "Issued flag has expired" };
        }

        return { status: 500, error: "Internal Server Error", message: "Invalid flag state" };
    }

    const resultCode = Number(result);
    if (resultCode === -5) {
        return { status: 500, error: "Internal Server Error", message: "Failed to persist flag session" };
    }

    return { status: 403, error: "Forbidden", message: "Session token already processed or invalid" };
}

/**
 * Redeems a solve flag atomically, incrementing the global solve counter once while
 * permitting idempotent retries within a 5-minute (300-second) grace window.
 *
 * @param {string} flag - 38-character candidate solve flag
 * @returns {Promise<{ status: number, solveNumber?: number, error?: string, message?: string }>}
 */
async function claimSolveFlag(flag) {
    const flagKey = `flag:${flag}`;
    const result = await redis.eval(CLAIM_FLAG_LUA, [flagKey, 'stats:solves'], []);
    const solveNumber = typeof result === 'number' ? result : Number(result);

    if (solveNumber === -1) {
        return { status: 401, error: "Unauthorized", message: "Invalid or expired flag" };
    }

    if (solveNumber <= 0 || !Number.isSafeInteger(solveNumber)) {
        return { status: 500, error: "Internal Server Error", message: "Unable to verify flag state" };
    }

    return { status: 200, solveNumber };
}

module.exports = {
    issueSessionFlag,
    claimSolveFlag,
    isValidFlagStatus
};
