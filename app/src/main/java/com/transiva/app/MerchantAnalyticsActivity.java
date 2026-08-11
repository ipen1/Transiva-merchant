package com.transiva.app;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantAnalyticsActivity extends MerchantBaseActivity {
    private LinearLayout root, dailyList, topList;
    private TextView revenue, avg, orders, trend, peak;

    @Override protected void onCreate(Bundle b){super.onCreate(b);build();load();}
    private void build(){
        root=new LinearLayout(this);setContentView(page(root));root.addView(title("Analitik Bisnis"));root.addView(sub("Performa 90 hari berdasarkan pesanan selesai"));
        LinearLayout r1=row();root.addView(r1);revenue=metric(r1,"Omzet 90 hari","Rp 0");avg=metric(r1,"Rata-rata order","Rp 0");
        LinearLayout r2=row();root.addView(r2);orders=metric(r2,"Order selesai","0");trend=metric(r2,"Tren 7 hari","0%");
        peak=card("Jam ramai: -");root.addView(peak);
        root.addView(label("14 Hari Terakhir"));dailyList=new LinearLayout(this);dailyList.setOrientation(LinearLayout.VERTICAL);root.addView(dailyList);
        root.addView(label("Menu Terlaris"));topList=new LinearLayout(this);topList.setOrientation(LinearLayout.VERTICAL);root.addView(topList);
        Button b=outlineBtn("↻ Refresh analitik");b.setOnClickListener(v->load());root.addView(b);
    }
    private TextView metric(LinearLayout parent,String label,String value){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(13),dp(14),dp(13));box.setBackground(round(Color.WHITE,dp(18)));box.setElevation(dp(2));TextView l=tv(label,11,MUTED,false),v=tv(value,17,NAVY,true);box.addView(l);box.addView(v);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);lp.setMargins(dp(4),dp(4),dp(4),dp(8));parent.addView(box,lp);return v;}
    private void load(){dailyList.removeAllViews();topList.removeAllViews();dailyList.addView(card("Memuat analitik..."));MerchantNetworkExecutor.executeRead(this, "analytics", ()->{try{JSONObject res=new JSONObject(get(BASE+"merchant_analytics.php?v="+System.currentTimeMillis()));runOnUiThread(()->show(res));}catch(Exception e){runOnUiThread(()->{dailyList.removeAllViews();dailyList.addView(card("Koneksi gagal."));});}});}
    private void show(JSONObject res){dailyList.removeAllViews();topList.removeAllViews();if(!res.optBoolean("success",false)){dailyList.addView(card(res.optString("message","Gagal memuat analitik")));return;}revenue.setText(rupiah(res.optLong("revenue",0)));avg.setText(rupiah(res.optLong("average_order_value",0)));orders.setText(String.valueOf(res.optInt("completed_orders",0)));double tr=res.optDouble("trend_percent",0);trend.setText((tr>0?"+":"")+String.format(java.util.Locale.US,"%.1f%%",tr));trend.setTextColor(tr>=0?Color.parseColor("#059669"):Color.parseColor("#DC2626"));peak.setText("🔥 Jam paling ramai\n"+res.optString("peak_hour","-")+"  •  "+res.optInt("peak_hour_orders",0)+" order");JSONArray days=res.optJSONArray("daily");long max=1;if(days!=null)for(int i=0;i<days.length();i++)max=Math.max(max,days.optJSONObject(i).optLong("revenue",0));if(days!=null)for(int i=0;i<days.length();i++){JSONObject d=days.optJSONObject(i);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView txt=tv(d.optString("date","")+"   "+d.optInt("orders",0)+" order   "+rupiah(d.optLong("revenue",0)),12,TEXT,false);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.min(1000,(d.optLong("revenue",0)*1000/max)));row.addView(txt);row.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));row.setPadding(dp(4),dp(5),dp(4),dp(8));dailyList.addView(row);}JSONArray top=res.optJSONArray("top_items");if(top==null||top.length()==0){topList.addView(card("Belum ada data menu terlaris."));return;}for(int i=0;i<top.length();i++){JSONObject x=top.optJSONObject(i);topList.addView(card((i+1)+". "+x.optString("name","Menu")+"\n"+x.optInt("qty",0)+" terjual  •  "+rupiah(x.optLong("revenue",0))));}}
}
