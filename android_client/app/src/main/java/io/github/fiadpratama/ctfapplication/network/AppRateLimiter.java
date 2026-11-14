package io.github.fiadpratama.ctfapplication.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Persistent client-side request rate limiter.
 *
 * Implements a sliding-window algorithm backed by SharedPreferences to throttle
 * outbound verification requests, persist active cooldown bans across application
 * restarts, and synchronize server-mandated HTTP 429 cooldowns.
 *
 * Reboot detection uses {@link Settings.Global#BOOT_COUNT} (API 24+) as the primary
 * indicator, falling back to monotonic elapsed-time regression when unavailable, and
 * recovers post-reboot bans consistently via bounded wall-clock and elapsed-time checks.
 */
public class AppRateLimiter {

    public static final long WINDOW_MS = 60_000L;
    public static final int MAX_REQUESTS = 10;
    public static final long BAN_DURATION_MS = 300_000L;
    private static final long MAX_SERVER_BAN_MS = 10_800_000L;

    private static final String PREFS_NAME = "ctf_rate_limiter_prefs";
    private static final String KEY_BANNED_UNTIL_ELAPSED = "banned_until_elapsed";
    private static final String KEY_BANNED_UNTIL_WALL = "banned_until_wall";
    private static final String KEY_LAST_ELAPSED = "last_elapsed";
    private static final String KEY_LAST_WALL = "last_wall";
    private static final String KEY_BOOT_COUNT = "boot_count";
    private static final String KEY_TIMESTAMPS = "request_timestamps";

    interface Clock {
        long elapsedRealtime();
        long currentTimeMillis();
    }

    interface BootCountProvider {
        int getBootCount();
    }

    private final SharedPreferences prefs;
    private final Clock clock;
    private final BootCountProvider bootCountProvider;
    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private long bannedUntilTimestamp = 0L;

    public AppRateLimiter(Context context) {
        this(
            context != null
                ? context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                : null,
            new Clock() {
                @Override
                public long elapsedRealtime() {
                    return SystemClock.elapsedRealtime();
                }

                @Override
                public long currentTimeMillis() {
                    return System.currentTimeMillis();
                }
            },
            createBootCountProvider(context != null ? context.getApplicationContext() : null)
        );
    }

    AppRateLimiter(SharedPreferences prefs, Clock clock, BootCountProvider bootCountProvider) {
        this.prefs = prefs;
        this.clock = clock;
        this.bootCountProvider = bootCountProvider;
        if (this.prefs != null) {
            loadPersistedState();
        }
    }

    private static BootCountProvider createBootCountProvider(final Context appContext) {
        return new BootCountProvider() {
            @Override
            public int getBootCount() {
                if (appContext == null) {
                    return -1;
                }
                try {
                    return Settings.Global.getInt(
                        appContext.getContentResolver(),
                        Settings.Global.BOOT_COUNT,
                        -1
                    );
                } catch (Exception ignored) {
                    return -1;
                }
            }
        };
    }

    /**
     * Evaluates whether a device reboot occurred since the previous persisted snapshot.
     */
    private static boolean hasDeviceRebooted(
            long nowElapsed,
            long lastElapsed,
            int savedBootCount,
            int currentBootCount) {
        if (savedBootCount >= 0 && currentBootCount >= 0) {
            if (savedBootCount != currentBootCount) {
                return true;
            }
            return lastElapsed >= 0L && nowElapsed < lastElapsed;
        }

        return lastElapsed >= 0L && nowElapsed < lastElapsed;
    }

    /**
     * Computes remaining ban duration (in ms) after a reboot by combining wall-clock expiration
     * with a monotonic upper bound anchored to the pre-reboot remaining duration minus new-boot uptime.
     */
    private static long computePostRebootRemainingBanMs(
            long nowElapsed,
            long nowWall,
            long lastElapsed,
            long lastWall,
            long savedBannedElapsed,
            long savedBannedWall) {
        if (savedBannedWall <= 0L) {
            return 0L;
        }

        long preRebootRemainingMs;
        if (lastElapsed >= 0L && savedBannedElapsed > lastElapsed) {
            preRebootRemainingMs = Math.min(savedBannedElapsed - lastElapsed, MAX_SERVER_BAN_MS);
        } else if (lastWall > 0L && savedBannedWall > lastWall) {
            preRebootRemainingMs = Math.min(savedBannedWall - lastWall, MAX_SERVER_BAN_MS);
        } else {
            preRebootRemainingMs = MAX_SERVER_BAN_MS;
        }

        long postBootUpperBoundMs = Math.max(0L, preRebootRemainingMs - Math.max(0L, nowElapsed));
        if (postBootUpperBoundMs <= 0L) {
            return 0L;
        }

        if (lastWall > 0L && nowWall < lastWall) {
            return postBootUpperBoundMs;
        }

        long wallRemainingMs = savedBannedWall - nowWall;
        if (wallRemainingMs <= 0L) {
            return 0L;
        }

        return Math.min(wallRemainingMs, postBootUpperBoundMs);
    }

    /**
     * Restores active cooldown bans and recent sliding-window timestamps from persistent storage.
     */
    private synchronized void loadPersistedState() {
        if (prefs == null) return;

        long nowElapsed = clock.elapsedRealtime();
        long nowWall = clock.currentTimeMillis();
        int currentBootCount = bootCountProvider.getBootCount();

        long savedBannedElapsed = prefs.getLong(KEY_BANNED_UNTIL_ELAPSED, 0L);
        long savedBannedWall = prefs.getLong(KEY_BANNED_UNTIL_WALL, 0L);
        long lastElapsed = prefs.getLong(KEY_LAST_ELAPSED, -1L);
        long lastWall = prefs.getLong(KEY_LAST_WALL, -1L);
        int savedBootCount = prefs.getInt(KEY_BOOT_COUNT, -1);
        String rawTimestamps = prefs.getString(KEY_TIMESTAMPS, "");

        boolean hasAnyPersistedState = savedBannedElapsed > 0L
            || savedBannedWall > 0L
            || lastElapsed >= 0L
            || (rawTimestamps != null && !rawTimestamps.isEmpty());
        if (!hasAnyPersistedState) {
            return;
        }

        boolean deviceRebooted = hasDeviceRebooted(
            nowElapsed,
            lastElapsed,
            savedBootCount,
            currentBootCount
        );

        if (!deviceRebooted) {
            if (savedBannedElapsed > nowElapsed) {
                long remainingMs = Math.min(savedBannedElapsed - nowElapsed, MAX_SERVER_BAN_MS);
                bannedUntilTimestamp = nowElapsed + remainingMs;
            }
            if (rawTimestamps != null && !rawTimestamps.isEmpty()) {
                String[] parts = rawTimestamps.split(",");
                for (String part : parts) {
                    try {
                        long ts = Long.parseLong(part.trim());
                        if (ts >= 0L && ts <= nowElapsed && (nowElapsed - ts) <= WINDOW_MS) {
                            requestTimestamps.add(ts);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (bannedUntilTimestamp == 0L && requestTimestamps.isEmpty()) {
                clearPersistedState();
            } else if ((savedBootCount < 0 && currentBootCount >= 0) || lastWall <= 0L) {
                persistState(nowElapsed, nowWall, currentBootCount);
            }
            return;
        }

        requestTimestamps.clear();
        long recoveredRemainingMs = computePostRebootRemainingBanMs(
            nowElapsed,
            nowWall,
            lastElapsed,
            lastWall,
            savedBannedElapsed,
            savedBannedWall
        );

        if (recoveredRemainingMs > 0L) {
            bannedUntilTimestamp = nowElapsed + recoveredRemainingMs;
            persistState(nowElapsed, nowWall, currentBootCount);
        } else {
            bannedUntilTimestamp = 0L;
            clearPersistedState();
        }
    }

    /**
     * Persists current rate-limiter state along with boot counter and synchronized wall/elapsed anchors.
     */
    private synchronized void persistState(long nowElapsed, long nowWall) {
        persistState(nowElapsed, nowWall, bootCountProvider.getBootCount());
    }

    private synchronized void persistState(long nowElapsed, long nowWall, int bootCount) {
        if (prefs == null) return;

        long remainingBanMs = Math.max(0L, bannedUntilTimestamp - nowElapsed);
        long effectiveUntilElapsed = remainingBanMs > 0L ? (nowElapsed + remainingBanMs) : 0L;
        long bannedWall = remainingBanMs > 0L ? (nowWall + remainingBanMs) : 0L;

        StringBuilder sb = new StringBuilder();
        for (Long ts : requestTimestamps) {
            if (sb.length() > 0) sb.append(',');
            sb.append(ts);
        }

        SharedPreferences.Editor editor = prefs.edit()
            .putLong(KEY_BANNED_UNTIL_ELAPSED, effectiveUntilElapsed)
            .putLong(KEY_BANNED_UNTIL_WALL, bannedWall)
            .putLong(KEY_LAST_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_WALL, nowWall)
            .putString(KEY_TIMESTAMPS, sb.toString());

        if (bootCount >= 0) {
            editor.putInt(KEY_BOOT_COUNT, bootCount);
        } else {
            editor.remove(KEY_BOOT_COUNT);
        }

        editor.apply();
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
        long now = clock.elapsedRealtime();
        if (now < bannedUntilTimestamp) {
            return true;
        }
        if (bannedUntilTimestamp > 0L) {
            bannedUntilTimestamp = 0L;
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
        long now = clock.elapsedRealtime();
        long nowWall = clock.currentTimeMillis();

        if (now < bannedUntilTimestamp) {
            return false;
        }
        if (bannedUntilTimestamp > 0L) {
            bannedUntilTimestamp = 0L;
        }

        while (!requestTimestamps.isEmpty() && (now - requestTimestamps.peek()) > WINDOW_MS) {
            requestTimestamps.poll();
        }

        if (requestTimestamps.size() >= MAX_REQUESTS) {
            bannedUntilTimestamp = now + BAN_DURATION_MS;
            requestTimestamps.clear();
            persistState(now, nowWall);
            return false;
        }

        requestTimestamps.add(now);
        persistState(now, nowWall);
        return true;
    }

    /**
     * Synchronizes a server-mandated cooldown duration with the local limiter state.
     *
     * @param seconds Ban duration enforced by the upstream server
     */
    public synchronized void applyServerBan(long seconds) {
        long now = clock.elapsedRealtime();
        long nowWall = clock.currentTimeMillis();
        long clampedSeconds = Math.min(Math.max(seconds, 60L), MAX_SERVER_BAN_MS / 1000L);
        long targetBanUntil = now + (clampedSeconds * 1000L);
        if (targetBanUntil > bannedUntilTimestamp) {
            bannedUntilTimestamp = targetBanUntil;
        }
        requestTimestamps.clear();
        persistState(now, nowWall);
    }

    /**
     * Calculates remaining cooldown time in seconds.
     *
     * @return Remaining cooldown duration in seconds
     */
    public synchronized long getRemainingBanSeconds() {
        long now = clock.elapsedRealtime();
        if (now >= bannedUntilTimestamp) {
            return 0L;
        }
        return (bannedUntilTimestamp - now + 999L) / 1000L;
    }

    /**
     * Clears all recorded timestamps and active bans to restore default state.
     */
    public synchronized void reset() {
        bannedUntilTimestamp = 0L;
        requestTimestamps.clear();
        clearPersistedState();
    }
}
