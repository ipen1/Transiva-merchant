package com.transiva.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class MerchantReviewsActivity extends MerchantBaseActivity {
    private static final int GOLD = Color.rgb(255, 202, 105);
    private static final int PALE_BLUE = Color.rgb(190, 222, 255);
    private final Locale indonesia = Locale.forLanguageTag("id-ID");

    private LinearLayout content;
    private Button refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title("Rating & Ulasan"), new LinearLayout.LayoutParams(0, -2, 1f));
        refresh = outlineBtn("↻");
        refresh.setContentDescription("Muat ulang ulasan");
        refresh.setPadding(0, 0, 0, 0);
        refresh.setElevation(0);
        refresh.setOnClickListener(v -> load());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);
        root.addView(sub("Masukan pelanggan untuk toko Anda."));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        load();
    }

    private void load() {
        if (!refresh.isEnabled()) return;
        refresh.setEnabled(false);
        showMessage("Memuat ulasan…");
        if (MerchantNetworkExecutor.executeRead(this, "reviews", () -> {
            try {
                JSONObject response = new JSONObject(get(BASE + "getMerchantReviews.php?v=" + System.currentTimeMillis()));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    refresh.setEnabled(true);
                    if (response.optBoolean("success")) show(response);
                    else showMessage(response.optString("message", "Gagal memuat ulasan."));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    refresh.setEnabled(true);
                    showMessage("Koneksi gagal. Coba muat ulang ulasan.");
                });
            }
        }) == null) {
            refresh.setEnabled(true);
            showMessage("Ulasan sedang sibuk dimuat. Coba lagi.");
        }
    }

    private void showMessage(String message) {
        content.removeAllViews();
        addLine(addCard(), message, 14, MUTED, false, 0);
    }

    private void show(JSONObject response) {
        content.removeAllViews();
        JSONObject summary = response.optJSONObject("restaurant");
        if (summary != null) addSummary(summary);

        boolean hasRestaurant = addSection("Ulasan Restoran", response.optJSONArray("restaurant_reviews"), false, false);
        boolean hasMenu = addSection("Ulasan Menu", response.optJSONArray("menu_reviews"), true, false);
        boolean hasOrder = addSection("Ulasan Pesanan", response.optJSONArray("reviews"), false, true);
        if (!hasRestaurant && !hasMenu && !hasOrder) {
            LinearLayout empty = addCard();
            addLine(empty, "Belum ada ulasan", 16, TEXT, true, 0);
            addLine(empty, "Penilaian pelanggan akan muncul di sini.", 13, MUTED, false, 4);
        }
    }

    private void addSummary(JSONObject summary) {
        LinearLayout hero = addCard();
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10, 34, 73), Color.rgb(10, 77, 161)});
        gradient.setCornerRadius(dp(20));
        hero.setBackground(gradient);

        addLine(hero, "RATING RESTORAN", 10, PALE_BLUE, true, 0).setLetterSpacing(0.1f);
        String name = s(summary, "name");
        if (!name.isEmpty()) addLine(hero, name, 15, Color.WHITE, true, 2);

        int count = Math.max(0, summary.optInt("review_count"));
        double rating = Math.max(0, Math.min(5, summary.optDouble("rating", 0)));
        LinearLayout scores = new LinearLayout(this);
        scores.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-1, -2);
        scoreParams.topMargin = dp(4);
        hero.addView(scores, scoreParams);
        TextView score = tv(count > 0 ? String.format(indonesia, "%.1f / 5", rating) : "—",
                28, Color.WHITE, true);
        scores.addView(score, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(14), 0, 0, 0);
        scores.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
        addLine(detail, stars(count > 0 ? (int)Math.round(rating) : 0), 18, GOLD, true, 0);
        addLine(detail, count > 0 ? count + " penilaian restoran" : "Belum ada penilaian",
                12, PALE_BLUE, false, 1);
    }

    private boolean addSection(String label, JSONArray reviews, boolean menu, boolean order) {
        if (reviews == null || reviews.length() == 0) return false;
        addLine(content, label, 15, NAVY, true, 6).setPadding(dp(2), 0, 0, dp(7));
        for (int i = 0; i < reviews.length(); i++) {
            JSONObject review = reviews.optJSONObject(i);
            if (review != null) addReview(review, menu, order);
        }
        return true;
    }

    private void addReview(JSONObject review, boolean menu, boolean order) {
        LinearLayout box = addCard();
        String customer = s(review, "customer_name");
        if (customer.isEmpty()) customer = "Pelanggan";
        String menuName = s(review, "menu_name");
        String headline = menu ? (menuName.isEmpty() ? "Menu #" + s(review, "menu_id") : menuName) : customer;

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = tv(headline, 15, TEXT, true);
        name.setPadding(0, 0, dp(10), 0);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        int rating = Math.max(0, Math.min(5, review.optInt("rating")));
        TextView badge = tv("★ " + rating + " / 5", 12, Color.rgb(151, 91, 15), true);
        boolean dark = MerchantAppSettings.isDarkMode(this);
        badge.setTextColor(dark ? GOLD : Color.rgb(151, 91, 15));
        badge.setBackground(round(dark ? Color.rgb(66, 47, 23) : Color.rgb(255, 245, 225), dp(10)));
        badge.setPadding(dp(9), dp(4), dp(9), dp(4));
        header.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        box.addView(header);

        String comment = s(review, "review");
        addLine(box, comment.isEmpty() ? "Penilaian tanpa komentar" : comment,
                14, comment.isEmpty() ? MUTED : TEXT, false, 5);
        if (menu) addLine(box, "Oleh " + customer, 12, MUTED, false, 5);
        if (order) {
            addLine(box, "Pesanan #" + s(review, "order_id", "id"), 12, MUTED, false, 5);
            addReply(box, review);
        }
    }

    private LinearLayout addCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(11), dp(13), dp(11));
        box.setBackground(stroke(Color.WHITE, Color.rgb(225, 234, 247), dp(17)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(box, params);
        return box;
    }

    private TextView addLine(LinearLayout parent, String text, int size, int color, boolean bold, int top) {
        TextView line = tv(text, size, color, bold);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(line, params);
        return line;
    }

    private String stars(int rating) {
        return "★★★★★".substring(0, rating) + "☆☆☆☆☆".substring(rating);
    }

    private void addReply(LinearLayout box, JSONObject review) {
        String existing = s(review, "merchant_reply").trim();
        if (!existing.isEmpty()) {
            addLine(box, "Balasan Anda", 12, TEXT, true, 11);
            addLine(box, existing, 13, MUTED, false, 2);
        }
        Button toggle = outlineBtn(existing.isEmpty() ? "Balas ulasan" : "Edit balasan");
        toggle.setTextSize(12);
        toggle.setElevation(0);
        box.addView(toggle);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setVisibility(android.view.View.GONE);
        box.addView(editor);
        toggle.setOnClickListener(v -> {
            boolean open = editor.getVisibility() == android.view.View.VISIBLE;
            editor.setVisibility(open ? android.view.View.GONE : android.view.View.VISIBLE);
            toggle.setText(open ? (existing.isEmpty() ? "Balas ulasan" : "Edit balasan") : "Tutup editor");
        });
        EditText reply = input(existing.isEmpty() ? "Tulis balasan ulasan…" : "Perbarui balasan…",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reply.setSingleLine(false);
        reply.setMinLines(2);
        reply.setMaxLines(4);
        reply.setText(existing);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        editor.addView(reply, params);

        Button send = btn(existing.isEmpty() ? "Balas Ulasan" : "Perbarui Balasan");
        send.setOnClickListener(v -> saveReply(review.optInt("id"), reply, send));
        editor.addView(send);
    }

    private void saveReply(int orderDbId, EditText input, Button button) {
        String reply = input.getText().toString().trim();
        if (reply.isEmpty()) {
            toast("Balasan tidak boleh kosong");
            return;
        }
        button.setEnabled(false);
        if (!MerchantNetworkExecutor.executeWrite("review-reply:" + orderDbId, () -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("order_db_id", orderDbId);
                payload.put("reply", reply);
                JSONObject response = new JSONObject(postJson(BASE + "merchant_review_reply.php", payload));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    button.setEnabled(true);
                    boolean success = response.optBoolean("success");
                    toast(response.optString("message", success ? "Tersimpan" : "Gagal menyimpan balasan"));
                    if (success) load();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    button.setEnabled(true);
                    toast("Koneksi gagal. Coba kirim kembali.");
                });
            }
        })) {
            button.setEnabled(true);
            toast("Balasan sedang diproses. Coba lagi.");
        }
    }
}
