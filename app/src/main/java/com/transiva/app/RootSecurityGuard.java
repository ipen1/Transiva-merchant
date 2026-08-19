package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Conservative root/hooking detector. Heavy checks run outside the UI thread. */
public final class RootSecurityGuard {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static WeakReference<AlertDialog> dialogRef = new WeakReference<>(null);

    // Remote security policy. Default is secure/enabled.
    private static final String POLICY_URL =
            "https://transiva.my.id/server/merchant_security_policy.php";
    private static final String POLICY_PREF = "merchant_security_policy";
    private static final long POLICY_CACHE_MS = 5L * 60L * 1000L;

    private static final String[] ROOT_PACKAGES = {
            "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu",
            "com.rifsxd.ksunext", "me.bmax.apatch", "org.lsposed.manager",
            "de.robv.android.xposed.installer", "com.saurik.substrate", "com.devadvance.rootcloak",
            "com.thirdparty.superuser", "eu.chainfire.supersu", "com.koushikdutta.superuser"
    };

    private static final String[] ROOT_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk", "/data/adb/magisk",
            "/data/adb/ksu", "/data/adb/ap", "/data/adb/modules", "/cache/su"
    };

    private RootSecurityGuard() { }

    public static void checkBeforeContinue(Activity activity, Runnable onAllowed) {
        check(activity, onAllowed);
    }

    public static void protect(Activity activity) {
        check(activity, null);
    }

    private static void check(Activity activity, Runnable onAllowed) {
        if (!usable(activity)) return;
        if (!RUNNING.compareAndSet(false, true)) {
            if (onAllowed != null) MAIN.postDelayed(() -> check(activity, onAllowed), 300L);
            return;
        }
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            boolean detectionEnabled = true;
            try {
                detectionEnabled = resolveDetectionEnabled(app);
            } catch (Throwable ignored) {
                // Fail-secure: jika policy tidak dapat dibaca dan cache belum ada,
                // perlindungan tetap aktif.
                detectionEnabled = cachedDetectionEnabled(app, true);
            }

            Detection result;
            if (!detectionEnabled) {
                result = Detection.clean();
            } else {
                try { result = detect(app); }
                catch (Throwable ignored) { result = Detection.clean(); }
            }

            Detection finalResult = result;
            boolean finalDetectionEnabled = detectionEnabled;
            MAIN.post(() -> {
                RUNNING.set(false);
                if (!usable(activity)) return;
                if (!finalDetectionEnabled) {
                    dismiss();
                    if (onAllowed != null) onAllowed.run();
                    return;
                }
                if (finalResult.blocked) showBlocked(activity, finalResult.reason);
                else {
                    dismiss();
                    if (onAllowed != null) onAllowed.run();
                }
            });
        });
    }


    /**
     * Effective policy:
     * 1) account override (jika user sudah login),
     * 2) global database setting,
     * 3) default ON.
     *
     * Response sengaja hanya boolean policy agar endpoint tidak membocorkan data akun.
     */
    private static boolean resolveDetectionEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(POLICY_PREF, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long cachedAt = prefs.getLong("checked_at", 0L);

        // Cache sebentar supaya setiap perpindahan Activity tidak menembak server.
        if (cachedAt > 0L && now - cachedAt < POLICY_CACHE_MS) {
            return prefs.getBoolean("root_detection_enabled", true);
        }

        String userId = "";
        String username = "";
        try {
            SessionManager session = new SessionManager(context);
            userId = session.getUserId();
            username = session.getUsername();
        } catch (Throwable ignored) { }

        HttpURLConnection connection = null;
        try {
            StringBuilder link = new StringBuilder(POLICY_URL);
            link.append("?app=merchant");
            if (userId != null && !userId.trim().isEmpty()) {
                link.append("&user_id=").append(android.net.Uri.encode(userId.trim()));
            }
            if (username != null && !username.trim().isEmpty()) {
                link.append("&username=").append(android.net.Uri.encode(username.trim()));
            }

            connection = (HttpURLConnection) new URL(link.toString()).openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Transiva-App", "Android-Merchant");

            int code = connection.getResponseCode();
            java.io.InputStream input = code >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();

            StringBuilder body = new StringBuilder();
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
            }
            if (code < 200 || code >= 300) {
                return cachedDetectionEnabled(context, true);
            }

            JSONObject json = new JSONObject(body.toString());
            if (!json.optBoolean("success", false)) {
                return cachedDetectionEnabled(context, true);
            }

            boolean enabled = json.optBoolean("root_detection_enabled", true);
            prefs.edit()
                    .putBoolean("root_detection_enabled", enabled)
                    .putLong("checked_at", now)
                    .apply();
            return enabled;
        } catch (Throwable ignored) {
            return cachedDetectionEnabled(context, true);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean cachedDetectionEnabled(Context context, boolean fallback) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(POLICY_PREF, Context.MODE_PRIVATE);
            if (prefs.contains("root_detection_enabled")) {
                return prefs.getBoolean("root_detection_enabled", fallback);
            }
        } catch (Throwable ignored) { }
        return fallback;
    }

    /** Paksa policy dibaca ulang pada login/logout berikutnya bila dibutuhkan. */
    public static void invalidatePolicyCache(Context context) {
        try {
            context.getSharedPreferences(POLICY_PREF, Context.MODE_PRIVATE)
                    .edit()
                    .remove("checked_at")
                    .apply();
        } catch (Throwable ignored) { }
    }

    private static Detection detect(Context context) {
        int score = 0;
        StringBuilder evidence = new StringBuilder();
        if (hasKnownPackage(context)) { score += 1; evidence.append("manager root terpasang; "); }
        int pathHits = 0;
        for (String path : ROOT_PATHS) if (new File(path).exists()) pathHits++;
        if (pathHits > 0) { score += Math.min(3, pathHits); evidence.append("komponen root ditemukan; "); }

        String tags = Build.TAGS == null ? "" : Build.TAGS.toLowerCase(Locale.US);
        if (tags.contains("test-keys")) { score += 1; evidence.append("test-keys; "); }
        String props = readCommand("getprop");
        if (props.contains("[ro.debuggable]: [1]") || props.contains("[ro.secure]: [0]")) { score += 2; evidence.append("properti sistem tidak aman; "); }
        String whichSu = readCommand("sh", "-c", "command -v su 2>/dev/null").trim();
        if (!whichSu.isEmpty()) { score += 3; evidence.append("binary su aktif; "); }

        String maps = readFile("/proc/self/maps").toLowerCase(Locale.US);
        for (String hook : Arrays.asList("xposed", "lsposed", "zygisk", "frida", "substrate")) {
            if (maps.contains(hook)) { score += 3; evidence.append(hook).append(" aktif; "); break; }
        }
        // A single weak signal no longer blocks custom ROM or an installed-but-disabled manager.
        return score >= 4 ? Detection.blocked("Indikasi modifikasi sistem terkonfirmasi (skor " + score + "): " + evidence) : Detection.clean();
    }

    private static boolean hasKnownPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String name : ROOT_PACKAGES) {
            try { pm.getPackageInfo(name, 0); return true; }
            catch (PackageManager.NameNotFoundException ignored) { }
            catch (Throwable ignored) { }
        }
        return false;
    }

    private static String readCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; int count = 0;
                while ((line = reader.readLine()) != null && count++ < 400) out.append(line).append('\n');
            }
            process.waitFor();
            return out.toString();
        } catch (Throwable ignored) { return ""; }
        finally { if (process != null) process.destroy(); }
    }

    private static String readFile(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path)))) {
            StringBuilder out = new StringBuilder(); String line; int count = 0;
            while ((line = reader.readLine()) != null && count++ < 3000) out.append(line).append('\n');
            return out.toString();
        } catch (Throwable ignored) { return ""; }
    }

    private static void showBlocked(Activity activity, String reason) {
        AlertDialog existing = dialogRef.get();
        if (existing != null && existing.isShowing()) return;
        try {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Perangkat tidak aman")
                    .setMessage(reason + "\n\nDemi keamanan akun dan transaksi, Transiva tidak dapat dijalankan pada perangkat yang di-root atau dimodifikasi.")
                    .setCancelable(false)
                    .setPositiveButton("Buka pengaturan", (d, w) -> openSettings(activity))
                    .setNegativeButton("Tutup aplikasi", (d, w) -> activity.finishAffinity())
                    .create();
            dialog.setOnDismissListener(d -> dialogRef.clear());
            dialogRef = new WeakReference<>(dialog);
            dialog.show();
        } catch (Throwable ignored) {
            Toast.makeText(activity, "Perangkat root/modifikasi terdeteksi.", Toast.LENGTH_LONG).show();
            MAIN.postDelayed(activity::finishAffinity, 1500L);
        }
    }

    private static void openSettings(Activity activity) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (Throwable ignored) { }
    }

    private static void dismiss() {
        AlertDialog d = dialogRef.get();
        if (d != null && d.isShowing()) try { d.dismiss(); } catch (Throwable ignored) { }
        dialogRef.clear();
    }

    private static boolean usable(Activity a) {
        return a != null && !a.isFinishing() && (Build.VERSION.SDK_INT < 17 || !a.isDestroyed());
    }

    private static final class Detection {
        final boolean blocked; final String reason;
        private Detection(boolean blocked, String reason) { this.blocked = blocked; this.reason = reason; }
        static Detection blocked(String reason) { return new Detection(true, reason); }
        static Detection clean() { return new Detection(false, ""); }
    }
}
