package com.transiva.app;

import com.bumptech.glide.Glide;
import android.os.Bundle;
import android.app.AlertDialog;
import android.graphics.Color;


import android.text.InputType;
import android.view.Gravity;
import android.widget.*;



import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantMenuListActivity extends MerchantBaseActivity {
    private LinearLayout root,list; private EditText search; private Spinner statusFilter; private JSONArray cached=new JSONArray();
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();load();}
    @Override protected void onResume(){super.onResume();load();}
    private void build(){root=new LinearLayout(this);setContentView(page(root));root.addView(title("Menu Toko"));root.addView(sub("Tambah menu baru atau kelola menu yang sudah ada."));
        search=input("🔎 Cari nama atau kategori",InputType.TYPE_CLASS_TEXT);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){render();}public void afterTextChanged(android.text.Editable e){}});root.addView(search);
        statusFilter=new Spinner(this);statusFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Semua menu","Aktif","Habis / nonaktif"}));statusFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){render();}public void onNothingSelected(android.widget.AdapterView<?> p){}});root.addView(statusFilter);
        LinearLayout actions=actionRow();Button add=btn("＋ Tambah menu");Button cats=outlineBtn("Kategori");actions.addView(add,actionLp());actions.addView(cats,actionLp());root.addView(actions);add.setOnClickListener(v->open(MerchantAddMenuActivity.class));cats.setOnClickListener(v->open(MerchantCategoriesActivity.class));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);}
    private void load(){if(list==null)return;list.removeAllViews();list.addView(card("Memuat menu..."));MerchantNetworkExecutor.executeRead(this, "menus", ()->{try{JSONObject r=new JSONObject(get(BASE+"merchant_get_menus.php?v="+System.currentTimeMillis()));runOnUiThread(()->{cached=r.optJSONArray("menus");if(cached==null)cached=new JSONArray();render();});}catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(card("Gagal memuat menu."));});}});}
    private void render(){if(list==null)return;list.removeAllViews();String q=search==null?"":search.getText().toString().trim().toLowerCase();int filter=statusFilter==null?0:statusFilter.getSelectedItemPosition();int shown=0;for(int i=0;i<cached.length();i++){JSONObject m=cached.optJSONObject(i);if(m==null)continue;String name=s(m,"name","menu_name","food_name"),cat=s(m,"category","type");int active=m.optInt("is_active",1);boolean sold=m.optBoolean("is_sold_out",false)||(m.optInt("track_stock",0)==1&&m.optInt("stock",0)<=0);if(!q.isEmpty()&&!name.toLowerCase().contains(q)&&!cat.toLowerCase().contains(q))continue;if(filter==1&&(active!=1||sold))continue;if(filter==2&&(active==1&&!sold))continue;list.addView(menuCard(m));shown++;}if(shown==0)list.addView(card("Tidak ada menu sesuai filter."));}
    private LinearLayout menuCard(JSONObject m){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(12),dp(12),dp(12));box.setBackground(round(Color.WHITE,dp(14)));box.setElevation(dp(2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));box.setLayoutParams(lp);String id=s(m,"id","menu_id"),name=s(m,"name","menu_name","food_name"),cat=s(m,"category","type"),image=s(m,"image","photo","image_url");int active=m.optInt("is_active",1),track=m.optInt("track_stock",0),stock=m.optInt("stock",0);boolean sold=m.optBoolean("is_sold_out",false)||(track==1&&stock<=0);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);FrameLayout iw=new FrameLayout(this);iw.setBackground(round(Color.parseColor("#EEF6FF"),dp(18)));ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);iw.addView(img,new FrameLayout.LayoutParams(-1,-1));TextView fallback=tv("🍽️",30,BLUE,true);fallback.setGravity(Gravity.CENTER);iw.addView(fallback,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(dp(68),dp(68));ilp.setMargins(0,0,dp(12),0);top.addView(iw,ilp);LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.addView(tv(name.isEmpty()?"Tanpa nama":name,15,NAVY,true));info.addView(tv(cat.isEmpty()?"Menu":cat,13,MUTED,false));info.addView(tv(rupiah(m.optLong("price",0)),15,BLUE,true));if(m.optInt("discount_active",0)==1&&m.optDouble("discount_percent",0)>0)info.addView(tv("🏷️ Diskon merchant "+String.format(java.util.Locale.US,"%.0f%%",m.optDouble("discount_percent",0))+" • realtime",12,Color.parseColor("#D97706"),true));String state=sold?"🔴 Habis":(active==1?"🟢 Tersedia":"🔴 Nonaktif");info.addView(tv(state,13,sold||active!=1?Color.parseColor("#B42318"):Color.parseColor("#16803A"),true));if(track==1)info.addView(tv("Stok: "+stock,12,MUTED,false));top.addView(info,new LinearLayout.LayoutParams(0,-2,1));box.addView(top);loadMenuImage(img,fallback,image);
        LinearLayout actions = actionRow();
        Button edit = outlineBtn("Edit menu");
        Button status = active == 1 ? outlineBtn("Nonaktif") : btn("Aktifkan");
        actions.addView(edit, actionLp());
        actions.addView(status, actionLp());
        box.addView(actions);
        edit.setOnClickListener(v -> editMenu(m, id, name, cat, image, track, stock));
        status.setOnClickListener(v -> updateStatus(id, active == 1 ? 0 : 1));
        Button delete = outlineBtn("Hapus menu");
        delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Hapus menu")
                .setMessage("Hapus " + name + " dari toko ini?")
                .setNegativeButton("Batal", null).setPositiveButton("Hapus", (d,w) -> deleteMenu(id)).show());
        box.addView(delete);
        return box;
    }

    private void editMenu(JSONObject menu, String id, String name, String category, String image, int track, int stock) {
        android.content.Intent intent = new android.content.Intent(this, MerchantAddMenuActivity.class);
        intent.putExtra("edit_mode", true);
        intent.putExtra("menu_id", id);
        intent.putExtra("name", name);
        intent.putExtra("category", category);
        intent.putExtra("price", menu.optLong("price", 0));
        intent.putExtra("original_price", menu.optLong("original_price", menu.optLong("merchant_price", 0)));
        intent.putExtra("grossup_fee", menu.optLong("grossup_fee", 0));
        intent.putExtra("image", image);
        intent.putExtra("description", menu.optString("description", ""));
        intent.putExtra("track_stock", track);
        intent.putExtra("stock", stock);
        intent.putExtra("discount_active", menu.optInt("discount_active", 0));
        intent.putExtra("discount_percent", menu.optDouble("discount_percent", 0));
        JSONArray options = menu.optJSONArray("options");
        if (options != null) intent.putExtra("options_json", options.toString());
        startActivity(intent);
    }
    private void loadMenuImage(ImageView iv,TextView fallback,String raw){String url=normalizeImageUrl(raw);if(url.isEmpty())return;Glide.with(this).load(url).centerCrop().thumbnail(0.25f).listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>(){@Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,Object model,com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,boolean first){fallback.setVisibility(android.view.View.VISIBLE);return false;}@Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,Object model,com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,com.bumptech.glide.load.DataSource dataSource,boolean first){fallback.setVisibility(android.view.View.GONE);return false;}}).into(iv);}
    private String normalizeImageUrl(String raw){if(raw==null)return"";String u=raw.trim();if(u.isEmpty()||"null".equalsIgnoreCase(u))return"";if(u.startsWith("http://transiva.my.id/"))return u.replace("http://transiva.my.id/","https://transiva.my.id/");if(u.startsWith("https://"))return u;if(u.startsWith("http://"))return"";if(u.startsWith("/"))return"https://transiva.my.id"+u;return"https://transiva.my.id/"+u;}
    private void updateStatus(String id,int active){MerchantNetworkExecutor.executeWrite("menu-status:"+id,()->{try{JSONObject f=new JSONObject();f.put("menu_id",id);f.put("is_active",active);JSONObject r=new JSONObject(postForm(BASE+"merchant_update_menu_status.php",f,null,"",""));runOnUiThread(()->{toast(r.optString("message","Berhasil"));load();});}catch(Exception e){runOnUiThread(()->alert("Error","Gagal update status menu."));}});}
    private void deleteMenu(String id){MerchantNetworkExecutor.executeWrite("menu-delete:"+id,()->{try{JSONObject f=new JSONObject();f.put("menu_id",id);JSONObject r=new JSONObject(postForm(BASE+"merchant_delete_menu.php",f,null,"",""));runOnUiThread(()->{toast(r.optString("message","Berhasil"));load();});}catch(Exception e){runOnUiThread(()->alert("Error","Gagal hapus menu."));}});}
}
