package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MerchantBranchesActivity extends MerchantBaseActivity {
    private static final int PICK_POINT = 931, EDIT_PROFILE = 932;
    private static final String ENDPOINT = BASE + "merchant_branches.php";
    private JSONArray branches = new JSONArray();
    private boolean editing, saving;
    private String requestKey;
    private int movingId;
    private MerchantPremiumUi ui;
    private TextView listFeedback;
    private double latitude = Double.NaN, longitude = Double.NaN;
    private LinearLayout root;
    private EditText name;
    private TextView status, point;
    private RadioGroup copy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new MerchantPremiumUi(this);
        if (state != null) {
            editing = state.getBoolean("editing");
            requestKey = state.getString("request_key");
            movingId = state.getInt("moving_id");
            latitude = state.getDouble("lat", Double.NaN);
            longitude = state.getDouble("lng", Double.NaN);
            try { branches = new JSONArray(state.getString("branches", "[]")); } catch (Exception ignored) {}
        }
        if (editing) showCreate(state.getString("name", ""), state.getInt("copy", 0));
        else if (movingId != 0) showList();
        else load();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("editing", editing);
        state.putString("request_key", requestKey);
        state.putInt("moving_id", movingId);
        state.putDouble("lat", latitude);
        state.putDouble("lng", longitude);
        state.putString("branches", branches.toString());
        if (editing) {
            state.putString("name", name.getText().toString());
            state.putInt("copy", selectedSource());
        }
        super.onSaveInstanceState(state);
    }

    private void show(String heading) {
        root = new LinearLayout(this);
        setContentView(page(root));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = ui.iconButton(R.drawable.ic_merchant_chevron, ui.accent(), "Kembali");
        back.setRotation(180f);
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(title(heading), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
    }

    private void load() {
        show("Toko & Cabang");
        root.addView(sub("Memuat daftar cabang…"));
        if (MerchantNetworkExecutor.executeRead(this, "branches", () -> {
            try {
                JSONObject result = new JSONObject(get(ENDPOINT));
                if (!result.optBoolean("success")) throw new IllegalStateException(result.optString("message"));
                JSONArray data = result.getJSONArray("branches");
                runOnUiThread(() -> {
                    if (!alive()) return;
                    branches = data;
                    showList();
                });
            } catch (Exception error) {
                runOnUiThread(this::loadFailed);
            }
        }) == null) loadFailed();
    }

    private void loadFailed() {
        if (!alive()) return;
        show("Toko & Cabang");
        root.addView(sub("Daftar toko belum bisa dimuat."));
        Button retry = btn("Coba lagi");
        retry.setOnClickListener(v -> load());
        root.addView(retry);
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void showList() {
        editing = false;
        show("Toko & Cabang");
        root.addView(sub("Pilih toko untuk mengelola menu, stok, dan pesanan."));
        listFeedback = sub("");
        listFeedback.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        listFeedback.setVisibility(android.view.View.GONE);
        root.addView(listFeedback);
        for (int i = 0; i < branches.length(); i++) {
            JSONObject row = branches.optJSONObject(i);
            if (row != null) addOutlet(row);
        }
        Button add = btn("+ Tambah cabang");
        add.setOnClickListener(v -> {
            if (saving) return;
            requestKey = java.util.UUID.randomUUID().toString();
            latitude = longitude = Double.NaN;
            showCreate("", 0);
        });
        root.addView(add);
    }

    private void addOutlet(JSONObject outlet) {
        int id = outlet.optInt("id");
        boolean main = outlet.optInt("is_main") == 1;
        String label = outlet.optString("name", "Toko");
        String selectedId = branchId().isEmpty() ? sessionManager.getRestaurantId() : branchId();
        boolean active = Integer.toString(id).equals(selectedId) || (main && branchId().isEmpty());

        LinearLayout card = new LinearLayout(this);
        card.setBackground(ui.surface(16));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(4), dp(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(8);
        root.addView(card, cardParams);

        LinearLayout selectArea = new LinearLayout(this);
        selectArea.setGravity(Gravity.CENTER_VERTICAL);
        selectArea.setBackground(ui.touch(null));
        selectArea.setOnClickListener(v -> { if (!saving) select(main ? "" : Integer.toString(id)); });
        card.addView(selectArea, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout photo = new FrameLayout(this);
        photo.setBackground(round(ui.dark() ? 0xFF20344B : 0xFFE8F1FF, dp(10)));
        photo.setClipToOutline(true);
        photo.addView(ui.icon(R.drawable.ic_merchant_store, ui.accent()),
                new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        ImageView image = new ImageView(this);
        photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
        String banner = outlet.optString("banner", "");
        if (banner.startsWith("http://transiva.my.id/")) banner = "https://" + banner.substring(7);
        if (!banner.isEmpty() && !banner.startsWith("http://")) {
            String url = banner.startsWith("https://") ? banner : "https://transiva.my.id/" + banner.replaceFirst("^/+", "");
            Glide.with(this).load(url).centerCrop().into(image);
        }
        selectArea.addView(photo, new LinearLayout.LayoutParams(dp(62), dp(54)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(10), 0, 0, 0);
        selectArea.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = tv(label, 15, NAVY, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(name);
        details.addView(tv((main ? "Toko utama" : "Cabang") + (active ? " · Dipilih" : ""), 12,
                active ? ui.accent() : MUTED, active));
        details.addView(tv(outlet.optBoolean("accepting_orders", outlet.optInt("is_open") == 1) ? "Buka" : "Tutup", 12, MUTED, false));

        ImageButton actions = ui.iconButton(R.drawable.ic_merchant_overflow, ui.accent(), "Kelola " + label);
        actions.setOnClickListener(v -> { if (!saving) showOutletActions(outlet, actions); });
        card.addView(actions, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    private void showOutletActions(JSONObject outlet, ImageButton anchor) {
        boolean main = outlet.optInt("is_main") == 1;
        int id = outlet.optInt("id");
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Edit profil & foto");
        menu.getMenu().add(0, 2, 1, "Pindah titik pickup");
        if (!main) menu.getMenu().add(0, 3, 2, "Hapus cabang");
        menu.setOnMenuItemClickListener(item -> {
            if (saving) return false;
            switch (item.getItemId()) {
                case 1:
                    Intent profile = new Intent(this, MerchantRestaurantProfileActivity.class);
                    profile.putExtra(EXTRA_BRANCH, main ? "" : Integer.toString(id));
                    startActivityForResult(profile, EDIT_PROFILE);
                    break;
                case 2:
                    movingId = id;
                    launchMap(outlet.optDouble("latitude", Double.NaN), outlet.optDouble("longitude", Double.NaN));
                    break;
                case 3:
                    new AlertDialog.Builder(this)
                            .setTitle("Hapus " + outlet.optString("name", "cabang") + "?")
                            .setMessage("Cabang akan ditutup dan hilang dari daftar. Riwayat pesanan tetap tersimpan.")
                            .setNegativeButton("Batal", null)
                            .setPositiveButton("Hapus cabang", (d, which) -> updateOutlet("delete", id, 0, 0))
                            .show();
                    break;
                default:
                    return false;
            }
            return true;
        });
        menu.show();
    }

    private void showFeedback(String message) {
        listFeedback.setText(message);
        listFeedback.setVisibility(android.view.View.VISIBLE);
    }

    private void updateOutlet(String action, int id, double lat, double lng) {
        if (saving) return;
        saving = true;
        showFeedback("Menyimpan perubahan…");
        boolean queued = MerchantNetworkExecutor.executeWrite("branch:" + id, () -> {
            try {
                JSONObject payload = new JSONObject().put("action", action).put("restaurant_id", id);
                if ("relocate".equals(action)) payload.put("latitude", lat).put("longitude", lng);
                JSONObject result = new JSONObject(postJson(ENDPOINT, payload));
                runOnUiThread(() -> {
                    if (!alive()) return;
                    saving = false;
                    if (!result.optBoolean("success")) {
                        showFeedback(result.optString("message", "Cabang belum bisa diperbarui."));
                        return;
                    }
                    toast(result.optString("message", "Cabang diperbarui."));
                    if ("delete".equals(action) && Integer.toString(id).equals(branchId())) select("");
                    else load();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    saving = false;
                    showFeedback("Perubahan belum dapat dikonfirmasi. Periksa koneksi dan coba lagi.");
                });
            }
        });
        if (!queued) {
            saving = false;
            showFeedback("Permintaan sedang antre. Coba lagi.");
        }
    }

    private void showCreate(String draftName, int sourceId) {
        editing = true;
        show("Tambah Cabang");
        name = input("Contoh: Es Milo Sitty · Cabang Palu", InputType.TYPE_CLASS_TEXT);
        name.setText(draftName);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        root.addView(name);
        Button map = outlineBtn("Pilih titik pickup di peta");
        map.setOnClickListener(v -> openMap());
        root.addView(map);
        point = sub("");
        root.addView(point);
        showPoint();

        root.addView(title("Menu awal"));
        copy = new RadioGroup(this);
        root.addView(copy);
        addSource("Tambah menu sendiri", 0, sourceId);
        for (int i = 0; i < branches.length(); i++) {
            JSONObject row = branches.optJSONObject(i);
            if (row != null) addSource("Salin dari " + row.optString("name"), row.optInt("id"), sourceId);
        }
        root.addView(sub("Menu yang disalin bisa diubah per cabang. Isi stok cabang sebelum membuka toko."));
        status = sub("");
        root.addView(status);
        Button submit = btn("Buat cabang");
        submit.setOnClickListener(v -> create(submit));
        root.addView(submit);
        Button back = outlineBtn("Kembali ke daftar cabang");
        back.setOnClickListener(v -> { if (!saving) showList(); });
        root.addView(back);
    }

    private void addSource(String label, int id, int selected) {
        RadioButton radio = new RadioButton(this);
        radio.setId(android.view.View.generateViewId());
        radio.setText(label);
        radio.setTag(id);
        copy.addView(radio);
        if (id == selected) copy.check(radio.getId());
    }

    private int selectedSource() {
        RadioButton radio = copy.findViewById(copy.getCheckedRadioButtonId());
        return radio == null ? 0 : (Integer)radio.getTag();
    }

    private void openMap() {
        if (saving) return;
        double lat = latitude, lng = longitude;
        if (Double.isNaN(lat) && branches.length() > 0) {
            JSONObject main = branches.optJSONObject(0);
            if (main != null) {
                lat = main.optDouble("latitude", Double.NaN);
                lng = main.optDouble("longitude", Double.NaN);
            }
        }
        launchMap(lat, lng);
    }

    private void launchMap(double lat, double lng) {
        Intent map = new Intent(this, MerchantBranchMapActivity.class);
        map.putExtra("lat", lat);
        map.putExtra("lng", lng);
        startActivityForResult(map, PICK_POINT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == EDIT_PROFILE && result == RESULT_OK) load();
        if (request != PICK_POINT) return;
        int target = movingId;
        movingId = 0;
        if (result != RESULT_OK || data == null) return;
        double lat = data.getDoubleExtra("lat", Double.NaN);
        double lng = data.getDoubleExtra("lng", Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) return;
        if (editing) {
            latitude = lat;
            longitude = lng;
            showPoint();
        } else if (target > 0) {
            updateOutlet("relocate", target, lat, lng);
        }
    }

    private void showPoint() {
        point.setText(Double.isNaN(latitude) ? "Titik pickup belum dipilih."
                : String.format(java.util.Locale.US, "Titik pickup dipilih · %.6f, %.6f", latitude, longitude));
    }

    private void create(Button button) {
        if (saving) return;
        String branchName = name.getText().toString().trim();
        if (branchName.isEmpty() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            status.setText("Isi nama cabang dan pilih titik pickup di peta.");
            return;
        }
        final double lat = latitude, lng = longitude;
        final int source = selectedSource();
        final String key = requestKey;
        saving = true;
        button.setEnabled(false);
        status.setText("Menyimpan cabang…");
        boolean queued = MerchantNetworkExecutor.executeWrite("create-branch:" + key, () -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("name", branchName);
                payload.put("latitude", lat);
                payload.put("longitude", lng);
                payload.put("copy_from", source);
                payload.put("request_key", key);
                JSONObject result = new JSONObject(postJson(ENDPOINT, payload));
                if (!result.optBoolean("success")) throw new IllegalStateException(result.optString("message"));
                String id = result.getString("restaurant_id");
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    toast("Cabang dibuat. Buka toko setelah menu dan stok siap.");
                    select(id);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    saving = false;
                    button.setEnabled(true);
                    String message = error instanceof IllegalStateException ? error.getMessage() : "";
                    status.setText(message == null || message.isEmpty() ? "Cabang belum bisa disimpan. Coba lagi." : message);
                });
            }
        });
        if (!queued) {
            saving = false;
            button.setEnabled(true);
            status.setText("Permintaan sedang antre. Coba lagi.");
        }
    }

    private void select(String id) {
        sessionManager.put("selected_merchant_branch", id);
        Intent dashboard = new Intent(this, MerchantDashboardActivity.class);
        dashboard.putExtra(EXTRA_BRANCH, id);
        dashboard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(dashboard);
    }

    @Override public void onBackPressed() {
        if (saving) return;
        if (editing) showList();
        else super.onBackPressed();
    }
}
