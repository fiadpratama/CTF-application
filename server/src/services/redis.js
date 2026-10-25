/**
 * Redis Client Service
 * 
 * Configures and exports a shared Upstash Redis HTTP client.
 * Operates over REST to provide low-latency stateless caching and rate limiting
 * compatible with serverless runtimes.
 */
const { Redis } = require('@upstash/redis');

const redis = Redis.fromEnv();

module.exports = redis;
