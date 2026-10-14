const express = require('express');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { Redis } = require('@upstash/redis');
const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '10kb' }));
app.set('trust proxy', 1);

const E2EE_KEY_STRING = process.env.E2EE_KEY;
const BACKDOOR_CODE = process.env.BACKDOOR_CODE;
const MULTIPLIER_STRING = process.env.SECRET_MULTIPLIER || '';
const SECRET_MULTIPLIER = Number(MULTIPLIER_STRING);
const JWT_SECRET = process.env.JWT_SECRET;
const MIN_VERSION_CODE = 5;

const configErrors = [];
if (!E2EE_KEY_STRING || Buffer.byteLength(E2EE_KEY_STRING, 'utf8') !== 32) {
    configErrors.push('E2EE_KEY must be exactly 32 bytes');
}
if (!BACKDOOR_CODE) configErrors.push('BACKDOOR_CODE is required');
if (!/^\d+$/.test(MULTIPLIER_STRING) || SECRET_MULTIPLIER < 1) {
    configErrors.push('SECRET_MULTIPLIER must be a positive integer');
}
if (!JWT_SECRET || JWT_SECRET.length < 32) {
    configErrors.push('JWT_SECRET must be at least 32 characters');
}
if (configErrors.length > 0) {
    throw new Error(`[CRITICAL] Invalid configuration: ${configErrors.join('; ')}`);
}

const E2EE_KEY = Buffer.from(E2EE_KEY_STRING);
const redis = Redis.fromEnv();

function generateSolveFlag() {
    const raw = crypto.randomBytes(16).toString('hex');
    const flag = `FLAG{${raw}}`;
    return redis.set(`flag:${flag}`, "unused", { ex: 900 }).then(() => flag);
}

async function checkRateLimit(ip) {
    try {
        const banKey = `banned:${ip}`;
        const isBanned = await redis.get(banKey);
        if (isBanned) return false;

        const key = `ratelimit:${ip}`;
        const count = await redis.incr(key);
        if (count === 1) {
            await redis.expire(key, 60);
        }

        if (count > 30) {
            await redis.set(banKey, "1", { ex: 1800 });
            return false;
        }
        return true;
    } catch (redisError) {
        console.error("[RATE_LIMIT_ERROR]", redisError.message);
        return true;
    }
}

function decryptPayload(encryptedBase64, ivBase64, authTagBase64) {
    try {
        const decipher = crypto.createDecipheriv('aes-256-gcm', E2EE_KEY, Buffer.from(ivBase64, 'base64'));
        decipher.setAuthTag(Buffer.from(authTagBase64, 'base64'));
        let decrypted = decipher.update(encryptedBase64, 'base64', 'utf8');
        decrypted += decipher.final('utf8');
        return JSON.parse(decrypted);
    } catch (decryptError) {
        return null;
    }
}

app.use(async (req, res, next) => {
    const allowed = await checkRateLimit(req.ip);
    if (!allowed) {
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

// ==========================================
// API ENDPOINT: /api/vault/stage2
// ==========================================
app.post('/api/vault/stage2', async (req, res) => {
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

// ==========================================
// API ENDPOINT: /api/vault/verify-flag
// ==========================================
app.post('/api/vault/verify-flag', async (req, res) => {
    const { flag } = req.body || {};
    if (!flag || typeof flag !== 'string') {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Flag parameter required" });
    }

    const status = await redis.get(`flag:${flag}`);

    if (!status) {
        return res.status(401).json({ status: 401, error: "Unauthorized", message: "Invalid or expired flag" });
    }

    if (status === "unused") {
        await redis.set(`flag:${flag}`, "claimed", { ex: 300 });
    }

    return res.status(200).json({
        success: true,
        title: "HALL OF FAME",
        message: "SECURITY ASSESSMENT COMPLETE\n\n" +
            "STATUS: VULNERABILITY EXPLOITED\n" +
            "TARGET: CTF APPLICATION CORE INFRASTRUCTURE\n\n" +
            "Congratulations on successfully completing this security assessment.\n\n" +
            "This challenge was designed to evaluate advanced reverse engineering, dynamic instrumentation, and cryptographic analysis skills.\n\n" +
            "By extracting the backdoor code from the native C++ layer (JNI) and intercepting the encrypted AES-256-GCM network payloads, you have demonstrated exceptional proficiency in mobile application security and protocol manipulation.\n\n" +
            "This concludes the technical evaluation.\n\n\n" +
            "DEVELOPER & ARCHITECT\nFiad Pratama\n\n" +
            "TECHNOLOGY STACK\nAndroid (Java/C++) & Node.js (Vercel Edge)\n\n" +
            "BACKGROUND SCORE\nW.A. Mozart - Dies Irae (Requiem in D minor)\n\n\n\n" +
            "SESSION TERMINATED\n\n" +
            "FLAG ACQUIRED\n" + flag
    });
});

app.use((err, req, res, unusedNext) => {
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
