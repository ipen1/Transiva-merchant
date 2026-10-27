package com.transiva.app;

import org.json.*;
import java.util.Locale;

/** Apply only acknowledged writes to the visible cache; database/driver status is untouched. */
final class MerchantOrderUiState {
 private MerchantOrderUiState(){}
 static boolean matches(JSONObject row,String id,String display){if(row==null)return false;for(String key:new String[]{"id","order_id","order_numeric_id","order_db_id"}){String value=row.optString(key,"");if(!value.isEmpty()&&((id!=null&&!id.isEmpty()&&value.equalsIgnoreCase(id))||(display!=null&&!display.isEmpty()&&value.equalsIgnoreCase(display))))return true;}return false;}
 static boolean terminal(String raw){String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);return s.equals("merchant_rejected")||s.equals("cancelled")||s.equals("canceled")||s.equals("finished")||s.equals("completed");}
 static JSONArray acknowledge(JSONArray rows,String id,String display,String requested,String reason,int minutes,JSONObject receipt,boolean history,long now)throws JSONException{
  JSONArray updated=new JSONArray();if(rows==null)return updated;
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.optJSONObject(i);if(row==null)continue;
   if(!matches(row,id,display)){updated.put(row);continue;}
   JSONObject copy=new JSONObject(row.toString());
   String global=receipt==null?"":receipt.optString("global_status","");
   if(global.isEmpty())global=("merchant_accepted".equals(requested)||"merchant_rejected".equals(requested))?requested:row.optString("status");
   String kitchen="merchant_accepted".equals(requested)?"preparing":receipt==null?requested:receipt.optString("merchant_status",requested);
   copy.put("status",(!terminal(global)&&("preparing".equals(kitchen)||"ready".equals(kitchen)))?kitchen:global);copy.put("global_status",global);copy.put("merchant_status",kitchen);
   if("merchant_accepted".equals(requested)||"preparing".equals(requested)){
    int cook=receipt==null?minutes:receipt.optInt("cook_minutes",minutes);if(cook<=0)cook=15;
    copy.put("cook_minutes",cook);copy.put("estimated_cook_minutes",cook);
    String ready=receipt==null?"":receipt.optString("estimated_ready_at","");long epoch=ready.isEmpty()?now+cook*60000L:MerchantServerClock.timestamp(ready);
    if(!ready.isEmpty())copy.put("estimated_ready_at",ready);copy.put("estimated_ready_at_epoch_ms",epoch>0?epoch:now+cook*60000L);
   }
   if("merchant_rejected".equals(requested)){copy.put("merchant_reject_reason",reason==null?"":reason);copy.put("reject_reason",reason==null?"":reason);}
   if(history==terminal(global))updated.put(copy);
  }
  return updated;
 }
}
