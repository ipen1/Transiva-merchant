package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
    private View formPage;
    private boolean categoryPage;
    private EditText nameInput, priceInput, descriptionInput, stockInput, discountInput;
    private CheckBox trackStockInput, discountActiveInput;
    private TextView categoryValue, originalText, fileText, previewName, previewPrice, previewIcon;
    private ImageView previewImage;
    private MerchantPhotoBadge badge;
    private String originalPhoto;
    private boolean saving;
    private TextView formError;
    private Button pickImageButton, saveMenuButton;
    private String legacyOptions = "[]";
    private MerchantToppingEditor toppingEditor;
    private Button toppingButton;
    private Uri imageUri;
    private PreparedImage preparedMenuImage;
    private volatile boolean imagePreparing;
    private final List<GrossupRule> grossupRules = new ArrayList<>();
    private volatile boolean grossupLoaded;
    private boolean editMode;
    private int initialStock;
    private boolean initialTrack;
    private String editMenuId = "";
    private String existingImage = "";
    private String selectedCategory = "";
    private String toppingRevision = "";
    private int subcategoryId;
    private String menuRequestKey = java.util.UUID.randomUUID().toString();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        editMode = getIntent().getBooleanExtra("edit_mode", false);
        editMenuId = safe(getIntent().getStringExtra("menu_id"));
        existingImage = safe(getIntent().getStringExtra("image"));
        originalPhoto = safe(getIntent().getStringExtra("original_photo"));
        subcategoryId = getIntent().getIntExtra("new_subcategory_id", 0);
        toppingRevision = safe(getIntent().getStringExtra("topping_revision"));
        if (b != null) {
            editMode = b.getBoolean("edit_mode", editMode);
            toppingRevision = b.getString("topping_revision", toppingRevision);
            editMenuId = b.getString("edit_menu_id", editMenuId);
            existingImage = b.getString("saved_image", existingImage);
            originalPhoto = b.getString("saved_original_photo", originalPhoto);
            menuRequestKey = b.getString("menu_request_key", menuRequestKey);
        }
        String options = getIntent().getStringExtra("options_json");
        if (editMode && options != null && !options.isEmpty()) legacyOptions = options;
        toppingEditor = new MerchantToppingEditor(this, this::mainPhoto, this::toppingCustomerPrice, legacyOptions);
        build();
        prefillEditData();
        if (!editMode && subcategoryId > 0) setCategory("Lainnya");
        String savedBadge = getIntent().getStringExtra("photo_badge");
        badge.load(b == null ? savedBadge : b.getString("saved_badge", savedBadge),
                b == null ? null : b.getString("photo_badge"));
        if (editMode && subcategoryId > 0) setResult(RESULT_OK);
        if (b!=null) {
            initialTrack = b.getBoolean("initial_track", initialTrack);
            initialStock = b.getInt("initial_stock", initialStock);
            nameInput.setText(b.getString("menu_name",""));
            priceInput.setText(b.getString("menu_price",""));
            descriptionInput.setText(b.getString("description",""));
            stockInput.setText(b.getString("stock",""));
            trackStockInput.setChecked(b.getBoolean("track_stock"));
            discountInput.setText(b.getString("discount",""));
            discountActiveInput.setChecked(b.getBoolean("discount_active"));
            setCategory(b.getString("category",""));
            preparedMenuImage=MerchantToppingEditor.unpackPhoto(b.getBundle("menu_photo"));
            if (preparedMenuImage!=null) {
                imageUri=preparedMenuImage.uri;
                fileText.setText("Foto menu siap.");
            } else if (b.getString("pending_photo") != null) {
                imageUri = Uri.parse(b.getString("pending_photo"));
                prepareMenuImageAi();
            }
            toppingEditor.restoreState(b.getBundle("topping_editor"));
            toppingButton.setText("Topping · "+toppingEditor.size()+" pilihan ›");
        }
        showPhotoPreview();
        loadGrossupRules();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("edit_mode", editMode);
        state.putString("topping_revision", toppingRevision);
        state.putBoolean("initial_track", initialTrack);
        state.putInt("initial_stock", initialStock);
        state.putString("edit_menu_id", editMenuId);
        state.putString("saved_image", existingImage);
        state.putString("saved_original_photo", originalPhoto);
        state.putString("menu_request_key", menuRequestKey);
        state.putString("saved_badge", getIntent().getStringExtra("photo_badge"));
        state.putString("menu_name",nameInput.getText().toString());
        state.putString("menu_price",priceInput.getText().toString());
        state.putString("description",descriptionInput.getText().toString());
        state.putString("stock",stockInput.getText().toString());
        state.putBoolean("track_stock",trackStockInput.isChecked());
        state.putString("discount",discountInput.getText().toString());
        state.putBoolean("discount_active",discountActiveInput.isChecked());
        state.putString("category",selectedCategory);
        state.putString("photo_badge",badge.value());
        if (imageUri != null && preparedMenuImage == null) state.putString("pending_photo",imageUri.toString());
        if (preparedMenuImage!=null) state.putBundle("menu_photo",MerchantToppingEditor.packPhoto(preparedMenuImage));
        state.putBundle("topping_editor",toppingEditor.saveState());
    }

    private void build() {
        root=new LinearLayout(this);
        saveMenuButton=editorButton(editMode?"Simpan perubahan":"Simpan menu",true);
        saveMenuButton.setOnClickListener(v->save(saveMenuButton));
        formError=tv("",12,Color.parseColor("#B42318"),true);
        formError.setVisibility(View.GONE);
        LinearLayout footer=editorFooter(saveMenuButton);
        footer.addView(formError,0);
        formPage=page(root,footer);
        setContentView(formPage);
        root.addView(editorHeader(editMode?"Edit Menu":"Tambah Menu",this::finish));
        root.addView(previewCard());

        LinearLayout core=editorSection();
        root.addView(core);
        core.addView(editorLabel("Nama menu"));
        nameInput=editorField("Contoh: Risol Original",InputType.TYPE_CLASS_TEXT);
        core.addView(nameInput);
        core.addView(editorLabel("Harga Tanpa Toping/Porsi (Rp)"));
        priceInput=editorField("Contoh: 7000",InputType.TYPE_CLASS_NUMBER);
        core.addView(priceInput);
        core.addView(sub("Kosongkan harga tanpa topping jika tidak menjual menu tanpa topping."));
        core.addView(categorySelector());
        if (subcategoryId > 0) core.addView(sub("Kategori toko: " + safe(getIntent().getStringExtra("new_subcategory_name"))));
        core.addView(editorLabel("Deskripsi · opsional"));
        descriptionInput=editorField("Rasa, isi, atau ukuran",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionInput.setMaxLines(3);
        core.addView(descriptionInput);

        toppingButton=editorButton("Topping · "+toppingEditor.size()+" pilihan ›",false);
        toppingButton.setOnClickListener(v -> {
            if (!grossupLoaded) {
                toast("Tunggu sampai aturan harga dimuat.");
                return;
            }
            if (nameInput.getText().toString().trim().isEmpty() || selectedCategory.isEmpty()) {
                showFormError("Isi nama dan kategori menu sebelum menambah topping.");
                return;
            }
            if (imagePreparing || (!editMode && preparedMenuImage == null)) {
                showFormError("Pilih foto menu dan tunggu sampai siap sebelum menambah topping.");
                return;
            }
            showFormError("");
            toppingEditor.show();
        });
        root.addView(toppingButton);

        LinearLayout advanced=editorSection();
        advanced.setVisibility(View.GONE);
        Button toggle=editorButton("Stok & diskon ▾",false);
        toggle.setOnClickListener(v->{
            boolean expanded=advanced.getVisibility()!=View.VISIBLE;
            advanced.setVisibility(expanded?View.VISIBLE:View.GONE);
            toggle.setText(expanded?"Stok & diskon ▴":"Stok & diskon ▾");
        });
        root.addView(toggle);
        trackStockInput=new CheckBox(this);
        trackStockInput.setText("Batasi stok menu");
        trackStockInput.setMinHeight(dp(44));
        trackStockInput.setTextColor(TEXT);
        advanced.addView(trackStockInput);
        stockInput=editorField("Jumlah stok tersedia",InputType.TYPE_CLASS_NUMBER);
        advanced.addView(stockInput);
        discountActiveInput=new CheckBox(this);
        discountActiveInput.setText("Aktifkan diskon");
        discountActiveInput.setMinHeight(dp(44));
        discountActiveInput.setTextColor(TEXT);
        advanced.addView(discountActiveInput);
        discountInput=editorField("Diskon (%) · maksimal 90",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        advanced.addView(discountInput);
        if (hasLegacyChoices()) advanced.addView(tv("Varian dan tambahan lama tersedia saat menu dijual tanpa topping.",11,MUTED,false));
        root.addView(advanced);

        addWatchers();
        updatePreview();
    }

    LinearLayout editorSection() {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(6),dp(10),dp(6));
        box.setBackground(stroke(Color.WHITE,Color.parseColor("#DCE8F4"),dp(15)));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,0,0,dp(6));
        box.setLayoutParams(params);
        return box;
    }

    LinearLayout editorHeader(String title, Runnable back) {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button arrow=editorButton("‹",false);
        arrow.setTextSize(22);
        arrow.setContentDescription("Kembali");
        arrow.setOnClickListener(v->back.run());
        LinearLayout.LayoutParams arrowLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        arrowLp.rightMargin=dp(10);
        row.addView(arrow,arrowLp);
        row.addView(tv(title,21,NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
        outer.bottomMargin=dp(7);
        row.setLayoutParams(outer);
        return row;
    }

    Button editorButton(String label, boolean primary) {
        Button button=primary?btn(label):outlineBtn(label);
        button.setTextSize(13);
        button.setMinimumHeight(dp(44));
        button.setMinHeight(dp(44));
        button.setPadding(dp(8),dp(4),dp(8),dp(4));
        button.setElevation(0);
        return button;
    }

    EditText editorField(String hint,int type) {
        EditText field=input(hint,type);
        field.setMinHeight(dp(44));
        field.setPadding(dp(10),dp(6),dp(10),dp(6));
        field.setElevation(0);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(5);
        field.setLayoutParams(params);
        return field;
    }

    TextView editorLabel(String title) {
        TextView field=tv(title,12,NAVY,true);
        field.setPadding(dp(2),dp(3),0,dp(2));
        return field;
    }

    LinearLayout editorFooter(Button action) {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(12),dp(4),dp(12),dp(3));
        bar.setBackground(stroke(Color.WHITE,Color.parseColor("#E4EDF7"),dp(12)));
        bar.addView(action);
        return bar;
    }

    private View categorySelector() {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setPadding(dp(9),0,dp(9),0);
        row.setBackground(stroke(Color.WHITE,Color.parseColor("#D5E5F8"),dp(12)));
        row.addView(tv("Kategori",12,MUTED,true));
        categoryValue=tv("Pilih kategori",13,NAVY,true);
        categoryValue.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        categoryValue.setPadding(dp(8),0,dp(6),0);
        row.addView(categoryValue,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(tv("›",18,BLUE,true));
        row.setFocusable(true);
        row.setOnClickListener(v->showCategoryPage());
        return row;
    }

    private void showCategoryPage() {
        categoryPage = true;
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(editorHeader("Pilih kategori",this::showFormPage));
        wrap.addView(sub("Pilih kategori yang sesuai dengan menu ini."));
        Runnable back = this::showFormPage;
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
                back.run();
            });
            wrap.addView(row);
        }
        Button cancel=editorButton("Kembali ke menu",false);
        cancel.setOnClickListener(v -> back.run());
        setContentView(page(wrap,editorFooter(cancel)));
    }

    private void showFormPage() {
        categoryPage = false;
        android.view.ViewGroup parent = (android.view.ViewGroup) formPage.getParent();
        if (parent != null) parent.removeView(formPage);
        setContentView(formPage);
    }

    @Override public void onBackPressed() {
        if (saving) return;
        if (toppingEditor.isOpen()) toppingEditor.back();
        else if (categoryPage) showFormPage();
        else super.onBackPressed();
    }

    private void setCategory(String category) {
        selectedCategory = normalizeCategory(category);
        categoryValue.setText(selectedCategory.isEmpty()?"Pilih kategori":selectedCategory);
        updatePreview();
    }

    private String normalizeCategory(String raw) { return MerchantMenuRules.normalizeCategory(raw); }

    private void prefillEditData() {
        if (!editMode) return;
        nameInput.setText(safe(getIntent().getStringExtra("name")));
        setCategory(safe(getIntent().getStringExtra("category")));
        descriptionInput.setText(safe(getIntent().getStringExtra("description")));
        initialTrack = getIntent().getIntExtra("track_stock", 0) == 1;
        initialStock = getIntent().getIntExtra("stock", 0);
        trackStockInput.setChecked(initialTrack);
        stockInput.setText(String.valueOf(initialStock));
        discountActiveInput.setChecked(getIntent().getIntExtra("discount_active", 0) == 1);
        double dpct = getIntent().getDoubleExtra("discount_percent", 0);
        if (dpct > 0) discountInput.setText(dpct == Math.rint(dpct) ? String.valueOf((int)dpct) : String.valueOf(dpct));
        long displayPrice = getIntent().getLongExtra("price", 0L);
        long original = getIntent().getLongExtra("original_price", 0L);
        long storedGrossup = Math.max(0L, getIntent().getLongExtra("grossup_fee", 0L));

        // Proteksi edit menu: field merchant harus selalu berisi harga sebelum gross-up.
        // Jika payload lama tidak konsisten, turunkan kembali dari harga tampil - gross-up.
        if (original <= 0 && storedGrossup > 0 && displayPrice > storedGrossup) {
            original = displayPrice - storedGrossup;
        }
        if (original <= 0) original = displayPrice;
        if (original > 0 && !MerchantToppingEditor.isRequired(legacyOptions)) priceInput.setText(String.valueOf(original));
        fileText.setText(existingImage.isEmpty()?"Tambahkan foto menu":"Foto menu saat ini");
        updatePreview();
    }

    private LinearLayout previewCard() {
        LinearLayout box=editorSection();
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(3),0,dp(3));
        FrameLayout imgWrap=new FrameLayout(this);
        imgWrap.setBackground(round(Color.parseColor("#EEF6FF"),dp(12)));
        imgWrap.setClipToOutline(true);
        previewImage=new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgWrap.addView(previewImage,new FrameLayout.LayoutParams(-1,-1));
        previewIcon=tv("🍽️",24,BLUE,true);
        previewIcon.setGravity(Gravity.CENTER);
        imgWrap.addView(previewIcon,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(dp(62),dp(62));
        imageLp.rightMargin=dp(9);
        row.addView(imgWrap,imageLp);
        imgWrap.setContentDescription("Pilih foto menu");
        imgWrap.setOnClickListener(v->{if(!imagePreparing) chooseImage();});
        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(tv("PRATINJAU PELANGGAN",10,MUTED,true));
        previewName=tv("Nama menu",14,NAVY,true);
        previewName.setMaxLines(2);
        info.addView(previewName);
        previewPrice=tv("Rp 0",16,BLUE,true);
        info.addView(previewPrice);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        pickImageButton=editorButton("Foto",false);
        pickImageButton.setContentDescription("Pilih atau ganti foto menu");
        pickImageButton.setOnClickListener(v->chooseImage());
        row.addView(pickImageButton,new LinearLayout.LayoutParams(-2,-2));
        box.addView(row);
        originalText=tv("",11,MUTED,false);
        box.addView(originalText);
        fileText=tv("Foto dioptimalkan otomatis",11,MUTED,false);
        box.addView(fileText);
        Button badgeButton = editorButton("Lencana foto", false);
        badge = new MerchantPhotoBadge(this, imgWrap, badgeButton, this::showPhotoPreview);
        box.addView(badgeButton, new LinearLayout.LayoutParams(-1, -2));
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
        if (discountInput != null) discountInput.addTextChangedListener(watcher);
        if (discountActiveInput != null) discountActiveInput.setOnCheckedChangeListener((buttonView, isChecked) -> {discountInput.setVisibility(isChecked?View.VISIBLE:View.GONE);updatePreview();});
        discountInput.setVisibility(discountActiveInput.isChecked()?View.VISIBLE:View.GONE);
        stockInput.setVisibility(trackStockInput.isChecked()?View.VISIBLE:View.GONE);
        trackStockInput.setOnCheckedChangeListener((v,checked)->stockInput.setVisibility(checked?View.VISIBLE:View.GONE));
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
                runOnUiThread(() -> { grossupRules.clear(); grossupRules.addAll(loaded); grossupLoaded = true; updatePreview(); toppingEditor.onRulesLoaded(); });
            } catch (Exception e) {
                runOnUiThread(() -> { grossupLoaded = false; updatePreview(); Toast.makeText(this, "Aturan gross-up belum dapat dimuat. Periksa API server.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private long gross(long price) {
        if (price <= 0) return 0;
        for (int i=grossupRules.size()-1;i>=0;i--) {
            GrossupRule rule=grossupRules.get(i);
            if (price>=rule.min && (rule.max==null || price<=rule.max)) return rule.fee;
        }
        return 0;
    }

    private void updatePreview() {
        if (originalText == null || priceInput == null) return;
        long base = baseToppingPrice();
        long original = base > 0 ? base : toppingEditor.lowestPrice();
        double discountPercent = Math.max(0d, Math.min(90d, parseDouble(discountInput == null ? "0" : discountInput.getText().toString())));
        boolean discountActive = discountActiveInput != null && discountActiveInput.isChecked() && discountPercent > 0;
        long effectiveOriginal = discountActive ? Math.max(1L, Math.round(original * (1d - discountPercent / 100d))) : original;
        long fee = gross(effectiveOriginal);
        long appPrice = effectiveOriginal + fee;
        originalText.setText(base == 0 ? (original == 0 ? "Tambahkan pilihan topping untuk menentukan harga." : "Pelanggan wajib memilih topping.")
                : "Harga tanpa topping " + rupiah(effectiveOriginal)
                  + (discountActive ? " setelah diskon" : "")
                  + (grossupLoaded ? " · Biaya " + rupiah(fee) : " · Menghitung biaya…"));
        String name = nameInput.getText().toString().trim();
        previewName.setText(name.isEmpty() ? "Nama menu" : name);
        previewPrice.setText(original <= 0 ? "Harga belum diisi" : (base == 0 ? "Mulai " : "") + rupiah(appPrice));
    }

    private boolean hasLegacyChoices() {
        try {
            JSONArray groups = new JSONArray(legacyOptions);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                JSONArray items = group == null || "bundle_topping".equals(group.optString("type")) ? null : group.optJSONArray("items");
                if (items == null || items.length() == 0) continue;
                if (!"variant".equals(group.optString("type")) || items.length() > 1) return true;
                JSONObject item = items.optJSONObject(0);
                if (item != null && item.optLong("price", 0) > 0) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void chooseImage() {
        if (saving || imagePreparing) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih gambar menu"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int r, int c, Intent data) {
        super.onActivityResult(r, c, data);
        if (r == MerchantToppingEditor.PICK_PHOTO) {
            toppingEditor.receivePhoto(c == RESULT_OK && data != null ? data.getData() : null);
            return;
        }
        if (r == PICK_IMAGE && c == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            preparedMenuImage = null;
            showPhotoPreview();
            prepareMenuImageAi();
        }
    }


    private void prepareMenuImageAi() {
        if (imageUri == null) return;
        imagePreparing = true;
        setButtonLoading(pickImageButton,true,"Foto","…");
        fileText.setText("Mengoptimalkan foto…");
        final Uri source = imageUri;
        if (!MerchantTaskExecutor.execute(() -> {
            try {
                PreparedImage result = prepareAiResizeToWebp(source, "menu", 900, 150 * 1024L, 110 * 1024L);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    preparedMenuImage = result;
                    imagePreparing = false;
                    setButtonLoading(pickImageButton,false,"Foto","");
                    fileText.setText("Foto siap · " + humanBytes(result.finalBytes));
                    showPhotoPreview();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    photoFailed(e.getMessage());
                });
            }
        })) photoFailed("Antrean foto penuh. Coba lagi.");
    }

    private void photoFailed(String detail) {
        imagePreparing = false;
        imageUri = null;
        preparedMenuImage = null;
        setButtonLoading(pickImageButton,false,"Foto","");
        fileText.setText(existingImage.isEmpty() ? "Tambahkan foto menu" : "Foto menu saat ini");
        showPhotoPreview();
        showFormError(detail == null ? "Pilih gambar lain." : detail);
    }

    private void showPhotoPreview() {
        boolean draft = imageUri != null || badge.changed();
        Object photo = draft && imageUri == null && !originalPhoto.isEmpty()
                ? MerchantToppingEditor.imageUrl(originalPhoto) : mainPhoto();
        boolean hasPhoto = imageUri != null || !existingImage.isEmpty();
        previewIcon.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        com.bumptech.glide.Glide.with(this).load(hasPhoto ? photo : null).fitCenter().into(previewImage);
        badge.showDraft(draft && hasPhoto);
        badge.setEnabled(!saving);
    }

    private void showFormError(String message) {
        String detail=message==null?"Periksa kembali data menu.":message;
        formError.setText(detail);
        formError.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    long baseToppingPrice() { return parseLong(priceInput.getText().toString()); }

    private Object mainPhoto() {
        if (preparedMenuImage != null) return preparedMenuImage.uri;
        if (imageUri != null) return imageUri;
        String path = existingImage;
        int query = path.indexOf('?');
        if (query>=0) path=path.substring(0,query);
        return MerchantToppingEditor.imageUrl(path);
    }

    private long toppingCustomerPrice(long amount) {
        double discount = discountActiveInput.isChecked()
                ? Math.max(0d,Math.min(90d,parseDouble(discountInput.getText().toString()))) : 0d;
        long discounted = Math.max(1,Math.round(amount*(1d-discount/100d)));
        return discounted+gross(discounted);
    }

    void returnFromToppings() {
        toppingButton.setText("Topping · "+toppingEditor.size()+" pilihan ›");
        if (editMode) saveMenuButton.setText("Simpan perubahan");
        showFormPage();
    }

    boolean isSavingMenu() { return saving; }

    void saveToppings(Button save, TextView error, boolean remove) {
        save(save, true, remove, detail -> {
            error.setText(detail);
            error.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void save(Button save) {
        save(save, false, false, this::showFormError);
    }

    private void save(Button save, boolean toppingWrite, boolean remove, java.util.function.Consumer<String> feedback) {
        if (saving) return;
        feedback.accept("");
        String name = nameInput.getText().toString().trim();
        String cat = selectedCategory.trim();
        String raw = priceInput.getText().toString().trim();
        long original = baseToppingPrice();
        if (name.isEmpty() || cat.isEmpty()) {
            feedback.accept("Isi nama dan kategori menu terlebih dahulu.");
            return;
        }
        if ((!raw.isEmpty() && original <= 0) || original > 100000000L) {
            feedback.accept("Isi harga tanpa topping Rp1–Rp100.000.000 atau kosongkan.");
            return;
        }
        if (!grossupLoaded) { feedback.accept("Biaya aplikasi belum berhasil dimuat. Buka ulang halaman untuk mencoba lagi."); return; }
        double discountPercent = Math.max(0d, Math.min(90d, parseDouble(discountInput == null ? "0" : discountInput.getText().toString())));
        boolean discountActive = discountActiveInput != null && discountActiveInput.isChecked() && discountPercent > 0;
        long effectiveOriginal = discountActive && original > 0 ? Math.max(1L, Math.round(original * (1d - discountPercent / 100d))) : original;
        long fee = gross(effectiveOriginal), appPrice = effectiveOriginal + fee;
        if (imagePreparing) { feedback.accept("Tunggu sampai gambar selesai disiapkan."); return; }
        if (!editMode && imageUri == null) { feedback.accept("Pilih gambar menu terlebih dahulu."); return; }
        if (imageUri != null && preparedMenuImage == null) { feedback.accept("Pilih ulang gambar lalu tunggu sampai siap."); return; }
        String normalButton = toppingWrite ? "Simpan topping" : editMode ? "Simpan perubahan" : "Simpan menu";
        setButtonLoading(save, true, normalButton, imageUri == null ? "Menyimpan..." : "Mengupload menu...");
        String description = descriptionInput.getText().toString().trim();
        boolean trackStock = trackStockInput.isChecked();
        long parsedStock = parseLong(stockInput.getText().toString());
        if (parsedStock > Integer.MAX_VALUE) {
            setButtonLoading(save, false, normalButton, "");
            feedback.accept("Jumlah stok terlalu besar.");
            return;
        }
        int stock = (int)Math.max(0, parsedStock);
        boolean changeStock = !editMode || trackStock != initialTrack || stock != initialStock;
        java.util.Map<String,PreparedImage> uploads = new java.util.LinkedHashMap<>();
        if (preparedMenuImage != null) uploads.put("image",preparedMenuImage);
        final JSONArray toppings;
        try {
            JSONArray checked = toppingEditor.request(original, uploads, toppingWrite, remove);
            if (original == 0 && checked.length() == 0) {
                throw new IllegalArgumentException("Tambahkan minimal satu topping jika harga tanpa topping kosong.");
            }
            toppings = (!editMode || toppingWrite || toppingEditor.isDirty()) ? checked : null;
        } catch (Exception error) {
            setButtonLoading(save,false,normalButton,"");
            feedback.accept(error.getMessage());
            return;
        }
        String photoBadge = !editMode || badge.changed() || preparedMenuImage != null ? badge.value() : null;
        saving = true;
        badge.setEnabled(false);
        pickImageButton.setEnabled(false);
        boolean queued = MerchantNetworkExecutor.executeWrite("menu-save:" + (editMode ? editMenuId : "new") + ":" + name, () -> {
            try {
                JSONObject f = new JSONObject();
                f.put("name", name); f.put("price", appPrice); f.put("original_price", original); f.put("grossup_fee", fee); f.put("category", cat);
                f.put("discount_percent", discountPercent); f.put("discount_active", discountActive ? 1 : 0);
                f.put("description", description);
                f.put("topping_required", original == 0 ? 1 : 0);
                if (!editMode) {
                    f.put("menu_request_key", menuRequestKey);
                    if (subcategoryId > 0) f.put("subcategory_id", subcategoryId);
                }
                if (photoBadge != null) f.put("photo_badge", photoBadge);
                if (!editMode) f.put("menu_format", "single_price");
                if (toppings != null) f.put("bundle_toppings",toppings.toString());
                if (changeStock) {
                    f.put("track_stock", trackStock ? 1 : 0);
                    f.put("stock", stock);
                }
                if (editMode) {
                    f.put("menu_id", editMenuId);
                    f.put("id", editMenuId);
                    f.put("action", "update");
                    if (!toppingRevision.isEmpty()) f.put("topping_revision", toppingRevision);
                }
                String endpoint = editMode ? BASE + "merchant_update_menu.php" : BASE + "add_food_menu.php";
                JSONObject res = new JSONObject(MerchantHttpClient.postMultipart(this,endpoint,f,uploads));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    unlockSave(save, normalButton);
                    if (!res.optBoolean("success", false)) {
                        feedback.accept(res.optString("message", "Menu belum tersimpan."));
                    } else if (!toppingWrite) {
                        setResult(RESULT_OK);
                        toast(res.optString("message", "Menu tersimpan."));
                        finish();
                    } else {
                        JSONArray saved = res.optJSONArray("options");
                        String id = res.optString("menu_id");
                        if (saved == null || id.isEmpty()) {
                            feedback.accept("Server belum mengirim konfirmasi topping. Muat ulang daftar menu.");
                            return;
                        }
                        editMode = true;
                        editMenuId = id;
                        toppingRevision = res.optString("topping_revision");
                        initialTrack = trackStock;
                        initialStock = stock;
                        existingImage = res.optString("image", existingImage);
                        originalPhoto = res.optString("original_photo", originalPhoto);
                        imageUri = null;
                        preparedMenuImage = null;
                        String savedBadge = res.optString("photo_badge", badge.value());
                        getIntent().putExtra("photo_badge", savedBadge);
                        badge.load(savedBadge, null);
                        legacyOptions = saved.toString();
                        toppingEditor.loadSaved(saved);
                        setResult(RESULT_OK);
                        fileText.setText("Foto menu saat ini");
                        showPhotoPreview();
                        updatePreview();
                        toast(remove ? "Topping dihapus." : "Topping tersimpan.");
                        toppingEditor.show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    unlockSave(save, normalButton);
                    feedback.accept("Server atau koneksi sedang bermasalah. Coba lagi.");
                });
            }
        });
        if (!queued) {
            unlockSave(save, normalButton);
            feedback.accept("Permintaan sedang antre. Coba lagi.");
        }
    }

    private void unlockSave(Button save, String label) {
        saving = false;
        badge.setEnabled(true);
        pickImageButton.setEnabled(true);
        setButtonLoading(save, false, label, "");
    }

    private long parseLong(String s) { try { return Long.parseLong(s == null ? "0" : s.trim()); } catch (Exception e) { return 0; } }
    private double parseDouble(String s) { try { return Double.parseDouble(s == null ? "0" : s.trim().replace(",", ".")); } catch (Exception e) { return 0d; } }
    private String safe(String s) { return s == null ? "" : s; }

    private static class GrossupRule {
        final long min; final Long max; final long fee;
        GrossupRule(long min, Long max, long fee) { this.min = min; this.max = max; this.fee = fee; }
    }
}
