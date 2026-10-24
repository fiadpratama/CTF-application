/**
 * Stage 2 Route: Computational Proof & Flag Generation
 * 
 * Validates JWT session claims, checks mathematical proof calculation,
 * and delegates atomic flag issuance to the vault state service.
 */
const express = require('express');
const jwt = require('jsonwebtoken');
const { SECRET_MULTIPLIER, JWT_SECRET } = require('../config');
const { decryptPayload } = require('../services/crypto');
const { issueSessionFlag } = require('../services/vaultState');

const router = express.Router();

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
        const result = await issueSessionFlag(decodedSession.jti);

        if (result.status === 200) {
            return res.status(200).json({
                status: 200,
                message: "Access granted",
                flag: result.flag
            });
        }

        return res.status(result.status).json({
            status: result.status,
            error: result.error,
            message: result.message
        });
    }

    return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid calculation" });
});

module.exports = router;
