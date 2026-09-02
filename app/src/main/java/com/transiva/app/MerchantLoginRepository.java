package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Authentication + FCM persistence kept outside LoginActivity. */
final class MerchantLoginRepository {
    private static final String TAG="TRANSIVA_LOGIN";
    private static final String LOGIN_URL="https://transiva.my.id/server/login.php";
    private static final String SAVE_FCM_URL="https://transiva.my.id/server/save_fcm_token.php";
    private static final int TIMEOUT_MS=25000;
    private MerchantLoginRepository(){}

    static final class Result {
        final boolean success; final String message; final String role; final JSONObject user;
        Result(boolean success,String message,String role,JSONObject user){this.success=success;this.message=message;this.role=role;this.user=user;}
        static Result ok(String message,String role,JSONObject user){return new Result(true,message,role,user);}
        static Result fail(String message){return new Result(false,message,"",null);}
    }

    static Result login(Activity a,String username,String password){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(LOGIN_URL).openConnection();
            c.setRequestMethod("POST");c.setConnectTimeout(TIMEOUT_MS);c.setReadTimeout(TIMEOUT_MS);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setDoInput(true);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Transiva-Client","Android-Native");c.setRequestProperty("X-App-Scope","merchant");c.setRequestProperty("X-Device-UUID",DeviceIdentityManager.getInstallationUuid(a));
            JSONObject p=new JSONObject();p.put("username",username);p.put("password",password);p.put("device_name",Build.MANUFACTURER+" "+Build.MODEL);p.put("platform","android_native");p.put("app_scope","merchant");p.put("installation_uuid",DeviceIdentityManager.getInstallationUuid(a));p.put("manufacturer",Build.MANUFACTURER);p.put("model",Build.MODEL);p.put("android_version",Build.VERSION.RELEASE);try{p.put("app_version",a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName);}catch(Exception ignored){p.put("app_version","unknown");}
            String fcm=cachedFcm(a);if(!fcm.isEmpty())p.put("fcm_token",fcm);
            try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(c.getOutputStream(),StandardCharsets.UTF_8))){w.write(p.toString());w.flush();}
            int code=c.getResponseCode();String raw=read(code>=200&&code<300?c.getInputStream():c.getErrorStream()).trim();Log.d(TAG,"Login HTTP="+code+", bodyLength="+raw.length());
            if(raw.isEmpty())return Result.fail("Server tidak mengirim response.");JSONObject r=new JSONObject(raw);boolean ok=r.optBoolean("success",false);String message=r.optString("message",ok?"Login berhasil":"Login gagal");if(!ok||code<200||code>=300)return Result.fail(message);JSONObject user=r.optJSONObject("user");if(user==null)return Result.fail("Data pengguna tidak ditemukan.");if(user.optString("token","").trim().isEmpty()){String t=r.optString("token","").trim();if(!t.isEmpty())user.put("token",t);}String role=normalizeRole(user.optString("role","customer"));user.put("role",role);return Result.ok(message,role,user);
        }catch(Exception e){Log.e(TAG,"Login gagal",e);return Result.fail("Server error atau koneksi gagal.");}finally{if(c!=null)c.disconnect();}
    }

    static void syncFcmAfterLogin(Activity a,JSONObject user){
        try{
            int userId=firstPositive(user.optInt("id",0),user.optInt("user_id",0),user.optInt("uid",0));String username=firstNotEmpty(user.optString("username",""),user.optString("user_name",""),user.optString("name",""));String role=normalizeRole(user.optString("role","merchant"));String cached=cachedFcm(a);if(!cached.isEmpty()){saveFcmLocal(a,cached,userId,username,role);uploadFcm(a,userId,username,role,cached);}FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{String clean=token==null?"":token.trim();if(clean.isEmpty())return;saveFcmLocal(a,clean,userId,username,role);uploadFcm(a,userId,username,role,clean);}).addOnFailureListener(e->Log.e(TAG,"FCM token gagal",e));
        }catch(Exception e){Log.e(TAG,"FCM setelah login gagal",e);}
    }

    private static void uploadFcm(Activity a,int userId,String username,String role,String token){if(token==null||token.trim().isEmpty())return;new Thread(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(SAVE_FCM_URL).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(TIMEOUT_MS);c.setReadTimeout(TIMEOUT_MS);c.setDoInput(true);c.setDoOutput(true);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");MerchantApiClient.applySecurity(a,c);JSONObject p=new JSONObject();p.put("user_id",userId);p.put("id",userId);p.put("username",username);p.put("role",role);p.put("fcm_token",token.trim());p.put("platform","android_native");p.put("installation_uuid",DeviceIdentityManager.getInstallationUuid(a));p.put("manufacturer",Build.MANUFACTURER);p.put("model",Build.MODEL);p.put("android_version",Build.VERSION.RELEASE);try{p.put("app_version",a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName);}catch(Exception ignored){p.put("app_version","unknown");}try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(c.getOutputStream(),StandardCharsets.UTF_8))){w.write(p.toString());}int code=c.getResponseCode();Log.d(TAG,"FCM upload HTTP="+code+", body="+read(code>=200&&code<300?c.getInputStream():c.getErrorStream()));}catch(Exception e){Log.e(TAG,"Upload FCM gagal",e);}finally{if(c!=null)c.disconnect();}},"merchant-fcm-sync").start();}
    private static String cachedFcm(Context c){try{String v=c.getSharedPreferences("transiva_fcm",Context.MODE_PRIVATE).getString("fcm_token","");if(v!=null&&!v.trim().isEmpty())return v.trim();}catch(Exception ignored){}try{String v=new SessionManager(c).getFcmToken();if(v!=null&&!v.trim().isEmpty())return v.trim();}catch(Exception ignored){}return "";}
    private static void saveFcmLocal(Context c,String token,int id,String username,String role){String clean=token==null?"":token.trim();c.getSharedPreferences("transiva_fcm",Context.MODE_PRIVATE).edit().putString("fcm_token",clean).putInt("user_id",id).putString("username",username).putString("role",role).putLong("fcm_token_saved_at",System.currentTimeMillis()).apply();try{new SessionManager(c).saveFcmToken(clean);}catch(Exception ignored){}}
    private static String normalizeRole(String role){String r=role==null?"":role.trim().toLowerCase(Locale.US);if(r.equals("restaurant")||r.equals("resto")||r.equals("merchant_admin"))return "merchant";return r;}
    private static int firstPositive(int...v){for(int x:v)if(x>0)return x;return 0;}private static String firstNotEmpty(String...v){for(String x:v)if(x!=null&&!x.trim().isEmpty())return x.trim();return "";}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);return sb.toString();}}
}
