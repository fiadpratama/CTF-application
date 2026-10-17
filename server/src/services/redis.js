/**
 * Redis Connection Service
 * 
 * Provides a shared Upstash Redis client instance initialized from environment variables.
 */
const { Redis } = require('@upstash/redis');

const redis = Redis.fromEnv();

module.exports = redis;
