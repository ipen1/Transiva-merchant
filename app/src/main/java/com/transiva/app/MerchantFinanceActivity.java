package com.transiva.app;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MerchantFinanceActivity extends MerchantBaseActivity {
 private MerchantReportUi ui;private LinearLayout root,list;private TextView today,week,month,total,totalCount,note,status,reload,explanation;private Button more;
 private JSONArray transactions=new JSONArray();private boolean loading;private int generation,visible=10;private boolean hasMore;private long nextBeforeId;private String screenAccountKey="";private java.util.concurrent.Future<?> activeRead;
 @Override protected void onCreate(Bundle state){super.onCreate(state);screenAccountKey=currentAccountKey();build();load();}
 private void build(){
  ui=new MerchantReportUi(this);root=new LinearLayout(this);setContentView(page(root));reload=ui.heading(root,"Keuangan Toko",this::load);root.addView(sub("Pendapatan dari pesanan selesai"));status=tv("",11,MUTED,false);root.addView(status);
  LinearLayout hero=ui.box();hero.setBackground(stroke(SOFT_BLUE,0xFFBEDCFF,dp(18)));hero.addView(tv("Pendapatan bulan ini",12,MUTED,false));month=tv("—",28,BLUE,true);month.setPadding(0,dp(3),0,dp(3));hero.addView(month);totalCount=tv("Menunggu data…",11,MUTED,false);hero.addView(totalCount);root.addView(hero);
  LinearLayout metrics=actionRow();root.addView(metrics);today=ui.metric(metrics,"Hari ini","—");week=ui.metric(metrics,"Minggu ini","—");
  LinearLayout lifetime=ui.box();lifetime.addView(tv("Total tercatat",11,MUTED,false));total=tv("—",19,NAVY,true);lifetime.addView(total);root.addView(lifetime);
  LinearLayout info=ui.box();note=tv("Memeriksa ketersediaan penarikan…",12,NAVY,true);info.addView(note);TextView details=tv("Cara hitung pendapatan  ⌄",12,BLUE,true);details.setMinHeight(dp(44));details.setGravity(Gravity.CENTER_VERTICAL);info.addView(details);explanation=tv("",12,MUTED,false);explanation.setVisibility(View.GONE);info.addView(explanation);details.setOnClickListener(v->{boolean open=explanation.getVisibility()!=View.VISIBLE;explanation.setVisibility(open?View.VISIBLE:View.GONE);details.setText(open?"Tutup penjelasan  ⌃":"Cara hitung pendapatan  ⌄");});root.addView(info);
  ui.section(root,"Riwayat pendapatan");list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);more=outlineBtn("Tampilkan 10 lagi");more.setVisibility(View.GONE);more.setOnClickListener(v->{if(loading)return;if(visible<transactions.length()){visible+=10;renderList();}else if(hasMore)loadPage(true);});root.addView(more);
 }
 private String currentAccountKey(){SessionManager session=new SessionManager(this);return session.getId()+"|"+session.getRestaurantId();}
 private boolean sameAccount(){return !screenAccountKey.isEmpty()&&screenAccountKey.equals(currentAccountKey());}
 @Override protected void onResume(){super.onResume();if(root!=null&&!loading)load();}
 @Override protected void onDestroy(){generation++;if(activeRead!=null)activeRead.cancel(true);super.onDestroy();}
 private void load(){loadPage(false);}
 private void loadPage(final boolean append){
  if(!sameAccount()){finish();return;}
  if(loading||(append&&(!hasMore||nextBeforeId<=0)))return;
  loading=true;reload.setEnabled(false);more.setEnabled(false);status.setText(append?"Memuat riwayat lebih lama…":"Memperbarui keuangan…");final int request=++generation;final long cursor=append?nextBeforeId:0;
  activeRead=MerchantNetworkExecutor.executeRead(this,"finance:"+request,()->{
   try{JSONObject res=new JSONObject(get(BASE+"merchant_finance.php?v="+System.currentTimeMillis()+(cursor>0?"&before_id="+cursor:"")));
    runOnUiThread(()->{
     if(!sameAccount()||isFinishing()||isDestroyed()||request!=generation)return;
     loading=false;activeRead=null;reload.setEnabled(true);more.setEnabled(true);
     if(!res.optBoolean("success")){status.setText(res.optString("message","Keuangan belum dapat dimuat."));return;}
     show(res,append);
    });
   }catch(Exception e){runOnUiThread(()->{if(sameAccount()&&!isFinishing()&&!isDestroyed()&&request==generation){loading=false;activeRead=null;reload.setEnabled(true);more.setEnabled(true);status.setText("Koneksi belum berhasil. Riwayat yang sudah tampil tetap tersedia. Tekan ↻ atau coba muat lagi.");}});}
  });
  if(activeRead==null){loading=false;reload.setEnabled(true);more.setEnabled(true);status.setText("Sedang sibuk. Tekan ↻ untuk mencoba lagi.");}
 }
 private void show(JSONObject res,boolean append){
  JSONObject summary=res.optJSONObject("summary");if(summary!=null){today.setText(rupiah(summary.optLong("today_revenue")));week.setText(rupiah(summary.optLong("week_revenue")));month.setText(rupiah(summary.optLong("month_revenue")));total.setText(rupiah(summary.optLong("lifetime_revenue")));totalCount.setText(summary.optInt("month_orders")+" pesanan selesai bulan ini");}
  status.setText("Data pesanan selesai · diperbarui barusan");note.setText(res.optBoolean("withdrawal_enabled")?"Penarikan tersedia":"Pendapatan tercatat · penarikan belum tersedia");explanation.setText(res.optString("settlement_note","Pendapatan toko tidak termasuk biaya tambahan aplikasi."));
  JSONArray page=res.optJSONArray("transactions");if(page==null)page=new JSONArray();
  if(!append){transactions=page;visible=10;}else{
   java.util.HashSet<Long> ids=new java.util.HashSet<>();for(int i=0;i<transactions.length();i++){JSONObject old=transactions.optJSONObject(i);if(old!=null)ids.add(old.optLong("id"));}
   for(int i=0;i<page.length();i++){JSONObject item=page.optJSONObject(i);if(item!=null&&ids.add(item.optLong("id")))transactions.put(item);}
   visible+=10;
  }
  long next=res.optLong("next_before_id",0);hasMore=res.optBoolean("has_more")&&next>0&&(!append||next<nextBeforeId);nextBeforeId=hasMore?next:0;renderList();
 }
 private void renderList(){list.removeAllViews();if(transactions.length()==0){list.addView(card("Pendapatan akan muncul setelah ada pesanan yang selesai."));more.setVisibility(View.GONE);return;}
  for(int i=0;i<Math.min(visible,transactions.length());i++){JSONObject t=transactions.optJSONObject(i);if(t==null)continue;LinearLayout box=ui.box();ui.line(box,"Pesanan selesai","+ "+rupiah(t.optLong("amount")),true);TextView meta=tv(MerchantReportUi.date(t.optString("created_at"),true)+" · "+MerchantReportUi.payment(t.optString("payment_method"))+" · "+t.optInt("item_count")+" item",11,MUTED,false);box.addView(meta);String id=t.optString("order_id",String.valueOf(t.optInt("id")));TextView reference=tv("#"+id+"  ›",10,BLUE,false);reference.setSingleLine();reference.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);reference.setPadding(0,dp(5),0,0);box.addView(reference);box.setOnClickListener(v->alert("Rincian pendapatan","Pesanan: "+id+"\nPendapatan toko: "+rupiah(t.optLong("amount"))+"\nJumlah: "+t.optInt("item_count")+" item\nPembayaran: "+MerchantReportUi.payment(t.optString("payment_method"))+"\nWaktu: "+MerchantReportUi.date(t.optString("created_at"),true)));list.addView(box);}
  more.setText(visible<transactions.length()?"Tampilkan 10 lagi":"Muat riwayat lebih lama");more.setVisibility(visible<transactions.length()||hasMore?View.VISIBLE:View.GONE);
 }
}
