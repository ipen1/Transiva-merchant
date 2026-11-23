package com.transiva.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MerchantPosActivity extends MerchantBaseActivity {
    private static final String POS = BASE + "merchant_pos.php";
    private static final String ASSET_ORIGIN = "https://appassets.androidplatform.net";
    private static final String PAGE = ASSET_ORIGIN + "/pos/index.html";
    private static final int IMPORT = 718, EXPORT = 719, QR_IMAGE = 720;
    private static final int MAX_FILE = 300000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private String exportedCsv;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (sessionManager.getToken().isEmpty()) {
            finish();
            return;
        }
        if (state != null) exportedCsv = state.getString("export_csv");
        FrameLayout shell = new FrameLayout(this);
        web = new WebView(this);
        shell.addView(web, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        setContentView(shell);
        MerchantWindowInsets.requestWhenAttached(shell);

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new PosBridge(), "Transiva");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String mime = "";
                if (PAGE.equals(url)) mime = "text/html";
                if ((ASSET_ORIGIN + "/pos/pos.css").equals(url)) mime = "text/css";
                if ((ASSET_ORIGIN + "/pos/pos.js").equals(url)) mime = "application/javascript";
                if (!mime.isEmpty() && request.getMethod().equals("GET")) {
                    try {
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(request.getUrl().getPath().substring(1)));
                    } catch (IOException ignored) {}
                }
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.loadUrl(PAGE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (exportedCsv != null && exportedCsv.length() <= MAX_FILE) state.putString("export_csv", exportedCsv);
    }

    @Override public void onBackPressed() {
        evaluate("back()");
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        if (web != null) {
            web.removeJavascriptInterface("Transiva");
            ((FrameLayout) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void evaluate(String script) {
        runOnUiThread(() -> {
            if (web != null && !isFinishing()) web.evaluateJavascript(script, null);
        });
    }

    private final class PosBridge {
        @JavascriptInterface public boolean staffMode() { return isStaff(); }
        @JavascriptInterface public String actorName() { return username(); }
        @JavascriptInterface public String selectedOutlet() { return branchId(); }
        @JavascriptInterface public void request(String id, String action, String outlet, String body) {
            if (!id.matches("[0-9]{1,9}") || !outlet.matches("[1-9][0-9]{0,9}|")
                    || body.length() > 2000000 || worker.isShutdown()) return;
            worker.execute(() -> {
                JSONObject result;
                try {
                    String link = POS + (outlet.isEmpty() ? "" : "?outlet=" + outlet);
                    switch (action) {
                        case "branches":
                            result = new JSONObject(get(BASE + "merchant_branches.php"));
                            break;
                        case "load":
                            result = new JSONObject(get(link));
                            break;
                        case "save":
                            result = new JSONObject(postJson(link, new JSONObject(body)));
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                } catch (Exception error) {
                    result = new JSONObject();
                    try {
                        result.put("message", "Koneksi terputus. Periksa jaringan dan coba lagi.");
                        result.put("uncertain", action.equals("save"));
                    } catch (Exception ignored) {}
                }
                evaluate("reply(" + id + "," + JSONObject.quote(result.toString()) + ")");
            });
        }

        @JavascriptInterface public void exit() { runOnUiThread(() -> finish()); }
        @JavascriptInterface public void branches() { runOnUiThread(() -> { if (!isStaff()) open(MerchantBranchesActivity.class); }); }
        @JavascriptInterface public void scan() {
            runOnUiThread(() -> new IntentIntegrator(MerchantPosActivity.this)
                    .setPrompt("Arahkan kamera ke kode batang produk")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .initiateScan());
        }

        @JavascriptInterface public void importCsv() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, IMPORT);
            });
        }

        @JavascriptInterface public void pickQrImage() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, QR_IMAGE);
            });
        }

        @JavascriptInterface public void exportCsv(String filename, String content) {
            if (!filename.matches("[a-z-]{1,40}\\.csv") || content.length() > MAX_FILE) {
                evaluate("notice('File melebihi batas ekspor.')");
                return;
            }
            runOnUiThread(() -> {
                exportedCsv = content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, filename);
                startActivityForResult(intent, EXPORT);
            });
        }

        @JavascriptInterface public void print() {
            runOnUiThread(() -> {
                PrintManager manager = getSystemService(PrintManager.class);
                if (manager != null && web != null) {
                    manager.print("Struk Transiva", web.createPrintDocumentAdapter("Struk Transiva"), null);
                }
            });
        }
    }

    @Override protected void onActivityResult(int code, int resultCode, Intent data) {
        super.onActivityResult(code, resultCode, data);
        IntentResult barcode = IntentIntegrator.parseActivityResult(code, resultCode, data);
        if (barcode != null && barcode.getContents() != null) {
            evaluate("scanResult(" + JSONObject.quote(barcode.getContents()) + ")");
        }
        if (code != IMPORT && code != EXPORT && code != QR_IMAGE) return;
        String csv = exportedCsv;
        if (code == EXPORT) exportedCsv = null;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) return;
        worker.execute(() -> {
            try {
                if (code == IMPORT) {
                    try (InputStream input = getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        if (input == null) throw new java.io.IOException();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > MAX_FILE) throw new java.io.IOException();
                            bytes.write(buffer, 0, count);
                        }
                        String contents = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                        evaluate("importResult(" + JSONObject.quote(contents) + ")");
                    }
                } else if (code == QR_IMAGE) {
                    evaluate("qrResult(" + JSONObject.quote(readQrImage(uri)) + ")");
                } else if (csv != null) {
                    try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                        if (output == null) throw new java.io.IOException();
                        output.write(csv.getBytes(StandardCharsets.UTF_8));
                    }
                    evaluate("notice('CSV berhasil disimpan.')");
                }
            } catch (Exception error) {
                if (code == QR_IMAGE) evaluate("notice('Gambar QRIS tidak dapat dibaca. Gunakan gambar dari penyedia QRIS.')");
                else evaluate("notice('File tidak dapat diproses. Batas ukuran 300 KB.')");
            }
        });
    }

    private String readQrImage(Uri uri) throws IOException {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth < 120 || bounds.outHeight < 120 || bounds.outWidth > 10000 || bounds.outHeight > 10000) {
            throw new IOException("Ukuran gambar QRIS tidak valid");
        }
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2;
        android.graphics.Bitmap image;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            image = android.graphics.BitmapFactory.decodeStream(input, null, options);
        }
        if (image == null) throw new IOException("Gambar QRIS tidak valid");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output) || output.size() > 300000) {
                throw new IOException("Gambar QRIS terlalu besar");
            }
            return "data:image/jpeg;base64," + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
        } finally {
            image.recycle();
        }
    }
}
