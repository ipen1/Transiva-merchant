package com.transiva.app;

import android.util.Log;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P2 network scheduler.
 *
 * READ:
 * - bounded 3..5 workers / 96 queue
 * - may reject a NEW stale read when saturated
 * - single-flight per Activity(owner)+key
 * - cancellable when Activity is destroyed
 *
 * WRITE:
 * - dedicated 2-worker queue
 * - never shares the droppable READ queue
 * - not cancelled by Activity lifecycle after submission
 */
public final class MerchantNetworkExecutor {
    private static final String TAG = "MerchantNetExecutor";

    private static final int READ_CORE_THREADS = 3;
    private static final int READ_MAX_THREADS = 5;
    private static final int READ_QUEUE_CAPACITY = 96;
    private static final int WRITE_THREADS = 2;

    private static final AtomicInteger READ_ID = new AtomicInteger(1);
    private static final AtomicInteger WRITE_ID = new AtomicInteger(1);

    private static ThreadFactory factory(String prefix, AtomicInteger id) {
        return runnable -> {
            Thread t = new Thread(runnable, prefix + id.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
    }

    private static final ThreadPoolExecutor READ_EXECUTOR = new ThreadPoolExecutor(
            READ_CORE_THREADS,
            READ_MAX_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(READ_QUEUE_CAPACITY),
            factory("transiva-read-", READ_ID),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final ThreadPoolExecutor WRITE_EXECUTOR = new ThreadPoolExecutor(
            WRITE_THREADS,
            WRITE_THREADS,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            factory("transiva-write-", WRITE_ID),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final ConcurrentHashMap<String, Boolean> READ_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Object, Set<Future<?>>> OWNER_READS = new ConcurrentHashMap<>();

    static {
        READ_EXECUTOR.allowCoreThreadTimeOut(true);
        WRITE_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private MerchantNetworkExecutor() {}

    /**
     * Runs a read request with single-flight deduplication.
     * @return Future when accepted, null when duplicate/rejected.
     */
    public static Future<?> executeRead(Object owner, String key, Runnable task) {
        if (task == null || READ_EXECUTOR.isShutdown()) return null;

        final String normalizedKey = key == null ? "read" : key.trim();
        final String flightKey = ownerKey(owner) + "|" + normalizedKey;
        if (READ_IN_FLIGHT.putIfAbsent(flightKey, Boolean.TRUE) != null) {
            Log.d(TAG, "Skipped duplicate read: " + normalizedKey);
            return null;
        }

        try {
            Future<?> future = READ_EXECUTOR.submit(() -> {
                try {
                    if (!Thread.currentThread().isInterrupted()) task.run();
                } finally {
                    READ_IN_FLIGHT.remove(flightKey);
                }
            });
            if (owner != null) {
                Set<Future<?>> ownerSet = OWNER_READS.get(owner);
                if (ownerSet == null) {
                    Set<Future<?>> created = Collections.newSetFromMap(new ConcurrentHashMap<Future<?>, Boolean>());
                    Set<Future<?>> existing = OWNER_READS.putIfAbsent(owner, created);
                    ownerSet = existing == null ? created : existing;
                }
                // P3: long-lived owners (especially Application for session/update checks)
                // must not retain every completed Future forever.
                for (Future<?> old : ownerSet) {
                    if (old == null || old.isDone() || old.isCancelled()) ownerSet.remove(old);
                }
                ownerSet.add(future);
            }
            return future;
        } catch (RejectedExecutionException rejected) {
            READ_IN_FLIGHT.remove(flightKey);
            Log.w(TAG, "Read queue saturated; skipped new read: " + normalizedKey);
            return null;
        }
    }

    /** Convenience read without lifecycle owner. */
    public static Future<?> executeRead(String key, Runnable task) {
        return executeRead(null, key, task);
    }

    /**
     * Writes are isolated from READ saturation and are never intentionally dropped/cancelled.
     */
    public static void executeWrite(Runnable task) {
        if (task == null || WRITE_EXECUTOR.isShutdown()) return;
        try {
            WRITE_EXECUTOR.execute(task);
        } catch (RejectedExecutionException rejected) {
            // This should only happen during executor shutdown; execute inline as the safest
            // last resort so a user-initiated mutation is not silently lost.
            Log.w(TAG, "Write executor unavailable; running write inline as last resort");
            task.run();
        }
    }

    /** Cancel only cancellable GET/read work owned by a destroyed Activity. */
    public static void cancelReads(Object owner) {
        if (owner == null) return;
        Set<Future<?>> futures = OWNER_READS.remove(owner);
        if (futures != null) {
            for (Future<?> future : futures) {
                if (future != null && !future.isDone()) future.cancel(true);
            }
        }
        String prefix = ownerKey(owner) + "|";
        for (String key : READ_IN_FLIGHT.keySet()) {
            if (key.startsWith(prefix)) READ_IN_FLIGHT.remove(key);
        }
    }

    public static int queuedReads() { return READ_EXECUTOR.getQueue().size(); }
    public static int queuedWrites() { return WRITE_EXECUTOR.getQueue().size(); }
    public static int activeReads() { return READ_EXECUTOR.getActiveCount(); }
    public static int activeWrites() { return WRITE_EXECUTOR.getActiveCount(); }

    private static String ownerKey(Object owner) {
        return owner == null ? "global" : owner.getClass().getName() + "@" + System.identityHashCode(owner);
    }
}
