package com.transiva.app;

import com.bumptech.glide.Glide;
import android.os.Bundle;
import android.app.AlertDialog;
import android.graphics.Color;


import android.text.InputType;
import android.view.Gravity;
import android.text.TextUtils;
import java.util.Locale;
import android.widget.*;



import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantMenuListActivity extends MerchantBaseActivity {
    private LinearLayout list;
    private EditText search;
    private Spinner statusFilter;
    private TextView resultCount;
    private JSONArray cached=new JSONArray();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Menu Toko"));

        LinearLayout actions=actionRow();
        Button add=compactAction("＋ Tambah menu",true);
        Button categories=compactAction("Kategori",false);
        add.setOnClickListener(v->open(MerchantAddMenuActivity.class));
        categories.setOnClickListener(v->open(MerchantCategoriesActivity.class));
        actions.addView(add,actionLp());
        actions.addView(categories,actionLp());
        root.addView(actions);

        search=input("Cari menu, kategori, atau topping",InputType.TYPE_CLASS_TEXT);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(search);
        LinearLayout filters=new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        resultCount=tv("",12,MUTED,true);
        filters.addView(resultCount,new LinearLayout.LayoutParams(0,-2,1));
        statusFilter=new Spinner(this);
        statusFilter.setContentDescription("Filter status menu");
        statusFilter.setMinimumHeight(dp(44));
        statusFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Semua","Tersedia","Habis / nonaktif"}));
        statusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,android.view.View view,int pos,long id) { render(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        filters.addView(statusFilter,new LinearLayout.LayoutParams(-2,-2));
        root.addView(filters);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
    }

    @Override protected void onResume(){super.onResume();load();}

    private void load() {
        list.removeAllViews();
        list.addView(card("Memuat menu…"));
        MerchantNetworkExecutor.executeRead(this,"menus",()->{
            try {
                JSONObject response=new JSONObject(get(BASE+"merchant_get_menus.php?v="+System.currentTimeMillis()));
                if (!response.optBoolean("success")) throw new IllegalStateException(response.optString("message"));
                JSONArray menus=response.optJSONArray("menus");
                runOnUiThread(()->{cached=menus==null?new JSONArray():menus;render();});
            } catch (Exception error) {
                runOnUiThread(()->{
                    list.removeAllViews();
                    Button retry=compactAction("Gagal memuat · Coba lagi",false);
                    retry.setOnClickListener(v->load());
                    list.addView(retry);
                });
            }
        });
    }

    private boolean soldOut(JSONObject menu) {
        return menu.optBoolean("is_sold_out") || menu.optInt("track_stock")==1 && menu.optInt("stock")<=0;
    }

    private java.util.ArrayList<String> toppingNames(JSONObject menu) {
        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        JSONArray groups=menu.optJSONArray("options");
        if (groups==null) return names;
        for (int i=0;i<groups.length();i++) {
            JSONObject group=groups.optJSONObject(i);
            if (group==null) continue;
            String type=group.optString("type");
            if (!"bundle_topping".equals(type) && !"topping".equals(type)) continue;
            JSONArray items=group.optJSONArray("items");
            if (items==null) continue;
            for (int j=0;j<items.length();j++) {
                JSONObject item=items.optJSONObject(j);
                if (item==null) continue;
                String name=item.optString("name").trim();
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    private void render() {
        if (list==null) return;
        list.removeAllViews();
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filter=statusFilter.getSelectedItemPosition(), shown=0;
        for (int i=0;i<cached.length();i++) {
            JSONObject menu=cached.optJSONObject(i);
            if (menu==null) continue;
            boolean available=menu.optInt("is_active",1)==1 && !soldOut(menu);
            if (filter==1 && !available || filter==2 && available) continue;
            java.util.ArrayList<String> toppings=toppingNames(menu);
            String searchable=s(menu,"name","menu_name","food_name")+" "+s(menu,"category","type")
                    +" "+TextUtils.join(" ",toppings);
            if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            list.addView(menuCard(menu,toppings));
            shown++;
        }
        resultCount.setText(shown+" menu");
        if (shown==0) list.addView(card("Tidak ada menu sesuai filter."));
    }

    private Button compactAction(String label, boolean primary) {
        Button button=primary?btn(label):outlineBtn(label);
        button.setTextSize(13);
        button.setMinimumHeight(dp(44)); button.setMinHeight(dp(44));
        button.setPadding(dp(8),dp(4),dp(8),dp(4));
        button.setElevation(0);
        return button;
    }

    private LinearLayout menuCard(JSONObject menu, java.util.List<String> toppings) {
        String id=s(menu,"id","menu_id"), name=s(menu,"name","menu_name","food_name");
        String category=s(menu,"category","type"), image=s(menu,"image","photo","image_url");
        int active=menu.optInt("is_active",1), track=menu.optInt("track_stock"), stock=menu.optInt("stock");
        boolean sold=soldOut(menu);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(9),dp(10),dp(5));
        box.setBackground(stroke(Color.WHITE,Color.parseColor("#DCE8F4"),dp(16)));
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
        outer.bottomMargin=dp(7);
        box.setLayoutParams(outer);

        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout thumb=new FrameLayout(this);
        thumb.setBackground(round(Color.parseColor("#EEF6FF"),dp(10)));
        thumb.setClipToOutline(true);
        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        TextView fallback=tv("🍽️",20,BLUE,true);
        fallback.setGravity(Gravity.CENTER);
        thumb.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams picture=new LinearLayout.LayoutParams(dp(60),dp(60));
        picture.rightMargin=dp(9);
        row.addView(thumb,picture);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(line(name.isEmpty()?"Tanpa nama":name,15,NAVY,true,2));
        String status=sold?"Habis":active==1?"Tersedia":"Nonaktif";
        String details=(category.isEmpty()?"Menu":category)+" · "+status+(track==1?" · Stok "+stock:"");
        info.addView(line(details,12,MUTED,false,2));
        String price=rupiah(menu.optLong("price",0));
        if (menu.optInt("discount_active")==1 && menu.optDouble("discount_percent")>0)
            price+=" · Diskon "+String.format(Locale.US,"%.0f%%",menu.optDouble("discount_percent"));
        info.addView(line(price,13,BLUE,true,2));
        if (!toppings.isEmpty()) {
            info.addView(line(toppings.size()+" topping · "+TextUtils.join(", ",toppings),11,BLUE,true,2));
        }
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        box.addView(row);
        loadMenuImage(photo,fallback,image);

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        Button edit=compactAction("Edit menu",false);
        Button more=compactAction("⋯",false);
        more.setContentDescription("Tindakan untuk "+name);
        LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(dp(48),-2);
        moreLp.leftMargin=dp(6);
        controls.addView(edit,new LinearLayout.LayoutParams(-2,-2));
        controls.addView(more,moreLp);
        box.addView(controls);
        edit.setOnClickListener(v->editMenu(menu,id,name,category,image,track,stock));
        more.setOnClickListener(v->{
            PopupMenu popup=new PopupMenu(this,more);
            popup.getMenu().add(0,1,0,active==1?"Nonaktifkan":"Aktifkan");
            popup.getMenu().add(0,2,1,"Hapus menu");
            popup.setOnMenuItemClickListener(item->{
                if (item.getItemId()==1) updateStatus(id,active==1?0:1);
                else PremiumDialogs.builder(this).setTitle("Hapus menu")
                        .setMessage("Hapus "+name+" dari toko ini?")
                        .setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->deleteMenu(id)).show();
                return true;
            });
            popup.show();
        });
        return box;
    }

    private TextView line(String value,int size,int color,boolean bold,int maxLines) {
        TextView text=tv(value,size,color,bold);
        text.setMaxLines(maxLines);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
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
