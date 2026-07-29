package com.transiva.app;

import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MerchantAddMenuActivity extends MerchantBaseActivity {
    private static final int PICK_IMAGE = 801;
    private static final String GROSSUP_ENDPOINT = BASE + "get_transfood_grossup_rules.php";

    private LinearLayout root;
    private EditText nameInput, priceInput, categoryInput, descriptionInput, stockInput, variantsInput, toppingsInput;
    private CheckBox trackStockInput;
    private TextView originalText, fileText, previewName, previewPrice, previewCategory, previewIcon;
    private ImageView previewImage;
    private Uri imageUri = null;
    private final List<GrossupRule> grossupRules = new ArrayList<>();
    private volatile boolean grossupLoaded = false;
    private boolean editMode = false;
    private String editMenuId = "";
    private String existingImage = "";

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        editMode = getIntent().getBooleanExtra("edit_mode", false);
        editMenuId = getIntent().getStringExtra("menu_id");
        if(editMenuId == null) editMenuId = "";
        existingImage = getIntent().getStringExtra("image");
        if(existingImage == null) existingImage = "";
        build();
        prefillEditData();
        loadGrossupRules();
    }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title(editMode ? "Edit Menu" : "Tambah Menu"));
        root.addView(sub("Preview di bawah mengikuti tampilan yang akan muncul di aplikasi customer"));

        root.addView(label("Nama Menu"));
        nameInput = input("Contoh: Nasi Goreng", InputType.TYPE_CLASS_TEXT);
        root.addView(nameInput);

        root.addView(label("Harga Asli Merchant"));
        priceInput = input("Contoh: 20000", InputType.TYPE_CLASS_NUMBER);
        root.addView(priceInput);

        root.addView(label("Kategori"));
        categoryInput = input("Makanan / Minuman", InputType.TYPE_CLASS_TEXT);
        root.addView(categoryInput);

        Button manageCategory = outlineBtn("Kelola Kategori");
        manageCategory.setOnClickListener(v -> open(MerchantCategoriesActivity.class));
        root.addView(manageCategory);

        root.addView(label("Deskripsi Menu"));
        descriptionInput = input("Contoh: Nasi goreng dengan ayam dan telur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(descriptionInput);

        trackStockInput = new CheckBox(this); trackStockInput.setText("Gunakan stok terbatas / tandai habis otomatis"); root.addView(trackStockInput);
        stockInput = input("Jumlah stok, contoh: 25", InputType.TYPE_CLASS_NUMBER); root.addView(stockInput);

        root.addView(label("Varian (satu per baris: Nama|Tambahan Harga)"));
        variantsInput = input("Regular|0\nLarge|7000", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); variantsInput.setMinLines(3); variantsInput.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(96))); root.addView(variantsInput);
        root.addView(label("Topping (satu per baris: Nama|Tambahan Harga)"));
        toppingsInput = input("Telur|5000\nAyam|8000", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); toppingsInput.setMinLines(3); toppingsInput.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(96))); root.addView(toppingsInput);

        originalText = card("Harga Asli: Rp 0\nFee Gross Up: memuat...\nHarga Tampil: Rp 0");
        root.addView(originalText);

        root.addView(label("Preview Tampilan di Aplikasi"));
        root.addView(previewCard());

        addWatchers();

        Button pick = outlineBtn("📷 Pilih Gambar Menu");
        pick.setOnClickListener(v -> chooseImage());
        root.addView(pick);
        fileText = sub("Gambar belum dipilih. Jika kosong, server memakai default.");
        root.addView(fileText);

        Button save = btn(editMode ? "Simpan Perubahan" : "Simpan Menu");
        save.setOnClickListener(v -> save(save));
        root.addView(save);

        Button back = outlineBtn("← Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        updatePreview();
    }


    private void prefillEditData(){
        if(!editMode) return;
        nameInput.setText(getIntent().getStringExtra("name"));
        categoryInput.setText(getIntent().getStringExtra("category"));
        descriptionInput.setText(getIntent().getStringExtra("description"));
        trackStockInput.setChecked(getIntent().getIntExtra("track_stock",0)==1);
        stockInput.setText(String.valueOf(getIntent().getIntExtra("stock",0)));
        applyOptionsJson(getIntent().getStringExtra("options_json"));
        long original = getIntent().getLongExtra("original_price", 0L);
        if(original <= 0) original = getIntent().getLongExtra("price", 0L);
        if(original > 0) priceInput.setText(String.valueOf(original));
        fileText.setText(existingImage.isEmpty() ? "Gambar lama tidak tersedia. Pilih gambar jika ingin mengganti." : "Gambar lama dipertahankan. Pilih gambar baru untuk mengganti.");
        updatePreview();
    }

    private LinearLayout previewCard(){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(round(Color.WHITE, dp(20)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(14));
        box.setLayoutParams(lp);

        FrameLayout imgWrap = new FrameLayout(this);
        imgWrap.setBackground(round(Color.parseColor("#EEF6FF"), dp(18)));
        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgWrap.addView(previewImage, new FrameLayout.LayoutParams(-1, -1));
        previewIcon = tv("🍽️", 34, BLUE, true);
        previewIcon.setGravity(Gravity.CENTER);
        imgWrap.addView(previewIcon, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        imgLp.setMargins(0, 0, dp(12), 0);
        box.addView(imgWrap, imgLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        previewName = tv("Nama menu", 17, NAVY, true);
        previewPrice = tv("Rp 0", 16, BLUE, true);
        previewCategory = tv("Kategori", 13, MUTED, false);
        TextView hint = tv("Harga ini yang tampil di aplikasi", 11, Color.parseColor("#98A2B3"), false);
        info.addView(previewName);
        info.addView(previewPrice);
        info.addView(previewCategory);
        info.addView(hint);
        box.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        return box;
    }

    private void addWatchers(){
        android.text.TextWatcher watcher = new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ updatePreview(); }
            public void afterTextChanged(android.text.Editable e){}
        };
        nameInput.addTextChangedListener(watcher);
        priceInput.addTextChangedListener(watcher);
        categoryInput.addTextChangedListener(watcher);
    }

    private void loadGrossupRules(){
        new Thread(() -> {
            try{
                JSONObject response = new JSONObject(get(GROSSUP_ENDPOINT));
                if(!response.optBoolean("success", false)) throw new Exception(response.optString("message", "Gagal memuat gross-up"));
                JSONArray rows = response.optJSONArray("grossup_rules");
                List<GrossupRule> loaded = new ArrayList<>();
                if(rows != null){
                    for(int i=0; i<rows.length(); i++){
                        JSONObject row = rows.optJSONObject(i);
                        if(row == null) continue;
                        long min = Math.max(0L, row.optLong("min_amount", 0L));
                        Long max = null;
                        if(!row.isNull("max_amount")) max = Math.max(min, row.optLong("max_amount", min));
                        long fee = Math.max(0L, row.optLong("fee", 0L));
                        loaded.add(new GrossupRule(min, max, fee));
                    }
                }
                runOnUiThread(() -> {
                    grossupRules.clear();
                    grossupRules.addAll(loaded);
                    grossupLoaded = true;
                    updatePreview();
                });
            }catch(Exception e){
                runOnUiThread(() -> {
                    grossupLoaded = false;
                    updatePreview();
                    Toast.makeText(this, "Aturan gross-up belum dapat dimuat. Periksa API server.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private long gross(long price){
        if(price <= 0) return 0;
        for(GrossupRule rule : grossupRules){
            boolean aboveMin = price >= rule.min;
            boolean belowMax = rule.max == null || price <= rule.max;
            if(aboveMin && belowMax) return rule.fee;
        }
        return 0;
    }

    private void updatePreview(){
        long original = 0; try{ original = Long.parseLong(priceInput.getText().toString().trim()); }catch(Exception ignored){}
        long fee = gross(original);
        long appPrice = original + fee;
        String feeText = grossupLoaded ? rupiah(fee) : "memuat...";
        originalText.setText("Harga Asli: " + rupiah(original) + "\nFee Gross Up: " + feeText + "\nHarga Tampil: " + rupiah(appPrice));
        String name = nameInput.getText().toString().trim();
        String cat = categoryInput.getText().toString().trim();
        previewName.setText(name.isEmpty() ? "Nama menu" : name);
        previewCategory.setText(cat.isEmpty() ? "Kategori" : cat);
        previewPrice.setText(rupiah(appPrice));
    }

    private void applyOptionsJson(String raw){
        if(raw == null || raw.trim().isEmpty()) return;
        try{
            JSONArray groups = new JSONArray(raw); StringBuilder vars = new StringBuilder(), tops = new StringBuilder();
            for(int i=0;i<groups.length();i++){
                JSONObject g=groups.optJSONObject(i); if(g==null) continue; String type=g.optString("type",""); JSONArray items=g.optJSONArray("items"); if(items==null) continue;
                StringBuilder target="topping".equals(type)?tops:vars;
                for(int j=0;j<items.length();j++){JSONObject it=items.optJSONObject(j);if(it==null)continue;if(target.length()>0)target.append("\n");target.append(it.optString("name","")).append("|").append(it.optLong("price",0));}
            }
            variantsInput.setText(vars.toString()); toppingsInput.setText(tops.toString());
        }catch(Exception ignored){}
    }

    private JSONArray buildOptions(){
        JSONArray groups=new JSONArray();
        try{
            groups.put(optionGroup("variant","Varian",variantsInput.getText().toString()));
            groups.put(optionGroup("topping","Topping",toppingsInput.getText().toString()));
        }catch(Exception ignored){}
        return groups;
    }
    private JSONObject optionGroup(String type,String label,String raw) throws Exception{
        JSONObject g=new JSONObject(); g.put("type",type); g.put("label",label); JSONArray items=new JSONArray();
        for(String line:raw.split("\n")){line=line.trim();if(line.isEmpty())continue;String[] p=line.split("\\|",2);JSONObject it=new JSONObject();it.put("name",p[0].trim());long price=0;if(p.length>1)try{price=Long.parseLong(p[1].trim());}catch(Exception ignored){}it.put("price",Math.max(0,price));items.put(it);}g.put("items",items);return g;
    }

    private void chooseImage(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih gambar menu"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int r, int c, Intent data){
        super.onActivityResult(r,c,data);
        if(r == PICK_IMAGE && c == RESULT_OK && data != null){
            imageUri = data.getData();
            fileText.setText("Gambar dipilih dan siap diupload.");
            showPickedImage(imageUri);
        }
    }

    private void showPickedImage(Uri uri){
        if(uri == null) return;
        try{
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if(bmp != null){
                previewIcon.setVisibility(android.view.View.GONE);
                previewImage.setImageBitmap(bmp);
            }
        }catch(Exception e){
            toast("Preview gambar gagal, tapi file masih bisa dicoba upload.");
        }
    }

    private void save(Button save){
        String name = nameInput.getText().toString().trim();
        String cat = categoryInput.getText().toString().trim();
        long original = 0; try{ original = Long.parseLong(priceInput.getText().toString().trim()); }catch(Exception ignored){}
        if(name.isEmpty() || cat.isEmpty() || original <= 0){ alert("Lengkapi Data", "Nama, harga, dan kategori wajib diisi."); return; }
        if(!grossupLoaded){ alert("Aturan Harga Belum Siap", "Aturan gross-up belum berhasil dimuat dari server. Coba buka ulang halaman atau periksa API server."); return; }
        long fee = gross(original); long appPrice = original + fee;

        final String finalName = name;
        final String finalCat = cat;
        final long finalOriginal = original;
        final long finalFee = fee;
        final long finalAppPrice = appPrice;
        final Uri finalImageUri = imageUri;

        save.setEnabled(false); save.setText(editMode ? "Menyimpan perubahan..." : "Mengupload...");
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject();
                f.put("name", finalName);
                f.put("price", finalAppPrice);
                f.put("original_price", finalOriginal);
                f.put("grossup_fee", finalFee);
                f.put("category", finalCat);
                f.put("description", descriptionInput.getText().toString().trim());
                f.put("track_stock", trackStockInput.isChecked() ? 1 : 0);
                int stock = 0; try{ stock = Integer.parseInt(stockInput.getText().toString().trim()); }catch(Exception ignored){}
                f.put("stock", Math.max(0, stock));
                f.put("options_json", buildOptions().toString());
                if(editMode) {
                    f.put("menu_id", editMenuId);
                    f.put("id", editMenuId);
                    f.put("action", "update");
                }
                String endpoint = editMode ? BASE + "merchant_update_menu.php" : BASE + "add_food_menu.php";
                JSONObject res = new JSONObject(postForm(endpoint, f, finalImageUri, "image", "menu.jpg"));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText(editMode ? "Simpan Perubahan" : "Simpan Menu");
                    if(res.optBoolean("success", false)){ toast(res.optString("message", editMode ? "Menu berhasil diperbarui" : "Menu berhasil disimpan")); finish(); }
                    else alert("Gagal", res.optString("message", editMode ? "Gagal memperbarui menu" : "Gagal menyimpan menu"));
                });
            }catch(Exception e){ runOnUiThread(() -> { save.setEnabled(true); save.setText(editMode ? "Simpan Perubahan" : "Simpan Menu"); alert("Error","Server error / koneksi gagal."); }); }
        }).start();
    }

    private static class GrossupRule {
        final long min;
        final Long max;
        final long fee;
        GrossupRule(long min, Long max, long fee){ this.min = min; this.max = max; this.fee = fee; }
    }
}
