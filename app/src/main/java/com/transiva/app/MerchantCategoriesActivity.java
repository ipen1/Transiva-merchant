package com.transiva.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantCategoriesActivity extends MerchantBaseActivity {
    private LinearLayout root, list;
    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }
    private void build(){
        root=new LinearLayout(this); setContentView(page(root));
        root.addView(title("Kategori Menu")); root.addView(sub("Kelola kategori agar daftar menu tetap rapi dan konsisten"));
        Button add=btn("＋ Tambah Kategori"); add.setOnClickListener(v->edit(null)); root.addView(add);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button back=outlineBtn("← Kembali"); back.setOnClickListener(v->finish()); root.addView(back);
    }
    private void load(){ list.removeAllViews(); list.addView(card("Memuat kategori...")); MerchantNetworkExecutor.executeRead(this, "categories", ()->{ try{ JSONObject r=new JSONObject(get(BASE+"merchant_categories.php?v="+System.currentTimeMillis())); runOnUiThread(()->show(r)); }catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(card("Gagal memuat kategori."));});}}); }
    private void show(JSONObject r){ list.removeAllViews(); JSONArray a=r.optJSONArray("categories"); if(a==null||a.length()==0){list.addView(card("Belum ada kategori. Tambahkan kategori pertama."));return;} for(int i=0;i<a.length();i++){ JSONObject c=a.optJSONObject(i); if(c==null)continue; LinearLayout row=row(); TextView n=card(c.optString("name","Kategori")); row.addView(n,new LinearLayout.LayoutParams(0,-2,1f)); Button e=outlineBtn("Edit"); e.setOnClickListener(v->edit(c)); row.addView(e,new LinearLayout.LayoutParams(dp(86),dp(48))); Button d=outlineBtn("Hapus"); d.setOnClickListener(v->remove(c.optInt("id"))); row.addView(d,new LinearLayout.LayoutParams(dp(90),dp(48))); list.addView(row); } }
    private void edit(JSONObject c){ final EditText input=new EditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setHint("Contoh: Makanan Utama"); if(c!=null)input.setText(c.optString("name")); new AlertDialog.Builder(this).setTitle(c==null?"Tambah Kategori":"Edit Kategori").setView(input).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->{String name=input.getText().toString().trim(); if(name.isEmpty())return; MerchantNetworkExecutor.executeWrite(()->{try{JSONObject p=new JSONObject();p.put("action","save");p.put("name",name);if(c!=null)p.put("id",c.optInt("id"));JSONObject r=new JSONObject(postJson(BASE+"merchant_categories.php",p));runOnUiThread(()->{toast(r.optString("message","Tersimpan"));load();});}catch(Exception ex){runOnUiThread(()->alert("Error","Gagal menyimpan kategori."));}});}).show(); }
    private void remove(int id){ new AlertDialog.Builder(this).setTitle("Hapus kategori?").setMessage("Menu yang sudah memakai nama kategori tetap aman.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->MerchantNetworkExecutor.executeWrite(()->{try{JSONObject p=new JSONObject();p.put("action","delete");p.put("id",id);JSONObject r=new JSONObject(postJson(BASE+"merchant_categories.php",p));runOnUiThread(()->{toast(r.optString("message","Dihapus"));load();});}catch(Exception e){runOnUiThread(()->alert("Error","Gagal menghapus kategori."));}})).show(); }
}
