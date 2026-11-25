/**
 * Standalone HTTP server entry point.
 *
 * Binds the Express application to a local port when executed directly.
 * Serverless deployments on Vercel use `api/index.js` as their entry point.
 */
const app = require('./src/app');

const PORT = process.env.PORT || 3000;
if (require.main === module) {
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`[CTF API] Multi-Layer Server running on port ${PORT}`);
    });
}

module.exports = app;
