package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MerchantRegisterActivity extends Activity {
    private static final String REGISTER_URL="https://transiva.my.id/server/merchant_register.php";
    private static final int REQ_LOCATION=501;
    private static final float TARGET_ACCURACY_M=25f, MAX_ACCEPTED_ACCURACY_M=50f;
    private static final long LOCATION_TIMEOUT_MS=25000L;
    private EditText name,category,username,email,phone,password,latitude,longitude;
    private TextView accuracyText,statusText,gpsHint;
    private Button locationButton,submitButton;
    private float locationAccuracy=0f; private Location bestLocation;
    private LocationManager locationManager; private LocationListener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->finishLocationSearch(false);

    @Override protected void onCreate(Bundle b){super.onCreate(b);buildUi();}

    private void buildUi(){
        getWindow().setStatusBarColor(Color.parseColor("#F4F8FE")); getWindow().setNavigationBarColor(Color.WHITE);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(18),dp(16),dp(28)); root.setBackgroundColor(Color.parseColor("#F4F8FE")); scroll.addView(root);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.parseColor("#071B3A"),Color.parseColor("#0B78FF"),Color.parseColor("#22B8FF")}); hg.setCornerRadius(dp(26)); hero.setBackground(hg); hero.setElevation(dp(6));
        hero.addView(text("TRANSIVA • MERCHANT",11,Color.parseColor("#DDF2FF"),true)); hero.addView(text("Daftarkan bisnis Anda",26,Color.WHITE,true)); TextView hs=text("Mulai menerima pesanan setelah akun diverifikasi admin.",13,Color.parseColor("#EAF6FF"),false);hs.setPadding(0,dp(6),0,0);hero.addView(hs); root.addView(hero,lp(-1,-2,0,0,0,14));

        LinearLayout card=section("Informasi Merchant","Data ini akan tampil pada ekosistem Transiva setelah verifikasi.");root.addView(card,lp(-1,-2,0,0,0,12));
        name=field(card,"Nama restoran / merchant",InputType.TYPE_CLASS_TEXT); category=field(card,"Kategori usaha",InputType.TYPE_CLASS_TEXT); username=field(card,"Username",InputType.TYPE_CLASS_TEXT); email=field(card,"Email",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(label("Nomor HP")); LinearLayout pr=new LinearLayout(this);pr.setGravity(Gravity.CENTER_VERTICAL);pr.setBackground(stroke("#FFFFFF","#D9E5F2",16)); TextView pref=text("+62",15,Color.parseColor("#12314F"),true);pref.setPadding(dp(14),0,dp(10),0);phone=input("8xxxxxxxxxx",InputType.TYPE_CLASS_PHONE);phone.setBackgroundColor(Color.TRANSPARENT);pr.addView(pref,new LinearLayout.LayoutParams(-2,dp(54)));pr.addView(phone,new LinearLayout.LayoutParams(0,dp(54),1));card.addView(pr,lp(-1,dp(54),0,5,0,12));
        password=field(card,"Password minimal 8 karakter",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout loc=section("Titik Merchant Presisi","Gunakan GPS di area terbuka. Sistem mencari beberapa sampel dan memilih titik terbaik.");root.addView(loc,lp(-1,-2,0,0,0,12));
        locationButton=button("◎  Ambil Lokasi Presisi");loc.addView(locationButton);
        accuracyText=text("Akurasi: belum tersedia",13,Color.parseColor("#667085"),true);accuracyText.setPadding(dp(4),dp(5),dp(4),dp(3));loc.addView(accuracyText);
        gpsHint=text("Target ≤25 m • maksimal pendaftaran 50 m",11,Color.parseColor("#7A8899"),false);gpsHint.setPadding(dp(4),0,dp(4),dp(10));loc.addView(gpsHint);
        latitude=field(loc,"Latitude",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);longitude=field(loc,"Longitude",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);

        statusText=text("",12,Color.parseColor("#B42318"),true);statusText.setVisibility(View.GONE);statusText.setPadding(dp(12),dp(11),dp(12),dp(11));statusText.setBackground(stroke("#FFF7F6","#FFD2CE",14));root.addView(statusText,lp(-1,-2,0,0,0,10));
        submitButton=button("Daftar Merchant");root.addView(submitButton);Button login=button("Sudah punya akun? Masuk");login.setTextColor(Color.parseColor("#0B78FF"));login.setBackground(stroke("#FFFFFF","#BEDAFF",16));root.addView(login);
        locationButton.setOnClickListener(v->requestLocation());submitButton.setOnClickListener(v->submit());login.setOnClickListener(v->finish());setContentView(scroll);
    }

    private LinearLayout section(String title,String sub){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(stroke("#FFFFFF","#E1EAF5",22));c.setElevation(dp(3));c.addView(text(title,18,Color.parseColor("#0A1A2E"),true));TextView s=text(sub,12,Color.parseColor("#667085"),false);s.setPadding(0,dp(3),0,dp(10));c.addView(s);return c;}

    private void requestLocation(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE); if(locationManager==null){showStatus("Layanan lokasi perangkat tidak tersedia.",false);return;}
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){showStatus("Aktifkan GPS/Lokasi Presisi perangkat lalu coba kembali.",false);startActivity(new android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;}
        bestLocation=null;locationAccuracy=0;locationButton.setEnabled(false);locationButton.setText("Mencari GPS terbaik...");accuracyText.setText("Mengumpulkan sampel lokasi...");
        listener=new LocationListener(){@Override public void onLocationChanged(Location l){considerLocation(l);}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle e){}};
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,LOCATION_TIMEOUT_MS);
        try{locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,500L,0f,listener);}catch(Exception ignored){}
        try{locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,700L,0f,listener);}catch(Exception ignored){}
    }
    private void considerLocation(Location l){if(l==null||!l.hasAccuracy())return;long age=Math.abs(System.currentTimeMillis()-l.getTime());if(age>15000L)return;if(bestLocation==null||l.getAccuracy()<bestLocation.getAccuracy()){bestLocation=new Location(l);applyLocation(bestLocation);}if(l.getAccuracy()<=TARGET_ACCURACY_M)finishLocationSearch(true);}
    private void applyLocation(Location l){locationAccuracy=l.getAccuracy();latitude.setText(String.format(Locale.US,"%.7f",l.getLatitude()));longitude.setText(String.format(Locale.US,"%.7f",l.getLongitude()));String quality=locationAccuracy<=15?"Sangat presisi":locationAccuracy<=25?"Presisi":locationAccuracy<=50?"Cukup":"Kurang akurat";accuracyText.setText(String.format(Locale.US,"%s • ±%.0f meter",quality,locationAccuracy));accuracyText.setTextColor(Color.parseColor(locationAccuracy<=25?"#067647":locationAccuracy<=50?"#B54708":"#B42318"));}
    private void finishLocationSearch(boolean targetReached){handler.removeCallbacks(timeout);stopLocation();locationButton.setEnabled(true);locationButton.setText("◎  Perbarui Lokasi Presisi");if(bestLocation==null){showStatus("GPS belum memperoleh titik. Coba di area terbuka dan aktifkan mode lokasi akurasi tinggi.",false);}else if(!targetReached&&bestLocation.getAccuracy()>MAX_ACCEPTED_ACCURACY_M){showStatus("Titik terbaik masih ±"+Math.round(bestLocation.getAccuracy())+" m. Pindah ke area terbuka lalu ambil ulang agar merchant tidak bergeser jauh.",false);}else showStatus("Lokasi merchant berhasil dikunci pada titik terbaik.",true);}
    private void stopLocation(){try{if(locationManager!=null&&listener!=null)locationManager.removeUpdates(listener);}catch(Exception ignored){}}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopLocation();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_LOCATION){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)requestLocation();else showStatus("Izin lokasi presisi wajib untuk menentukan titik merchant.",false);}}

    private void submit(){String n=name.getText().toString().trim(),c=category.getText().toString().trim(),u=username.getText().toString().trim().toLowerCase(),e=email.getText().toString().trim().toLowerCase(),ph=phone.getText().toString().trim(),pw=password.getText().toString(),lat=latitude.getText().toString().trim(),lng=longitude.getText().toString().trim();if(n.isEmpty()||c.isEmpty()||u.isEmpty()||e.isEmpty()||ph.isEmpty()||pw.isEmpty()||lat.isEmpty()||lng.isEmpty()){showStatus("Lengkapi seluruh data pendaftaran.",false);return;}if(!ph.matches("^8\\d{7,12}$")){phone.setError("Isi mulai angka 8 setelah +62");return;}if(pw.length()<8){password.setError("Minimal 8 karakter");return;}if(locationAccuracy<=0||locationAccuracy>MAX_ACCEPTED_ACCURACY_M){showStatus("Ambil ulang lokasi sampai akurasi maksimal ±50 meter. Target terbaik ≤25 meter.",false);return;}submitButton.setEnabled(false);submitButton.setText("Mendaftarkan...");MerchantNetworkExecutor.executeWrite("merchant-register",()->{try{JSONObject p=new JSONObject();p.put("name",n);p.put("category",c);p.put("username",u);p.put("email",e);p.put("phone",ph);p.put("password",pw);p.put("latitude",Double.parseDouble(lat));p.put("longitude",Double.parseDouble(lng));p.put("location_accuracy_m",locationAccuracy);JSONObject r=new JSONObject(postPublic(p));runOnUiThread(()->{submitButton.setEnabled(true);submitButton.setText("Daftar Merchant");if(r.optBoolean("success"))new AlertDialog.Builder(this).setTitle("Pendaftaran berhasil").setMessage(r.optString("message")).setCancelable(false).setPositiveButton("Ke Login",(d,w)->finish()).show();else showStatus(r.optString("message","Pendaftaran gagal."),false);});}catch(Exception ex){runOnUiThread(()->{submitButton.setEnabled(true);submitButton.setText("Daftar Merchant");showStatus("Koneksi gagal. Coba kembali.",false);});}});}
    private String postPublic(JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(REGISTER_URL).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}c.disconnect();return sb.toString();}
    private EditText field(LinearLayout r,String h,int t){r.addView(label(h));EditText e=input(h,t);r.addView(e,lp(-1,dp(54),0,4,0,12));return e;}private TextView label(String s){TextView v=text(s,13,Color.parseColor("#16314C"),true);v.setPadding(dp(4),dp(5),0,0);return v;}private EditText input(String h,int t){EditText e=new EditText(this);e.setHint(h);e.setInputType(t);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(Color.parseColor("#172033"));e.setHintTextColor(Color.parseColor("#98A2B3"));e.setPadding(dp(14),0,dp(14),0);e.setBackground(stroke("#FFFFFF","#D9E5F2",16));return e;}private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#0876F9"),Color.parseColor("#28A6FF")});g.setCornerRadius(dp(16));b.setBackground(g);b.setElevation(dp(3));b.setLayoutParams(lp(-1,dp(52),0,5,0,8));return b;}private TextView text(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}private void showStatus(String s,boolean ok){statusText.setVisibility(View.VISIBLE);statusText.setText(s);statusText.setTextColor(Color.parseColor(ok?"#067647":"#B42318"));statusText.setBackground(stroke(ok?"#F0FFF7":"#FFF7F6",ok?"#B7E8CC":"#FFD2CE",14));}private GradientDrawable stroke(String fill,String line,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.parseColor(line));return g;}private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
