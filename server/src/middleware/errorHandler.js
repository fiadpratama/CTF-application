/**
 * Global Error Handler Middleware
 * 
 * Catches unhandled exceptions, payload size violations, and JSON syntax errors
 * to prevent server crash and ensure uniform JSON error responses.
 */
function errorHandler(err, req, res, _next) {
    if (res.headersSent) {
        return _next(err);
    }
    if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Malformed JSON payload" });
    }
    if (err && (err.status === 413 || err.type === 'entity.too.large')) {
        return res.status(413).json({ status: 413, error: "Payload Too Large", message: "Request payload exceeds 10kb limit" });
    }
    return res.status(500).json({ status: 500, error: "Internal Server Error", message: "Internal system error" });
}

module.exports = errorHandler;
