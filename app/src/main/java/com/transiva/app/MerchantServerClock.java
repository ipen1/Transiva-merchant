package com.transiva.app;

import android.os.SystemClock;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class MerchantServerClock {
    private static final MerchantClockTimeline CLOCK=new MerchantClockTimeline(System.currentTimeMillis(),SystemClock.elapsedRealtime());
    private static volatile String timezone="Asia/Makassar";
    private MerchantServerClock(){}
    static void sync(long serverEpochMs){long now=SystemClock.elapsedRealtime();CLOCK.sync(serverEpochMs,now,now);}
    static void sync(JSONObject response,long sent,long received){
        String zone=response.optString("server_timezone","");
        if(!zone.isEmpty())timezone=zone;
        CLOCK.sync(response.optLong("server_time_epoch_ms",0),sent,received);
    }
    static long now(){return CLOCK.now(SystemClock.elapsedRealtime());}
    static boolean isSynced(){return CLOCK.isSynced();}
    static long timestamp(String raw){
        if(raw==null||raw.trim().isEmpty())return 0;
        String[] patterns={"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX"};
        for(String pattern:patterns){try{
            SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone(timezone));
            java.text.ParsePosition position=new java.text.ParsePosition(0);Date d=f.parse(raw.trim(),position);
            if(d!=null&&position.getIndex()==raw.trim().length())return d.getTime();
        }catch(Exception ignored){}}
        return 0;
    }
    static long readyAt(JSONObject order){long epoch=order.optLong("estimated_ready_at_epoch_ms",0);return epoch>0?epoch:timestamp(order.optString("estimated_ready_at",""));}
    static long createdAt(JSONObject order){long epoch=order.optLong("created_at_epoch_ms",0);if(epoch>0)return epoch;
        for(String key:new String[]{"created_at","order_date","created"}){long value=timestamp(order.optString(key,""));if(value>0)return value;}return 0;
    }
}
