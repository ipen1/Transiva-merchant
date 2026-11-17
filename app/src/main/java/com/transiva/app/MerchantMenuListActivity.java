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

public class MerchantMenuListActivity extends MerchantBaseActivity {
    private LinearLayout list;
    private EditText search;
    private Spinner statusFilter;
    private TextView resultCount;
    private Button loadNotice;
    private JSONArray cached = new JSONArray();
    private final HashSet<String> pending = new HashSet<>();
    private final HashMap<String, MenuControls> visibleControls = new HashMap<>();
    private boolean loaded;
    private boolean loading;
    private boolean refreshNeeded = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setFocusableInTouchMode(true);
        setContentView(page(root));
        root.addView(title("Semua Menu"));

        LinearLayout actions = actionRow();
        Button add = compactAction("+ Tambah Menu", true);
        Button categories = compactAction("Kategori", false);
        add.setOnClickListener(v -> open(MerchantAddMenuActivity.class));
        categories.setOnClickListener(v -> open(MerchantCategoriesActivity.class));
        actions.addView(add, actionLp());
        actions.addView(categories, actionLp());
        root.addView(actions);

        search = input("Cari menu, kategori, atau topping", InputType.TYPE_CLASS_TEXT);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(search);

        LinearLayout filters = actionRow();
        filters.setGravity(Gravity.CENTER_VERTICAL);
        resultCount = tv("", 12, MUTED, true);
        resultCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        filters.addView(resultCount, new LinearLayout.LayoutParams(stackControls() ? -1 : 0, -2, stackControls() ? 0 : 1));
        statusFilter = new Spinner(this);
        statusFilter.setContentDescription("Filter ketersediaan menu");
        statusFilter.setMinimumHeight(dp(48));
        statusFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Semua status", "Tersedia", "Tidak tersedia"}));
        statusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { render(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        filters.addView(statusFilter, new LinearLayout.LayoutParams(-2, -2));
        root.addView(filters);

        loadNotice = compactAction("Memuat menu…", false);
        loadNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        loadNotice.setOnClickListener(v -> load());
        root.addView(loadNotice);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        root.requestFocus();
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

    private ArrayList<String> toppingNames(JSONObject menu) {
        ArrayList<String> names=new ArrayList<>();
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
        if (list == null || !loaded) return;
        visibleControls.clear();
        list.removeAllViews();
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filter=statusFilter.getSelectedItemPosition(), shown=0;
        for (int i=0;i<cached.length();i++) {
            JSONObject menu=cached.optJSONObject(i);
            if (menu==null) continue;
            boolean available = available(menu);
            if (filter==1 && !available || filter==2 && available) continue;
            ArrayList<String> toppings=toppingNames(menu);
            String searchable=s(menu,"name","menu_name","food_name")+" "+s(menu,"category","type")
                    +" "+TextUtils.join(" ",toppings);
            if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            list.addView(menuCard(menu,toppings));
            shown++;
        }
        resultCount.setText(shown+" menu");
        if (shown == 0) list.addView(card(cached.length() == 0
                ? "Belum ada menu di toko ini." : "Tidak ada menu sesuai pencarian atau filter."));
    }

    private Button compactAction(String label, boolean primary) {
        Button button=primary?btn(label):outlineBtn(label);
        button.setTextSize(13);
        if (!primary) button.setTextColor(accent());
        button.setMinimumHeight(dp(48)); button.setMinHeight(dp(48));
        button.setPadding(dp(8),dp(4),dp(8),dp(4));
        button.setElevation(0);
        return button;
    }

    private int accent() {
        return MerchantAppSettings.isDarkMode(this) ? 0xFF83C4FF : 0xFF155BB4;
    }

    private LinearLayout menuCard(JSONObject menu, List<String> toppings) {
        String id = s(menu, "id", "menu_id");
        String name = s(menu, "name", "menu_name", "food_name");
        if (name.isEmpty()) name = "Tanpa nama";
        String category = s(menu, "category", "type");
        String image = s(menu, "image", "photo", "image_url");
        int track = menu.optInt("track_stock");
        int stock = menu.optInt("stock");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(8), dp(9), dp(4));
        box.setBackground(stroke(Color.WHITE, 0xFFDCE8F5, dp(16)));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(7);
        box.setLayoutParams(outer);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout thumb = new FrameLayout(this);
        thumb.setBackground(round(0xFFEEF6FF, dp(10)));
        thumb.setClipToOutline(true);
        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        TextView fallback = tv("🍽", 19, accent(), true);
        fallback.setGravity(Gravity.CENTER);
        thumb.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams picture = new LinearLayout.LayoutParams(dp(46), dp(46));
        picture.rightMargin = dp(8);
        row.addView(thumb, picture);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(line(name, 15, NAVY, true, 2));
        String details = category.isEmpty() ? "Tanpa kategori" : category;
        if (track == 1) details += " · Stok " + stock;
        info.addView(line(details, 12, MUTED, false, 2));
        String price = rupiah(menu.optLong("price", 0));
        if (menu.optInt("discount_active") == 1 && menu.optDouble("discount_percent") > 0) {
            price += " · Diskon " + String.format(Locale.US, "%.0f%%", menu.optDouble("discount_percent"));
        }
        info.addView(line(price, 13, accent(), true, 2));
        if (!toppings.isEmpty()) {
            info.addView(line(toppings.size() + " topping · " + TextUtils.join(", ", toppings), 11, MUTED, false, 2));
        }
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        loadMenuImage(photo, fallback, image);

        Runnable editMenu = editAction(menu, id, name, category, image, track, stock);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button edit = compactAction("Edit menu", false);
        Button more = compactAction("⋯", false);
        more.setContentDescription("Tindakan untuk " + name);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), -2);
        moreParams.leftMargin = dp(5);
        actions.addView(edit, new LinearLayout.LayoutParams(-2, -2));
        actions.addView(more, moreParams);

        MenuControls controls = new MenuControls(menu, id, name, edit, more, editMenu);
        visibleControls.put(id, controls);
        controls.bind();
        if (screenWidthDp() < 320 || getResources().getConfiguration().fontScale > 1.2f) {
            LinearLayout statusRow = new LinearLayout(this);
            statusRow.setGravity(Gravity.END);
            statusRow.addView(controls.panel, new LinearLayout.LayoutParams(-2, -2));
            box.addView(statusRow);
        } else {
            LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-2, -2);
            panelParams.leftMargin = dp(6);
            row.addView(controls.panel, panelParams);
        }
        box.addView(actions);

        edit.setOnClickListener(v -> editMenu.run());
        String menuName = name;
        more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, more);
            popup.getMenu().add("Hapus menu");
            popup.setOnMenuItemClickListener(item -> {
                PremiumDialogs.builder(this).setTitle("Hapus menu")
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
        final String id;
        final String name;
        final Button edit;
        final Button more;
        final LinearLayout panel;
        final Switch toggle;
        final TextView status;
        final Runnable editAction;

        MenuControls(JSONObject menu, String id, String name, Button edit, Button more, Runnable editAction) {
            this.menu = menu;
            this.id = id;
            this.name = name;
            this.edit = edit;
            this.more = more;
            this.editAction = editAction;

            panel = new LinearLayout(MerchantMenuListActivity.this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            toggle = new Switch(MerchantMenuListActivity.this);
            toggle.setGravity(Gravity.CENTER);
            toggle.setMinimumHeight(dp(48));
            toggle.setMinimumWidth(dp(72));
            toggle.setPadding(dp(10), 0, dp(10), 0);
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
                    new int[]{0xFF0F765C, 0xFF64748B}));
            panel.addView(toggle, new LinearLayout.LayoutParams(dp(72), -2));

            status = tv("", 11, MUTED, true);
            status.setGravity(Gravity.CENTER);
            status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            status.setPadding(dp(6), 0, dp(6), dp(4));
            panel.addView(status);
        }

        void bind() {
            boolean busy = pending.contains(id);
            boolean active = available(menu);
            String label;
            if (busy) label = "Memproses…";
            else if (refreshNeeded) label = loading ? "Memuat status" : "Perlu dimuat ulang";
            else if (active) label = "Tersedia";
            else label = soldOut(menu) ? "Stok habis" : "Tidak tersedia";
            boolean dark = MerchantAppSettings.isDarkMode(MerchantMenuListActivity.this);
            int color = active && !busy && !refreshNeeded ? (dark ? 0xFF76E2B5 : 0xFF126148) : MUTED;
            int surface = active ? (dark ? 0xFF14392F : 0xFFE6F6EE)
                    : (dark ? MerchantAppSettings.DARK_CARD_SOFT : 0xFFF0F4F8);
            panel.setBackground(round(surface, dp(12)));
            status.setText(label);
            status.setTextColor(color);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(active);
            toggle.setEnabled(!busy && !refreshNeeded && !id.isEmpty());
            if (Build.VERSION.SDK_INT >= 30) {
                toggle.setContentDescription("Ketersediaan " + name);
                toggle.setStateDescription(label);
            } else {
                toggle.setContentDescription("Ketersediaan " + name + ". " + label);
            }
            toggle.setOnCheckedChangeListener((button, checked) -> change(checked));
            edit.setEnabled(!busy && !refreshNeeded && !id.isEmpty());
            more.setEnabled(edit.isEnabled());
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
    private void writeMenu(JSONObject menu, String id, Integer active) {
        if (refreshNeeded || id.isEmpty() || !pending.add(id)) {
            bindVisible(id);
            return;
        }
        bindVisible(id);
        boolean accepted = MerchantNetworkExecutor.executeWrite("menu-mutation:" + id, () -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("menu_id", id);
                if (active != null) payload.put("is_active", active);
                String path = active == null ? "merchant_delete_menu.php" : "merchant_update_menu_status.php";
                JSONObject response = new JSONObject(postJson(BASE + path, payload));
                if (!response.has("success")) throw new IllegalStateException();
                runOnUiThread(() -> finishWrite(menu, id, active, response));
            } catch (Exception error) {
                runOnUiThread(() -> finishWrite(menu, id, active, null));
            }
        });
        if (!accepted) {
            pending.remove(id);
            toast("Perubahan belum dikirim. Coba lagi.");
            load();
        }
    }

    private void finishWrite(JSONObject menu, String id, Integer active, JSONObject response) {
        if (!canUpdateUi()) return;
        pending.remove(id);
        if (response == null) {
            toast("Status belum terkonfirmasi. Memeriksa ulang menu…");
            refreshNeeded = true;
        } else if (!response.optBoolean("success")) {
            toast(response.optString("message", "Perubahan menu gagal disimpan."));
            refreshNeeded = true;
        } else if (active == null) {
            toast("Menu dihapus.");
            refreshNeeded = true;
        } else {
            int confirmed = response.optInt("is_active", active);
            try {
                menu.put("is_active", confirmed);
            } catch (org.json.JSONException error) {
                refreshNeeded = true;
            }
            toast(confirmed == 1 ? "Menu tersedia." : "Menu dinonaktifkan.");
        }

        if (refreshNeeded) {
            updateVisibleControls();
            if (pending.isEmpty()) load();
        } else if (statusFilter.getSelectedItemPosition() > 0) {
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
