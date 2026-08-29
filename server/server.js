const express = require('express');
const crypto = require('crypto');
const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '10kb' }));
app.set('trust proxy', 1);

const E2EE_KEY_STRING = process.env.E2EE_KEY;
const BACKDOOR_CODE = process.env.BACKDOOR_CODE;
const SECRET_MULTIPLIER = parseInt(process.env.SECRET_MULTIPLIER, 10);
const MIN_VERSION_CODE = 5;

if (!E2EE_KEY_STRING || !BACKDOOR_CODE || !SECRET_MULTIPLIER || isNaN(SECRET_MULTIPLIER)) {
    throw new Error("[CRITICAL] Missing required environment variables. Server initialization aborted.");
}

const E2EE_KEY = Buffer.from(E2EE_KEY_STRING);

const stage1Sessions = new Map();
const solvedFlags = new Map();
const rateLimit = new Map();

setInterval(() => {
    const now = Date.now();
    for (const [token, data] of stage1Sessions.entries()) {
        if (now - data.createdAt > 10 * 60 * 1000) stage1Sessions.delete(token);
    }
}, 5 * 60 * 1000);

setInterval(() => {
    const now = Date.now();
    for (const [flag, data] of solvedFlags.entries()) {
        if (now - data.createdAt > 15 * 60 * 1000) solvedFlags.delete(flag);
    }
}, 5 * 60 * 1000);

setInterval(() => {
    const now = Date.now();
    for (const [ip, record] of rateLimit.entries()) {
        if (now - record.start > 60000) rateLimit.delete(ip);
    }
}, 60 * 1000);

function generateSolveFlag() {
    const raw = crypto.randomBytes(16).toString('hex');
    const flag = `FLAG{${raw}}`;
    solvedFlags.set(flag, { createdAt: Date.now(), used: false });
    return flag;
}

function checkRateLimit(ip) {
    const now = Date.now();
    if (!rateLimit.has(ip)) {
        rateLimit.set(ip, { count: 1, start: now });
        return true;
    }
    const record = rateLimit.get(ip);
    if (now - record.start > 60000) {
        rateLimit.set(ip, { count: 1, start: now });
        return true;
    }
    if (record.count >= 30) return false;
    record.count++;
    return true;
}

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

app.use((req, res, next) => {
    if (!checkRateLimit(req.ip)) {
        return res.status(429).json({ status: 429, error: "Too Many Requests", message: "Rate limit exceeded" });
    }
    next();
});

app.use((req, res, next) => {
    const clientVersionCode = parseInt(req.headers['x-app-version-code'], 10);
    if (!clientVersionCode || isNaN(clientVersionCode) || clientVersionCode < MIN_VERSION_CODE) {
        return res.status(426).json({
            status: 426,
            error: "Upgrade Required",
            message: "Outdated app version, download the latest release"
        });
    }
    next();
});

// ==========================================
// API ENDPOINT: /api/vault/stage1
// ==========================================
app.post('/api/vault/stage1', (req, res) => {
    const { payload, iv, tag } = req.body;
    if (!payload || !iv || !tag) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "E2EE payload required" });
    }

    const decryptedData = decryptPayload(payload, iv, tag);
    if (!decryptedData) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Decryption failed" });
    }

    if (decryptedData.backdoor_code === BACKDOOR_CODE) {
        const challengeNum = Math.floor(Math.random() * 10000) + 1000;
        const sessionToken = crypto.randomBytes(32).toString('hex');

        stage1Sessions.set(sessionToken, { challenge: challengeNum, createdAt: Date.now() });

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

// ==========================================
// API ENDPOINT: /api/vault/stage2
// ==========================================
app.post('/api/vault/stage2', (req, res) => {
    const sessionToken = req.headers['x-session-token'];
    if (!sessionToken || !stage1Sessions.has(sessionToken)) {
        return res.status(403).json({ status: 403, error: "Forbidden", message: "Stage 1 not completed" });
    }

    const { payload, iv, tag } = req.body;
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

    const session = stage1Sessions.get(sessionToken);
    const expectedAnswer = session.challenge * SECRET_MULTIPLIER;

    stage1Sessions.delete(sessionToken);

    if (parseInt(answer, 10) === expectedAnswer) {
        return res.status(200).json({
            status: 200,
            message: "Access granted",
            flag: generateSolveFlag()
        });
    }

    return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid calculation" });
});

// ==========================================
// API ENDPOINT: /api/vault/verify-flag
// ==========================================
app.post('/api/vault/verify-flag', (req, res) => {
    const { flag } = req.body;
    if (!flag || typeof flag !== 'string') {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Flag parameter required" });
    }

    const record = solvedFlags.get(flag);
    if (!record || record.used) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or already-claimed flag" });
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

app.use((err, req, res, next) => {
    if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Malformed JSON payload" });
    }
    return res.status(500).json({ status: 500, error: "Internal Server Error", message: "Internal system error" });
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`[CTF API] Multi-Layer Server running on port ${PORT}`);
    });
}

module.exports = app;
