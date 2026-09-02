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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
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
        int side = compactScreen() ? 12 : 16;
        root.setPadding(dp(side), dp(compactScreen() ? 14 : 18), dp(side), dp(24));
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
        shell.addView(bottom, new LinearLayout.LayoutParams(-1, dp(bottomNavHeightDp())));
        ViewCompat.setOnApplyWindowInsetsListener(shell, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(shell);
        return shell;
    }

    protected TextView title(String text){
        TextView v = tv(text, compactScreen() ? 21 : 24, NAVY, true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(v, 18, compactScreen() ? 22 : 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
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
        b.setTextSize(compactScreen() ? 13 : 15);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 10, compactScreen() ? 14 : 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(BLUE, dp(16)));
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(compactScreen() ? 48 : 50));
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

    protected int screenWidthDp(){ return getResources().getConfiguration().screenWidthDp; }
    protected int screenHeightDp(){ return getResources().getConfiguration().screenHeightDp; }
    protected boolean compactScreen(){ return screenWidthDp() > 0 && screenWidthDp() < 380; }
    protected int bottomNavHeightDp(){
        float fs = getResources().getConfiguration().fontScale;
        int base = compactScreen() ? 64 : 66;
        return Math.max(base, Math.round(base * Math.min(1.22f, Math.max(1f, fs))));
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

    protected String get(String link) throws Exception { return MerchantHttpClient.get(this, link); }
    protected String postJson(String link, JSONObject payload) throws Exception { return MerchantHttpClient.postJson(this, link, payload); }

    protected static class PreparedImage {
        public final Uri uri; public final String mimeType; public final String fileName;
        public final long originalBytes; public final long finalBytes; public final boolean transformed;
        PreparedImage(Uri uri,String mimeType,String fileName,long originalBytes,long finalBytes,boolean transformed){this.uri=uri;this.mimeType=mimeType;this.fileName=fileName;this.originalBytes=originalBytes;this.finalBytes=finalBytes;this.transformed=transformed;}
    }
    protected PreparedImage prepareAiResizeToWebp(Uri source,String prefix,int maxDimension,long optimizeAboveBytes,long targetBytes)throws Exception{return MerchantImageProcessor.prepare(this,source,prefix,maxDimension,optimizeAboveBytes,targetBytes);}
    protected String postFormPrepared(String link,JSONObject fields,PreparedImage image,String fileField)throws Exception{return MerchantHttpClient.postMultipart(this,link,fields,image==null?null:image.uri,fileField,image==null?"":image.fileName,image==null?"application/octet-stream":image.mimeType);}
    protected String postForm(String link,JSONObject fields,Uri fileUri,String fileField,String fileName)throws Exception{return MerchantHttpClient.postMultipart(this,link,fields,fileUri,fileField,fileName,"image/jpeg");}
    protected void setButtonLoading(Button button,boolean loading,String normalText,String loadingText){MerchantUiHelpers.setButtonLoading(this,button,loading,normalText,loadingText);}
    protected String humanBytes(long bytes){return MerchantUiHelpers.humanBytes(bytes);}
    void handleSessionExpired(){toast("Sesi merchant berakhir. Silakan login ulang.");logout();}

    protected void logout(){
        try { sessionManager.clearSession(); } catch(Exception ignored){}
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
}
