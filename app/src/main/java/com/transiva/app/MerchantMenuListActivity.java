package com.transiva.app;

import com.bumptech.glide.Glide;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MerchantMenuListActivity extends MerchantBaseActivity {
    private LinearLayout list;
    private EditText search;
    private TextView statusFilter;
    private int selectedFilter;
    private MerchantPremiumUi ui;
    private static final String[] STATUS_FILTERS = {"Semua status", "Tersedia", "Tidak tersedia"};
    private TextView resultCount;
    private Button loadNotice;
    private JSONArray cached = new JSONArray();
    private final HashSet<String> pending = new HashSet<>();
    private final HashSet<String> expanded = new HashSet<>();
    private final HashMap<String, MenuControls> visibleControls = new HashMap<>();
    private boolean loaded;
    private boolean loading;
    private boolean refreshNeeded = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new MerchantPremiumUi(this);
        if (state != null) {
            selectedFilter = state.getInt("status_filter");
            ArrayList<String> opened = state.getStringArrayList("expanded_menus");
            if (opened != null) expanded.addAll(opened);
        }
        LinearLayout root = new LinearLayout(this);
        root.setFocusableInTouchMode(true);
        setContentView(page(root));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        boolean narrow = screenWidthDp() < 340 || getResources().getConfiguration().fontScale > 1.25f;
        if (narrow) header.setOrientation(LinearLayout.VERTICAL);
        TextView heading = tv("Semua Menu", 24, NAVY, true);
        heading.setLetterSpacing(-.025f);
        header.addView(heading, new LinearLayout.LayoutParams(narrow ? -1 : 0, -2, narrow ? 0 : 1));
        Button add = compactAction("+ Tambah Menu", true);
        add.setOnClickListener(v -> open(MerchantAddMenuActivity.class));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(narrow ? -1 : -2, -2);
        addParams.topMargin = narrow ? dp(6) : 0;
        addParams.leftMargin = narrow ? 0 : dp(6);
        header.addView(add, addParams);
        root.addView(header);

        search = input("Cari menu, kategori, atau topping", InputType.TYPE_CLASS_TEXT);
        search.setElevation(0);
        search.setBackground(ui.surface(14));
        search.setCompoundDrawablesRelative(ui.iconDrawable(R.drawable.ic_merchant_search, MUTED, 21), null, null, null);
        search.setCompoundDrawablePadding(dp(8));
        search.setContentDescription("Cari menu, kategori, atau topping");
        search.setText(state == null ? "" : state.getString("search", ""));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.topMargin = dp(12);
        searchParams.bottomMargin = dp(7);
        root.addView(search, searchParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        if (narrow) filters.setOrientation(LinearLayout.VERTICAL);
        resultCount = tv("", 12, MUTED, true);
        resultCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        filters.addView(resultCount, new LinearLayout.LayoutParams(narrow ? -1 : 0, -2, narrow ? 0 : 1));

        LinearLayout chips = new LinearLayout(this);
        if (getResources().getConfiguration().fontScale > 1.45f) chips.setOrientation(LinearLayout.VERTICAL);
        TextView categories = chip("Kategori", ui.accent());
        categories.setCompoundDrawablesRelative(ui.iconDrawable(R.drawable.ic_merchant_tag, ui.accent(), 16), null, null, null);
        categories.setOnClickListener(v -> open(MerchantCategoriesActivity.class));
        chips.addView(categories, new LinearLayout.LayoutParams(-2, -2));

        statusFilter = chip(STATUS_FILTERS[selectedFilter], NAVY);
        statusFilter.setCompoundDrawablesRelative(null, null, ui.iconDrawable(R.drawable.ic_merchant_expand, MUTED, 16), null);
        statusFilter.setOnClickListener(v -> showStatusFilter());
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-2, -2);
        if (chips.getOrientation() == LinearLayout.VERTICAL) filterParams.topMargin = dp(5);
        else filterParams.leftMargin = dp(6);
        chips.addView(statusFilter, filterParams);
        filters.addView(chips, new LinearLayout.LayoutParams(narrow ? -1 : -2, -2));
        LinearLayout.LayoutParams filtersParams = new LinearLayout.LayoutParams(-1, -2);
        filtersParams.bottomMargin = dp(8);
        root.addView(filters, filtersParams);

        loadNotice = compactAction("Memuat menu…", false);
        loadNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        loadNotice.setOnClickListener(v -> load());
        root.addView(loadNotice);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        root.requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("search", search.getText().toString());
        state.putInt("status_filter", selectedFilter);
        state.putStringArrayList("expanded_menus", new ArrayList<>(expanded));
        super.onSaveInstanceState(state);
    }

    private TextView chip(String label, int color) {
        TextView chip = tv(label, 12, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setMinimumHeight(dp(48));
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        chip.setCompoundDrawablePadding(dp(5));
        chip.setBackground(ui.touch(ui.surface(13)));
        chip.setFocusable(true);
        return chip;
    }

    private void showStatusFilter() {
        PopupMenu popup = new PopupMenu(this, statusFilter);
        for (int i = 0; i < STATUS_FILTERS.length; i++) popup.getMenu().add(0, i, i, STATUS_FILTERS[i]);
        popup.getMenu().setGroupCheckable(0, true, true);
        popup.getMenu().findItem(selectedFilter).setChecked(true);
        popup.setOnMenuItemClickListener(item -> {
            selectedFilter = item.getItemId();
            statusFilter.setText(STATUS_FILTERS[selectedFilter]);
            render();
            return true;
        });
        popup.show();
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private boolean canUpdateUi() {
        return !isFinishing() && !isDestroyed();
    }

    private void load() {
        if (loading || !canUpdateUi()) return;
        refreshNeeded = true;
        if (!pending.isEmpty()) return;
        loading = true;
        showLoadNotice("Memuat menu…", false);
        updateVisibleControls();

        if (MerchantNetworkExecutor.executeRead(this, "menus", () -> {
            try {
                JSONObject response = new JSONObject(get(BASE + "merchant_get_menus.php?v=" + System.currentTimeMillis()));
                JSONArray menus = response.getJSONArray("menus");
                if (!response.optBoolean("success")) throw new IllegalStateException();
                runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    loading = false;
                    refreshNeeded = false;
                    loaded = true;
                    cached = menus;
                    loadNotice.setVisibility(View.GONE);
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(this::showLoadError);
            }
        }) == null) showLoadError();
    }

    private void showLoadNotice(String message, boolean retry) {
        loadNotice.setText(message);
        loadNotice.setEnabled(retry);
        loadNotice.setVisibility(View.VISIBLE);
    }

    private void showLoadError() {
        if (!canUpdateUi()) return;
        loading = false;
        showLoadNotice(loaded ? "Status belum diperbarui · Coba lagi" : "Gagal memuat menu · Coba lagi", true);
        updateVisibleControls();
    }

    private void updateVisibleControls() {
        for (MenuControls controls : visibleControls.values()) controls.bind();
    }

    private boolean available(JSONObject menu) {
        return menu.optInt("is_active", 1) == 1 && !soldOut(menu);
    }

    private boolean soldOut(JSONObject menu) {
        return menu.optBoolean("is_sold_out") || menu.optInt("track_stock")==1 && menu.optInt("stock")<=0;
    }

    private List<Topping> toppings(JSONObject menu) {
        List<Topping> result = new ArrayList<>();
        JSONArray groups = menu.optJSONArray("options");
        if (groups == null) return result;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String type = group.optString("type").trim().toLowerCase(Locale.ROOT);
            if (!"bundle_topping".equals(type) && !"topping".equals(type)) continue;
            JSONArray items = group.optJSONArray("items");
            if (items == null) continue;
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                String name = item.optString("name").trim();
                if (!name.isEmpty()) result.add(new Topping(type, name, item.optString("is_available", "true")));
            }
        }
        return result;
    }

    private static final class Topping {
        final String type, name;
        final boolean available;

        Topping(String type, String name, String available) {
            this.type = type;
            this.name = name;
            this.available = !"false".equalsIgnoreCase(available) && !"0".equals(available);
        }
    }

    private void render() {
        if (list == null || !loaded) return;
        visibleControls.clear();
        list.removeAllViews();
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filter = selectedFilter, shown = 0;
        java.util.LinkedHashMap<Integer, List<JSONObject>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < cached.length(); i++) {
            JSONObject menu = cached.optJSONObject(i);
            if (menu == null) continue;
            boolean available = available(menu);
            if ((filter == 1 && !available) || (filter == 2 && available)) continue;
            StringBuilder searchable = new StringBuilder(s(menu, "name", "menu_name", "food_name"))
                    .append(' ').append(s(menu, "category", "type")).append(' ').append(s(menu, "subcategory_name"));
            for (Topping topping : toppings(menu)) searchable.append(' ').append(topping.name);
            if (!searchable.toString().toLowerCase(Locale.ROOT).contains(query)) continue;
            int group = s(menu, "subcategory_name").isEmpty() ? 0 : menu.optInt("subcategory_id");
            groups.computeIfAbsent(group, unused -> new ArrayList<>()).add(menu);
            shown++;
        }
        List<Integer> order = new ArrayList<>(groups.keySet());
        order.sort((left, right) -> {
            if (left.equals(right)) return 0;
            if (left == 0) return 1;
            if (right == 0) return -1;
            JSONObject a = groups.get(left).get(0);
            JSONObject b = groups.get(right).get(0);
            int sort = Integer.compare(a.optInt("subcategory_sort"), b.optInt("subcategory_sort"));
            return sort != 0 ? sort : s(a, "subcategory_name").compareToIgnoreCase(s(b, "subcategory_name"));
        });
        for (Integer group : order) {
            List<JSONObject> items = groups.get(group);
            if (group != 0 || order.size() > 1) {
                String label = group == 0 ? "Tanpa subkategori" : s(items.get(0), "subcategory_name");
                TextView heading = tv(label, 15, NAVY, true);
                heading.setPadding(dp(2), dp(6), 0, dp(5));
                list.addView(heading);
            }
            for (JSONObject item : items) list.addView(menuCard(item));
        }
        resultCount.setText(shown+" menu");
        if (shown == 0) list.addView(card(cached.length() == 0
                ? "Belum ada menu di toko ini." : "Tidak ada menu sesuai pencarian atau filter."));
    }

    private Button compactAction(String label, boolean primary) {
        Button button = primary ? btn(label) : outlineBtn(label);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.WHITE : ui.accent());
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(5), dp(12), dp(5));
        button.setElevation(0);
        button.setBackground(ui.touch(primary ? round(MerchantPremiumUi.BLUE, dp(12)) : ui.surface(12)));
        return button;
    }

    private LinearLayout menuCard(JSONObject menu) {
        String id = s(menu, "id", "menu_id");
        String name = s(menu, "name", "menu_name", "food_name");
        if (name.isEmpty()) name = "Tanpa nama";
        String category = s(menu, "category", "type");
        String image = s(menu, "image", "photo", "image_url");
        int track = menu.optInt("track_stock");
        int stock = menu.optInt("stock");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(9), dp(6), dp(9));
        box.setBackground(ui.surface(15));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(8);
        box.setLayoutParams(outer);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean stack = screenWidthDp() < 315 || getResources().getConfiguration().fontScale > 1.22f;
        if (stack) row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout product = new LinearLayout(this);
        product.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout thumb = new FrameLayout(this);
        thumb.setBackground(round(0xFFEEF6FF, dp(10)));
        thumb.setClipToOutline(true);
        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        TextView fallback = tv("🍽", 19, ui.accent(), true);
        fallback.setGravity(Gravity.CENTER);
        thumb.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int photoSize = screenWidthDp() < 360 ? 45 : 53;
        LinearLayout.LayoutParams picture = new LinearLayout.LayoutParams(dp(photoSize), dp(photoSize));
        picture.rightMargin = dp(8);
        product.addView(thumb, picture);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(line(name, 15, NAVY, true, 3));
        String details = category.isEmpty() ? "Tanpa kategori" : category;
        if (track == 1) details += " · Stok " + stock;
        info.addView(line(details, 12, MUTED, false, 2));
        String price = (menu.optBoolean("topping_required") ? "Mulai " : "") + rupiah(menu.optLong("price", 0));
        if (menu.optInt("discount_active") == 1 && menu.optDouble("discount_percent") > 0) {
            price += " · Diskon " + String.format(Locale.US, "%.0f%%", menu.optDouble("discount_percent"));
        }
        info.addView(line(price, 14, ui.accent(), true, 3));
        product.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(product, new LinearLayout.LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        box.addView(row);
        loadMenuImage(photo, fallback, image);

        Runnable editMenu = editAction(menu, id, name, category, image, track, stock);
        ImageButton more = ui.iconButton(R.drawable.ic_merchant_overflow, MUTED, "Tindakan untuk " + name);
        MenuControls controls = new MenuControls(menu, id, name, more, editMenu);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(stack ? -1 : -2, -2);
        if (stack) actionParams.topMargin = dp(6);
        else actionParams.leftMargin = dp(4);
        row.addView(actions, actionParams);
        actions.addView(controls.panel, new LinearLayout.LayoutParams(-2, -2));
        View separator = new View(this);
        separator.setBackgroundColor(ui.divider());
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(dp(1), dp(36));
        separatorParams.leftMargin = dp(4);
        actions.addView(separator, separatorParams);
        actions.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));

        box.addView(controls.toppingSection);
        visibleControls.put(id, controls);
        controls.bind();

        String menuName = name;
        more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, more);
            popup.getMenu().add(0, 1, 0, "Edit menu");
            popup.getMenu().add(0, 2, 1, "Hapus menu");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) editMenu.run();
                else PremiumDialogs.builder(this).setTitle("Hapus menu")
                        .setMessage("Hapus " + menuName + " dari toko ini?")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus", (d, w) -> writeMenu(menu, id, null)).show();
                return true;
            });
            popup.show();
        });
        return box;
    }

    private final class MenuControls {
        final JSONObject menu;
        final String id, name;
        final ImageButton more;
        final LinearLayout panel, toppingSection, toppingRows;
        final TextView status, toppingHeader;
        final Switch toggle;
        final Runnable editAction;

        MenuControls(JSONObject menu, String id, String name, ImageButton more, Runnable editAction) {
            this.menu = menu;
            this.id = id;
            this.name = name;
            this.more = more;
            this.editAction = editAction;

            panel = new LinearLayout(MerchantMenuListActivity.this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            toggle = availabilitySwitch();
            panel.addView(toggle, new LinearLayout.LayoutParams(dp(64), -2));
            status = tv("", 10, MUTED, true);
            status.setGravity(Gravity.CENTER);
            status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            status.setMaxWidth(dp(84));
            status.setPadding(dp(4), 0, dp(4), dp(5));
            panel.addView(status);

            toppingSection = new LinearLayout(MerchantMenuListActivity.this);
            toppingSection.setOrientation(LinearLayout.VERTICAL);
            toppingSection.setPadding(0, dp(7), dp(3), 0);
            toppingHeader = chip("", ui.accent());
            toppingHeader.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            toppingHeader.setOnClickListener(v -> {
                if (!expanded.remove(id)) expanded.add(id);
                bindToppings();
            });
            toppingSection.addView(toppingHeader, new LinearLayout.LayoutParams(-1, -2));
            toppingRows = new LinearLayout(MerchantMenuListActivity.this);
            toppingRows.setOrientation(LinearLayout.VERTICAL);
            toppingSection.addView(toppingRows);
        }

        boolean editable() {
            return !refreshNeeded && !pending.contains(id) && !id.isEmpty();
        }

        void bind() {
            boolean active = available(menu);
            String label = active ? "Tersedia" : soldOut(menu) ? "Stok habis" : "Tidak tersedia";
            if (pending.contains(id)) label = "Menyimpan…";
            else if (refreshNeeded) label = "Muat ulang";
            int color = active && editable() ? (ui.dark() ? 0xFF76E2B5 : 0xFF126148) : MUTED;
            int surface = active ? (ui.dark() ? 0xFF14392F : 0xFFE6F6EE)
                    : (ui.dark() ? MerchantAppSettings.DARK_CARD_SOFT : 0xFFF0F4F8);
            panel.setBackground(round(surface, dp(12)));
            status.setText(label);
            status.setTextColor(color);
            toggle.setOnCheckedChangeListener(null);
            bindSwitch(toggle, active, editable(), "Ketersediaan menu " + name, label);
            toggle.setOnCheckedChangeListener((button, checked) -> change(checked));
            more.setEnabled(editable());
            bindToppings();
        }

        private void bindToppings() {
            List<Topping> options = toppings(menu);
            boolean open = expanded.contains(id);
            toppingSection.setVisibility(options.isEmpty() ? View.GONE : View.VISIBLE);
            if (options.isEmpty()) return;
            int availableCount = 0;
            for (Topping option : options) if (option.available) availableCount++;
            String summary = options.size() + " topping · " + availableCount + " tersedia";
            toppingHeader.setText(summary);
            toppingHeader.setContentDescription((open ? "Tutup " : "Buka ") + "topping " + name + ". " + summary);
            int indicator = open ? R.drawable.ic_merchant_collapse : R.drawable.ic_merchant_expand;
            toppingHeader.setCompoundDrawablesRelative(null, null, ui.iconDrawable(indicator, ui.accent(), 17), null);
            toppingRows.removeAllViews();
            toppingRows.setVisibility(open ? View.VISIBLE : View.GONE);
            if (!open) return;
            for (Topping option : options) {
                LinearLayout choice = new LinearLayout(MerchantMenuListActivity.this);
                choice.setGravity(Gravity.CENTER_VERTICAL);
                choice.setPadding(dp(8), dp(3), dp(2), dp(3));
                choice.setBackground(ui.surface(10));
                LinearLayout.LayoutParams padding = new LinearLayout.LayoutParams(-1, -2);
                padding.topMargin = dp(4);
                toppingRows.addView(choice, padding);

                LinearLayout labels = new LinearLayout(MerchantMenuListActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.addView(tv(option.name, 13, NAVY, true));
                String label = option.available ? "Tersedia" : "Tidak tersedia";
                labels.addView(tv(label, 11, option.available ? (ui.dark() ? 0xFF76E2B5 : 0xFF126148) : MUTED, true));
                choice.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                Switch optionSwitch = availabilitySwitch();
                bindSwitch(optionSwitch, option.available, editable(), "Topping " + option.name, label);
                optionSwitch.setOnCheckedChangeListener((button, checked) -> {
                    optionSwitch.setOnCheckedChangeListener(null);
                    optionSwitch.setChecked(option.available);
                    writeTopping(menu, id, option, checked);
                });
                choice.addView(optionSwitch, new LinearLayout.LayoutParams(dp(64), -2));
            }
        }

        private void change(boolean checked) {
            if (checked && soldOut(menu)) {
                bind();
                PremiumDialogs.builder(MerchantMenuListActivity.this).setTitle("Stok menu habis")
                        .setMessage("Tambah stok untuk mengaktifkan " + name + ".")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Edit stok", (dialog, which) -> editAction.run()).show();
                return;
            }
            writeMenu(menu, id, checked ? 1 : 0);
        }
    }

    private Switch availabilitySwitch() {
        Switch toggle = new Switch(this);
        toggle.setTag("merchant-availability");
        toggle.setGravity(Gravity.CENTER);
        toggle.setMinimumHeight(dp(48));
        toggle.setPadding(dp(6), 0, dp(6), 0);
        toggle.setSwitchMinWidth(dp(52));
        toggle.setSplitTrack(false);
        toggle.setShowText(false);
        GradientDrawable track = round(0xFF8496AF, dp(18));
        track.setSize(dp(52), dp(28));
        toggle.setTrackDrawable(track);
        GradientDrawable thumb = round(Color.WHITE, dp(18));
        thumb.setSize(dp(24), dp(24));
        thumb.setStroke(dp(1), 0x330D2742);
        toggle.setThumbDrawable(thumb);
        toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[0]},
                new int[]{0xFF0D7759, 0xFF68778B}));
        return toggle;
    }

    private void bindSwitch(Switch toggle, boolean active, boolean enabled, String name, String label) {
        toggle.setChecked(active);
        toggle.setEnabled(enabled);
        toggle.setContentDescription(Build.VERSION.SDK_INT >= 30 ? name : name + ". " + label);
        if (Build.VERSION.SDK_INT >= 30) toggle.setStateDescription(label);
    }

    private Runnable editAction(JSONObject menu, String id, String name, String category, String image, int track, int stock) {
        return () -> {
            if (!refreshNeeded && !pending.contains(id) && !id.isEmpty()) editMenu(menu, id, name, category, image, track, stock);
        };
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
        intent.putExtra("photo_badge", menu.optString("photo_badge", ""));
        intent.putExtra("original_photo", menu.optString("original_photo", ""));
        intent.putExtra("description", menu.optString("description", ""));
        intent.putExtra("track_stock", track);
        intent.putExtra("stock", stock);
        intent.putExtra("discount_active", menu.optInt("discount_active", 0));
        intent.putExtra("discount_percent", menu.optDouble("discount_percent", 0));
        JSONArray options = menu.optJSONArray("options");
        if (options != null) intent.putExtra("options_json", options.toString());
        intent.putExtra("topping_revision", menu.optString("topping_revision"));
        startActivity(intent);
    }
    private void loadMenuImage(ImageView iv,TextView fallback,String raw){String url=normalizeImageUrl(raw);if(url.isEmpty())return;Glide.with(this).load(url).centerCrop().thumbnail(0.25f).listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>(){@Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,Object model,com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,boolean first){fallback.setVisibility(android.view.View.VISIBLE);return false;}@Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,Object model,com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,com.bumptech.glide.load.DataSource dataSource,boolean first){fallback.setVisibility(android.view.View.GONE);return false;}}).into(iv);}
    private String normalizeImageUrl(String raw){if(raw==null)return"";String u=raw.trim();if(u.isEmpty()||"null".equalsIgnoreCase(u))return"";if(u.startsWith("http://transiva.my.id/"))return u.replace("http://transiva.my.id/","https://transiva.my.id/");if(u.startsWith("https://"))return u;if(u.startsWith("http://"))return"";if(u.startsWith("/"))return"https://transiva.my.id"+u;return"https://transiva.my.id/"+u;}
    private interface SavedMutation {
        void apply(JSONObject response) throws JSONException;
    }

    private void writeMenu(JSONObject menu, String id, Integer active) {
        HashMap<String, Object> fields = new HashMap<>();
        if (active != null) fields.put("is_active", active);
        String path = active == null ? "merchant_delete_menu.php" : "merchant_update_menu_status.php";
        write(id, path, fields, active == null, active != null, response -> {
            if (active != null) menu.put("is_active", response.optInt("is_active", active));
            else expanded.remove(id);
        });
    }

    private void writeTopping(JSONObject menu, String id, Topping option, boolean available) {
        HashMap<String, Object> fields = new HashMap<>();
        fields.put("type", option.type);
        fields.put("name", option.name);
        fields.put("is_available", available);
        write(id, "merchant_update_topping_status.php", fields, false, false,
                response -> menu.put("options", response.getJSONArray("options")));
    }

    private void write(String id, String path, HashMap<String, Object> fields,
                       boolean reload, boolean statusChanged, SavedMutation mutation) {
        if (refreshNeeded || id.isEmpty() || !pending.add(id)) {
            bindVisible(id);
            return;
        }
        bindVisible(id);
        fields.put("menu_id", id);
        boolean accepted = MerchantNetworkExecutor.executeWrite("menu-mutation:" + id, () -> {
            JSONObject result;
            try {
                result = new JSONObject(postJson(BASE + path, new JSONObject(fields)));
            } catch (Exception error) {
                result = null;
            }
            JSONObject response = result;
            runOnUiThread(() -> finishWrite(id, response, reload, statusChanged, mutation));
        });
        if (!accepted) {
            pending.remove(id);
            toast("Perubahan belum dikirim. Coba lagi.");
            load();
        }
    }

    private void finishWrite(String id, JSONObject response, boolean reload, boolean statusChanged,
                             SavedMutation mutation) {
        if (!canUpdateUi()) return;
        pending.remove(id);
        if (response == null || !response.has("success")) {
            toast("Status belum terkonfirmasi. Memeriksa ulang menu…");
            refreshNeeded = true;
        } else if (!response.optBoolean("success")) {
            toast(response.optString("message", "Perubahan belum disimpan."));
            refreshNeeded = true;
        } else {
            try {
                mutation.apply(response);
                if (reload) refreshNeeded = true;
                toast(response.optString("message", "Perubahan disimpan."));
            } catch (JSONException error) {
                refreshNeeded = true;
            }
        }

        if (refreshNeeded) {
            updateVisibleControls();
            if (pending.isEmpty()) load();
        } else if (statusChanged && selectedFilter > 0) {
            render();
        } else {
            bindVisible(id);
        }
    }

    private void bindVisible(String id) {
        MenuControls controls = visibleControls.get(id);
        if (controls != null) controls.bind();
    }
}
