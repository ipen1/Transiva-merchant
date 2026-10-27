package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LoginActivity extends MerchantAuthActivity {

    private static final String TAG = "TRANSIVA_LOGIN";
    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String LOGIN_URL = BASE_URL + "server/login.php";
    private static final String SAVE_FCM_URL =
            BASE_URL + "server/save_fcm_token.php";
    private static final String PRIVACY_URL = BASE_URL + "privacy.html";
    private static final String TERMS_URL = BASE_URL + "terms.html";
    private static final int TIMEOUT_MS = 25000;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView messageText;
    private ProgressBar loadingView;
    private ImageButton eyeButton;

    private int loginStep;
    private TextView stepLabel, previous, identity, usernameLabel, passwordLabel;
    private View passwordBox;
    private LinearLayout loginCard;
    private boolean loading;
    private boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildScreen();
        if (savedInstanceState != null) {
            usernameInput.setText(savedInstanceState.getString("username", ""));
            loginStep = savedInstanceState.getInt("step", 0) == 1 ? 1 : 0;
            updateLoginStep(false);
        }

        try {
            TransivaNotificationPermission.ask(this);
        } catch (Exception error) {
            Log.w(TAG, "Permission notifikasi gagal", error);
        }
    }

    private void buildScreen() {
        authShell("Selamat datang kembali", "Kelola usaha, sajikan yang terbaik. Mulai dari sini.");
        loginCard = authCard();
        stepLabel = authText("", 12, true); stepLabel.setTextColor(Color.rgb(52, 145, 255));
        stepLabel.setPadding(0, 0, 0, dp(16)); loginCard.addView(stepLabel);
        identity = authText("", 20, true); identity.setPadding(0, 0, 0, dp(16)); loginCard.addView(identity);
        messageText = authText("", 13, false); messageText.setPadding(dp(12), dp(10), dp(12), dp(10));
        messageText.setVisibility(View.GONE); loginCard.addView(messageText);
        usernameLabel = authText("Nama pengguna", 14, true); loginCard.addView(usernameLabel);
        usernameInput = input("Masukkan nama pengguna", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false);
        usernameInput.setSaveEnabled(false);
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT); loginCard.addView(usernameInput, fieldLayout());
        passwordLabel = authText("Kata sandi", 14, true); loginCard.addView(passwordLabel);
        FrameLayout box = new FrameLayout(this); passwordBox = box;
        passwordInput = input("Masukkan kata sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
        passwordInput.setSaveEnabled(false);
        box.addView(passwordInput, new FrameLayout.LayoutParams(-1, -1));
        eyeButton = new ImageButton(this); eyeButton.setImageResource(android.R.drawable.ic_menu_view);
        eyeButton.setContentDescription("Tampilkan kata sandi"); eyeButton.setBackgroundColor(Color.TRANSPARENT);
        eyeButton.setColorFilter(Color.rgb(52,145,255));
        FrameLayout.LayoutParams eyeLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        box.addView(eyeButton, eyeLp); loginCard.addView(box, fieldLayout());
        loginButton = new Button(this); loginButton.setAllCaps(false); loginButton.setTextSize(16);
        loginButton.setTextColor(Color.WHITE); loginButton.setTypeface(Typeface.DEFAULT_BOLD);
        loginButton.setBackground(gradient("#006BEF", "#2E9BFF", dp(17)));
        loginCard.addView(loginButton, new LinearLayout.LayoutParams(-1, dp(54)));
        loadingView = new ProgressBar(this); loadingView.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        loadingLp.gravity = Gravity.CENTER; loginCard.addView(loadingView, loadingLp);
        previous = authText("← Ganti nama pengguna", 14, true); previous.setGravity(Gravity.CENTER);
        previous.setMinHeight(dp(48)); loginCard.addView(previous);
        TextView register = authText("Belum bergabung? Daftar Merchant →", 14, true);
        register.setGravity(Gravity.CENTER); register.setMinHeight(dp(56)); authRoot.addView(register);
        TextView legal = authText("Kebijakan Privasi  •  Syarat & Ketentuan", 12, false);
        legal.setTextColor(mutedColor); legal.setGravity(Gravity.CENTER); legal.setMinHeight(dp(48)); authRoot.addView(legal);
        legal.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Informasi Transiva")
                .setItems(new String[]{"Kebijakan Privasi", "Syarat & Ketentuan"}, (d, w) -> openBrowser(w == 0 ? PRIVACY_URL : TERMS_URL)).show());
        loginButton.setOnClickListener(v -> advanceLogin());
        usernameInput.setOnEditorActionListener((v, id, event) -> { if(id == EditorInfo.IME_ACTION_NEXT) {advanceLogin(); return true;} return false; });
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, id, event) -> { if(id == EditorInfo.IME_ACTION_DONE) {advanceLogin(); return true;} return false; });
        eyeButton.setOnClickListener(v -> togglePassword());
        previous.setOnClickListener(v -> returnToUsername());
        register.setOnClickListener(v -> {if(!loading) openRegister();});
        updateLoginStep(false); mountAuth();
    }

    private void advanceLogin() {
        if (loading) return;
        if (loginStep == 1) {attemptLogin(); return;}
        if (usernameInput.getText().toString().trim().isEmpty()) {usernameInput.setError("Masukkan nama pengguna"); return;}
        clearMessage(); loginStep = 1; updateLoginStep(true); passwordInput.requestFocus();
    }
    private void updateLoginStep(boolean animate) {
        boolean password = loginStep == 1;
        stepLabel.setText(password ? "LANGKAH 2 DARI 2 • KATA SANDI" : "LANGKAH 1 DARI 2 • IDENTITAS");
        usernameLabel.setVisibility(password ? View.GONE : View.VISIBLE);
        usernameInput.setVisibility(password ? View.GONE : View.VISIBLE);
        passwordLabel.setVisibility(password ? View.VISIBLE : View.GONE);
        passwordBox.setVisibility(password ? View.VISIBLE : View.GONE);
        identity.setText("Halo, " + usernameInput.getText().toString().trim());
        identity.setVisibility(password ? View.VISIBLE : View.GONE);
        previous.setVisibility(password ? View.VISIBLE : View.GONE);
        loginButton.setText(password ? "Masuk →" : "Lanjut →");
        if (animate) animateStep(loginCard);
    }
    private void returnToUsername() {
        if (loading) return;
        passwordInput.setText(""); loginStep = 0; clearMessage(); updateLoginStep(true); usernameInput.requestFocus();
    }
    @Override public void onBackPressed() {
        if (loading) return;
        if (loginStep == 1) returnToUsername(); else super.onBackPressed();
    }
    @Override protected boolean canChangeTheme() { return !loading; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("username", usernameInput.getText().toString()); state.putInt("step", loginStep);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null); super.onDestroy();
    }

    private void attemptLogin() {
        if (loading) return;

        clearMessage();

        String username =
                usernameInput.getText().toString().trim();
        String password =
                passwordInput.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage(
                    "Lengkapi Nama Pengguna dan Kata Sandi",
                    false
            );
            return;
        }

        setLoading(true);

        boolean accepted = MerchantNetworkExecutor.executeWrite("merchant-login", () -> {
            MerchantLoginRepository.Result result = MerchantLoginRepository.login(this, username, password);

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (!result.success) {
                    showMessage(result.message, false);
                    return;
                }

                if (result.user == null) {
                    showMessage(
                            "Server tidak mengirim data pengguna.",
                            false
                    );
                    return;
                }

                String apiToken =
                        result.user.optString("token", "").trim();

                if (apiToken.isEmpty()) {
                    showMessage(
                            "Login berhasil, tetapi token sesi kosong. "
                                    + "Pastikan login.php sudah di-upgrade.",
                            false
                    );
                    return;
                }

                String role = normalizeRole(
                        result.user.optString(
                                "role",
                                result.role
                        )
                );

                if (!"merchant".equals(role)) {
                    showMessage("Akun ini bukan akun Merchant Transiva.", false);
                    return;
                }

                try {
                    result.user.put("role", "merchant");
                } catch (Exception ignored) {}

                SessionManager session =
                        new SessionManager(LoginActivity.this);

                boolean sessionSaved;

                try {
                    /*
                     * Bersihkan session lama agar token/customer/driver
                     * tidak tercampur.
                     */
                    session.forceLogout("replace_login_session");
                    sessionSaved = session.saveUser(result.user);
                } catch (Exception error) {
                    Log.e(TAG, "Gagal menyimpan session", error);
                    sessionSaved = false;
                }

                if (!sessionSaved
                        || !session.isLoggedIn()
                        || session.getUsername().trim().isEmpty()
                        || session.getToken().trim().isEmpty()) {

                    session.forceLogout("login_session_invalid");

                    showMessage(
                            "Login berhasil, tetapi sesi gagal disimpan.",
                            false
                    );
                    return;
                }

                try {
                    TransivaSession.saveUser(
                            LoginActivity.this,
                            result.user
                    );
                } catch (Exception error) {
                    Log.w(TAG, "Legacy session gagal", error);
                }

                Log.d(
                        TAG,
                        "Session valid role=" + session.getRole()
                                + ", user=" + session.getUsername()
                                + ", tokenLength="
                                + session.getToken().length()
                );

                MerchantLoginRepository.syncFcmAfterLogin(LoginActivity.this, result.user);

                showMessage("Login berhasil", true);
                setLoading(true);

                String finalRole = role;

                mainHandler.postDelayed(
                        () -> openPinPage(finalRole),
                        500
                );
            });
        });
        if (!accepted) {
            setLoading(false);
            showMessage("Permintaan login sedang diproses. Coba lagi sesaat.", false);
        }
    }











    private void openPinPage(String role) {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("native_role", normalizeRole(role));
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        startActivity(intent);
        finish();
    }

    private void openRolePage(String role) {
        Intent intent = new Intent(this, MerchantDashboardActivity.class);
        intent.putExtra("native_role", "merchant");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private String normalizeRole(String role) {
        String clean =
                role == null
                        ? ""
                        : role.trim().toLowerCase(Locale.US);

        if (clean.equals("driver")
                || clean.equals("kurir")
                || clean.equals("ojek")
                || clean.equals("rider")) {
            return "driver";
        }

        if (clean.equals("merchant")
                || clean.equals("merchen")
                || clean.equals("resto")
                || clean.equals("restaurant")
                || clean.equals("penjual")) {
            return "merchant";
        }

        if (clean.equals("admin")
                || clean.equals("administrator")
                || clean.equals("owner")
                || clean.equals("superadmin")) {
            return "admin";
        }

        if (clean.equals("wisata")
                || clean.equals("wisataowner")
                || clean.equals("wisata_owner")
                || clean.equals("owner_wisata")) {
            return "wisata";
        }

        return "customer";
    }

    private void togglePassword() {
        int selection =
                passwordInput.getSelectionStart();

        passwordVisible = !passwordVisible;
        eyeButton.setContentDescription(passwordVisible ? "Sembunyikan kata sandi" : "Tampilkan kata sandi");

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | (
                        passwordVisible
                                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                : InputType.TYPE_TEXT_VARIATION_PASSWORD
                )
        );

        passwordInput.setSelection(
                Math.max(0, selection)
        );
    }

    private void setLoading(boolean value) {
        loading = value;

        loadingView.setVisibility(
                value ? View.VISIBLE : View.GONE);

        loginButton.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        eyeButton.setEnabled(!value);

        loginButton.setText(
                value ? "Memuat..." : loginStep == 0 ? "Lanjut →" : "Masuk →");
    }

    private void showMessage(
            String message,
            boolean success
    ) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        boolean dark = MerchantAppSettings.isDarkMode(this);
        messageText.setTextColor(Color.parseColor(dark ? (success ? "#70E0AE" : "#FFB4AB") : (success ? "#166534" : "#B91C1C")));
        messageText.setBackground(authShape(Color.parseColor(dark ? (success ? "#153C32" : "#48272F") : (success ? "#DCFCE7" : "#FEE2E2")), 12));
    }

    private void clearMessage() {
        messageText.setText("");
        messageText.setVisibility(View.GONE);
    }

    private void openRegister() {
        startActivity(new Intent(this, MerchantRegisterActivity.class));
    }

    private void openBrowser(String url) {
        try {
            startActivity(
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    )
            );
        } catch (Exception error) {
            showInfo(
                    "Tidak dapat membuka halaman",
                    url
            );
        }
    }

    private void showInfo(
            String title,
            String message
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }



    private TextView label(String value) { return MerchantLoginUi.label(this, value); }

    private EditText input(
            String hint,
            int inputType,
            boolean hasEye
    ) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTextColor(inkColor);
        field.setHintTextColor(mutedColor);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setPadding(
                dp(18),
                0,
                hasEye ? dp(52) : dp(18),
                0
        );
        field.setBackground(authShape(surfaceColor, 16));
        return field;
    }

    private LinearLayout.LayoutParams fieldLayout() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, 0, 0, dp(13));
        return lp;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable round(
            String fill,
            int radius
    ) { return MerchantLoginUi.round(fill, radius, this); }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) { return MerchantLoginUi.roundStroke(fill, stroke, radius, width, this); }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) { return MerchantLoginUi.gradient(start, end, radius, this); }

    private int findDrawable(String name) {
        return getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );
    }

    private int dp(int value) { return MerchantLoginUi.dp(this, value); }

    private int firstPositiveInt(int... values) {
        if (values == null) return 0;

        for (int value : values) {
            if (value > 0) return value;
        }

        return 0;
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value != null
                    && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }


}
