package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.OpenableColumns;
import androidx.exifinterface.media.ExifInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;

public class MerchantBaseActivity extends Activity {
    protected static final String BASE = "https://transiva.my.id/server/";
    protected SessionManager sessionManager;
    protected final int BLUE = Color.parseColor("#1677F2");
    protected final int NAVY = Color.parseColor("#0A1A2E");
    protected final int TEXT = Color.parseColor("#172033");
    protected final int MUTED = Color.parseColor("#667085");
    protected final int BG = Color.parseColor("#F5F8FF");

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
        } catch(Exception ignored){}
        sessionManager = new SessionManager(this);
    }

    @Override protected void onResume(){
        super.onResume();
        MerchantAppSettings.apply(this);
        RootSecurityGuard.protect(this);
        SessionValidationClient.validate(this);
    }

    @Override protected void onDestroy(){
        // P2: only GET/read work is lifecycle-cancellable. Submitted writes are never cancelled.
        MerchantNetworkExecutor.cancelReads(this);
        super.onDestroy();
    }

    protected String username(){
        try { String v = sessionManager.getUsername(); if(v != null && !v.trim().isEmpty()) return v.trim(); } catch(Exception ignored){}
        return getSharedPreferences("transiva_fcm", MODE_PRIVATE).getString("username", "");
    }

    protected int userId(){
        try {
            Method m = sessionManager.getClass().getMethod("getUserId");
            Object v = m.invoke(sessionManager);
            if(v instanceof Integer && (Integer)v > 0) return (Integer)v;
            if(v instanceof String && Integer.parseInt((String)v) > 0) return Integer.parseInt((String)v);
        } catch(Exception ignored){}
        try {
            Method m = sessionManager.getClass().getMethod("getId");
            Object v = m.invoke(sessionManager);
            if(v instanceof Integer && (Integer)v > 0) return (Integer)v;
            if(v instanceof String && Integer.parseInt((String)v) > 0) return Integer.parseInt((String)v);
        } catch(Exception ignored){}
        int id = getSharedPreferences("transiva_fcm", MODE_PRIVATE).getInt("user_id", 0);
        if(id > 0) return id;
        return getSharedPreferences("transiva", MODE_PRIVATE).getInt("user_id", 0);
    }

    protected View page(LinearLayout root){
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        root.setBackgroundColor(BG);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View bottom = MerchantBottomNavigation.build(this, MerchantBottomNavigation.resolve(this));
        shell.addView(bottom, new LinearLayout.LayoutParams(-1, dp(66)));
        return shell;
    }

    protected TextView title(String text){
        TextView v = tv(text, 24, NAVY, true);
        v.setPadding(dp(4), dp(4), dp(4), dp(2));
        return v;
    }

    protected TextView sub(String text){
        TextView v = tv(text, 13, MUTED, false);
        v.setPadding(dp(4), 0, dp(4), dp(14));
        return v;
    }

    protected TextView tv(String text, int sp, int color, boolean bold){
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(true);
        if(bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    protected TextView card(String text){
        TextView v = tv(text, 14, TEXT, false);
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.setBackground(round(Color.WHITE, dp(18)));
        v.setLineSpacing(dp(2), 1f);
        v.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        v.setLayoutParams(lp);
        return v;
    }

    protected Button btn(String text){
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(BLUE, dp(16)));
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(5), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    protected Button outlineBtn(String text){
        Button b = btn(text);
        b.setTextColor(BLUE);
        b.setBackground(stroke(Color.WHITE, Color.parseColor("#BBD9FF"), dp(16)));
        return b;
    }

    protected EditText input(String hint, int type){
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(false);
        e.setMinLines(1);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.parseColor("#98A2B3"));
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(stroke(Color.WHITE, Color.parseColor("#DDE7F3"), dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(4), 0, dp(12));
        e.setLayoutParams(lp);
        return e;
    }

    protected TextView label(String s){
        TextView v = tv(s, 13, NAVY, true);
        v.setPadding(dp(4), dp(4), dp(4), 0);
        return v;
    }

    protected LinearLayout row(){
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, 0, 0, dp(8));
        return r;
    }

    protected GradientDrawable round(int color, int radius){
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
    protected GradientDrawable stroke(int color, int stroke, int radius){
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(1), stroke);
        return g;
    }

    protected int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    protected String enc(String v){ try{return URLEncoder.encode(v == null ? "" : v, "UTF-8");}catch(Exception e){return "";} }
    protected String rupiah(long v){ return "Rp " + NumberFormat.getNumberInstance(new Locale("id","ID")).format(v); }
    protected String s(JSONObject o, String... keys){
        if(o == null) return "";
        for(String k: keys){ String v = o.optString(k, ""); if(v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v)) return v; }
        return "";
    }

    protected void toast(String msg){ Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    protected void alert(String title, String msg){ new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }
    protected void open(Class<?> c){ startActivity(new Intent(this, c)); }
    protected void backMerchant(){ finish(); }

    private void applyMerchantAuth(HttpURLConnection c) {
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("X-Transiva-App", "Android-Merchant");
        c.setRequestProperty("X-App-Scope", "merchant");
        c.setRequestProperty("X-Android-SDK", String.valueOf(Build.VERSION.SDK_INT));
        String token = "";
        try { token = sessionManager == null ? "" : sessionManager.getToken().trim(); } catch(Exception ignored){}
        if(!token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));
        }
    }

    private String response(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = read(is);

        // Jangan otomatis logout hanya karena HTTP 403. Beberapa endpoint dapat
        // memakai 403 untuk izin fitur/merchant, bukan berarti token sesi mati.
        if(code == 401) {
            String apiCode = "";
            try { apiCode = new JSONObject(out).optString("code", ""); } catch(Exception ignored){}
            final String finalCode = apiCode;
            if(finalCode.isEmpty()
                    || "UNAUTHORIZED".equalsIgnoreCase(finalCode)
                    || "SESSION_EXPIRED".equalsIgnoreCase(finalCode)
                    || "SESSION_REVOKED".equalsIgnoreCase(finalCode)
                    || "INVALID_TOKEN".equalsIgnoreCase(finalCode)) {
                runOnUiThread(() -> {
                    toast("Sesi merchant berakhir. Silakan login ulang.");
                    logout();
                });
            }
        }
        return out;
    }

    private HttpURLConnection openConnection(String link) throws Exception {
        HttpURLConnection c = MerchantApiClient.open(this, link);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setDoInput(true);
        return c;
    }

    protected String get(String link) throws Exception {
        Exception last = null;
        // GET aman diulang. Retry membantu saat koneksi pooled/TLS lama putus
        // setelah perubahan sertifikat, CDN, DNS, atau jaringan seluler.
        for(int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c = null;
            try {
                c = openConnection(link);
                c.setRequestMethod("GET");
                return response(c);
            } catch(SocketTimeoutException | UnknownHostException | SSLException e) {
                last = e;
                if(attempt == 0) {
                    try { Thread.sleep(350L); } catch(InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            } finally {
                if(c != null) c.disconnect();
            }
        }
        throw last == null ? new IOException("Koneksi merchant gagal") : last;
    }

    protected String postJson(String link, JSONObject payload) throws Exception {
        HttpURLConnection c = null;
        try {
            c = openConnection(link);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            OutputStream os = c.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8")); os.flush(); os.close();
            return response(c);
        } finally {
            if(c != null) c.disconnect();
        }
    }


    protected static class PreparedImage {
        public final Uri uri;
        public final String mimeType;
        public final String fileName;
        public final long originalBytes;
        public final long finalBytes;
        public final boolean transformed;

        PreparedImage(Uri uri, String mimeType, String fileName, long originalBytes, long finalBytes, boolean transformed) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.fileName = fileName;
            this.originalBytes = originalBytes;
            this.finalBytes = finalBytes;
            this.transformed = transformed;
        }
    }

    /** AI Resize to WebP: file kecil dipertahankan, file besar di-resize + WebP. */
    protected PreparedImage prepareAiResizeToWebp(Uri source, String prefix, int maxDimension, long optimizeAboveBytes, long targetBytes) throws Exception {
        if (source == null) return null;
        long originalBytes = contentLength(source);
        String originalMime = safeImageMime(getContentResolver().getType(source));
        String originalName = resolveDisplayName(source, prefix + "_image");

        if (originalBytes > 0 && originalBytes <= optimizeAboveBytes) {
            String ext = extensionForMime(originalMime);
            if (!originalName.toLowerCase(Locale.US).endsWith(ext)) originalName = prefix + "_" + System.currentTimeMillis() + ext;
            return new PreparedImage(source, originalMime, originalName, originalBytes, originalBytes, false);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(source)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("File gambar tidak dapat dibaca.");

        int sample = 1;
        int biggest = Math.max(bounds.outWidth, bounds.outHeight);
        while (biggest / sample > Math.max(maxDimension * 2, 1600)) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(source)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
        if (bitmap == null) throw new IllegalArgumentException("Gambar gagal didekode.");

        int rotation = readExifRotation(source);
        if (rotation != 0) {
            Matrix matrix = new Matrix(); matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            bitmap = rotated;
        }

        int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxSide > maxDimension) {
            float scale = maxDimension / (float) maxSide;
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth()*scale)), Math.max(1, Math.round(bitmap.getHeight()*scale)), true);
            if (resized != bitmap) bitmap.recycle();
            bitmap = resized;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = 78;
        Bitmap.CompressFormat webpFormat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        while (true) {
            out.reset();
            if (!bitmap.compress(webpFormat, quality, out)) { bitmap.recycle(); throw new IOException("Gagal membuat WebP."); }
            if (out.size() <= targetBytes || quality <= 52) break;
            quality -= 6;
        }
        bitmap.recycle();

        File dir = new File(getCacheDir(), "ai_resize_webp");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cache AI Resize tidak dapat dibuat.");
        File output = new File(dir, prefix + "_" + System.currentTimeMillis() + ".webp");
        try (FileOutputStream fos = new FileOutputStream(output)) { out.writeTo(fos); fos.flush(); }
        long finalBytes = output.length();
        return new PreparedImage(Uri.fromFile(output), "image/webp", output.getName(), originalBytes > 0 ? originalBytes : finalBytes, finalBytes, true);
    }

    protected String postFormPrepared(String link, JSONObject fields, PreparedImage image, String fileField) throws Exception {
        return postMultipartInternal(link, fields, image == null ? null : image.uri, fileField,
                image == null ? "" : image.fileName, image == null ? "application/octet-stream" : image.mimeType);
    }

    protected void setButtonLoading(Button button, boolean loading, String normalText, String loadingText) {
        if (button == null) return;
        button.setEnabled(!loading);
        if (!loading) {
            button.setText(normalText); button.setCompoundDrawables(null, null, null, null); button.setCompoundDrawablePadding(0); return;
        }
        button.setText(loadingText);
        ProgressBar progress = new ProgressBar(this);
        Drawable drawable = progress.getIndeterminateDrawable();
        if (drawable != null) {
            try { drawable.setTint(button.getCurrentTextColor()); } catch (Exception ignored) {}
            int size = dp(18); drawable.setBounds(0,0,size,size);
            button.setCompoundDrawables(drawable, null, null, null); button.setCompoundDrawablePadding(dp(8));
            if (drawable instanceof Animatable) ((Animatable) drawable).start();
        }
    }

    protected String humanBytes(long bytes) {
        if (bytes <= 0) return "0 KB";
        if (bytes < 1024L*1024L) return Math.max(1, Math.round(bytes/1024f)) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes/(1024f*1024f));
    }

    private long contentLength(Uri uri) {
        try (AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r")) { if (afd != null && afd.getLength() >= 0) return afd.getLength(); } catch(Exception ignored) {}
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return -1; byte[] buf = new byte[8192]; long total=0; int n; while((n=in.read(buf))!=-1) total += n; return total;
        } catch(Exception ignored) { return -1; }
    }

    private String resolveDisplayName(Uri uri, String fallback) {
        if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) { String name = cursor.getString(0); if (name != null && !name.trim().isEmpty()) return name.trim(); }
            } catch(Exception ignored) {}
        }
        String path = uri == null ? "" : uri.getLastPathSegment();
        if (path != null && !path.trim().isEmpty()) return new File(path).getName();
        return fallback;
    }

    private String safeImageMime(String mime) {
        String m = mime == null ? "" : mime.trim().toLowerCase(Locale.US);
        if ("image/png".equals(m) || "image/webp".equals(m) || "image/jpeg".equals(m)) return m;
        return "image/jpeg";
    }
    private String extensionForMime(String mime) {
        if ("image/png".equalsIgnoreCase(mime)) return ".png";
        if ("image/webp".equalsIgnoreCase(mime)) return ".webp";
        return ".jpg";
    }
    private int readExifRotation(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch(Exception ignored) {}
        return 0;
    }

    private String postMultipartInternal(String link, JSONObject fields, Uri fileUri, String fileField, String fileName, String mimeType) throws Exception {
        String boundary = "----Transiva" + System.currentTimeMillis();
        HttpURLConnection c = MerchantApiClient.open(this, link);
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setDoOutput(true); c.setUseCaches(false); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary); applyMerchantAuth(c);
        OutputStream os = c.getOutputStream();
        java.util.Iterator<String> keys = fields.keys();
        while(keys.hasNext()) { String key=keys.next(); write(os,"--"+boundary+"\r\n"); write(os,"Content-Disposition: form-data; name=\""+key+"\"\r\n\r\n"); write(os,fields.optString(key,"")+"\r\n"); }
        if (fileUri != null) {
            write(os,"--"+boundary+"\r\n"); write(os,"Content-Disposition: form-data; name=\""+fileField+"\"; filename=\""+fileName+"\"\r\n");
            write(os,"Content-Type: "+mimeType+"\r\n\r\n");
            try (InputStream in = getContentResolver().openInputStream(fileUri)) {
                if (in == null) throw new IOException("File upload tidak dapat dibaca.");
                byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) os.write(buf,0,n);
            }
            write(os,"\r\n");
        }
        write(os,"--"+boundary+"--\r\n"); os.flush(); os.close(); String out=response(c); c.disconnect(); return out;
    }

    protected String postForm(String link, JSONObject fields, Uri fileUri, String fileField, String fileName) throws Exception {
        return postMultipartInternal(link, fields, fileUri, fileField, fileName, "image/jpeg");
    }

    private void write(OutputStream os, String s) throws Exception { os.write(s.getBytes("UTF-8")); }
    protected String read(InputStream is) throws Exception {
        if(is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while((line = br.readLine()) != null) sb.append(line);
        br.close(); return sb.toString();
    }

    protected void logout(){
        try { sessionManager.clearSession(); } catch(Exception ignored){}
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
}
