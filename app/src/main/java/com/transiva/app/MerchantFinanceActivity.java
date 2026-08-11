package com.transiva.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantFinanceActivity extends MerchantBaseActivity {
    private LinearLayout root, list;
    private TextView today, week, month, lifetime, note;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }

    private void build(){
        root=new LinearLayout(this); setContentView(page(root));
        root.addView(title("Keuangan Merchant"));
        root.addView(sub("Ringkasan pendapatan dari pesanan food yang sudah selesai"));

        LinearLayout r1=row(); root.addView(r1);
        today=metric(r1,"Hari ini","Rp 0"); week=metric(r1,"Minggu ini","Rp 0");
        LinearLayout r2=row(); root.addView(r2);
        month=metric(r2,"Bulan ini","Rp 0"); lifetime=metric(r2,"Total tercatat","Rp 0");

        note=card("Memuat status settlement..."); root.addView(note);
        root.addView(label("Mutasi Pesanan"));
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button refresh=outlineBtn("↻ Refresh keuangan"); refresh.setOnClickListener(v->load()); root.addView(refresh);
    }

    private TextView metric(LinearLayout parent,String label,String value){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(13),dp(14),dp(13)); box.setBackground(round(Color.WHITE,dp(18))); box.setElevation(dp(2));
        TextView l=tv(label,11,MUTED,false); TextView v=tv(value,17,NAVY,true); box.addView(l); box.addView(v);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f); lp.setMargins(dp(4),dp(4),dp(4),dp(8)); parent.addView(box,lp); return v;
    }

    private void load(){
        list.removeAllViews(); list.addView(card("Memuat mutasi..."));
        MerchantNetworkExecutor.execute(()->{
            try{ JSONObject res=new JSONObject(get(BASE+"merchant_finance.php?v="+System.currentTimeMillis())); runOnUiThread(()->show(res)); }
            catch(Exception e){ runOnUiThread(()->{ list.removeAllViews(); list.addView(card("Koneksi gagal.")); }); }
        });
    }

    private void show(JSONObject res){
        list.removeAllViews(); if(!res.optBoolean("success",false)){ list.addView(card(res.optString("message","Gagal memuat keuangan"))); return; }
        JSONObject s=res.optJSONObject("summary"); if(s!=null){ today.setText(rupiah(s.optLong("today_revenue",0))); week.setText(rupiah(s.optLong("week_revenue",0))); month.setText(rupiah(s.optLong("month_revenue",0))); lifetime.setText(rupiah(s.optLong("lifetime_revenue",0))); }
        boolean withdrawal=res.optBoolean("withdrawal_enabled",false);
        note.setText((withdrawal?"✅ Penarikan saldo aktif":"ℹ️ Pendapatan tercatat")+"\n"+res.optString("settlement_note",""));
        JSONArray arr=res.optJSONArray("transactions"); if(arr==null||arr.length()==0){list.addView(card("Belum ada pesanan selesai."));return;}
        for(int i=0;i<arr.length();i++){
            JSONObject t=arr.optJSONObject(i); if(t==null)continue;
            String pay=t.optString("payment_method",""); if(pay.isEmpty())pay="-";
            list.addView(card("+ "+rupiah(t.optLong("amount",0))+"\nOrder #"+t.optString("order_id",String.valueOf(t.optInt("id")))+"  •  "+t.optInt("item_count",0)+" item\nPembayaran: "+pay+"\n"+t.optString("created_at","")));
        }
    }
}
