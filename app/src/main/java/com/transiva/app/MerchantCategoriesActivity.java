package com.transiva.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.Locale;
import java.util.TreeSet;

public class MerchantCategoriesActivity extends MerchantBaseActivity {
    private static final String ENDPOINT = BASE + "merchant_categories.php";
    private MerchantPremiumUi ui;
    private LinearLayout list;
    private JSONArray categories = new JSONArray();
    private JSONArray menus = new JSONArray();
    private boolean loaded, editing, saving;
    private int loadGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new MerchantPremiumUi(this);
        showList();
        load();
    }

    private void showList() {
        editing = false;
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        LinearLayout header = actionRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title("Kategori Menu"),
                new LinearLayout.LayoutParams(stackControls() ? -1 : 0, -2, stackControls() ? 0 : 1));
        Button add = action("+ Tambah", true);
        add.setContentDescription("Tambah kategori menu");
        add.setOnClickListener(v -> edit(null));
        add.setEnabled(loaded && !saving);
        header.addView(add, new LinearLayout.LayoutParams(stackControls() ? -1 : -2, -2));
        root.addView(header);
        root.addView(sub("Kelompokkan menu pilihan. Menu lainnya tetap tampil di kategori utama."));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        if (loaded) renderCategories();
    }

    private void load() {
        final int generation = ++loadGeneration;
        if (!loaded) {
            list.removeAllViews();
            list.addView(card("Memuat kategori…"));
        }
        if (MerchantNetworkExecutor.executeRead(this, "categories:" + generation, () -> {
            try {
                JSONObject response = new JSONObject(get(ENDPOINT + "?v=" + System.currentTimeMillis()));
                if (!response.optBoolean("success")) throw new IllegalStateException();
                JSONArray groups = response.getJSONArray("categories");
                JSONArray items = response.getJSONArray("menus");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != loadGeneration || editing) return;
                    categories = groups;
                    menus = items;
                    loaded = true;
                    showList();
                });
            } catch (Exception error) {
                runOnUiThread(() -> readFailed(generation));
            }
        }) == null) readFailed(generation);
    }

    private void readFailed(int generation) {
        if (isFinishing() || isDestroyed() || generation != loadGeneration || editing) return;
        loaded = false;
        showList();
        Button retry = action("Gagal memuat · Coba lagi", false);
        retry.setOnClickListener(v -> load());
        list.addView(retry, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderCategories() {
        if (categories.length() == 0) {
            list.addView(card("Belum ada kategori tambahan."));
            return;
        }
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category == null) continue;
            String name = s(category, "name");
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(4), dp(3), dp(4));
            row.setBackground(ui.surface(14));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            details.setPadding(0, dp(5), 0, dp(5));
            details.setMinimumHeight(dp(48));
            details.addView(tv(name, 15, NAVY, true));
            details.addView(tv(category.optInt("menu_count") + " menu · Kelola pilihan", 12, MUTED, false));
            details.setBackground(ui.touch(null));
            details.setFocusable(true);
            details.setOnClickListener(v -> edit(category));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            ImageButton more = ui.iconButton(R.drawable.ic_merchant_overflow, MUTED, "Tindakan untuk " + name);
            more.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(this, more);
                popup.getMenu().add(0, 1, 0, "Edit kategori");
                popup.getMenu().add(0, 2, 1, "Hapus kategori");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) edit(category);
                    else confirmDelete(category);
                    return true;
                });
                popup.show();
            });
            row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            list.addView(row, params);
        }
    }

    private void edit(JSONObject category) {
        if (saving || !loaded) return;
        editing = true;
        int groupId = category == null ? 0 : category.optInt("id");
        TreeSet<Integer> selected = new TreeSet<>();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject menu = menus.optJSONObject(i);
            if (menu != null && groupId > 0 && menu.optInt("subcategory_id") == groupId) {
                selected.add(menu.optInt("id"));
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setFocusableInTouchMode(true);
        setContentView(page(root));
        root.requestFocus();
        root.addView(title(groupId == 0 ? "Tambah Kategori" : "Edit Kategori"));
        root.addView(label("Nama kategori"));
        EditText name = input("Contoh: Dingin dan Manis", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine();
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        if (category != null) name.setText(s(category, "name"));
        root.addView(name);
        TextView count = tv("", 14, NAVY, true);
        root.addView(count);
        root.addView(sub("Opsional. Menu terpilih pindah ke kategori ini jika sebelumnya dikelompokkan."));
        EditText search = input("Cari menu", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine();
        root.addView(search);
        TextView message = sub("");
        root.addView(message);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        Button save = action("Simpan kategori", true);
        Button back = action("Kembali", false);
        back.setOnClickListener(v -> leaveEditor());
        LinearLayout controls = actionRow();
        controls.addView(save, actionLp());
        controls.addView(back, actionLp());
        root.addView(controls);
        root.addView(choices);

        Runnable draw = () -> renderChoices(choices, count, selected, groupId, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { draw.run(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        draw.run();
        save.setOnClickListener(v -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty()) {
                name.setError("Masukkan nama kategori.");
                return;
            }
            try {
                JSONObject payload = new JSONObject().put("action", "save").put("id", groupId)
                        .put("name", label).put("menu_ids", new JSONArray(selected));
                save.setEnabled(false);
                submit(payload, error -> { message.setText(error); save.setEnabled(true); });
            } catch (Exception error) {
                message.setText("Data kategori belum dapat disiapkan.");
            }
        });
    }

    private void renderChoices(LinearLayout choices, TextView count, Set<Integer> selected,
                               int groupId, String search) {
        choices.removeAllViews();
        updateCount(count, selected.size());
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < menus.length(); i++) {
            JSONObject menu = menus.optJSONObject(i);
            if (menu == null || menu.optInt("id") <= 0) continue;
            String name = s(menu, "name");
            String baseCategory = s(menu, "category");
            String previous = s(menu, "subcategory_name");
            if (!(name + " " + baseCategory + " " + previous).toLowerCase(Locale.ROOT).contains(query)) continue;
            int id = menu.optInt("id");
            String details = baseCategory.isEmpty() ? "Menu" : baseCategory;
            if (!previous.isEmpty() && menu.optInt("subcategory_id") != groupId) details += " · " + previous;
            CheckBox check = new CheckBox(this);
            check.setText(name + "\n" + details);
            check.setTextColor(NAVY);
            check.setTextSize(13);
            check.setChecked(selected.contains(id));
            check.setMinimumHeight(dp(48));
            check.setPadding(dp(7), dp(4), dp(7), dp(4));
            check.setBackground(ui.touch(ui.surface(12)));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(id);
                else selected.remove(id);
                updateCount(count, selected.size());
            });
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(-1, -2);
            item.bottomMargin = dp(5);
            choices.addView(check, item);
        }
        if (choices.getChildCount() == 0) choices.addView(card("Tidak ada menu yang cocok."));
    }

    private void updateCount(TextView count, int selected) {
        count.setText("Pilih menu · " + selected + " dipilih");
    }

    private void confirmDelete(JSONObject category) {
        if (saving) return;
        PremiumDialogs.builder(this).setTitle("Hapus kategori?")
                .setMessage("Menu di kategori ini kembali ke daftar tanpa subkategori.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    try {
                        submit(new JSONObject().put("action", "delete").put("id", category.optInt("id")),
                                error -> alert("Kategori", error));
                    } catch (Exception error) {
                        alert("Kategori", "Permintaan belum dapat disiapkan.");
                    }
                }).show();
    }

    private void submit(JSONObject payload, java.util.function.Consumer<String> failed) {
        if (saving) return;
        saving = true;
        if (!MerchantNetworkExecutor.executeWrite("merchant-categories:" + branchId(), () -> {
            JSONObject response;
            try {
                response = new JSONObject(postJson(ENDPOINT, payload));
            } catch (Exception error) {
                response = null;
            }
            JSONObject result = response;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                saving = false;
                if (result == null || !result.optBoolean("success")) {
                    failed.accept(result == null ? "Perubahan belum terkonfirmasi. Kembali dan muat ulang kategori."
                            : result.optString("message", "Kategori belum tersimpan."));
                    return;
                }
                toast(result.optString("message", "Kategori disimpan."));
                loaded = false;
                showList();
                load();
            });
        })) {
            saving = false;
            failed.accept("Permintaan masih diproses. Coba lagi.");
        }
    }

    private Button action(String label, boolean primary) {
        Button button = primary ? btn(label) : outlineBtn(label);
        button.setTextSize(13);
        button.setElevation(0);
        button.setTextColor(primary ? Color.WHITE : ui.accent());
        button.setBackground(ui.touch(primary ? round(MerchantPremiumUi.BLUE, dp(12)) : ui.surface(12)));
        return button;
    }

    private void leaveEditor() {
        if (saving) return;
        loaded = false;
        showList();
        load();
    }

    @Override public void onBackPressed() {
        if (editing) leaveEditor();
        else super.onBackPressed();
    }
}
