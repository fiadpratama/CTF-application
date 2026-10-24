/**
 * Express Application Configuration
 * 
 * Configures middleware pipeline, security headers, request parsing,
 * rate limiting, client version verification, and stage route mounting.
 */
const express = require('express');
const rateLimitMiddleware = require('./middleware/rateLimit');
const versionGateMiddleware = require('./middleware/versionGate');
const errorHandler = require('./middleware/errorHandler');

const stage1Router = require('./routes/stage1');
const stage2Router = require('./routes/stage2');
const verifyFlagRouter = require('./routes/verifyFlag');

const app = express();

app.disable('x-powered-by');
app.set('trust proxy', 1);

app.use(rateLimitMiddleware);
app.use(versionGateMiddleware);
app.use(express.json({ limit: '10kb' }));

app.use('/api/vault', stage1Router);
app.use('/api/vault', stage2Router);
app.use('/api/vault', verifyFlagRouter);

app.use((req, res) => {
    res.status(404).json({ status: 404, error: "Not Found", message: "Resource not found" });
});

app.use(errorHandler);

module.exports = app;
