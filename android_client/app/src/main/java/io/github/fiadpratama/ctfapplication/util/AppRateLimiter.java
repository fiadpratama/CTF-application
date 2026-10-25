package io.github.fiadpratama.ctfapplication.util;

import android.os.SystemClock;
import java.util.LinkedList;
import java.util.Queue;

/**
 * In-memory client-side rate limiter.
 *
 * Implements a sliding-window algorithm to throttle outbound verification requests,
 * mitigating upstream server load and guiding deliberative interaction.
 */
public class AppRateLimiter {

    public static final long WINDOW_MS = 60_000L;
    public static final int MAX_REQUESTS = 10;
    public static final long BAN_DURATION_MS = 300_000L;

    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private long bannedUntilTimestamp = 0;

    /**
     * Determines whether the client is currently subject to an active cooldown ban.
     *
     * @return true if cooldown is active; false otherwise
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
     * Records an outbound request attempt and evaluates sliding window constraints.
     * Initiates a local cooldown if the maximum request threshold is exceeded.
     *
     * @return true if the request is permitted; false if rate-limited or banned
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
     * Synchronizes a server-mandated cooldown duration with the local limiter state.
     *
     * @param seconds Ban duration enforced by the upstream server
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
     * @return Remaining cooldown duration in seconds
     */
    public synchronized long getRemainingBanSeconds() {
        long now = SystemClock.elapsedRealtime();
        if (now >= bannedUntilTimestamp) {
            return 0;
        }
        return (bannedUntilTimestamp - now + 999) / 1000;
    }

    /**
     * Clears all recorded timestamps and active bans to restore default state.
     */
    public synchronized void reset() {
        bannedUntilTimestamp = 0;
        requestTimestamps.clear();
    }
}
