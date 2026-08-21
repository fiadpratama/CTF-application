const express = require('express');
const crypto = require('crypto');
const app = express();

app.use(express.json());

app.set('trust proxy', 1);

const E2EE_KEY_STRING = process.env.E2EE_KEY;
const BACKDOOR_CODE = process.env.BACKDOOR_CODE;

if (!E2EE_KEY_STRING || !BACKDOOR_CODE) {
    throw new Error("Missing required environment variables: E2EE_KEY and/or BACKDOOR_CODE. Server refuses to start without them.");
}

const E2EE_KEY = Buffer.from(E2EE_KEY_STRING);

const stage1Sessions = new Map();

setInterval(() => {
    const now = Date.now();
    for (const [token, data] of stage1Sessions.entries()) {
        if (now - data.createdAt > 10 * 60 * 1000) {
            stage1Sessions.delete(token);
        }
    }
}, 5 * 60 * 1000);

const solvedFlags = new Map();

function generateSolveFlag() {
    const raw = crypto.randomBytes(16).toString('hex');
    const flag = `FLAG{${raw}}`;
    solvedFlags.set(flag, { createdAt: Date.now(), used: false });
    return flag;
}

setInterval(() => {
    const now = Date.now();
    for (const [flag, data] of solvedFlags.entries()) {
        if (now - data.createdAt > 15 * 60 * 1000) solvedFlags.delete(flag);
    }
}, 5 * 60 * 1000);

const rateLimit = new Map();
function checkRateLimit(ip) {
    const now = Date.now();
    const windowMs = 60 * 1000;
    const maxRequests = 30;

    if (!rateLimit.has(ip)) {
        rateLimit.set(ip, { count: 1, start: now });
        return true;
    }
    const record = rateLimit.get(ip);
    if (now - record.start > windowMs) {
        rateLimit.set(ip, { count: 1, start: now });
        return true;
    }
    if (record.count >= maxRequests) return false;
    record.count++;
    return true;
}

setInterval(() => {
    const now = Date.now();
    for (const [ip, record] of rateLimit.entries()) {
        if (now - record.start > 60 * 1000) {
            rateLimit.delete(ip);
        }
    }
}, 60 * 1000);

function decryptPayload(encryptedBase64, ivBase64, authTagBase64) {
    try {
        const decipher = crypto.createDecipheriv('aes-256-gcm', E2EE_KEY, Buffer.from(ivBase64, 'base64'));
        decipher.setAuthTag(Buffer.from(authTagBase64, 'base64'));
        let decrypted = decipher.update(encryptedBase64, 'base64', 'utf8');
        decrypted += decipher.final('utf8');
        return JSON.parse(decrypted);
    } catch (e) {
        return null;
    }
}

// ==========================================
// API ENDPOINT: /api/vault/stage1
// ==========================================
app.post('/api/vault/stage1', (req, res) => {
    const clientIp = req.ip;
    if (!checkRateLimit(clientIp)) {
        return res.status(429).json({ error: "Too many requests. Slow down." });
    }

    const { payload, iv, tag } = req.body;
    if (!payload || !iv || !tag) {
        return res.status(400).json({ error: "E2EE Required" });
    }

    const decryptedData = decryptPayload(payload, iv, tag);
    if (!decryptedData) {
        return res.status(401).json({ error: "Decryption failed." });
    }

    if (decryptedData.backdoor_code === BACKDOOR_CODE) {
        const challengeNum = Math.floor(Math.random() * 10000) + 1000;

        const sessionToken = crypto.randomBytes(32).toString('hex');
        stage1Sessions.set(sessionToken, { challenge: challengeNum, createdAt: Date.now() });

        res.setHeader('X-Secret-Multiplier', Buffer.from('109').toString('base64'));
        res.setHeader('X-Session-Token', sessionToken);

        return res.status(200).json({
            message: "Stage 1 Cleared.",
            instruction: "Find the multiplier, multiply it with the challenge number, and send it to /api/vault/stage2 encrypted. Include the X-Session-Token header in your next request.",
            challenge: challengeNum
        });
    }

    return res.status(401).json({ error: "Invalid Backdoor Code" });
});

// ==========================================
// API ENDPOINT: /api/vault/stage2
// ==========================================
app.post('/api/vault/stage2', (req, res) => {
    const clientIp = req.ip;
    if (!checkRateLimit(clientIp)) {
        return res.status(429).json({ error: "Too many requests. Slow down." });
    }

    const sessionToken = req.headers['x-session-token'];
    if (!sessionToken || !stage1Sessions.has(sessionToken)) {
        return res.status(403).json({ error: "Stage 1 must be completed first." });
    }

    const { payload, iv, tag } = req.body;
    if (!payload || !iv || !tag) {
        return res.status(400).json({ error: "E2EE Required" });
    }

    const decryptedData = decryptPayload(payload, iv, tag);
    if (!decryptedData) {
        return res.status(401).json({ error: "Decryption failed." });
    }

    const { answer } = decryptedData;
    if (!answer) {
        return res.status(400).json({ error: "Invalid payload format." });
    }

    const session = stage1Sessions.get(sessionToken);
    const expectedAnswer = session.challenge * 109;

    stage1Sessions.delete(sessionToken);

    if (parseInt(answer) === expectedAnswer) {
        const flag = generateSolveFlag();
        return res.status(200).json({
            message: "ACCESS GRANTED.",
            flag
        });
    }

    return res.status(401).json({ error: "Invalid calculation." });
});

// ==========================================
// API ENDPOINT: /api/vault/verify-flag
// ==========================================
app.post('/api/vault/verify-flag', (req, res) => {
    const clientIp = req.ip;
    if (!checkRateLimit(clientIp)) {
        return res.status(429).json({ error: "Too many requests. Slow down." });
    }

    const { flag } = req.body;
    if (!flag || typeof flag !== 'string') {
        return res.status(400).json({ error: "Flag is required." });
    }

    const record = solvedFlags.get(flag);
    if (!record || record.used) {
        return res.status(401).json({ error: "INVALID FLAG" });
    }

    record.used = true;

    return res.status(200).json({
        success: true,
        title: "HALL OF FAME",
        message: "SECURITY ASSESSMENT COMPLETE\n\n" +
            "[+] STATUS: VULNERABILITY EXPLOITED\n" +
            "[+] TARGET: CTF APPLICATION CORE INFRASTRUCTURE\n\n" +
            "Congratulations on successfully completing this security assessment.\n\n" +
            "This challenge was designed to evaluate advanced reverse engineering, dynamic instrumentation, and cryptographic analysis skills.\n\n" +
            "By extracting the backdoor code from the native C++ layer (JNI) and intercepting the encrypted AES-256-GCM network payloads, you have demonstrated exceptional proficiency in mobile application security and protocol manipulation.\n\n" +
            "This concludes the technical evaluation.\n\n\n\n\n" +
            "DEVELOPER & ARCHITECT\nFiad Pratama\n\n\n" +
            "TECHNOLOGY STACK\nAndroid (Java/C++) & Node.js (Vercel Edge)\n\n\n" +
            "BACKGROUND SCORE\nW.A. Mozart - Dies Irae (Requiem in D minor)\n\n\n\n\n\n\n\n" +
            "[+] SESSION TERMINATED.\n\n\n" +
            "FLAG ACQUIRED:\n" + flag
    });
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`[CTF API] Multi-Layer Server running on port ${PORT}`);
    });
}

module.exports = app;
