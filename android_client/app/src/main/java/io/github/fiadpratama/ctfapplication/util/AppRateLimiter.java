package io.github.fiadpratama.ctfapplication.util;

import android.os.SystemClock;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Client-Side Rate Limiter & Abuse Prevention
 *
 * Enforces in-app request quotas (maximum 10 requests within a 60-second sliding window)
 * to prevent automated flooding and mitigate upstream server-side IP bans.
 * Escalates to a 5-minute client cooldown upon violation.
 */
public class AppRateLimiter {

    public static final long WINDOW_MS = 60_000L;          // 1 minute window
    public static final int MAX_REQUESTS = 10;             // Max 10 requests per window
    public static final long BAN_DURATION_MS = 300_000L;   // 5 minutes ban (300 seconds)

    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private long bannedUntilTimestamp = 0;

    /**
     * Checks if the client is currently subject to an active rate-limiting ban.
     *
     * @return true if cooldown ban is active; false otherwise
     */
    public synchronized boolean isBanned() {
        long now = SystemClock.elapsedRealtime();
        if (now < bannedUntilTimestamp) {
            return true;
        }
        if (bannedUntilTimestamp > 0) {
            bannedUntilTimestamp = 0;
            requestTimestamps.clear();
        }
        return false;
    }

    /**
     * Records a new request attempt and evaluates whether it exceeds rate limits.
     * If the quota is exceeded (> 10 requests within 60 seconds), initiates a 5-minute ban.
     *
     * @return true if request is permitted; false if rate-limit ban is triggered or active
     */
    public synchronized boolean recordAndCheck() {
        long now = SystemClock.elapsedRealtime();

        if (now < bannedUntilTimestamp) {
            return false;
        }

        while (!requestTimestamps.isEmpty() && (now - requestTimestamps.peek()) > WINDOW_MS) {
            requestTimestamps.poll();
        }

        if (requestTimestamps.size() >= MAX_REQUESTS) {
            bannedUntilTimestamp = now + BAN_DURATION_MS;
            requestTimestamps.clear();
            return false;
        }

        requestTimestamps.add(now);
        return true;
    }

    /**
     * Synchronizes a server-enforced cooldown ban into the local client limiter.
     *
     * @param seconds Ban duration in seconds
     */
    public synchronized void applyServerBan(long seconds) {
        long durationMs = Math.max(seconds, 60) * 1000L;
        long targetBanUntil = SystemClock.elapsedRealtime() + durationMs;
        if (targetBanUntil > bannedUntilTimestamp) {
            bannedUntilTimestamp = targetBanUntil;
        }
        requestTimestamps.clear();
    }

    /**
     * Calculates remaining cooldown time in seconds.
     *
     * @return Remaining seconds until cooldown expires
     */
    public synchronized long getRemainingBanSeconds() {
        long now = SystemClock.elapsedRealtime();
        if (now >= bannedUntilTimestamp) {
            return 0;
        }
        return (bannedUntilTimestamp - now + 999) / 1000;
    }

    /**
     * Resets rate limiter state (for testing or session reboots).
     */
    public synchronized void reset() {
        bannedUntilTimestamp = 0;
        requestTimestamps.clear();
    }
}
