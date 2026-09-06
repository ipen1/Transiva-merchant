package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MerchantRegisterActivity extends Activity {
    private static final String REGISTER_URL = "https://transiva.my.id/server/merchant_register.php";
    private static final int REQ_LOCATION = 501;
    private EditText name, category, username, email, phone, password, latitude, longitude;
    private TextView accuracyText, statusText;
    private Button locationButton, submitButton;
    private float locationAccuracy = 0f;
    private LocationManager locationManager;
    private LocationListener listener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(28));
        root.setBackgroundColor(Color.parseColor("#F5F9FF"));
        scroll.addView(root);

        TextView title = text("Daftar Merchant Transiva", 25, Color.parseColor("#0F2B46"), true);
        root.addView(title);
        TextView sub = text("Buat akun merchant. Akun baru menunggu verifikasi admin dan toko dimulai dalam status Tutup.", 13, Color.parseColor("#667085"), false);
        sub.setPadding(0, dp(4), 0, dp(18)); root.addView(sub);

        name = field(root, "Nama restoran / merchant", InputType.TYPE_CLASS_TEXT);
        category = field(root, "Kategori usaha", InputType.TYPE_CLASS_TEXT);
        username = field(root, "Username", InputType.TYPE_CLASS_TEXT);
        email = field(root, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        root.addView(label("Nomor HP"));
        LinearLayout phoneRow = new LinearLayout(this); phoneRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prefix = text("+62", 15, Color.parseColor("#0F2B46"), true); prefix.setPadding(dp(12),0,dp(10),0);
        phone = input("8xxxxxxxxxx", InputType.TYPE_CLASS_PHONE);
        phoneRow.addView(prefix, new LinearLayout.LayoutParams(-2, dp(52)));
        phoneRow.addView(phone, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(phoneRow);

        password = field(root, "Password (min. 8, huruf + angka)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        root.addView(label("Lokasi Merchant"));
        locationButton = button("📍 Ambil Lokasi Saya"); root.addView(locationButton);
        accuracyText = text("Akurasi: belum tersedia", 12, Color.parseColor("#667085"), false); accuracyText.setPadding(dp(4),0,0,dp(8)); root.addView(accuracyText);
        latitude = field(root, "Latitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        longitude = field(root, "Longitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        statusText = text("", 12, Color.parseColor("#B42318"), false); statusText.setVisibility(View.GONE); statusText.setPadding(dp(10),dp(8),dp(10),dp(8)); root.addView(statusText);
        submitButton = button("Daftar Merchant"); root.addView(submitButton);
        Button login = button("Sudah punya akun? Masuk"); login.setTextColor(Color.parseColor("#1E88F5")); login.setBackgroundColor(Color.TRANSPARENT); root.addView(login);

        locationButton.setOnClickListener(v -> requestLocation());
        submitButton.setOnClickListener(v -> submit());
        login.setOnClickListener(v -> finish());
        setContentView(scroll);
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        locationButton.setEnabled(false); locationButton.setText("Mencari lokasi...");
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { applyLocation(location); stopLocation(); }
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
        Location best = null;
        try { Location a = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); if (a != null) best = a; } catch(Exception ignored) {}
        try { Location a = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); if (a != null && (best == null || a.getAccuracy() < best.getAccuracy())) best = a; } catch(Exception ignored) {}
        if (best != null && System.currentTimeMillis() - best.getTime() < 120000L) applyLocation(best);
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener); } catch(Exception ignored) {}
        try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener); } catch(Exception ignored) {}
    }

    private void applyLocation(Location l) {
        locationAccuracy = l.hasAccuracy() ? l.getAccuracy() : 0f;
        latitude.setText(String.format(Locale.US, "%.7f", l.getLatitude()));
        longitude.setText(String.format(Locale.US, "%.7f", l.getLongitude()));
        accuracyText.setText(locationAccuracy > 0 ? String.format(Locale.US, "Akurasi: ±%.0f meter", locationAccuracy) : "Akurasi: tidak dilaporkan perangkat");
        locationButton.setEnabled(true); locationButton.setText("📍 Perbarui Lokasi Saya");
    }

    private void stopLocation() { try { if(locationManager != null && listener != null) locationManager.removeUpdates(listener); } catch(Exception ignored) {} }
    @Override protected void onDestroy(){ stopLocation(); super.onDestroy(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQ_LOCATION){ boolean granted=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED; if(granted) requestLocation(); else showStatus("Izin lokasi diperlukan untuk mengisi koordinat merchant.", false); }
    }

    private void submit() {
        String n=name.getText().toString().trim(), c=category.getText().toString().trim(), u=username.getText().toString().trim().toLowerCase(), e=email.getText().toString().trim().toLowerCase();
        String ph=phone.getText().toString().trim(), pw=password.getText().toString(), lat=latitude.getText().toString().trim(), lng=longitude.getText().toString().trim();
        if(n.isEmpty()||c.isEmpty()||u.isEmpty()||e.isEmpty()||ph.isEmpty()||pw.isEmpty()||lat.isEmpty()||lng.isEmpty()){showStatus("Lengkapi seluruh data pendaftaran.",false);return;}
        if(!ph.matches("^8\\d{7,12}$")){phone.setError("Isi mulai angka 8 setelah +62");return;}
        submitButton.setEnabled(false); submitButton.setText("Mendaftarkan...");
        MerchantNetworkExecutor.executeWrite("merchant-register", () -> {
            try {
                JSONObject p=new JSONObject();p.put("name",n);p.put("category",c);p.put("username",u);p.put("email",e);p.put("phone",ph);p.put("password",pw);p.put("latitude",Double.parseDouble(lat));p.put("longitude",Double.parseDouble(lng));p.put("location_accuracy_m",locationAccuracy);
                JSONObject r=new JSONObject(postPublic(p));
                runOnUiThread(() -> {submitButton.setEnabled(true);submitButton.setText("Daftar Merchant");if(r.optBoolean("success")){new AlertDialog.Builder(this).setTitle("Pendaftaran berhasil").setMessage(r.optString("message")).setCancelable(false).setPositiveButton("Ke Login",(d,w)->finish()).show();}else showStatus(r.optString("message","Pendaftaran gagal."),false);});
            }catch(Exception ex){runOnUiThread(() -> {submitButton.setEnabled(true);submitButton.setText("Daftar Merchant");showStatus("Koneksi gagal. Coba kembali.",false);});}
        });
    }

    private String postPublic(JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(REGISTER_URL).openConnection(); c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}c.disconnect();return sb.toString();
    }

    private EditText field(LinearLayout root,String hint,int type){root.addView(label(hint));EditText e=input(hint,type);root.addView(e);return e;}
    private TextView label(String s){TextView v=text(s,13,Color.parseColor("#0F2B46"),true);v.setPadding(dp(4),dp(8),0,dp(4));return v;}
    private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackgroundColor(Color.WHITE);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.parseColor("#1E88F5"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(8),0,dp(8));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private void showStatus(String s,boolean ok){statusText.setVisibility(View.VISIBLE);statusText.setText(s);statusText.setTextColor(Color.parseColor(ok?"#067647":"#B42318"));}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
