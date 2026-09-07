package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local inbox. No sensitive payload is stored; only display metadata. */
public final class MerchantNotificationStore {
    private static final String PREF="merchant_notification_inbox", KEY="items"; private static final int MAX=80;
    private MerchantNotificationStore(){}
    public static synchronized void add(Context c,String title,String body,String type,String orderId){
        try{SharedPreferences sp=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);JSONArray old=new JSONArray(sp.getString(KEY,"[]"));JSONArray out=new JSONArray();JSONObject n=new JSONObject();n.put("title",safe(title,120));n.put("body",safe(body,300));n.put("type",safe(type,50));n.put("order_id",safe(orderId,60));n.put("at",System.currentTimeMillis());out.put(n);for(int i=0;i<old.length()&&out.length()<MAX;i++)out.put(old.optJSONObject(i));sp.edit().putString(KEY,out.toString()).apply();}catch(Exception ignored){}
    }
    public static JSONArray get(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void clear(Context c){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY).apply();}
    private static String safe(String s,int max){s=s==null?"":s.trim();return s.length()>max?s.substring(0,max):s;}
}
