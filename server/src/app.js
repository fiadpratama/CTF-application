/**
 * Express Application Configuration
 * 
 * Configures middleware pipeline, security headers, request parsing,
 * rate limiting, client version verification, and route mounting.
 */
const express = require('express');
const rateLimitMiddleware = require('./middleware/rateLimit');
const versionGateMiddleware = require('./middleware/versionGate');
const errorHandler = require('./middleware/errorHandler');
const vaultRouter = require('./routes/vault');

const app = express();

// Security & Transport Configuration
app.disable('x-powered-by');
app.use(express.json({ limit: '10kb' }));
app.set('trust proxy', 1);

// Global Middleware Pipeline
app.use(rateLimitMiddleware);
app.use(versionGateMiddleware);

// API Route Registration
app.use('/api/vault', vaultRouter);

// Fallback for undefined routes
app.use((req, res) => {
    res.status(404).json({ status: 404, error: "Not Found", message: "Resource not found" });
});

// Centralized Error Handling
app.use(errorHandler);

module.exports = app;
