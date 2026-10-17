/**
 * Client Version Enforcement Middleware
 * 
 * Rejects requests from outdated Android client builds via X-App-Version-Code header.
 */
const { MIN_VERSION_CODE } = require('../config');

/**
 * Express middleware to verify minimum client version compatibility.
 */
function versionGateMiddleware(req, res, next) {
    const clientVersionCode = parseInt(req.headers['x-app-version-code'], 10);
    if (!clientVersionCode || isNaN(clientVersionCode) || clientVersionCode < MIN_VERSION_CODE) {
        return res.status(426).json({
            status: 426,
            error: "Upgrade Required",
            message: "Outdated app version, download the latest release"
        });
    }
    next();
}

module.exports = versionGateMiddleware;
