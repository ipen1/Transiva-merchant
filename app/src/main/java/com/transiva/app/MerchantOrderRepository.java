package com.transiva.app;
import org.json.JSONObject;
final class MerchantOrderRepository {
    interface UpdateCallback { void onResult(boolean success,String message,boolean networkError); }
    private MerchantOrderRepository() {}
    static void updateStatus(MerchantOrdersActivity a,String id,String displayId,String status,String rejectReason,int cookMinutes,UpdateCallback cb){
        MerchantNetworkExecutor.executeWrite("order-status:"+id+":"+status,()->{try{JSONObject p=new JSONObject();p.put("id",id);p.put("order_id",displayId);p.put("status",status);if(cookMinutes>0)p.put("cook_minutes",cookMinutes);if(rejectReason!=null&&!rejectReason.isEmpty())p.put("reject_reason",rejectReason);JSONObject r=new JSONObject(MerchantHttpClient.postJson(a,MerchantBaseActivity.BASE+"updateMerchantOrder.php",p));a.runOnUiThread(()->cb.onResult(r.optBoolean("success",false),r.optString("message",r.optBoolean("success",false)?"Berhasil":"Gagal mengubah status."),false));}catch(Exception e){a.runOnUiThread(()->cb.onResult(false,"Status belum diubah. Periksa koneksi lalu coba lagi. Aplikasi tidak akan mengirim ulang otomatis agar pesanan tidak terproses dua kali.",true));}});
    }
}
