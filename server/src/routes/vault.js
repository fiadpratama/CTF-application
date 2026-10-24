/**
 * Vault Challenge Routes
 * 
 * Manages the multi-stage cryptographic and protocol verification endpoints:
 * - POST /api/vault/stage1: Ingestion and validation of initial client handshake payload
 * - POST /api/vault/stage2: Verification of session token state and mathematical proof
 * - POST /api/vault/verify-flag: Final flag redemption, solve metrics tracking, and debriefing
 */
const express = require('express');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { BACKDOOR_CODE, SECRET_MULTIPLIER, JWT_SECRET } = require('../config');
const redis = require('../services/redis');
const { decryptPayload } = require('../services/crypto');
const { createFlagString } = require('../services/flags');
const { buildHallOfFameMessage } = require('../services/hallOfFame');

const router = express.Router();

/**
 * Atomic Lua script for Stage 2 flag issuance with best-effort rollback protection.
 * KEYS: [1] session token (jti:<id>), [2] flag record (flag:<str>)
 * ARGV: [1] candidate flag, [2] session TTL, [3] flag TTL
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
 * Atomic Lua script to claim a solve flag with best-effort rollback on write error.
 * KEYS: [1] flag record (flag:<str>), [2] global solves counter (stats:solves)
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
 * Stage 1: Client Handshake & Session Issuance
 * Decrypts AES-256-GCM payload, validates presence and correctness of backdoor_code
 * via timingSafeEqual, and issues a signed JWT session token with multiplier header.
 */
router.post('/stage1', (req, res) => {
    const { payload, iv, tag } = req.body || {};
    if (!payload || !iv || !tag) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "E2EE payload required" });
    }

    const decryptedData = decryptPayload(payload, iv, tag);
    if (!decryptedData) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Decryption failed" });
    }

    const { backdoor_code: candidateBackdoor } = decryptedData;
    if (candidateBackdoor === undefined || candidateBackdoor === null || typeof candidateBackdoor !== 'string' || candidateBackdoor.trim() === '') {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Missing backdoor_code parameter" });
    }

    const candidateBuf = Buffer.from(candidateBackdoor, 'utf8');
    const expectedBuf = Buffer.from(BACKDOOR_CODE, 'utf8');
    const isAuthorized = candidateBuf.length === expectedBuf.length && crypto.timingSafeEqual(candidateBuf, expectedBuf);

    if (isAuthorized) {
        const challengeNum = crypto.randomInt(1000, 10000);
        const jti = crypto.randomBytes(16).toString('hex');

        const sessionToken = jwt.sign(
            { challenge: challengeNum, jti },
            JWT_SECRET,
            { expiresIn: '10m', algorithm: 'HS256' }
        );

        res.setHeader('X-Secret-Multiplier', Buffer.from(String(SECRET_MULTIPLIER)).toString('base64'));
        res.setHeader('X-Session-Token', sessionToken);

        return res.status(200).json({
            status: 200,
            message: "Stage 1 cleared",
            instruction: "Find the multiplier, multiply it with the challenge number, and send it to /api/vault/stage2 encrypted. Include the X-Session-Token header in your next request.",
            challenge: challengeNum
        });
    }

    return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid backdoor code" });
});

/**
 * Stage 2: Computational Proof & Flag Generation
 * Validates JWT session claims, enforces idempotent flag issuance via atomic Lua,
 * verifies mathematical computation, and issues an ephemeral solve flag.
 */
router.post('/stage2', async (req, res) => {
    const sessionToken = req.headers['x-session-token'];
    if (!sessionToken || typeof sessionToken !== 'string') {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Stage 1 not completed" });
    }

    let decodedSession;
    try {
        decodedSession = jwt.verify(sessionToken, JWT_SECRET, { algorithms: ['HS256'] });
    } catch {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Invalid or expired session token" });
    }

    if (!decodedSession ||
        typeof decodedSession.challenge !== 'number' ||
        !Number.isInteger(decodedSession.challenge) ||
        typeof decodedSession.jti !== 'string' ||
        !/^[0-9a-f]{32}$/.test(decodedSession.jti)) {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Invalid session token format" });
    }

    const { payload, iv, tag } = req.body || {};
    if (!payload || !iv || !tag) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "E2EE payload required" });
    }

    const decryptedData = decryptPayload(payload, iv, tag);
    if (!decryptedData) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Decryption failed" });
    }

    const { answer } = decryptedData;
    if (answer === undefined || answer === null || (typeof answer !== 'string' && typeof answer !== 'number')) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Missing answer parameter" });
    }

    const expectedAnswer = decodedSession.challenge * SECRET_MULTIPLIER;
    if (!Number.isSafeInteger(expectedAnswer)) {
        return res.status(500).json({ status: 500, error: "Internal Server Error", message: "Calculation overflow" });
    }

    const answerText = String(answer).trim();

    if (/^\d+$/.test(answerText) && Number(answerText) === expectedAnswer) {
        const usedKey = `jti:${decodedSession.jti}`;

        let candidateFlag = createFlagString();
        let flagKey = `flag:${candidateFlag}`;
        let result = await redis.eval(ISSUE_FLAG_LUA, [usedKey, flagKey], [candidateFlag, '660', '900']);

        if (result === -6) {
            candidateFlag = createFlagString();
            flagKey = `flag:${candidateFlag}`;
            result = await redis.eval(ISSUE_FLAG_LUA, [usedKey, flagKey], [candidateFlag, '660', '900']);
        }

        if (typeof result === 'string' && result.length === 38 && /^FLAG\{[0-9a-f]{32}\}$/.test(result)) {
            // Fast path: newly issued flag was just created atomically as 'unused' by ISSUE_FLAG_LUA
            if (result === candidateFlag) {
                return res.status(200).json({
                    status: 200,
                    message: "Access granted",
                    flag: result
                });
            }

            // Idempotent retry path: verify lifecycle state of previously issued flag
            const status = await redis.get(`flag:${result}`);
            if (isValidFlagStatus(status)) {
                return res.status(200).json({
                    status: 200,
                    message: "Access granted",
                    flag: result
                });
            }

            if (!status) {
                return res.status(410).json({
                    status: 410,
                    error: "Gone",
                    message: "Issued flag has expired"
                });
            }

            return res.status(500).json({
                status: 500,
                error: "Internal Server Error",
                message: "Invalid flag state"
            });
        }

        const resultCode = Number(result);
        if (resultCode === -5) {
            return res.status(500).json({
                status: 500,
                error: "Internal Server Error",
                message: "Failed to persist flag session"
            });
        }

        return res.status(403).json({
            status: 403,
            error: "Forbidden",
            message: "Session token already processed or invalid"
        });
    }

    return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid calculation" });
});

/**
 * Stage 3: Flag Redemption & Debriefing
 * Atomically verifies and claims the flag via Lua script, updates solve metrics,
 * and returns the completion debriefing message.
 */
router.post('/verify-flag', async (req, res) => {
    const { flag } = req.body || {};
    if (!flag || typeof flag !== 'string' || flag.length !== 38 || !/^FLAG\{[0-9a-f]{32}\}$/.test(flag)) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Valid flag format required" });
    }

    const flagKey = `flag:${flag}`;
    const result = await redis.eval(CLAIM_FLAG_LUA, [flagKey, 'stats:solves'], []);
    const solveNumber = typeof result === 'number' ? result : Number(result);

    if (solveNumber === -1) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or expired flag" });
    }

    if (solveNumber <= 0 || !Number.isSafeInteger(solveNumber)) {
        return res.status(500).json({ status: 500, error: "Internal Server Error", message: "Unable to verify flag state" });
    }

    return res.status(200).json({
        status: 200,
        success: true,
        title: "HALL OF FAME",
        solve_number: solveNumber,
        message: buildHallOfFameMessage(solveNumber, flag)
    });
});

module.exports = router;
