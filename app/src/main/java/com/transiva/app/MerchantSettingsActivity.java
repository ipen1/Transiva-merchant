package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.os.Bundle;

import org.json.JSONObject;

/** Pengaturan lokal dan keamanan akun khusus sisi merchant. */
public class MerchantSettingsActivity extends MerchantBaseActivity {
    private static final String CHANGE_PIN_URL = BASE + "merchant_change_pin.php";
    private static final String CHANGE_CREDENTIALS_URL = BASE + "merchant_change_credentials.php";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        build(root);
        MerchantAppSettings.apply(this);
    }

    private void build(LinearLayout root) {
        root.addView(title("Pengaturan Merchant"));
        root.addView(sub("Atur aplikasi, keamanan akun, dan sesi Transiva Merchant"));

        addSection(root, "Tampilan", 8);
        LinearLayout card = settingCard();
        card.addView(toggleRow("Mode Malam",
                "Aktifkan tema gelap pada seluruh halaman merchant",
                MerchantAppSettings.isDarkMode(this),
                (button, checked) -> {
                    MerchantAppSettings.setDarkMode(this, checked);
                    recreate();
                }));
        root.addView(card);

        addSection(root, "Pembaruan", 18);
        LinearLayout updateCard = settingCard();
        updateCard.addView(actionRow("Cek Pembaruan Aplikasi",
                "Versi terpasang " + AppUpdateClient.installedVersionName(this),
                () -> {
                    Intent i = new Intent(this, UpdateDownloadActivity.class);
                    i.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "merchant");
                    startActivity(i);
                }));
        root.addView(updateCard);

        addSection(root, "Akun Merchant", 18);
        LinearLayout accountCard = settingCard();
        accountCard.addView(actionRow("Profil Merchant",
                "Kelola nama restoran dan banner merchant",
                () -> open(MerchantRestaurantProfileActivity.class)));
        accountCard.addView(divider());
        accountCard.addView(actionRow("Ubah Username",
                "Ganti nama pengguna untuk login merchant",
                this::showChangeUsername));
        accountCard.addView(divider());
        accountCard.addView(actionRow("Ubah Password",
                "Gunakan password baru yang kuat dan unik",
                this::showChangePassword));
        accountCard.addView(divider());
        accountCard.addView(actionRow("Ubah PIN",
                "PIN keamanan 6 angka untuk akses aplikasi",
                this::showChangePin));
        root.addView(accountCard);

        TextView securityNote = tv(
                "Untuk keamanan, perubahan username dikonfirmasi dengan password saat ini. Perubahan password memerlukan password saat ini + PIN 6 angka. Setelah berhasil, seluruh sesi lama akan dikeluarkan dan Anda perlu login kembali.",
                11, MUTED, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(4), dp(12), dp(4), dp(4));
        root.addView(securityNote, noteLp);

        addSection(root, "Sesi", 18);
        Button logout = new Button(this);
        logout.setText("Keluar dari Akun");
        logout.setAllCaps(false);
        logout.setTextSize(15);
        logout.setTextColor(Color.WHITE);
        logout.setGravity(Gravity.CENTER);
        logout.setBackground(round(Color.parseColor("#D92D20"), dp(16)));
        logout.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(-1, dp(52));
        logoutLp.setMargins(0, 0, 0, dp(20));
        root.addView(logout, logoutLp);
    }

    private void addSection(LinearLayout root, String text, int topMargin) {
        TextView section = tv(text, 13, NAVY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(topMargin), 0, dp(8));
        root.addView(section, lp);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#E9EEF5"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, dp(12), 0, dp(12));
        line.setLayoutParams(lp);
        return line;
    }

    private LinearLayout actionRow(String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(tv(title, 15, NAVY, true));
        labels.addView(tv(subtitle, 11, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(tv("›", 30, BLUE, true));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private LinearLayout toggleRow(String title, String subtitle, boolean checked,
                                   CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(tv(title, 15, NAVY, true));
        labels.addView(tv(subtitle, 11, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private LinearLayout settingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(Color.WHITE, dp(20)));
        card.setElevation(dp(2));
        return card;
    }

    private EditText secureInput(String hint, int inputType) {
        EditText e = input(hint, inputType);
        return e;
    }

    private LinearLayout dialogForm(EditText... fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(4), p, 0);
        for (EditText field : fields) box.addView(field);
        return box;
    }

    private void showChangeUsername() {
        EditText username = secureInput("Username baru", InputType.TYPE_CLASS_TEXT);
        username.setText(sessionManager == null ? "" : sessionManager.getUsername());
        username.setSelection(username.getText().length());
        EditText currentPassword = secureInput("Password saat ini",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ubah Username")
                .setMessage("Username 3–32 karakter: huruf kecil, angka, titik, _ atau -.")
                .setView(dialogForm(username, currentPassword))
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newUsername = username.getText().toString().trim().toLowerCase();
            String current = currentPassword.getText().toString();
            if (!MerchantSecurityRules.isUsernameValid(newUsername)) { username.setError("Username tidak valid"); return; }
            if (current.isEmpty()) { currentPassword.setError("Wajib diisi"); return; }
            submitCredentials(dialog, newUsername, "", "", current, "");
        }));
        dialog.show();
    }

    private void showChangePassword() {
        EditText current = secureInput("Password saat ini",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText next = secureInput("Password baru (min. 8 karakter)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = secureInput("Ulangi password baru",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText securityPin = secureInput("PIN keamanan 6 angka",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ubah Password")
                .setMessage("Masukkan password saat ini dan PIN keamanan. Password baru minimal 8 karakter dan harus mengandung huruf serta angka.")
                .setView(dialogForm(current, next, confirm, securityPin))
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldPass = current.getText().toString();
            String newPass = next.getText().toString();
            String conf = confirm.getText().toString();
            if (oldPass.isEmpty()) { current.setError("Wajib diisi"); return; }
            if (newPass.length() < 8 || !newPass.matches(".*[A-Za-z].*") || !newPass.matches(".*\\d.*")) {
                next.setError("Minimal 8 karakter, berisi huruf dan angka"); return;
            }
            if (!newPass.equals(conf)) { confirm.setError("Konfirmasi tidak sama"); return; }
            if (newPass.equals(oldPass)) { next.setError("Harus berbeda dari password lama"); return; }
            String pin = securityPin.getText().toString().trim();
            if (!MerchantSecurityRules.isPinValid(pin)) { securityPin.setError("PIN harus tepat 6 angka"); return; }
            submitCredentials(dialog, "", newPass, conf, oldPass, pin);
        }));
        dialog.show();
    }

    private void showChangePin() {
        EditText oldPin = secureInput("PIN lama", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText newPin = secureInput("PIN baru (6 angka)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText confirmPin = secureInput("Ulangi PIN baru", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ubah PIN")
                .setMessage("Masukkan PIN lama untuk verifikasi. Setelah 5 kali salah, perubahan PIN dikunci sementara.")
                .setView(dialogForm(oldPin, newPin, confirmPin))
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldValue = oldPin.getText().toString().trim();
            String newValue = newPin.getText().toString().trim();
            String confValue = confirmPin.getText().toString().trim();
            if (!oldValue.matches("^\\d{6}$")) { oldPin.setError("PIN harus 6 angka"); return; }
            if (!newValue.matches("^\\d{6}$")) { newPin.setError("PIN harus 6 angka"); return; }
            if (oldValue.equals(newValue)) { newPin.setError("PIN baru harus berbeda"); return; }
            if (!newValue.equals(confValue)) { confirmPin.setError("Konfirmasi tidak sama"); return; }
            submitPin(dialog, oldValue, newValue, confValue);
        }));
        dialog.show();
    }

    private void submitPin(AlertDialog dialog, String oldPin, String newPin, String confirmPin) {
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(false);
        save.setText("Menyimpan...");
        MerchantNetworkExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("old_pin", oldPin);
                body.put("new_pin", newPin);
                body.put("confirm_pin", confirmPin);
                JSONObject result = new JSONObject(postJson(CHANGE_PIN_URL, body));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText("Simpan");
                    if (result.optBoolean("success")) { dialog.dismiss(); alert("Berhasil", result.optString("message", "PIN berhasil diubah.")); }
                    else alert("Tidak dapat mengubah PIN", result.optString("message", "Perubahan PIN gagal."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> { save.setEnabled(true); save.setText("Simpan"); alert("Koneksi gagal", "Tidak dapat terhubung ke server. Coba kembali."); });
            }
        });
    }

    private void submitCredentials(AlertDialog dialog, String username, String newPassword,
                                   String confirmPassword, String currentPassword, String securityPin) {
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(false);
        save.setText("Menyimpan...");
        MerchantNetworkExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("current_password", currentPassword);
                if (!username.isEmpty()) body.put("new_username", username);
                if (!newPassword.isEmpty()) {
                    body.put("new_password", newPassword);
                    body.put("confirm_password", confirmPassword);
                    body.put("security_pin", securityPin);
                }
                JSONObject result = new JSONObject(postJson(CHANGE_CREDENTIALS_URL, body));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText("Simpan");
                    if (result.optBoolean("success")) {
                        dialog.dismiss();
                        new AlertDialog.Builder(this)
                                .setTitle("Berhasil")
                                .setMessage(result.optString("message", "Data login berhasil diubah. Silakan login kembali."))
                                .setCancelable(false)
                                .setPositiveButton("Login Ulang", (d, w) -> logout())
                                .show();
                    } else {
                        alert("Perubahan ditolak", result.optString("message", "Data login tidak dapat diubah."));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { save.setEnabled(true); save.setText("Simpan"); alert("Koneksi gagal", "Tidak dapat terhubung ke server. Coba kembali."); });
            }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar dari akun?")
                .setMessage("Anda perlu login kembali untuk menggunakan Transiva Merchant.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Keluar", (d, w) -> logout())
                .show();
    }
}
