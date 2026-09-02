package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MerchantAddMenuActivity extends MerchantBaseActivity {
    private static final int PICK_IMAGE = 801;
    private static final String GROSSUP_ENDPOINT = BASE + "get_transfood_grossup_rules.php";

    private static final String[][] CATEGORIES = {
            {"🍛", "Makanan", "Nasi, lauk, ayam, mie, bakso, seafood, dll."},
            {"🧋", "Minuman", "Kopi, teh, jus, boba, es, minuman kekinian."},
            {"🍟", "Snack & Jajanan", "Gorengan, kentang, cireng, dimsum, jajanan pasar."},
            {"🍰", "Roti & Dessert", "Roti, donat, cake, martabak manis, dessert."},
            {"🍗", "Fast Food", "Burger, pizza, fried chicken, kebab."},
            {"🍜", "Mie & Bakso", "Mie ayam, mie goreng, bakso, ramen."},
            {"☕", "Kopi & Kafe", "Coffee shop, cafe, pastry."}
    };

    private LinearLayout root;
    private EditText nameInput, priceInput, descriptionInput, stockInput;
    private CheckBox trackStockInput;
    private TextView categoryValue, originalText, fileText, previewName, previewPrice, previewCategory, previewIcon;
    private ImageView previewImage;
    private Button pickImageButton, saveMenuButton;
    private LinearLayout variantsBox, toppingsBox;
    private final List<OptionRow> variantRows = new ArrayList<>();
    private final List<OptionRow> toppingRows = new ArrayList<>();
    private Uri imageUri;
    private PreparedImage preparedMenuImage;
    private volatile boolean imagePreparing;
    private final List<GrossupRule> grossupRules = new ArrayList<>();
    private volatile boolean grossupLoaded;
    private boolean editMode;
    private String editMenuId = "";
    private String existingImage = "";
    private String selectedCategory = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        editMode = getIntent().getBooleanExtra("edit_mode", false);
        editMenuId = safe(getIntent().getStringExtra("menu_id"));
        existingImage = safe(getIntent().getStringExtra("image"));
        build();
        prefillEditData();
        loadGrossupRules();
    }

    private void build() {
        root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title(editMode ? "Edit Menu" : "Tambah Menu"));
        root.addView(sub("Atur menu, kategori, varian dan topping dengan tampilan yang lebih rapi."));

        root.addView(label("Nama Menu"));
        nameInput = input("Contoh: Nasi Goreng Spesial", InputType.TYPE_CLASS_TEXT);
        root.addView(nameInput);

        root.addView(label("Harga Asli Merchant"));
        priceInput = input("Contoh: 20000", InputType.TYPE_CLASS_NUMBER);
        root.addView(priceInput);

        root.addView(label("Kategori"));
        root.addView(categorySelector());

        root.addView(label("Deskripsi Menu"));
        descriptionInput = input("Contoh: Nasi goreng dengan ayam dan telur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionInput.setMinLines(2);
        descriptionInput.setLayoutParams(heightLp(78));
        root.addView(descriptionInput);

        trackStockInput = new CheckBox(this);
        trackStockInput.setText("Gunakan stok terbatas / tandai habis otomatis");
        root.addView(trackStockInput);
        stockInput = input("Jumlah stok, contoh: 25", InputType.TYPE_CLASS_NUMBER);
        root.addView(stockInput);

        root.addView(sectionTitle("Varian"));
        root.addView(sub("Nama varian dan tambahan harga dibuat berdampingan. Varian pertama otomatis Regular • Rp 0."));
        variantsBox = new LinearLayout(this);
        variantsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(variantsBox);
        addOptionRow(variantsBox, variantRows, "Reguler", "0", true, "Nama varian");
        Button addVariant = outlineBtn("＋ Tambah Varian");
        addVariant.setOnClickListener(v -> addOptionRow(variantsBox, variantRows, "", "", false, "Nama varian"));
        root.addView(addVariant);

        root.addView(sectionTitle("Topping"));
        root.addView(sub("Isi nama topping dan harga tambahannya. Kosongkan jika menu tidak memakai topping."));
        toppingsBox = new LinearLayout(this);
        toppingsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(toppingsBox);
        addOptionRow(toppingsBox, toppingRows, "", "", false, "Nama topping");
        Button addTopping = outlineBtn("＋ Tambah Topping");
        addTopping.setOnClickListener(v -> addOptionRow(toppingsBox, toppingRows, "", "", false, "Nama topping"));
        root.addView(addTopping);

        originalText = card("Harga Asli: Rp 0\nFee Gross Up: memuat...\nHarga Tampil: Rp 0");
        root.addView(originalText);

        root.addView(label("Preview Tampilan di Aplikasi"));
        root.addView(previewCard());

        addWatchers();

        root.addView(sub("✨ AI Resize to WebP aktif — foto besar otomatis diringankan sebelum upload, foto kecil tetap dipertahankan."));
        pickImageButton = outlineBtn("📷 Pilih Gambar Menu");
        pickImageButton.setOnClickListener(v -> chooseImage());
        root.addView(pickImageButton);
        fileText = sub("Gambar belum dipilih. Jika kosong, server memakai default.");
        root.addView(fileText);

        saveMenuButton = btn(editMode ? "Simpan Perubahan" : "Simpan Menu");
        saveMenuButton.setOnClickListener(v -> save(saveMenuButton));
        root.addView(saveMenuButton);

        Button back = outlineBtn("← Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        updatePreview();
    }

    private View categorySelector() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(stroke(Color.WHITE, Color.parseColor("#CFE2FF"), dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(66));
        lp.setMargins(0, dp(4), 0, dp(12));
        card.setLayoutParams(lp);

        TextView icon = tv("🍽️", 25, NAVY, false);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), -1));
        categoryValue = tv("Pilih kategori menu", 14, NAVY, true);
        categoryValue.setPadding(dp(8), 0, 0, 0);
        card.addView(categoryValue, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = tv("›", 28, BLUE, true);
        card.addView(arrow);
        card.setOnClickListener(v -> showCategoryDialog());
        return card;
    }

    private void showCategoryDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18), dp(18), dp(18), dp(12));
        wrap.setBackgroundColor(Color.WHITE);
        TextView h = tv("Pilih Kategori Menu", 20, NAVY, true);
        wrap.addView(h);
        TextView sh = tv("Kategori membantu customer menemukan menu lebih cepat.", 12, MUTED, false);
        sh.setPadding(0, dp(4), 0, dp(12));
        wrap.addView(sh);
        for (String[] item : CATEGORIES) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(stroke(Color.parseColor("#F8FBFF"), Color.parseColor("#DFEAF7"), dp(14)));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rlp);
            TextView em = tv(item[0], 24, NAVY, false);
            em.setGravity(Gravity.CENTER);
            row.addView(em, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv(item[1], 14, NAVY, true);
            TextView ds = tv(item[2], 11, MUTED, false);
            texts.addView(nm); texts.addView(ds);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> {
                setCategory(item[1]);
                dialog.dismiss();
            });
            wrap.addView(row);
        }
        Button cancel = outlineBtn("Batal");
        cancel.setOnClickListener(v -> dialog.dismiss());
        wrap.addView(cancel);
        dialog.setView(wrap);
        dialog.show();
    }

    private void setCategory(String category) {
        selectedCategory = normalizeCategory(category);
        String emoji = "🍽️";
        for (String[] c : CATEGORIES) if (c[1].equalsIgnoreCase(selectedCategory)) { emoji = c[0]; break; }
        categoryValue.setText(emoji + "  " + (selectedCategory.isEmpty() ? "Pilih kategori menu" : selectedCategory));
        updatePreview();
    }

    private String normalizeCategory(String raw) { return MerchantMenuRules.normalizeCategory(raw); }

    private TextView sectionTitle(String text) {
        TextView t = tv(text, 16, NAVY, true);
        t.setPadding(dp(4), dp(8), dp(4), 0);
        return t;
    }

    private void addOptionRow(LinearLayout parent, List<OptionRow> list, String name, String price, boolean locked, String nameHint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(54));
        rowLp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowLp);

        EditText nameInput = smallInput(nameHint, InputType.TYPE_CLASS_TEXT);
        nameInput.setText(name);
        if (locked) nameInput.setEnabled(false);
        row.addView(nameInput, new LinearLayout.LayoutParams(0, -1, 1.55f));

        EditText priceInput = smallInput("Harga", InputType.TYPE_CLASS_NUMBER);
        priceInput.setText(price);
        if (locked) priceInput.setEnabled(false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -1, 0.9f);
        pp.setMargins(dp(8), 0, 0, 0);
        row.addView(priceInput, pp);

        Button remove = null;
        if (!locked) {
            remove = new Button(this);
            remove.setText("×");
            remove.setTextSize(19);
            remove.setTextColor(Color.parseColor("#D92D20"));
            remove.setAllCaps(false);
            remove.setPadding(0, 0, 0, 0);
            remove.setBackground(round(Color.parseColor("#FFF1F0"), dp(12)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(46), -1);
            rp.setMargins(dp(8), 0, 0, 0);
            row.addView(remove, rp);
        }
        OptionRow option = new OptionRow(row, nameInput, priceInput, locked);
        list.add(option);
        if (remove != null) remove.setOnClickListener(v -> {
            parent.removeView(row);
            list.remove(option);
            if (parent == toppingsBox && list.isEmpty()) addOptionRow(parent, list, "", "", false, "Nama topping");
        });
        parent.addView(row);
    }

    private EditText smallInput(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.parseColor("#98A2B3"));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(stroke(Color.WHITE, Color.parseColor("#DDE7F3"), dp(13)));
        return e;
    }

    private LinearLayout.LayoutParams heightLp(int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(h));
        lp.setMargins(0, dp(4), 0, dp(12));
        return lp;
    }

    private void prefillEditData() {
        if (!editMode) return;
        nameInput.setText(safe(getIntent().getStringExtra("name")));
        setCategory(safe(getIntent().getStringExtra("category")));
        descriptionInput.setText(safe(getIntent().getStringExtra("description")));
        trackStockInput.setChecked(getIntent().getIntExtra("track_stock", 0) == 1);
        stockInput.setText(String.valueOf(getIntent().getIntExtra("stock", 0)));
        applyOptionsJson(getIntent().getStringExtra("options_json"));
        long displayPrice = getIntent().getLongExtra("price", 0L);
        long original = getIntent().getLongExtra("original_price", 0L);
        long storedGrossup = Math.max(0L, getIntent().getLongExtra("grossup_fee", 0L));

        // Proteksi edit menu: field merchant harus selalu berisi harga sebelum gross-up.
        // Jika payload lama tidak konsisten, turunkan kembali dari harga tampil - gross-up.
        if (storedGrossup > 0 && displayPrice > storedGrossup &&
                (original <= 0 || original + storedGrossup != displayPrice)) {
            original = displayPrice - storedGrossup;
        }
        if (original <= 0) original = displayPrice;
        if (original > 0) priceInput.setText(String.valueOf(original));
        fileText.setText(existingImage.isEmpty() ? "Gambar lama tidak tersedia. Pilih gambar jika ingin mengganti." : "Gambar lama dipertahankan. Pilih gambar baru untuk mengganti.");
        updatePreview();
    }

    private LinearLayout previewCard() {
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
        info.addView(previewName); info.addView(previewPrice); info.addView(previewCategory); info.addView(hint);
        box.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        return box;
    }

    private void addWatchers() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { updatePreview(); }
            public void afterTextChanged(android.text.Editable e) {}
        };
        nameInput.addTextChangedListener(watcher);
        priceInput.addTextChangedListener(watcher);
    }

    private void loadGrossupRules() {
        MerchantNetworkExecutor.executeRead(this, "grossup-rules", () -> {
            try {
                JSONObject response = new JSONObject(get(GROSSUP_ENDPOINT));
                if (!response.optBoolean("success", false)) throw new Exception(response.optString("message", "Gagal memuat gross-up"));
                JSONArray rows = response.optJSONArray("grossup_rules");
                List<GrossupRule> loaded = new ArrayList<>();
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                    long min = Math.max(0L, row.optLong("min_amount", 0L));
                    Long max = row.isNull("max_amount") ? null : Math.max(min, row.optLong("max_amount", min));
                    loaded.add(new GrossupRule(min, max, Math.max(0L, row.optLong("fee", 0L))));
                }
                runOnUiThread(() -> { grossupRules.clear(); grossupRules.addAll(loaded); grossupLoaded = true; updatePreview(); });
            } catch (Exception e) {
                runOnUiThread(() -> { grossupLoaded = false; updatePreview(); Toast.makeText(this, "Aturan gross-up belum dapat dimuat. Periksa API server.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private long gross(long price) {
        if (price <= 0) return 0;
        for (GrossupRule rule : grossupRules) if (price >= rule.min && (rule.max == null || price <= rule.max)) return rule.fee;
        return 0;
    }

    private void updatePreview() {
        if (originalText == null || priceInput == null) return;
        long original = parseLong(priceInput.getText().toString());
        long fee = gross(original);
        long appPrice = original + fee;
        originalText.setText("Harga Asli: " + rupiah(original) + "\nFee Gross Up: " + (grossupLoaded ? rupiah(fee) : "memuat...") + "\nHarga Tampil: " + rupiah(appPrice));
        String name = nameInput.getText().toString().trim();
        previewName.setText(name.isEmpty() ? "Nama menu" : name);
        previewCategory.setText(selectedCategory.isEmpty() ? "Kategori" : selectedCategory);
        previewPrice.setText(rupiah(appPrice));
    }

    private void applyOptionsJson(String raw) {
        variantRows.clear(); variantsBox.removeAllViews();
        toppingRows.clear(); toppingsBox.removeAllViews();
        addOptionRow(variantsBox, variantRows, "Reguler", "0", true, "Nama varian");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONArray groups = new JSONArray(raw);
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject g = groups.optJSONObject(i); if (g == null) continue;
                    String type = g.optString("type", ""); JSONArray items = g.optJSONArray("items"); if (items == null) continue;
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject it = items.optJSONObject(j); if (it == null) continue;
                        String nm = it.optString("name", "").trim();
                        if (nm.isEmpty()) continue;
                        String pr = String.valueOf(Math.max(0, it.optLong("price", 0)));
                        if ("topping".equals(type)) {
                            addOptionRow(toppingsBox, toppingRows, nm, pr, false, "Nama topping");
                        } else if (!nm.equalsIgnoreCase("regular") && !nm.equalsIgnoreCase("reguler")) {
                            addOptionRow(variantsBox, variantRows, nm, pr, false, "Nama varian");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (toppingRows.isEmpty()) addOptionRow(toppingsBox, toppingRows, "", "", false, "Nama topping");
    }

    private JSONArray buildOptions() {
        JSONArray groups = new JSONArray();
        try {
            groups.put(optionGroup("variant", "Varian", variantRows, true));
            groups.put(optionGroup("topping", "Topping", toppingRows, false));
        } catch (Exception ignored) {}
        return groups;
    }

    private JSONObject optionGroup(String type, String label, List<OptionRow> rows, boolean ensureRegular) throws Exception {
        JSONObject g = new JSONObject(); g.put("type", type); g.put("label", label); JSONArray items = new JSONArray();
        if (ensureRegular) { JSONObject regular = new JSONObject(); regular.put("name", "Reguler"); regular.put("price", 0); items.put(regular); }
        for (OptionRow r : rows) {
            if (r.locked && ensureRegular) continue;
            String name = r.name.getText().toString().trim();
            if (name.isEmpty()) continue;
            JSONObject it = new JSONObject(); it.put("name", name); it.put("price", Math.max(0, parseLong(r.price.getText().toString()))); items.put(it);
        }
        g.put("items", items); return g;
    }

    private void chooseImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih gambar menu"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int r, int c, Intent data) {
        super.onActivityResult(r, c, data);
        if (r == PICK_IMAGE && c == RESULT_OK && data != null) {
            imageUri = data.getData(); preparedMenuImage = null; showPickedImage(imageUri); prepareMenuImageAi();
        }
    }


    private void prepareMenuImageAi() {
        if (imageUri == null) return;
        imagePreparing = true;
        setButtonLoading(pickImageButton, true, "📷 Pilih Gambar Menu", "AI Resize to WebP...");
        fileText.setText("AI Resize to WebP sedang menganalisis gambar...");
        final Uri source = imageUri;
        new Thread(() -> {
            try {
                PreparedImage result = prepareAiResizeToWebp(source, "menu", 900, 150 * 1024L, 110 * 1024L);
                preparedMenuImage = result;
                runOnUiThread(() -> {
                    imagePreparing = false;
                    setButtonLoading(pickImageButton, false, "📷 Pilih Gambar Menu", "");
                    if (result != null && result.transformed) {
                        fileText.setText("AI Resize to WebP: " + humanBytes(result.originalBytes) + " → " + humanBytes(result.finalBytes) + ". Siap upload.");
                    } else if (result != null) {
                        fileText.setText("Ukuran sudah kecil (" + humanBytes(result.finalBytes) + "). File asli dipertahankan, tidak diperkecil lagi.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    imagePreparing = false; preparedMenuImage = null;
                    setButtonLoading(pickImageButton, false, "📷 Pilih Gambar Menu", "");
                    alert("Gambar Tidak Dapat Diproses", e.getMessage() == null ? "Pilih gambar lain." : e.getMessage());
                });
            }
        }).start();
    }

    private void showPickedImage(Uri uri) {
        if (uri == null) return;
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if (bmp != null) { previewIcon.setVisibility(View.GONE); previewImage.setImageBitmap(bmp); }
        } catch (Exception e) { toast("Preview gambar gagal, tetapi file tetap dapat diupload."); }
    }

    private void save(Button save) {
        String name = nameInput.getText().toString().trim();
        String cat = selectedCategory.trim();
        long original = parseLong(priceInput.getText().toString());
        if (name.isEmpty() || cat.isEmpty() || original <= 0) { alert("Lengkapi Data", "Nama, harga, dan kategori wajib diisi."); return; }
        if (!grossupLoaded) { alert("Aturan Harga Belum Siap", "Aturan gross-up belum berhasil dimuat dari server. Coba buka ulang halaman atau periksa API server."); return; }
        long fee = gross(original), appPrice = original + fee;
        if (imagePreparing) { alert("AI Resize Masih Berjalan", "Tunggu sebentar sampai gambar selesai disiapkan."); return; }
        if (!editMode && imageUri == null) { alert("Gambar Wajib", "Pilih gambar menu terlebih dahulu."); return; }
        if (imageUri != null && preparedMenuImage == null) { alert("Gambar Belum Siap", "Pilih ulang gambar lalu tunggu proses AI Resize selesai."); return; }
        String normalButton = editMode ? "Simpan Perubahan" : "Simpan Menu";
        setButtonLoading(save, true, normalButton, imageUri == null ? "Menyimpan..." : "Mengupload menu...");
        MerchantNetworkExecutor.executeWrite("menu-save:" + (editMode ? editMenuId : "new") + ":" + name, () -> {
            try {
                JSONObject f = new JSONObject();
                f.put("name", name); f.put("price", appPrice); f.put("original_price", original); f.put("grossup_fee", fee); f.put("category", cat);
                f.put("description", descriptionInput.getText().toString().trim()); f.put("track_stock", trackStockInput.isChecked() ? 1 : 0);
                f.put("stock", Math.max(0, (int)parseLong(stockInput.getText().toString()))); f.put("options_json", buildOptions().toString());
                if (editMode) { f.put("menu_id", editMenuId); f.put("id", editMenuId); f.put("action", "update"); }
                String endpoint = editMode ? BASE + "merchant_update_menu.php" : BASE + "add_food_menu.php";
                JSONObject res = new JSONObject(postFormPrepared(endpoint, f, preparedMenuImage, "image"));
                runOnUiThread(() -> {
                    setButtonLoading(save, false, editMode ? "Simpan Perubahan" : "Simpan Menu", "");
                    if (res.optBoolean("success", false)) { toast(res.optString("message", editMode ? "Menu berhasil diperbarui" : "Menu berhasil disimpan")); finish(); }
                    else alert("Gagal", res.optString("message", editMode ? "Gagal memperbarui menu" : "Gagal menyimpan menu"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setButtonLoading(save, false, editMode ? "Simpan Perubahan" : "Simpan Menu", ""); alert("Error", "Server error / koneksi gagal."); });
            }
        });
    }

    private long parseLong(String s) { try { return Long.parseLong(s == null ? "0" : s.trim()); } catch (Exception e) { return 0; } }
    private String safe(String s) { return s == null ? "" : s; }

    private static class OptionRow {
        final LinearLayout row; final EditText name; final EditText price; final boolean locked;
        OptionRow(LinearLayout row, EditText name, EditText price, boolean locked) { this.row = row; this.name = name; this.price = price; this.locked = locked; }
    }
    private static class GrossupRule {
        final long min; final Long max; final long fee;
        GrossupRule(long min, Long max, long fee) { this.min = min; this.max = max; this.fee = fee; }
    }
}
