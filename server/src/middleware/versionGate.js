/**
 * Client version enforcement middleware.
 * 
 * Strictly validates the 'X-App-Version-Code' request header as a positive safe integer
 * and rejects outdated client builds with HTTP 426 (Upgrade Required).
 */
const { MIN_VERSION_CODE } = require('../config');

/**
 * Express middleware to verify minimum client version compatibility.
 */
function versionGateMiddleware(req, res, next) {
    const rawHeader = req.headers['x-app-version-code'];
    const normalized = typeof rawHeader === 'string' ? rawHeader.trim() : '';

    if (!/^\d+$/.test(normalized)) {
        return res.status(426).json({
            status: 426,
            error: "Upgrade Required",
            message: "Outdated app version, download the latest release"
        });
    }

    const clientVersionCode = Number(normalized);
    if (!Number.isSafeInteger(clientVersionCode) || clientVersionCode < MIN_VERSION_CODE) {
        return res.status(426).json({
            status: 426,
            error: "Upgrade Required",
            message: "Outdated app version, download the latest release"
        });
    }
    next();
}

module.exports = versionGateMiddleware;
