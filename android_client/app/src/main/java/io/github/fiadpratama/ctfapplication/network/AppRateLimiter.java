package io.github.fiadpratama.ctfapplication.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Persistent client-side request rate limiter.
 *
 * Implements a sliding-window algorithm backed by SharedPreferences to throttle
 * outbound verification requests, persist active cooldown bans across application
 * restarts, and synchronize server-mandated HTTP 429 cooldowns.
 */
public class AppRateLimiter {

    public static final long WINDOW_MS = 60_000L;
    public static final int MAX_REQUESTS = 10;
    public static final long BAN_DURATION_MS = 300_000L;
    private static final long MAX_SERVER_BAN_MS = 10_800_000L; // 3 hours max tier

    private static final String PREFS_NAME = "ctf_rate_limiter_prefs";
    private static final String KEY_BANNED_UNTIL_ELAPSED = "banned_until_elapsed";
    private static final String KEY_BANNED_UNTIL_WALL = "banned_until_wall";
    private static final String KEY_LAST_ELAPSED = "last_elapsed";
    private static final String KEY_TIMESTAMPS = "request_timestamps";

    private final SharedPreferences prefs;
    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private long bannedUntilTimestamp = 0;

    public AppRateLimiter(Context context) {
        if (context != null) {
            this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            loadPersistedState();
        } else {
            this.prefs = null;
        }
    }

    /**
     * Restores active cooldown bans and recent sliding-window timestamps from persistent storage.
     * Uses monotonic elapsed time within the same boot session and falls back to wall-clock delta
     * if the device was rebooted.
     */
    private synchronized void loadPersistedState() {
        if (prefs == null) return;

        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        long savedBannedElapsed = prefs.getLong(KEY_BANNED_UNTIL_ELAPSED, 0L);
        long savedBannedWall = prefs.getLong(KEY_BANNED_UNTIL_WALL, 0L);
        long lastElapsed = prefs.getLong(KEY_LAST_ELAPSED, 0L);

        boolean deviceRebooted = lastElapsed > 0L && nowElapsed < lastElapsed;

        if (!deviceRebooted) {
            if (savedBannedElapsed > nowElapsed) {
                bannedUntilTimestamp = savedBannedElapsed;
            }
            String rawTimestamps = prefs.getString(KEY_TIMESTAMPS, "");
            if (rawTimestamps != null && !rawTimestamps.isEmpty()) {
                String[] parts = rawTimestamps.split(",");
                for (String part : parts) {
                    try {
                        long ts = Long.parseLong(part.trim());
                        if (ts <= nowElapsed && (nowElapsed - ts) <= WINDOW_MS) {
                            requestTimestamps.add(ts);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } else if (savedBannedWall > nowWall) {
            long remainingMs = Math.min(savedBannedWall - nowWall, MAX_SERVER_BAN_MS);
            bannedUntilTimestamp = nowElapsed + remainingMs;
            persistState(nowElapsed);
        } else {
            clearPersistedState();
        }
    }

    /**
     * Persists current rate-limiter state to SharedPreferences.
     *
     * @param nowElapsed Current monotonic elapsed realtime in ms
     */
    private synchronized void persistState(long nowElapsed) {
        if (prefs == null) return;

        long remainingBanMs = Math.max(0L, bannedUntilTimestamp - nowElapsed);
        long bannedWall = remainingBanMs > 0L ? (System.currentTimeMillis() + remainingBanMs) : 0L;

        StringBuilder sb = new StringBuilder();
        for (Long ts : requestTimestamps) {
            if (sb.length() > 0) sb.append(',');
            sb.append(ts);
        }

        prefs.edit()
            .putLong(KEY_BANNED_UNTIL_ELAPSED, bannedUntilTimestamp)
            .putLong(KEY_BANNED_UNTIL_WALL, bannedWall)
            .putLong(KEY_LAST_ELAPSED, nowElapsed)
            .putString(KEY_TIMESTAMPS, sb.toString())
            .apply();
    }

    private synchronized void clearPersistedState() {
        if (prefs == null) return;
        prefs.edit().clear().apply();
    }

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
            clearPersistedState();
        }
        return false;
    }

    /**
     * Records an outbound request attempt and evaluates sliding window constraints.
     * Initiates and persists a 5-minute local cooldown if the maximum request threshold is exceeded.
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
            persistState(now);
            return false;
        }

        requestTimestamps.add(now);
        persistState(now);
        return true;
    }

    /**
     * Synchronizes a server-mandated cooldown duration with the local limiter state.
     *
     * @param seconds Ban duration enforced by the upstream server
     */
    public synchronized void applyServerBan(long seconds) {
        long now = SystemClock.elapsedRealtime();
        long durationMs = Math.max(seconds, 60) * 1000L;
        long targetBanUntil = now + durationMs;
        if (targetBanUntil > bannedUntilTimestamp) {
            bannedUntilTimestamp = targetBanUntil;
        }
        requestTimestamps.clear();
        persistState(now);
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
        clearPersistedState();
    }
}
