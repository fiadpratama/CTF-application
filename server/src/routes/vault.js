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
 * Ingestion and validation endpoint for Stage 1.
 * Decrypts the incoming payload using AES-256-GCM, validates authorization claims,
 * and issues a signed, single-use JWT session token for the next stage.
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

    if (decryptedData.backdoor_code === BACKDOOR_CODE) {
        const challengeNum = Math.floor(Math.random() * 10000) + 1000;
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
 * Verification endpoint for Stage 2.
 * Validates JWT session claims, checks replay protection (jti),
 * validates the submitted computation against server state, and releases the solve flag.
 */
router.post('/stage2', async (req, res) => {
    const sessionToken = req.headers['x-session-token'];
    if (!sessionToken) {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Stage 1 not completed" });
    }

    let decodedSession;
    try {
        decodedSession = jwt.verify(sessionToken, JWT_SECRET, { algorithms: ['HS256'] });
    } catch (jwtError) {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Invalid or expired session token" });
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
    if (!answer) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Missing answer parameter" });
    }

    const expectedAnswer = decodedSession.challenge * SECRET_MULTIPLIER;
    const answerText = String(answer);

    if (/^\d+$/.test(answerText) && Number(answerText) === expectedAnswer) {
        if (!decodedSession.jti) {
            return res.status(403).json({ status: 403, error: "Forbidden", message: "Invalid session token format" });
        }

        const usedKey = `jti:${decodedSession.jti}`;
        const isAcquired = await redis.set(usedKey, "1", { nx: true, ex: 600 });
        if (!isAcquired) {
            return res.status(403).json({ status: 403, error: "Forbidden", message: "Session token already used" });
        }

        const flag = await generateSolveFlag();
        return res.status(200).json({
            status: 200,
            message: "Access granted",
            flag
        });
    }

    return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid calculation" });
});

/**
 * Flag redemption and verification endpoint.
 * Validates the submitted flag against ephemeral Redis records,
 * increments the global solve counter, and returns the completion debrief message.
 */
router.post('/verify-flag', async (req, res) => {
    const { flag } = req.body || {};
    if (!flag || typeof flag !== 'string') {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Flag parameter required" });
    }

    const flagKey = `flag:${flag}`;
    const status = await redis.get(flagKey);

    if (!status) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or expired flag" });
    }

    let solveNumber = null;
    if (status === "unused") {
        solveNumber = await redis.incr('stats:solves');
        await redis.set(flagKey, `claimed:${solveNumber}`, { ex: 300 });
    } else if (typeof status === 'string' && status.startsWith('claimed:')) {
        solveNumber = Number(status.slice('claimed:'.length)) || null;
    }

    return res.status(200).json({
        success: true,
        title: "HALL OF FAME",
        solve_number: solveNumber,
        message: buildHallOfFameMessage(solveNumber, flag)
    });
});

module.exports = router;
