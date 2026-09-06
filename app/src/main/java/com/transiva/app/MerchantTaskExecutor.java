package com.transiva.app;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded executor for non-network background work such as image preparation. */
public final class MerchantTaskExecutor {
    private static final String TAG = "MerchantTaskExecutor";
    private static final AtomicInteger ID = new AtomicInteger(1);
    private static final ThreadFactory FACTORY = runnable -> {
        Thread t = new Thread(runnable, "transiva-task-" + ID.getAndIncrement());
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    };
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), FACTORY,
            new ThreadPoolExecutor.AbortPolicy());
    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private MerchantTaskExecutor() {}
    public static boolean execute(Runnable task) {
        if (task == null || EXECUTOR.isShutdown()) return false;
        try { EXECUTOR.execute(task); return true; }
        catch (RejectedExecutionException e) { Log.w(TAG, "Background queue penuh; task ditolak aman."); return false; }
    }
}
