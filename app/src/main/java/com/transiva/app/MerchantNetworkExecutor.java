package com.transiva.app;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bounded executor for Merchant network / short I/O work.
 * Prevents every screen from creating an unbounded number of raw Threads.
 */
public final class MerchantNetworkExecutor {
    private static final String TAG = "MerchantNetExecutor";
    private static final int CORE_THREADS = 3;
    private static final int MAX_THREADS = 5;
    private static final int QUEUE_CAPACITY = 96;

    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    private static final ThreadFactory FACTORY = runnable -> {
        Thread t = new Thread(runnable, "transiva-net-" + THREAD_ID.getAndIncrement());
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    };

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_THREADS,
            MAX_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            FACTORY,
            (runnable, executor) -> {
                // Queue is intentionally bounded. Drop the oldest stale queued read/work
                // rather than blocking the UI thread or spawning unlimited threads.
                Runnable dropped = executor.getQueue().poll();
                if (dropped != null && executor.getQueue().offer(runnable)) {
                    Log.w(TAG, "Network queue saturated; replaced one stale queued task");
                } else {
                    Log.w(TAG, "Network queue saturated; task rejected safely");
                }
            }
    );

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private MerchantNetworkExecutor() {}

    public static void execute(Runnable task) {
        if (task == null || EXECUTOR.isShutdown()) return;
        EXECUTOR.execute(task);
    }

    public static int queuedTasks() {
        return EXECUTOR.getQueue().size();
    }
}
