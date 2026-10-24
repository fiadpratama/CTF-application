/**
 * Stage 1 Route: Client Handshake & Session Issuance
 * 
 * Ingests encrypted client handshake, verifies backdoor authorization token
 * via constant-time comparison, and issues an ephemeral signed JWT session token.
 */
const express = require('express');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { BACKDOOR_CODE, SECRET_MULTIPLIER, JWT_SECRET } = require('../config');
const { decryptPayload } = require('../services/crypto');

const router = express.Router();

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

module.exports = router;
