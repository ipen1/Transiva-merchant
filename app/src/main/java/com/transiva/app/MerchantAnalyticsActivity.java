package com.transiva.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.Locale;

public class MerchantAnalyticsActivity extends MerchantBaseActivity {
 private LinearLayout root,topList,dayDetails;private MerchantReportUi ui;private MerchantRevenueChart chart;
 private TextView revenue,avg,orders,trend,peak,status,reload,selected,trendNote;private boolean loading;private int generation;
 @Override protected void onCreate(Bundle state){super.onCreate(state);build();load();}
 private void build(){
  ui=new MerchantReportUi(this);root=new LinearLayout(this);setContentView(page(root));reload=ui.heading(root,"Analitik Bisnis",this::load);root.addView(sub("Ringkasan penjualan · 90 hari"));
  status=tv("",11,MUTED,false);root.addView(status);
  LinearLayout one=actionRow();root.addView(one);revenue=ui.metric(one,"Omzet tercatat","—");orders=ui.metric(one,"Pesanan selesai","—");
  LinearLayout two=actionRow();root.addView(two);avg=ui.metric(two,"Rata-rata pesanan","—");trend=ui.metric(two,"Tren 7 hari","—");
  trendNote=tv("",11,MUTED,false);trendNote.setPadding(dp(3),0,dp(3),dp(8));root.addView(trendNote);
  LinearLayout peakBox=ui.box();peak=tv("Jam ramai belum tersedia",13,NAVY,true);peakBox.addView(peak);root.addView(peakBox);
  LinearLayout chartBox=ui.box();chartBox.addView(tv("Pendapatan 14 hari",14,NAVY,true));chartBox.addView(tv("Ketuk batang untuk melihat harinya",11,MUTED,false));chart=new MerchantRevenueChart(this);chartBox.addView(chart,new LinearLayout.LayoutParams(-1,dp(165)));selected=tv("Belum ada data",12,MUTED,false);selected.setPadding(0,dp(6),0,dp(4));chartBox.addView(selected);
  TextView details=tv("Rincian harian  ⌄",12,BLUE,true);details.setMinHeight(dp(44));details.setGravity(android.view.Gravity.CENTER_VERTICAL);chartBox.addView(details);dayDetails=new LinearLayout(this);dayDetails.setOrientation(LinearLayout.VERTICAL);dayDetails.setVisibility(View.GONE);chartBox.addView(dayDetails);details.setOnClickListener(v->{boolean open=dayDetails.getVisibility()!=View.VISIBLE;dayDetails.setVisibility(open?View.VISIBLE:View.GONE);details.setText(open?"Sembunyikan rincian  ⌃":"Rincian harian  ⌄");});root.addView(chartBox);
  ui.section(root,"Menu terlaris");topList=new LinearLayout(this);topList.setOrientation(LinearLayout.VERTICAL);root.addView(topList);
 }
 private void load(){if(loading)return;loading=true;reload.setEnabled(false);status.setText("Memperbarui analitik…");final int request=++generation;
  java.util.concurrent.Future<?> job=MerchantNetworkExecutor.executeRead(this,"analytics",()->{try{JSONObject res=new JSONObject(get(BASE+"merchant_analytics.php?v="+System.currentTimeMillis()));runOnUiThread(()->{if(isFinishing()||isDestroyed()||request!=generation)return;loading=false;reload.setEnabled(true);if(!res.optBoolean("success")){status.setText(res.optString("message","Analitik belum bisa dimuat. Tekan ↻ untuk mencoba lagi."));return;}show(res);});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&request==generation){loading=false;reload.setEnabled(true);status.setText("Koneksi belum berhasil. Tekan ↻ untuk mencoba lagi.");}});}});
  if(job==null){loading=false;reload.setEnabled(true);status.setText("Sedang sibuk. Tekan ↻ untuk mencoba lagi.");}
 }
 private void show(JSONObject res){
  status.setText("Data pesanan selesai · diperbarui barusan");revenue.setText(rupiah(res.optLong("revenue")));avg.setText(rupiah(res.optLong("average_order_value")));orders.setText(String.valueOf(res.optInt("completed_orders")));
  long current=res.optLong("current_7d_revenue"),previous=res.optLong("previous_7d_revenue");double tr=res.optDouble("trend_percent");
  if(previous==0){trend.setText(current>0?"Mulai tumbuh":"Belum ada tren");trend.setTextColor(NAVY);trendNote.setText(current>0?"Ada penjualan minggu ini; minggu sebelumnya belum ada omzet.":"Tren muncul setelah ada penjualan untuk dibandingkan.");}
  else{trend.setText((tr>0?"+":"")+String.format(Locale.US,"%.1f%%",tr));trend.setTextColor(tr>=0?Color.parseColor("#16A374"):Color.parseColor("#DC5969"));trendNote.setText("Dibandingkan 7 hari sebelumnya.");}
  int peakOrders=res.optInt("peak_hour_orders");peak.setText(peakOrders>0?"Jam ramai · "+res.optString("peak_hour","—")+" · "+peakOrders+" pesanan":"Jam ramai belum tersedia");
  JSONArray days=res.optJSONArray("daily");dayDetails.removeAllViews();if(days!=null)for(int i=days.length()-1;i>=0;i--){JSONObject d=days.optJSONObject(i);if(d!=null)ui.line(dayDetails,MerchantReportUi.date(d.optString("date"),false)+" · "+d.optInt("orders")+" pesanan",rupiah(d.optLong("revenue")),false);}
  if(days==null||days.length()==0)selected.setText("Belum ada data harian");
  chart.setDays(days,d->{if(d!=null)selected.setText(MerchantReportUi.date(d.optString("date"),false)+" · "+d.optInt("orders")+" pesanan · "+rupiah(d.optLong("revenue")));});
  topList.removeAllViews();JSONArray top=res.optJSONArray("top_items");if(top==null||top.length()==0){topList.addView(card("Menu terlaris akan muncul setelah pesanan selesai."));return;}
  for(int i=0;i<top.length();i++){JSONObject x=top.optJSONObject(i);if(x==null)continue;LinearLayout box=ui.box();TextView name=tv((i+1)+". "+x.optString("name","Menu"),14,NAVY,true);name.setMaxLines(2);box.addView(name);ui.line(box,x.optInt("qty")+" terjual",rupiah(x.optLong("revenue")),true);topList.addView(box);}
 }
}
