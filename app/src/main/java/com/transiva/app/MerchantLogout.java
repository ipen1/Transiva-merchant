package com.transiva.app;

import android.app.Activity;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;

/** Explicit logout is complete only when the current server session is revoked. */
final class MerchantLogout {
    interface Callback { void onResult(boolean success); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static void revoke(Activity a,String token,Callback callback){
        android.content.Context app=a.getApplicationContext();
        IO.execute(()->{
            boolean success=false;HttpURLConnection c=null;
            try{
                c=MerchantApiClient.open(app,"https://transiva.my.id/server/merchant_logout.php");
                c.setRequestProperty("Authorization","Bearer "+token);
                c.setRequestMethod("POST");c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=c.getOutputStream()){out.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                int code=c.getResponseCode();
                JSONObject response=new JSONObject(MerchantHttpClient.read(code>=400?c.getErrorStream():c.getInputStream()));
                String error=response.optString("code","");
                success=(code>=200&&code<300&&response.optBoolean("success")) ||
                    (code==401&&(error.equals("UNAUTHORIZED")||error.equals("SESSION_EXPIRED")||error.equals("SESSION_REVOKED")||error.equals("INVALID_TOKEN")));
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
            boolean result=success;a.runOnUiThread(()->callback.onResult(result));
        });
    }
}
