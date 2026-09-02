package com.transiva.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.messaging.FirebaseMessaging;

import java.lang.ref.WeakReference;

/** Global foreground hook for the Merchant force-update gate. */
public class TransivaMerchantApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile TransivaMerchantApplication instance;
    private static volatile WeakReference<Activity> currentActivity =
            new WeakReference<>(null);

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(this);

        // Global ON/OFF Merchant dikirim melalui topic ini.
        try {
            FirebaseMessaging.getInstance()
                    .subscribeToTopic("transiva_merchant_security");
        } catch (Throwable ignored) { }

        // Resource-only update: no APK install / unknown-source permission.
        MerchantResourceUpdater.checkAsync(this, false, null);
    }

    @Override public void onActivityResumed(Activity activity) {
        currentActivity = new WeakReference<>(activity);

        // Security guard tetap berjalan seperti sebelumnya.
        if (!(activity instanceof SplashActivity)) {
            try {
                SessionManager session = new SessionManager(activity);

                if (session.isLoggedIn()
                        && "merchant".equalsIgnoreCase(session.getRole())) {
                    RootSecurityGuard.protect(activity);
                }
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Dipanggil dari FCM saat admin mengubah Keamanan Merchant.
     * Database/server tetap source of truth; FCM hanya trigger refresh.
     */
    public static void onSecurityPolicyChanged() {
        TransivaMerchantApplication app = instance;
        if (app == null) return;

        RootSecurityGuard.invalidatePolicyCache(app);

        app.main.post(() -> {
            Activity activity = currentActivity.get();

            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()
                    || activity instanceof SplashActivity) {
                return;
            }

            try {
                SessionManager session = new SessionManager(activity);

                if (!session.isLoggedIn()
                        || !"merchant".equalsIgnoreCase(session.getRole())) {
                    return;
                }
            } catch (Throwable ignored) {
                return;
            }

            RootSecurityGuard.protectFresh(activity);
        });
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) {
        Activity current = currentActivity.get();

        if (current == activity) {
            currentActivity = new WeakReference<>(null);
        }
    }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
