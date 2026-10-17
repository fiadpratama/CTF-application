/**
 * Global Error Handler Middleware
 * 
 * Catches unhandled exceptions and JSON syntax errors to prevent server crash
 * and ensure uniform JSON error responses.
 */
function errorHandler(err, req, res, unusedNext) {
    if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Malformed JSON payload" });
    }
    return res.status(500).json({ status: 500, error: "Internal Server Error", message: "Internal system error" });
}

module.exports = errorHandler;
