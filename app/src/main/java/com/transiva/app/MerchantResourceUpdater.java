package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resource-only updater. It NEVER installs APKs and never requests unknown-source permission.
 * Downloaded files live in app-private storage and are verified before activation.
 */
public final class MerchantResourceUpdater {
    public interface Callback { void onDone(boolean updated, String message); }
    private static final String MANIFEST = "https://transiva.my.id/server/merchant_resource_manifest.php";
    private static final String PREF = "merchant_resource_update";
    private static final String KEY_VERSION = "resource_version";
    private static final int MAX_ZIP_BYTES = 32 * 1024 * 1024;
    private static final int MAX_EXTRACTED_BYTES = 64 * 1024 * 1024;
    private static final int MAX_FILES = 500;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private MerchantResourceUpdater() {}

    public static int installedVersion(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_VERSION, 0);
    }

    public static File activeDir(Context c) { return new File(c.getFilesDir(), "merchant_resources/current"); }

    public static File resolve(Context c, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) return null;
        try {
            File base = activeDir(c).getCanonicalFile();
            File file = new File(base, relativePath).getCanonicalFile();
            if (!file.getPath().startsWith(base.getPath() + File.separator)) return null;
            return file.isFile() ? file : null;
        } catch (Exception e) { return null; }
    }

    public static void checkAsync(Context context, boolean userInitiated, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean updated = false;
            String message;
            try {
                int current = installedVersion(app);
                int appVersion = installedAppVersion(app);
                String manifestUrl = MANIFEST + "?resource_version=" + current + "&app_version_code=" + appVersion;
                JSONObject root = new JSONObject(httpGet(manifestUrl, 12_000, MAX_ZIP_BYTES));
                if (!root.optBoolean("success", false)) throw new IOException(root.optString("message", "Manifest resource gagal."));
                JSONObject data = root.optJSONObject("data");
                if (data == null || !data.optBoolean("update_available", false)) {
                    message = "Resource merchant sudah terbaru (v" + current + ").";
                } else {
                    int version = data.optInt("version", 0);
                    int minApp = data.optInt("min_app_version_code", 0);
                    String url = data.optString("url", "").trim();
                    String sha = data.optString("sha256", "").trim().toLowerCase(Locale.US);
                    long expectedSize = data.optLong("size", 0L);
                    if (version <= current || version <= 0 || url.isEmpty() || sha.length() != 64) throw new IOException("Manifest resource tidak valid.");
                    if (minApp > 0 && appVersion < minApp) throw new IOException("Resource membutuhkan versi aplikasi yang lebih baru dari Play Store.");
                    if (expectedSize <= 0 || expectedSize > MAX_ZIP_BYTES) throw new IOException("Ukuran resource tidak aman.");
                    install(app, version, url, sha, expectedSize);
                    updated = true;
                    message = "Resource merchant diperbarui ke v" + version + ".";
                }
            } catch (Exception e) {
                message = userInitiated ? ("Pembaruan resource gagal: " + safe(e.getMessage())) : "";
            }
            if (callback != null) {
                boolean finalUpdated = updated; String finalMessage = message;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onDone(finalUpdated, finalMessage));
            }
        });
    }

    private static void install(Context c, int version, String url, String sha256, long expectedSize) throws Exception {
        File root = new File(c.getFilesDir(), "merchant_resources");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Folder resource tidak dapat dibuat.");
        File zip = new File(c.getCacheDir(), "merchant_resource_" + version + ".zip");
        download(url, zip, expectedSize);
        if (!sha256.equals(sha256(zip))) { zip.delete(); throw new SecurityException("Checksum resource tidak cocok."); }

        File staging = new File(root, "staging_" + version);
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("Folder staging tidak dapat dibuat.");
        unzipSafe(zip, staging);
        zip.delete();

        File current = new File(root, "current");
        File backup = new File(root, "previous");
        deleteTree(backup);
        if (current.exists() && !current.renameTo(backup)) throw new IOException("Resource aktif tidak dapat dipindahkan.");
        if (!staging.renameTo(current)) {
            if (backup.exists()) backup.renameTo(current);
            throw new IOException("Resource baru tidak dapat diaktifkan.");
        }
        deleteTree(backup);
        SharedPreferences.Editor edit = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
        edit.putInt(KEY_VERSION, version).putLong("updated_at", System.currentTimeMillis()).apply();
    }

    private static void download(String url, File out, long expectedSize) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12_000); c.setReadTimeout(25_000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/zip,application/octet-stream");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) { c.disconnect(); throw new IOException("HTTP " + code); }
        long declared = Build.VERSION.SDK_INT >= 24 ? c.getContentLengthLong() : c.getContentLength();
        if (declared > MAX_ZIP_BYTES || (declared > 0 && expectedSize > 0 && declared != expectedSize)) { c.disconnect(); throw new IOException("Ukuran resource berubah."); }
        long total = 0;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024]; int n;
            while ((n = in.read(buf)) != -1) {
                total += n; if (total > MAX_ZIP_BYTES) throw new IOException("Resource terlalu besar.");
                os.write(buf, 0, n);
            }
            os.flush();
        } finally { c.disconnect(); }
        if (expectedSize > 0 && total != expectedSize) { out.delete(); throw new IOException("Ukuran resource tidak cocok."); }
    }

    private static void unzipSafe(File zip, File dest) throws Exception {
        String base = dest.getCanonicalPath() + File.separator;
        int files = 0; long total = 0;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (++files > MAX_FILES) throw new IOException("Terlalu banyak file resource.");
                File target = new File(dest, e.getName()).getCanonicalFile();
                if (!target.getPath().startsWith(base)) throw new SecurityException("Path resource tidak aman.");
                if (e.isDirectory()) { if (!target.exists() && !target.mkdirs()) throw new IOException("Folder resource gagal dibuat."); }
                else {
                    File parent = target.getParentFile(); if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Folder resource gagal dibuat.");
                    try (OutputStream os = new FileOutputStream(target)) {
                        byte[] buf = new byte[16 * 1024]; int n;
                        while ((n = zin.read(buf)) != -1) { total += n; if (total > MAX_EXTRACTED_BYTES) throw new IOException("Isi resource terlalu besar."); os.write(buf,0,n); }
                    }
                }
                zin.closeEntry();
            }
        }
    }

    private static String httpGet(String url, int timeout, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout); c.setReadTimeout(timeout); c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode(); InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n, total = 0;
        while (in != null && (n = in.read(buf)) != -1) { total += n; if (total > maxBytes) throw new IOException("Respons terlalu besar."); out.write(buf,0,n); }
        if (in != null) in.close(); c.disconnect();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        return out.toString("UTF-8");
    }

    private static int installedAppVersion(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 28) return (int)Math.min(Integer.MAX_VALUE, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode());
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[16384]; int n; while ((n=in.read(b))!=-1) md.update(b,0,n); }
        StringBuilder sb = new StringBuilder(); for (byte b: md.digest()) sb.append(String.format(Locale.US, "%02x", b & 0xff)); return sb.toString();
    }
    private static void deleteTree(File f) { if (f == null || !f.exists()) return; File[] kids=f.listFiles(); if(kids!=null) for(File k:kids) deleteTree(k); try{f.delete();}catch(Exception ignored){} }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "Koneksi tidak tersedia." : s.trim(); }
}
