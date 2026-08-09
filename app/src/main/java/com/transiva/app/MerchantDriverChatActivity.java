package com.transiva.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantDriverChatActivity extends MerchantBaseActivity {
    private static final long REFRESH_MS = 2500L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private TextView status;
    private String orderId = "", orderDbId = "", driverName = "Driver";
    private int lastId = 0;
    private boolean loading = false, sending = false, stopped = false;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!stopped) { load(false); main.postDelayed(this, REFRESH_MS); }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        orderId = safe(getIntent().getStringExtra("order_id"));
        orderDbId = safe(getIntent().getStringExtra("order_db_id"));
        driverName = first(getIntent().getStringExtra("driver_name"), "Driver");
        build();
        if (orderId.isEmpty() && orderDbId.isEmpty()) {
            alert("Chat tidak tersedia", "ID order tidak ditemukan.");
            send.setEnabled(false);
            return;
        }
        load(true);
    }

    @Override protected void onResume() { super.onResume(); stopped = false; main.removeCallbacks(refresh); main.postDelayed(refresh, REFRESH_MS); }
    @Override protected void onPause() { super.onPause(); stopped = true; main.removeCallbacks(refresh); }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(14), dp(14), dp(12));
        page.setBackgroundColor(BG);
        setContentView(page);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(10), dp(12), dp(10));
        head.setBackground(round(Color.WHITE, dp(18)));
        TextView back = tv("←", 26, BLUE, true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(8),0,0,0);
        info.addView(tv(driverName, 17, NAVY, true));
        status = tv("Chat Driver • Order #" + (orderId.isEmpty()?orderDbId:orderId), 11, MUTED, false); info.addView(status);
        head.addView(info, new LinearLayout.LayoutParams(0,-2,1));
        page.addView(head);

        LinearLayout quick = new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setPadding(0,dp(10),0,dp(4));
        addQuick(quick,"Pesanan sedang disiapkan"); addQuick(quick,"Pesanan sudah siap");
        page.addView(quick);
        LinearLayout quick2 = new LinearLayout(this); quick2.setOrientation(LinearLayout.HORIZONTAL); quick2.setPadding(0,0,0,dp(8));
        addQuick(quick2,"Tunggu sebentar ya"); addQuick(quick2,"Silakan ambil pesanan");
        page.addView(quick2);

        scroll = new ScrollView(this); scroll.setFillViewport(true);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(2),dp(6),dp(2),dp(8));
        scroll.addView(messages, new ScrollView.LayoutParams(-1,-2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(dp(8),dp(7),dp(8),dp(7)); composer.setBackground(round(Color.WHITE,dp(18)));
        input = new EditText(this); input.setHint("Ketik pesan ke driver..."); input.setSingleLine(false); input.setMaxLines(3); input.setTextSize(14); input.setTextColor(TEXT); input.setHintTextColor(Color.parseColor("#98A2B3")); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setBackground(stroke(Color.parseColor("#F8FBFF"),Color.parseColor("#DDE7F3"),dp(14))); input.setPadding(dp(12),0,dp(12),0);
        composer.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        send = btn("Kirim"); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(86),dp(50));sp.setMargins(dp(8),0,0,0);composer.addView(send,sp); send.setOnClickListener(v->sendText(input.getText().toString()));
        page.addView(composer);
    }

    private void addQuick(LinearLayout parent, String text) {
        Button b = outlineBtn(text); b.setTextSize(11); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1); lp.setMargins(dp(3),0,dp(3),0); parent.addView(b,lp); b.setOnClickListener(v->sendText(text));
    }

    private void load(boolean initial) {
        if (loading) return; loading=true;
        new Thread(() -> {
            try {
                String url = BASE + "getMerchantDriverChat.php?order_id=" + enc(orderId) + "&order_db_id=" + enc(orderDbId) + "&last_id=" + lastId + "&_=" + System.currentTimeMillis();
                JSONObject r = new JSONObject(get(url));
                runOnUiThread(() -> apply(r, initial));
            } catch(Exception e) { if(initial) runOnUiThread(() -> status.setText("Koneksi chat belum tersedia • akan mencoba lagi")); }
            finally { loading=false; }
        }).start();
    }

    private void apply(JSONObject r, boolean initial) {
        if (!r.optBoolean("success",false)) { status.setText(r.optString("message","Chat belum tersedia")); return; }
        status.setText(r.optBoolean("ended",false)?"Riwayat chat • order selesai":"Online • chat merchant ↔ driver");
        JSONArray a=r.optJSONArray("messages"); if(a!=null) for(int i=0;i<a.length();i++){ JSONObject m=a.optJSONObject(i); if(m==null)continue; addMessage(m); lastId=Math.max(lastId,m.optInt("id",0)); }
        if(initial && messages.getChildCount()==0){ TextView empty=tv("Belum ada pesan. Gunakan Quick Chat atau ketik pesan ke driver.",12,MUTED,false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),dp(40),dp(20),dp(20)); messages.addView(empty); }
        if(r.optBoolean("ended",false)){ input.setEnabled(false); send.setEnabled(false); }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessage(JSONObject m) {
        if(messages.getChildCount()==1 && messages.getChildAt(0) instanceof TextView && ((TextView)messages.getChildAt(0)).getText().toString().startsWith("Belum ada pesan")) messages.removeAllViews();
        boolean mine="customer".equalsIgnoreCase(m.optString("sender_type",""));
        LinearLayout line=new LinearLayout(this); line.setGravity(mine?Gravity.END:Gravity.START);
        TextView bubble=tv(m.optString("message",""),14,mine?Color.WHITE:TEXT,false); bubble.setPadding(dp(12),dp(9),dp(12),dp(9)); bubble.setBackground(round(mine?BLUE:Color.WHITE,dp(16))); bubble.setMaxWidth(dp(285));
        line.addView(bubble); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(3));messages.addView(line,lp);
    }

    private void sendText(String raw) {
        String text=safe(raw).trim(); if(text.isEmpty()||sending)return; if(text.length()>500){toast("Pesan maksimal 500 karakter.");return;}
        sending=true; send.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject p=new JSONObject();p.put("order_id",orderId);p.put("order_db_id",orderDbId);p.put("message",text);
                JSONObject r=new JSONObject(postJson(BASE+"sendMerchantDriverChat.php",p));
                runOnUiThread(() -> { if(r.optBoolean("success",false)){input.setText("");load(false);} else toast(r.optString("message","Pesan gagal dikirim")); });
            }catch(Exception e){runOnUiThread(()->toast("Koneksi gagal. Pesan belum dikirim."));}
            finally{sending=false;runOnUiThread(()->send.setEnabled(true));}
        }).start();
    }

    private String first(String... v){ if(v!=null)for(String s:v)if(s!=null&&!s.trim().isEmpty())return s.trim();return ""; }
    private String safe(String s){return s==null?"":s;}
}
