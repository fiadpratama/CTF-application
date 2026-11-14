/**
 * Vercel Serverless Function entry point.
 *
 * Re-exports the configured Express application instance for zero-config
 * Vercel Function bundling while keeping `server.js` for standalone execution.
 */
module.exports = require('../src/app');
