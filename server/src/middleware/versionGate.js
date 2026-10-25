/**
 * Client version enforcement middleware.
 * 
 * Inspects the 'X-App-Version-Code' request header and rejects outdated client builds
 * with HTTP 426 (Upgrade Required) to ensure active protocol and security compatibility.
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
