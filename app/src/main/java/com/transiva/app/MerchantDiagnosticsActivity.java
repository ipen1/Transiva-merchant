package com.transiva.app;
import android.Manifest;import android.app.NotificationManager;import android.content.Context;import android.content.pm.PackageManager;import android.location.LocationManager;import android.os.*;import android.provider.Settings;import android.widget.*;import androidx.core.content.ContextCompat;import com.google.firebase.messaging.FirebaseMessaging;
public class MerchantDiagnosticsActivity extends MerchantBaseActivity{
 private LinearLayout list;@Override protected void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);setContentView(page(root));root.addView(title("Diagnostik Merchant"));root.addView(sub("Periksa kesiapan perangkat menerima dan memproses pesanan"));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);Button r=outlineBtn("↻ Periksa Ulang");r.setOnClickListener(v->run());root.addView(r);run();}
 private int statusGeneration;
 private void run(){list.removeAllViews();boolean net=MerchantConnectivity.isOnline(this);boolean loc=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);boolean gps=false;try{gps=lm!=null&&lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}boolean notif=true;if(Build.VERSION.SDK_INT>=33)notif=ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;if(Build.VERSION.SDK_INT>=24){NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);notif=notif&&nm!=null&&nm.areNotificationsEnabled();}row("Internet",net,"Diperlukan untuk sinkronisasi order");row("Lokasi Presisi",loc&&gps,loc?"GPS "+(gps?"aktif":"nonaktif"):"Izin belum diberikan");row("Notifikasi",notif,"Pesanan baru membutuhkan notifikasi aktif");row("Sesi",sessionManager!=null&&sessionManager.isLoggedIn(),"Autentikasi merchant");android.content.SharedPreferences push=getSharedPreferences("merchant_push_health",0);long ack=push.getLong("confirmed_at",0);row("Registrasi push",ack>0,push.getString("registration","Belum terkonfirmasi")+(ack>0?"\nKonfirmasi terakhir: "+android.text.format.DateFormat.format("dd MMM HH:mm:ss",ack):""));long received=push.getLong("received_at",0);row("Push terakhir",received>0,received>0?android.text.format.DateFormat.format("dd MMM HH:mm:ss",received)+" • "+push.getString("display",""):"Belum ada push diterima");row("Crash lokal",true,MerchantCrashReporter.summary(this));row("Versi",true,AppUpdateClient.installedVersionName(this));TextView f=card("Firebase: memeriksa token...");list.addView(f);loadPushStatus();FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t->f.setText(t.isSuccessful()&&!t.getResult().isEmpty()?"✅ Firebase: token FCM tersedia":"⚠ Firebase: token belum tersedia"));}
 private void loadPushStatus(){
  final int request=++statusGeneration;
  TextView result=card("Pengiriman server: memeriksa…");list.addView(result);
  MerchantPushRegistration.sync(this,true);
  java.util.concurrent.Future<?> pending=MerchantNetworkExecutor.executeRead(this,"push-status:"+request,()->{
   String message;
   try{
    org.json.JSONObject response=new org.json.JSONObject(get(BASE+"merchant_push_status.php"));
    if(!response.optBoolean("success"))message="Pengiriman server: "+response.optString("message","Belum dapat diperiksa");
    else{
     org.json.JSONObject data=response.optJSONObject("data");if(data==null)data=response;
     String token=data.optBoolean("registered")?"Token sesi tersedia":"Token sesi belum terdaftar — periksa Registrasi push";
     org.json.JSONObject job=data.optJSONObject("latest_order");
     if(job==null)message="Pengiriman server\n"+token+"\nBelum ada order makanan.";
     else{
      String state=job.optString("queue_status");String detail;
      switch(state){
       case "sent":detail="Diterima FCM; cek Push terakhir dan izin notifikasi HP";break;
       case "not_queued":detail="Order belum mempunyai antrean notifikasi";break;
       case "pending":detail="Menunggu pengiriman/retry; cek worker jika tidak bergerak";break;
       case "processing":detail="Sedang dikirim oleh server";break;
       case "expired":detail="Alert kedaluwarsa atau order sudah diproses";break;
       default:detail="Pengiriman belum berhasil";
      }
      message="Pengiriman server\n"+token+"\nOrder: "+job.optString("order_status")+"\nAntrean: "+state+" • Percobaan: "+job.optInt("attempts")+"\n"+detail;
      String error=job.optString("error_code");if(!error.isEmpty())message+="\nKode: "+error;
     }
    }
   }catch(Exception error){message="Status server belum tersedia. Pastikan merchant_push_status.php sudah diupload.";}
   final String text=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&request==statusGeneration)result.setText(text);});
  });
  if(pending==null)result.setText("Pemeriksaan server sedang sibuk. Tekan Periksa Ulang.");
 }
 private void row(String n,boolean ok,String detail){list.addView(card((ok?"✅ ":"⚠ ")+n+"\n"+detail));}
}
