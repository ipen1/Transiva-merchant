package com.transiva.app;

import android.content.Intent;
import android.os.Bundle;
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
    private static final int PICK_POINT = 931;
    private static final String ENDPOINT = BASE + "merchant_branches.php";
    private JSONArray branches = new JSONArray();
    private boolean editing, saving;
    private String requestKey;
    private double latitude = Double.NaN, longitude = Double.NaN;
    private LinearLayout root;
    private EditText name;
    private TextView status, point;
    private RadioGroup copy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            editing = state.getBoolean("editing");
            requestKey = state.getString("request_key");
            latitude = state.getDouble("lat", Double.NaN);
            longitude = state.getDouble("lng", Double.NaN);
            try { branches = new JSONArray(state.getString("branches", "[]")); } catch (Exception ignored) {}
        }
        if (editing) showCreate(state.getString("name", ""), state.getInt("copy", 0));
        else load();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("editing", editing);
        state.putString("request_key", requestKey);
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
        root.addView(title(heading));
    }

    private void load() {
        show("Toko & Cabang");
        root.addView(sub("Memuat daftar cabang…"));
        MerchantNetworkExecutor.executeRead(this, "branches", () -> {
            try {
                JSONObject result = new JSONObject(get(ENDPOINT));
                if (!result.optBoolean("success")) throw new IllegalStateException(result.optString("message"));
                JSONArray data = result.getJSONArray("branches");
                runOnUiThread(() -> { branches = data; showList(); });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    show("Toko & Cabang");
                    root.addView(card("Daftar cabang belum bisa dimuat."));
                    Button retry = btn("Coba lagi");
                    retry.setOnClickListener(v -> load());
                    root.addView(retry);
                });
            }
        });
    }

    private void showList() {
        editing = false;
        show("Toko & Cabang");
        root.addView(sub("Pilih toko untuk mengelola menu, stok, jam buka dan pesanan dari akun ini."));
        String selected = branchId().isEmpty() ? sessionManager.getRestaurantId() : branchId();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject row = branches.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id");
            boolean main = row.optInt("is_main") == 1;
            String label = row.optString("name", "Toko") + (main ? " · Utama" : " · Cabang");
            Button choice = outlineBtn(label + (id.equals(selected) ? " ✓" : ""));
            choice.setOnClickListener(v -> select(main ? "" : id));
            root.addView(choice);
        }
        Button add = btn("Tambah cabang");
        add.setOnClickListener(v -> {
            requestKey = java.util.UUID.randomUUID().toString();
            latitude = longitude = Double.NaN;
            showCreate("", 0);
        });
        root.addView(add);
        Button back = outlineBtn("Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
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
        Intent map = new Intent(this, MerchantBranchMapActivity.class);
        map.putExtra("lat", lat);
        map.putExtra("lng", lng);
        startActivityForResult(map, PICK_POINT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_POINT || result != RESULT_OK || data == null || !editing) return;
        latitude = data.getDoubleExtra("lat", Double.NaN);
        longitude = data.getDoubleExtra("lng", Double.NaN);
        showPoint();
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
