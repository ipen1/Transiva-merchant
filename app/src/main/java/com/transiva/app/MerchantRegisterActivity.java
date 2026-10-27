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

public class MerchantRegisterActivity extends MerchantAuthActivity {
    private static final String REGISTER_URL="https://transiva.my.id/server/merchant_register.php";
    private static final int REQ_LOCATION=501;
    private static final float TARGET_ACCURACY_M=25f, MAX_ACCEPTED_ACCURACY_M=40f;
    private static final long LOCATION_TIMEOUT_MS=25000L;
    private EditText name,category,username,email,phone,password,latitude,longitude;
    private TextView accuracyText,statusText,gpsHint;
    private Button locationButton,submitButton;
    private float locationAccuracy=0f; private Location bestLocation;
    private LocationManager locationManager; private LocationListener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->finishLocationSearch(false);

    private final LinearLayout[] steps = new LinearLayout[4];
    private int step;
    private boolean busy;
    private EditText confirm;
    private TextView position, heading, description;
    private ProgressBar progress;
    private Button back;
    private LinearLayout formCard;
    private final String[] titles = {"Kenalkan usaha Anda", "Kontak & identitas", "Amankan akun Anda", "Tentukan titik usaha"};
    private final String[] descriptions = {"Buat pelanggan mudah mengenali usaha Anda.", "Gunakan kontak aktif agar mudah dihubungi.", "Pilih kata sandi yang aman dan mudah Anda ingat.", "Ambil lokasi saat berada di tempat usaha Anda."};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); buildUi();
        if (b != null) {
            EditText[] fields = {name, category, username, email, phone};
            String[] keys = {"name", "category", "username", "email", "phone"};
            for (int i = 0; i < fields.length; i++) fields[i].setText(b.getString(keys[i], ""));
            // Passwords and GPS fixes are deliberately reacquired after recreation.
            step = Math.min(2, Math.max(0, b.getInt("step", 0)));
        }
        showStep(false);
    }

    private void buildUi() {
        authShell("Usaha Anda, makin dikenal", "Bergabung dengan Transiva dan jangkau pelanggan di sekitar Anda.");
        formCard = authCard();
        position = authText("", 12, true); position.setTextColor(Color.rgb(52,145,255)); formCard.addView(position);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(4); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(52,145,255)));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(borderColor));
        formCard.addView(progress, lp(-1, dp(5), 0, 12, 0, 16));
        heading = authText("", 22, true); formCard.addView(heading);
        description = authText("", 13, false); description.setTextColor(mutedColor);
        formCard.addView(description, lp(-1,-2,0,6,0,16));
        for (int i=0; i<steps.length; i++) {steps[i]=new LinearLayout(this); steps[i].setOrientation(LinearLayout.VERTICAL); formCard.addView(steps[i]);}
        name=field(steps[0],"Nama restoran / merchant",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        category=field(steps[0],"Kategori usaha",InputType.TYPE_CLASS_TEXT);
        username=field(steps[1],"Nama pengguna",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        email=field(steps[1],"Email",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        phone=field(steps[1],"Nomor HP • 08… atau +628…",InputType.TYPE_CLASS_PHONE);
        password=field(steps[2],"Kata sandi minimal 8 karakter",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm=field(steps[2],"Ulangi kata sandi",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox visible = new CheckBox(this); visible.setText("Tampilkan kata sandi"); visible.setTextColor(inkColor);
        visible.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(52,145,255))); steps[2].addView(visible);
        visible.setOnCheckedChangeListener((v, checked) -> {
            for(EditText e : new EditText[]{password, confirm}) {
                int cursor=e.getSelectionStart(); e.setTransformationMethod(checked ? null : android.text.method.PasswordTransformationMethod.getInstance());
                e.setSelection(Math.max(0, cursor));
            }
        });
        locationButton=button("◎  Ambil lokasi presisi"); steps[3].addView(locationButton);
        accuracyText=authText("Akurasi: belum tersedia",13,true); accuracyText.setTextColor(mutedColor); steps[3].addView(accuracyText);
        gpsHint=authText("Target ≤25 m • maksimal 40 m. Buka di area terbuka agar titik jemput lebih akurat.",12,false);
        gpsHint.setTextColor(mutedColor); gpsHint.setPadding(0,dp(8),0,dp(12)); steps[3].addView(gpsHint);
        latitude=field(steps[3],"Latitude",InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        longitude=field(steps[3],"Longitude",InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        for(EditText e : new EditText[]{latitude,longitude}) {e.setFocusable(false);e.setClickable(false);}
        TextView note=authText("Setelah mendaftar, akun akan ditinjau oleh admin Transiva.",12,false);
        note.setTextColor(mutedColor); steps[3].addView(note);
        statusText=authText("",13,true); statusText.setPadding(dp(12),dp(12),dp(12),dp(12));
        statusText.setVisibility(View.GONE); formCard.addView(statusText);
        submitButton=button("Lanjut →"); formCard.addView(submitButton);
        back=button("← Sebelumnya"); back.setBackground(authShape(surfaceColor,16)); back.setTextColor(inkColor); formCard.addView(back);
        TextView login=authText("Sudah punya akun? Masuk →",14,true); login.setGravity(Gravity.CENTER);login.setMinHeight(dp(56));authRoot.addView(login);
        locationButton.setOnClickListener(v->requestLocation());
        submitButton.setOnClickListener(v->{if(busy)return;if(step<3){if(validateStep()){step++;showStep(true);}}else submit();});
        back.setOnClickListener(v->goBack()); login.setOnClickListener(v->{if(!busy)finish();});
        mountAuth();
    }
    private void showStep(boolean animate) {
        position.setText("LANGKAH "+(step+1)+" DARI 4"); progress.setProgress(step+1);
        heading.setText(titles[step]); description.setText(descriptions[step]);
        for(int i=0;i<steps.length;i++) steps[i].setVisibility(i==step?View.VISIBLE:View.GONE);
        back.setVisibility(step==0?View.GONE:View.VISIBLE);
        submitButton.setText(step==3?"Daftar Merchant →":"Lanjut →");
        statusText.setVisibility(View.GONE);
        if(animate) animateStep(formCard);
    }
    private boolean required(EditText e) {
        if(e.getText().toString().trim().isEmpty()){e.setError("Wajib diisi");e.requestFocus();return false;}return true;
    }
    private String normalizedPhone() {
        String value=phone.getText().toString().replaceAll("[^0-9]", "");
        if(value.startsWith("62")) value=value.substring(2);
        else if(value.startsWith("0")) value=value.substring(1);
        return value;
    }
    private boolean validateStep() {
        if(step==0) return required(name)&&required(category);
        if(step==1) {
            if(!required(username)||!required(email)||!required(phone)) return false;
            if(!username.getText().toString().trim().toLowerCase(Locale.US).matches("[a-z0-9._-]{4,40}")) {username.setError("Gunakan 4–40 huruf, angka, titik, _ atau -");username.requestFocus();return false;}
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()){email.setError("Email belum valid");email.requestFocus();return false;}
            if(!normalizedPhone().matches("8\\d{7,12}")){phone.setError("Masukkan nomor HP Indonesia yang valid");phone.requestFocus();return false;}
        }
        if(step==2){
            if(password.getText().length()<8 || !password.getText().toString().matches(".*[A-Za-z].*") || !password.getText().toString().matches(".*\\d.*")){password.setError("Minimal 8 karakter, berisi huruf dan angka");password.requestFocus();return false;}
            if(!password.getText().toString().equals(confirm.getText().toString())){confirm.setError("Kata sandi belum sama");confirm.requestFocus();return false;}
        }
        return true;
    }
    private void goBack(){if(busy)return;if(step>0){step--;showStep(true);}else finish();}
    @Override public void onBackPressed(){goBack();}
    @Override protected boolean canChangeTheme() { return !busy; }
    @Override protected void onSaveInstanceState(Bundle state){
        EditText[] fields={name,category,username,email,phone};String[] keys={"name","category","username","email","phone"};
        for(int i=0;i<fields.length;i++)state.putString(keys[i],fields[i].getText().toString());
        state.putInt("step",step);super.onSaveInstanceState(state);
    }
    @Override protected void onStop(){
        handler.removeCallbacks(timeout); stopLocation();
        if(locationButton!=null){locationButton.setEnabled(true);locationButton.setText("◎  Ambil lokasi presisi");}
        super.onStop();
    }

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
    private void applyLocation(Location l){locationAccuracy=l.getAccuracy();latitude.setText(String.format(Locale.US,"%.7f",l.getLatitude()));longitude.setText(String.format(Locale.US,"%.7f",l.getLongitude()));String quality=locationAccuracy<=15?"Sangat presisi":locationAccuracy<=25?"Presisi":locationAccuracy<=40?"Cukup":"Kurang akurat";accuracyText.setText(String.format(Locale.US,"%s • ±%.0f meter",quality,locationAccuracy));accuracyText.setTextColor(Color.parseColor(locationAccuracy<=25?"#067647":locationAccuracy<=40?"#B54708":"#B42318"));}
    private void finishLocationSearch(boolean targetReached){handler.removeCallbacks(timeout);stopLocation();locationButton.setEnabled(true);locationButton.setText("◎  Perbarui Lokasi Presisi");if(bestLocation==null){showStatus("GPS belum memperoleh titik. Coba di area terbuka dan aktifkan mode lokasi akurasi tinggi.",false);}else if(!targetReached&&bestLocation.getAccuracy()>MAX_ACCEPTED_ACCURACY_M){showStatus("Titik terbaik masih ±"+Math.round(bestLocation.getAccuracy())+" m. Pindah ke area terbuka lalu ambil ulang agar merchant tidak bergeser jauh.",false);}else showStatus("Lokasi merchant berhasil dikunci pada titik terbaik.",true);}
    private void stopLocation(){try{if(locationManager!=null&&listener!=null)locationManager.removeUpdates(listener);}catch(Exception ignored){}}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopLocation();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_LOCATION){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)requestLocation();else showStatus("Izin lokasi presisi wajib untuk menentukan titik merchant.",false);}}

    private void submit() {
        if(busy) return;
        // Recheck all steps, including secrets intentionally cleared after recreation.
        for(int i=0;i<3;i++) {
            int previousStep=step;step=i;
            if(!validateStep()){showStep(true);return;}step=previousStep;
        }
        if(bestLocation==null||locationAccuracy<=0||locationAccuracy>MAX_ACCEPTED_ACCURACY_M){showStatus("Ambil lokasi dengan akurasi maksimal ±40 meter.",false);return;}
        final JSONObject body=new JSONObject();
        try {
            body.put("name",name.getText().toString().trim());body.put("category",category.getText().toString().trim());
            body.put("username",username.getText().toString().trim().toLowerCase(Locale.US));
            body.put("email",email.getText().toString().trim().toLowerCase(Locale.US));body.put("phone",normalizedPhone());
            body.put("password",password.getText().toString());
            body.put("latitude",bestLocation.getLatitude());body.put("longitude",bestLocation.getLongitude());
            body.put("location_accuracy_m",locationAccuracy);
        }catch(Exception error){showStatus("Data belum lengkap. Periksa kembali.",false);return;}
        setBusy(true);
        boolean accepted=MerchantNetworkExecutor.executeWrite("merchant-register",()->{
            try {
                JSONObject response=new JSONObject(postPublic(body));
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;setBusy(false);
                    if(response.optBoolean("success")) {
                        busy=true;submitButton.setEnabled(false);back.setEnabled(false);
                        new AlertDialog.Builder(this).setTitle("Pendaftaran berhasil")
                            .setMessage(response.optString("message","Akun Anda akan ditinjau oleh admin Transiva."))
                            .setCancelable(false).setPositiveButton("Ke Login",(d,w)->finish()).show();
                    } else showStatus(response.optString("message","Pendaftaran gagal. Periksa kembali data Anda."),false);
                });
            }catch(Exception error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setBusy(false);showStatus("Koneksi gagal. Coba kembali.",false);});}
        });
        if(!accepted){setBusy(false);showStatus("Pendaftaran sedang diproses. Coba sesaat lagi.",false);}
    }
    private void setBusy(boolean value){busy=value;submitButton.setEnabled(!value);back.setEnabled(!value);locationButton.setEnabled(!value);submitButton.setText(value?"Mendaftarkan…":"Daftar Merchant →");}
    private String postPublic(JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(REGISTER_URL).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}c.disconnect();return sb.toString();}
    private EditText field(LinearLayout r,String h,int t){r.addView(label(h));EditText e=input(h,t);r.addView(e,lp(-1,dp(54),0,4,0,12));return e;}private TextView label(String s){TextView v=text(s,13,inkColor,true);v.setPadding(dp(4),dp(5),0,0);return v;}private EditText input(String h,int t){EditText e=new EditText(this);e.setSaveEnabled(false);e.setHint(h);e.setInputType(t);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(inkColor);e.setHintTextColor(mutedColor);e.setPadding(dp(14),0,dp(14),0);e.setBackground(authShape(surfaceColor,16));return e;}private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#0876F9"),Color.parseColor("#28A6FF")});g.setCornerRadius(dp(16));b.setBackground(g);b.setElevation(dp(3));b.setLayoutParams(lp(-1,dp(52),0,5,0,8));return b;}private TextView text(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}private void showStatus(String s,boolean ok){statusText.setVisibility(View.VISIBLE);statusText.setText(s);boolean dark=MerchantAppSettings.isDarkMode(this);statusText.setTextColor(Color.parseColor(dark?(ok?"#70E0AE":"#FFB4AB"):(ok?"#067647":"#B42318")));statusText.setBackground(authShape(Color.parseColor(dark?(ok?"#153C32":"#48272F"):(ok?"#F0FFF7":"#FFF7F6")),14));}private GradientDrawable stroke(String fill,String line,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.parseColor(line));return g;}private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
