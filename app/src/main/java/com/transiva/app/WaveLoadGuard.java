package com.transiva.app;

import java.util.concurrent.ThreadLocalRandom;

/** Anti-thundering-herd helper for fallback polling. */
public final class WaveLoadGuard {
    private WaveLoadGuard() {}
    public static long jitter(long baseMs) {
        if (baseMs <= 0L) return 1000L;
        long spread = Math.max(1L, (baseMs * 18L) / 100L);
        long delta = ThreadLocalRandom.current().nextLong(-spread, spread + 1L);
        return Math.max(1000L, baseMs + delta);
    }
}
