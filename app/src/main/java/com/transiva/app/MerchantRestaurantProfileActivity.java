package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import org.json.JSONObject;

public class MerchantRestaurantProfileActivity extends MerchantBaseActivity {
    private static final int PICK_BANNER = 802;
    private MerchantPremiumUi ui;
    private EditText nameInput;
    private TextView bannerTitle, areaText, bannerStatus, feedback;
    private ImageView bannerImage;
    private Button pickBannerButton, saveProfileButton, retryButton;
    private Uri draftBanner;
    private PreparedImage preparedBanner;
    private String savedName = "", savedBanner = "";
    private boolean loaded, preparing, saving;
    private MerchantPhotoBadge badge;
    private String originalBanner = "", restoredBadge;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new MerchantPremiumUi(this);
        build(state);
        load();
    }

    private void build(Bundle state) {
        LinearLayout root = column();
        root.setFocusableInTouchMode(true);
        LinearLayout footer = column();
        footer.setBackgroundColor(BG);
        saveProfileButton = btn("Simpan perubahan");
        saveProfileButton.setBackground(ui.touch(round(MerchantPremiumUi.BLUE, dp(14))));
        saveProfileButton.setElevation(0);
        saveProfileButton.setOnClickListener(v -> save());
        int side = Math.max(12, (screenWidthDp() - 840) / 2);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, -2);
        saveParams.setMargins(dp(side), dp(6), dp(side), dp(8));
        footer.addView(saveProfileButton, saveParams);
        View navigation = MerchantBottomNavigation.build(this, MerchantBottomNavigation.ActiveItem.SETTINGS);
        navigation.setMinimumHeight(dp(bottomNavHeightDp()));
        footer.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(footer, (view, insets) -> {
            navigation.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return insets;
        });
        setContentView(page(root, footer));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = ui.iconButton(R.drawable.ic_merchant_chevron, ui.accent(), "Kembali");
        back.setRotation(180f);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = title("Profil Merchant");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        LinearLayout profile = panel();
        FrameLayout preview = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = View.MeasureSpec.getSize(widthSpec);
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(
                        Math.min(dp(235), Math.round(width / 2.1f)), View.MeasureSpec.EXACTLY));
            }
        };
        preview.setBackground(round(ui.dark() ? 0xFF20344B : 0xFFE8F1FF, dp(12)));
        preview.setClipToOutline(true);
        ImageView placeholder = ui.icon(R.drawable.ic_merchant_store, ui.accent());
        preview.addView(placeholder, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        bannerImage = new ImageView(this);
        bannerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bannerImage.setContentDescription("Pratinjau banner merchant");
        preview.addView(bannerImage, new FrameLayout.LayoutParams(-1, -1));
        profile.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        bannerTitle = tv("Profil toko", 20, NAVY, true);
        bannerTitle.setPadding(0, dp(9), 0, dp(2));
        profile.addView(bannerTitle);
        areaText = tv("", 13, MUTED, false);
        areaText.setCompoundDrawablesRelative(ui.iconDrawable(R.drawable.ic_merchant_pin, MUTED, 17), null, null, null);
        areaText.setCompoundDrawablePadding(dp(5));
        areaText.setVisibility(View.GONE);
        profile.addView(areaText);
        View divider = new View(this);
        divider.setBackgroundColor(ui.divider());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(11), 0, dp(6));
        profile.addView(divider, dividerParams);

        LinearLayout actions = actionRow();
        boolean stacked = stackControls();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        bannerStatus = tv("Memuat profil…", 13, MUTED, true);
        bannerStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        actions.addView(bannerStatus, new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
        pickBannerButton = outlineBtn("Ganti banner");
        pickBannerButton.setTextSize(13);
        pickBannerButton.setTextColor(ui.accent());
        pickBannerButton.setElevation(0);
        pickBannerButton.setBackground(ui.touch(stroke(Color.WHITE, ui.accent(), dp(12))));
        pickBannerButton.setOnClickListener(v -> choose());
        actions.addView(pickBannerButton, new LinearLayout.LayoutParams(stacked ? -1 : -2, -2));
        profile.addView(actions);
        Button badgeButton = outlineBtn("Lencana foto");
        badgeButton.setBackground(ui.touch(ui.surface(12)));
        badgeButton.setTextColor(ui.accent());
        badgeButton.setElevation(0);
        badge = new MerchantPhotoBadge(this, preview, badgeButton, () -> {
            showBannerPreview();
            updateControls();
        });
        profile.addView(badgeButton);
        addPanel(root, profile);

        LinearLayout form = panel();
        form.addView(tv("Nama Merchant / Restoran", 14, NAVY, true));
        nameInput = input("Masukkan nama merchant", InputType.TYPE_CLASS_TEXT);
        nameInput.setSingleLine(true);
        nameInput.setElevation(0);
        nameInput.setBackground(ui.surface(11));
        if (state != null) nameInput.setText(state.getString("draft_name", ""));
        form.addView(nameInput);
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                String pendingName = text.toString().trim();
                bannerTitle.setText(pendingName.isEmpty() ? "Profil toko" : pendingName);
                updateControls();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        addPanel(root, form);
        feedback = sub("");
        feedback.setVisibility(View.GONE);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(feedback);
        retryButton = outlineBtn("Coba lagi");
        retryButton.setOnClickListener(v -> load());
        retryButton.setVisibility(View.GONE);
        root.addView(retryButton);

        if (state != null && !state.getString("draft_banner", "").isEmpty()) {
            draftBanner = Uri.parse(state.getString("draft_banner"));
        }
        restoredBadge = state == null ? null : state.getString("draft_badge");
        updateControls();
        root.requestFocus();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout panel() {
        LinearLayout layout = column();
        layout.setPadding(dp(12), dp(12), dp(12), dp(12));
        layout.setBackground(ui.surface(16));
        return layout;
    }

    private void addPanel(LinearLayout root, View panel) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        root.addView(panel, params);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("draft_name", nameInput.getText().toString());
        if (draftBanner != null) state.putString("draft_banner", draftBanner.toString());
        state.putString("draft_badge", loaded ? badge.value() : restoredBadge);
        super.onSaveInstanceState(state);
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void updateControls() {
        boolean editable = loaded && !saving;
        nameInput.setEnabled(editable);
        pickBannerButton.setEnabled(editable && !preparing);
        badge.setEnabled(editable && !preparing);
        boolean dirty = !savedName.equals(nameInput.getText().toString().trim()) || preparedBanner != null || badge.changed();
        saveProfileButton.setEnabled(editable && !preparing && dirty);
        saveProfileButton.setAlpha(saveProfileButton.isEnabled() || saving ? 1f : .60f);
    }

    private void message(String text) {
        feedback.setText(text);
        feedback.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void load() {
        retryButton.setVisibility(View.GONE);
        message("");
        if (MerchantNetworkExecutor.executeRead(this, "restaurant-profile", () -> {
            try {
                JSONObject result = new JSONObject(get(BASE + "get_restaurant_profile.php?v=" + System.currentTimeMillis()));
                runOnUiThread(() -> {
                    if (!alive()) return;
                    if (result.optBoolean("success", false)) showProfile(result);
                    else loadFailed(result.optString("message", "Profil belum dapat dimuat."));
                });
            } catch (Exception error) {
                runOnUiThread(() -> loadFailed("Profil belum dapat dimuat. Periksa koneksi."));
            }
        }) == null) loadFailed("Sistem sedang sibuk. Coba lagi.");
    }

    private void loadFailed(String error) {
        if (!alive()) return;
        bannerStatus.setText("Profil belum dimuat");
        message(error);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void showProfile(JSONObject result) {
        JSONObject restaurant = result.optJSONObject("restaurant");
        if (restaurant == null) restaurant = result;
        savedName = s(restaurant, "name", "restaurant_name", "merchant_name", "store_name");
        savedBanner = normalizeImageUrl(s(restaurant, "banner", "banner_url", "image", "photo", "foto", "cover", "cover_image", "restaurant_banner"));
        originalBanner = normalizeImageUrl(restaurant.optString("original_photo", ""));
        badge.load(restaurant.optString("photo_badge", ""), restoredBadge);
        restoredBadge = null;
        if (nameInput.length() == 0) nameInput.setText(savedName);
        bannerTitle.setText(nameInput.length() == 0 ? "Profil toko" : nameInput.getText());
        TransivaCluster.Item area = TransivaCluster.fromServer(result.optJSONObject("cluster"));
        if (area != null && area.id > 0) {
            String region = area.region == null || area.region.name.isEmpty() ? "" : "Regional " + area.region.name;
            String cluster = area.name.isEmpty() ? "" : "Cluster " + area.name;
            String label = region + (region.isEmpty() || cluster.isEmpty() ? "" : " · ") + cluster;
            areaText.setText(label);
            areaText.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        }
        loaded = true;
        showSavedBanner();
        updateControls();
        if (draftBanner != null) prepareBanner(draftBanner);
    }

    private void choose() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_BANNER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_BANNER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri source = data.getData();
        prepareBanner(source);
    }

    private void prepareBanner(Uri source) {
        draftBanner = source;
        preparedBanner = null;
        preparing = true;
        message("");
        bannerStatus.setText("Menyiapkan foto…");
        bannerStatus.setTextColor(MUTED);
        showBannerPreview();
        updateControls();
        if (!MerchantTaskExecutor.execute(() -> {
            try {
                PreparedImage photo = prepareAiResizeToWebp(source, "merchant_banner", 1280, 220 * 1024L, 170 * 1024L);
                runOnUiThread(() -> {
                    if (!alive()) return;
                    if (photo == null) {
                        prepareFailed();
                        return;
                    }
                    preparedBanner = photo;
                    preparing = false;
                    bannerStatus.setText("Banner siap disimpan");
                    updateControls();
                });
            } catch (Exception error) {
                runOnUiThread(this::prepareFailed);
            }
        })) prepareFailed();
    }

    private void prepareFailed() {
        if (!alive()) return;
        preparing = false;
        preparedBanner = null;
        draftBanner = null;
        showSavedBanner();
        message("Foto belum dapat diproses. Pilih ulang banner.");
        updateControls();
    }

    private void showSavedBanner() {
        boolean active = !savedBanner.isEmpty();
        bannerStatus.setText(active ? "●  Banner aktif" : "Belum ada banner");
        bannerStatus.setTextColor(active ? (ui.dark() ? 0xFF76E2B5 : 0xFF13734F) : MUTED);
        showBannerPreview();
    }

    private void showBannerPreview() {
        boolean draft = draftBanner != null || badge.changed();
        Object source = draftBanner != null ? draftBanner
                : draft && !originalBanner.isEmpty() ? originalBanner : savedBanner;
        boolean hasPhoto = draftBanner != null || !savedBanner.isEmpty();
        if (!hasPhoto) source = null;
        Glide.with(this).load(source).fitCenter().into(bannerImage);
        badge.showDraft(draft && hasPhoto);
    }

    private String normalizeImageUrl(String url) {
        if (url.startsWith("http://transiva.my.id/")) return "https://" + url.substring(7);
        if (url.startsWith("https://")) return url;
        if (url.isEmpty() || url.startsWith("http://")) return "";
        return "https://transiva.my.id" + (url.startsWith("/") ? "" : "/") + url;
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Nama merchant wajib diisi.");
            return;
        }
        if (!loaded || saving || preparing) return;
        PreparedImage upload = preparedBanner;
        String selection = badge.changed() || upload != null ? badge.value() : null;
        saving = true;
        message("");
        saveProfileButton.setText("Menyimpan…");
        updateControls();
        if (!MerchantNetworkExecutor.executeWrite("restaurant-profile-save:" + branchId(), () -> {
            try {
                JSONObject fields = new JSONObject().put("name", name);
                if (selection != null) fields.put("photo_badge", selection);
                JSONObject response = new JSONObject(postFormPrepared(BASE + "update_restaurant_profile.php", fields, upload, "banner"));
                runOnUiThread(() -> saveCompleted(name, response));
            } catch (Exception error) {
                runOnUiThread(() -> saveCompleted(name, null));
            }
        })) saveCompleted(name, null);
    }

    private void saveCompleted(String name, JSONObject response) {
        if (!alive()) return;
        saving = false;
        saveProfileButton.setText("Simpan perubahan");
        if (response != null && response.optBoolean("success")) {
            savedName = name;
            savedBanner = normalizeImageUrl(response.optString("banner", savedBanner));
            originalBanner = normalizeImageUrl(response.optString("original_photo", ""));
            badge.load(response.optString("photo_badge", ""), null);
            preparedBanner = null;
            draftBanner = null;
            nameInput.setText(name);
            showSavedBanner();
            setResult(RESULT_OK);
            toast("Profil disimpan.");
        } else message(response == null ? "Profil belum tersimpan. Coba lagi."
                : response.optString("message", "Profil belum tersimpan."));
        updateControls();
    }
}
