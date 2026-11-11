package com.transiva.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantCategoriesActivity extends MerchantBaseActivity {
    private LinearLayout root, list;
    private boolean editing;
    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); load(); }
    private void build(){
        editing=false;
        root=new LinearLayout(this); setContentView(page(root));
        root.addView(title("Kategori Menu")); root.addView(sub("Kelola kategori agar daftar menu tetap rapi dan konsisten"));
        Button add=btn("＋ Tambah Kategori"); add.setOnClickListener(v->edit(null)); root.addView(add);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button back=outlineBtn("← Kembali"); back.setOnClickListener(v->finish()); root.addView(back);
    }
    private void load(){ list.removeAllViews(); list.addView(card("Memuat kategori...")); MerchantNetworkExecutor.executeRead(this, "categories", ()->{ try{ JSONObject r=new JSONObject(get(BASE+"merchant_categories.php?v="+System.currentTimeMillis())); runOnUiThread(()->show(r)); }catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(card("Gagal memuat kategori."));});}}); }
    private void show(JSONObject r){ list.removeAllViews(); JSONArray a=r.optJSONArray("categories"); if(a==null||a.length()==0){list.addView(card("Belum ada kategori. Tambahkan kategori pertama."));return;} for(int i=0;i<a.length();i++){ JSONObject c=a.optJSONObject(i); if(c==null)continue; LinearLayout row=actionRow(); TextView n=card(c.optString("name","Kategori")); row.addView(n,actionLp()); Button e=outlineBtn("Edit"); e.setOnClickListener(v->edit(c)); row.addView(e,actionLp()); Button d=outlineBtn("Hapus"); d.setOnClickListener(v->remove(c.optInt("id"))); row.addView(d,actionLp()); list.addView(row); } }
    private void edit(JSONObject category) {
        editing = true;
        LinearLayout form = new LinearLayout(this);
        setContentView(page(form));
        form.addView(title(category == null ? "Tambah Kategori" : "Edit Kategori"));
        EditText nameInput = input("Contoh: Makanan Utama", InputType.TYPE_CLASS_TEXT);
        if (category != null) nameInput.setText(category.optString("name"));
        form.addView(nameInput);
        TextView message = sub("");
        form.addView(message);
        Button save = btn("Simpan kategori");
        form.addView(save);
        Button back = outlineBtn("Kembali");
        form.addView(back);
        back.setOnClickListener(v -> { build(); load(); });
        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                message.setText("Masukkan nama kategori.");
                return;
            }
            int id = category == null ? 0 : category.optInt("id");
            save.setEnabled(false);
            boolean queued = MerchantNetworkExecutor.executeWrite("category-save:" + id, () -> {
                try {
                    JSONObject payload = new JSONObject().put("action", "save").put("name", name).put("id", id);
                    JSONObject result = new JSONObject(postJson(BASE + "merchant_categories.php", payload));
                    runOnUiThread(() -> {
                        if (!result.optBoolean("success")) {
                            message.setText(result.optString("message", "Kategori belum tersimpan."));
                            save.setEnabled(true);
                            return;
                        }
                        if (message.isAttachedToWindow()) { build(); load(); }
                        else if (!editing) load();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        save.setEnabled(true);
                        message.setText("Kategori belum tersimpan. Coba lagi.");
                    });
                }
            });
            if (!queued) {
                save.setEnabled(true);
                message.setText("Permintaan sedang antre. Coba lagi.");
            }
        });
    }

    @Override public void onBackPressed() {
        if (editing) { build(); load(); }
        else super.onBackPressed();
    }

    private void remove(int id){ PremiumDialogs.builder(this).setTitle("Hapus kategori?").setMessage("Menu yang sudah memakai nama kategori tetap aman.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->MerchantNetworkExecutor.executeWrite("category-delete:"+id,()->{try{JSONObject p=new JSONObject();p.put("action","delete");p.put("id",id);JSONObject r=new JSONObject(postJson(BASE+"merchant_categories.php",p));runOnUiThread(()->{toast(r.optString("message","Dihapus"));load();});}catch(Exception e){runOnUiThread(()->alert("Error","Gagal menghapus kategori."));}})).show(); }
}
