package com.transiva.app;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MerchantFinanceActivity extends MerchantBaseActivity {
 private MerchantReportUi ui;private LinearLayout root,list;private TextView today,week,month,total,totalCount,note,status,reload,explanation;private Button more;
 private JSONArray transactions=new JSONArray();private boolean loading;private int generation,visible=10;
 @Override protected void onCreate(Bundle state){super.onCreate(state);build();load();}
 private void build(){
  ui=new MerchantReportUi(this);root=new LinearLayout(this);setContentView(page(root));reload=ui.heading(root,"Keuangan Toko",this::load);root.addView(sub("Pendapatan dari pesanan selesai"));status=tv("",11,MUTED,false);root.addView(status);
  LinearLayout hero=ui.box();hero.setBackground(stroke(SOFT_BLUE,0xFFBEDCFF,dp(18)));hero.addView(tv("Pendapatan bulan ini",12,MUTED,false));month=tv("—",28,BLUE,true);month.setPadding(0,dp(3),0,dp(3));hero.addView(month);totalCount=tv("Menunggu data…",11,MUTED,false);hero.addView(totalCount);root.addView(hero);
  LinearLayout metrics=actionRow();root.addView(metrics);today=ui.metric(metrics,"Hari ini","—");week=ui.metric(metrics,"Minggu ini","—");
  LinearLayout lifetime=ui.box();lifetime.addView(tv("Total tercatat",11,MUTED,false));total=tv("—",19,NAVY,true);lifetime.addView(total);root.addView(lifetime);
  LinearLayout info=ui.box();note=tv("Memeriksa ketersediaan penarikan…",12,NAVY,true);info.addView(note);TextView details=tv("Cara hitung pendapatan  ⌄",12,BLUE,true);details.setMinHeight(dp(44));details.setGravity(Gravity.CENTER_VERTICAL);info.addView(details);explanation=tv("",12,MUTED,false);explanation.setVisibility(View.GONE);info.addView(explanation);details.setOnClickListener(v->{boolean open=explanation.getVisibility()!=View.VISIBLE;explanation.setVisibility(open?View.VISIBLE:View.GONE);details.setText(open?"Tutup penjelasan  ⌃":"Cara hitung pendapatan  ⌄");});root.addView(info);
  ui.section(root,"Riwayat pendapatan");list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);more=outlineBtn("Tampilkan 10 lagi");more.setVisibility(View.GONE);more.setOnClickListener(v->{visible+=10;renderList();});root.addView(more);
 }
 private void load(){if(loading)return;loading=true;reload.setEnabled(false);status.setText("Memperbarui keuangan…");final int request=++generation;
  java.util.concurrent.Future<?> job=MerchantNetworkExecutor.executeRead(this,"finance",()->{try{JSONObject res=new JSONObject(get(BASE+"merchant_finance.php?v="+System.currentTimeMillis()));runOnUiThread(()->{if(isFinishing()||isDestroyed()||request!=generation)return;loading=false;reload.setEnabled(true);if(!res.optBoolean("success")){status.setText(res.optString("message","Keuangan belum dapat dimuat."));return;}show(res);});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&request==generation){loading=false;reload.setEnabled(true);status.setText("Koneksi belum berhasil. Tekan ↻ untuk mencoba lagi.");}});}});
  if(job==null){loading=false;reload.setEnabled(true);status.setText("Sedang sibuk. Tekan ↻ untuk mencoba lagi.");}
 }
 private void show(JSONObject res){JSONObject summary=res.optJSONObject("summary");if(summary!=null){today.setText(rupiah(summary.optLong("today_revenue")));week.setText(rupiah(summary.optLong("week_revenue")));month.setText(rupiah(summary.optLong("month_revenue")));total.setText(rupiah(summary.optLong("lifetime_revenue")));totalCount.setText(summary.optInt("month_orders")+" pesanan selesai bulan ini");}
  status.setText("Data pesanan selesai · diperbarui barusan");boolean withdraw=res.optBoolean("withdrawal_enabled");note.setText(withdraw?"Penarikan tersedia":"Pendapatan tercatat · penarikan belum tersedia");explanation.setText(res.optString("settlement_note","Pendapatan dihitung dari harga asli menu, varian, dan topping pada pesanan selesai. Biaya tambahan aplikasi tidak termasuk pendapatan toko."));
  transactions=res.optJSONArray("transactions");if(transactions==null)transactions=new JSONArray();renderList();
 }
 private void renderList(){list.removeAllViews();if(transactions.length()==0){list.addView(card("Pendapatan akan muncul setelah ada pesanan yang selesai."));more.setVisibility(View.GONE);return;}
  for(int i=0;i<Math.min(visible,transactions.length());i++){JSONObject t=transactions.optJSONObject(i);if(t==null)continue;LinearLayout box=ui.box();ui.line(box,"Pesanan selesai","+ "+rupiah(t.optLong("amount")),true);TextView meta=tv(MerchantReportUi.date(t.optString("created_at"),true)+" · "+MerchantReportUi.payment(t.optString("payment_method"))+" · "+t.optInt("item_count")+" item",11,MUTED,false);box.addView(meta);String id=t.optString("order_id",String.valueOf(t.optInt("id")));TextView reference=tv("#"+id+"  ›",10,BLUE,false);reference.setSingleLine();reference.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);reference.setPadding(0,dp(5),0,0);box.addView(reference);box.setOnClickListener(v->alert("Rincian pendapatan","Pesanan: "+id+"\nPendapatan toko: "+rupiah(t.optLong("amount"))+"\nJumlah: "+t.optInt("item_count")+" item\nPembayaran: "+MerchantReportUi.payment(t.optString("payment_method"))+"\nWaktu: "+MerchantReportUi.date(t.optString("created_at"),true)));list.addView(box);}
  more.setVisibility(visible<transactions.length()?View.VISIBLE:View.GONE);
 }
}
