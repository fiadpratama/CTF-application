/**
 * Application Entry Point
 * 
 * Thin wrapper that initializes and binds the HTTP listener when executed
 * directly, or exports the Express app instance for serverless runtime (Vercel).
 */
const app = require('./src/app');

const PORT = process.env.PORT || 3000;
if (require.main === module) {
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`[CTF API] Multi-Layer Server running on port ${PORT}`);
    });
}

module.exports = app;
