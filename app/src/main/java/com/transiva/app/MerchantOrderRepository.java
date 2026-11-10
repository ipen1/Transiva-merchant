package com.transiva.app;
import org.json.JSONObject;
import java.util.UUID;
final class MerchantOrderRepository {
 interface UpdateCallback {void onResult(boolean success,String message,boolean networkError,JSONObject receipt);}
 private MerchantOrderRepository(){}
 static void updateStatus(MerchantOrdersActivity a,String id,String displayId,int outletId,String status,String rejectReason,int cookMinutes,boolean restoreStock,UpdateCallback callback){
  boolean accepted=MerchantNetworkExecutor.executeWrite("order-status:"+id+":"+status,()->{
   try{
    String requestId=UUID.randomUUID().toString();JSONObject payload=new JSONObject();payload.put("id",id);payload.put("order_id",displayId);payload.put("status",status);if(outletId>0)payload.put("restaurant_id",outletId);payload.put("idempotency_key",requestId);
    if(restoreStock)payload.put("restore_stock",true);
    if(cookMinutes>0)payload.put("cook_minutes",cookMinutes);if(rejectReason!=null&&!rejectReason.isEmpty())payload.put("reject_reason",rejectReason);
    long sent=android.os.SystemClock.elapsedRealtime();JSONObject response=new JSONObject(MerchantHttpClient.postJson(a,MerchantBaseActivity.BASE+"updateMerchantOrder.php",payload,requestId));long received=android.os.SystemClock.elapsedRealtime();
    a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;MerchantServerClock.sync(response,sent,received);callback.onResult(response.optBoolean("success",false),response.optString("message",response.optBoolean("success",false)?"Berhasil":"Gagal mengubah status."),false,response);});
   }catch(Exception e){a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())callback.onResult(false,"Hasil perubahan belum dapat dipastikan. Status pesanan akan diperiksa kembali. Tidak ada pengiriman ulang otomatis.",true,null);});}
  });
  if(!accepted)callback.onResult(false,"Permintaan sedang sibuk dan belum dikirim. Coba lagi sebentar.",false,null);
 }
}
