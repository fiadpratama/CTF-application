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
const { generateSolveFlag } = require('../services/flags');
const { buildHallOfFameMessage } = require('../services/hallOfFame');

const router = express.Router();

/**
 * Atomic Lua script to claim a solve flag.
 * Transitions state from 'unused' to 'claimed:<num>' and increments global solves in a single atomic step.
 * Returns solve number (> 0), -1 if not found/expired, or -2 if corrupt/invalid.
 */
const CLAIM_FLAG_LUA = `
local status = redis.call('GET', KEYS[1])
if not status then
    return -1
end
if status == 'unused' then
    local solveNum = redis.call('INCR', KEYS[2])
    redis.call('SET', KEYS[1], 'claimed:' .. tostring(solveNum), 'EX', 300)
    return solveNum
end
if string.match(status, '^claimed:%d+$') then
    local num = tonumber(string.sub(status, 9))
    if num and num >= 1 and num <= 9007199254740991 and num == math.floor(num) then
        return num
    end
end
return -2
`;

/**
 * Stage 1: Client Handshake & Session Issuance
 * Decrypts AES-256-GCM payload, validates backdoor code via timingSafeEqual,
 * and issues a signed, single-use JWT session token with multiplier header.
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

    const candidateBackdoor = typeof decryptedData.backdoor_code === 'string' ? decryptedData.backdoor_code : '';
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
 * Validates JWT session claims, enforces idempotent flag issuance,
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
        const existingFlag = await redis.get(usedKey);
        if (existingFlag && typeof existingFlag === 'string' && /^FLAG\{[0-9a-f]{32}\}$/.test(existingFlag)) {
            return res.status(200).json({
                status: 200,
                message: "Access granted",
                flag: existingFlag
            });
        }

        const flag = await generateSolveFlag();
        const isAcquired = await redis.set(usedKey, flag, { nx: true, ex: 660 });
        const issuedFlag = isAcquired ? flag : (await redis.get(usedKey)) || flag;

        return res.status(200).json({
            status: 200,
            message: "Access granted",
            flag: issuedFlag
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
    if (!flag || typeof flag !== 'string' || !/^FLAG\{[0-9a-f]{32}\}$/.test(flag)) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Valid flag format required" });
    }

    const flagKey = `flag:${flag}`;
    const result = await redis.eval(CLAIM_FLAG_LUA, [flagKey, 'stats:solves'], []);
    const solveNumber = typeof result === 'number' ? result : Number(result);

    if (solveNumber === -1) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or expired flag" });
    }

    if (solveNumber <= 0 || !Number.isSafeInteger(solveNumber)) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or unrecognized flag status" });
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
