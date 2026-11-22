package com.transiva.app;

import android.graphics.Color;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantDriverChatActivity extends MerchantBaseActivity {
    private static final long ACTIVE_REFRESH_MS = 30000L;
    private static final long IDLE_REFRESH_MS = 45000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout messages;
    private TextView emptyConversation;
    private ScrollView scroll;
    private EditText input;
    private final OrderChatStyle chatStyle=new OrderChatStyle(this);
    private final android.util.SparseArray<TextView> receiptViews=new android.util.SparseArray<>();
    private long focusedSince, latestShownAt;
    private int readThrough;
    private final Runnable reportVisibleRead=()->{
        if(canRead()&&this.lastId>readThrough)load(false);
    };
    private Button send;
    private TextView status;
    private Button callDriver, quick;
    private TextView driverTitle;
    private boolean chatOpen;
    private String orderId = "", orderDbId = "", driverName = "Driver";
    private int lastId = 0;
    private int quietPolls = 0;
    private boolean loading = false, sending = false, stopped = false, realtimeRegistered = false;
    private boolean reloadAfterCurrent;
    private final Runnable reload = () -> load(false);

    private final BroadcastReceiver realtimeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String reason = intent == null ? "" : safe(intent.getStringExtra(MerchantRealtime.EXTRA_REASON));
            String pushedOrder = intent == null ? "" : safe(intent.getStringExtra(MerchantRealtime.EXTRA_ORDER_ID));
            if ("merchant_driver_chat".equals(reason) && (pushedOrder.isEmpty() || pushedOrder.equals(orderId) || pushedOrder.equals(orderDbId))) {
                quietPolls = 0;
                load(false);
            }
        }
    };

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!stopped) {
                load(false);
                long delay = quietPolls >= 3 ? IDLE_REFRESH_MS : ACTIVE_REFRESH_MS;
                main.postDelayed(this, WaveLoadGuard.jitter(delay));
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        orderId = safe(getIntent().getStringExtra("order_id"));
        orderDbId = safe(getIntent().getStringExtra("order_db_id"));
        driverName = first(getIntent().getStringExtra("driver_name"), "Driver");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        build();
        if (b != null) input.setText(b.getString("chat_draft", ""));
        if (orderId.isEmpty() && orderDbId.isEmpty()) {
            alert("Chat tidak tersedia", "ID order tidak ditemukan.");
            send.setEnabled(false);
            return;
        }
        load(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (input != null) out.putString("chat_draft", input.getText().toString());
    }

    @Override protected void onResume() {
        super.onResume();
        stopped = false;
        if(hasWindowFocus()){focusedSince=android.os.SystemClock.elapsedRealtime();main.postDelayed(reportVisibleRead,1250);}
        if (!realtimeRegistered) { MerchantRealtime.register(this, realtimeReceiver); realtimeRegistered = true; }
        main.removeCallbacks(refresh);
        main.postDelayed(refresh, WaveLoadGuard.jitter(ACTIVE_REFRESH_MS));
        if (reloadAfterCurrent && !loading) load(false);
    }
    @Override protected void onPause() {
        super.onPause();
        stopped = true;focusedSince=0;main.removeCallbacks(reportVisibleRead);
        main.removeCallbacks(refresh);
        main.removeCallbacks(reload);
        if (realtimeRegistered) { try { unregisterReceiver(realtimeReceiver); } catch (Exception ignored) {} realtimeRegistered = false; }
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(8), dp(12), dp(8));
        page.setBackgroundColor(BG);
        chatStyle.backdrop(page);
        setContentView(page);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(3), dp(2), dp(6), dp(2));
        head.setBackground(round(Color.WHITE, dp(18)));
        TextView back = tv("‹", 25, BLUE, true); back.setGravity(Gravity.CENTER); back.setContentDescription("Kembali"); back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        chatStyle.addAvatar(head,driverName,getIntent().getStringExtra("participant_photo"));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(0,0,dp(6),0);
        driverTitle=tv(driverName, 16, NAVY, true);
        driverTitle.setMaxLines(2);
        driverTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(driverTitle);
        status = tv("Pesanan #" + (orderId.isEmpty()?orderDbId:orderId), 11, MUTED, false); info.addView(status);
        head.addView(info, new LinearLayout.LayoutParams(0,-2,1));
        callDriver = outlineBtn("☎");
        callDriver.setContentDescription("Panggil Driver");
        callDriver.setEnabled(false);
        callDriver.setOnClickListener(v -> MerchantDriverCommunication.chooseCall(this, orderId, orderDbId, driverName));
        head.addView(callDriver, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(head);

        callDriver.setPadding(0,0,0,0);
        callDriver.setElevation(0);
        quick=outlineBtn("Pesan cepat  ▾");
        quick.setBackgroundColor(Color.TRANSPARENT);
        quick.setPadding(0,0,0,0);
        quick.setElevation(0);
        quick.setTextSize(12);
        quick.setEnabled(false);
        quick.setOnClickListener(v -> {
            String[] texts = {"Pesanan sedang disiapkan", "Pesanan sudah siap", "Tunggu sebentar ya", "Silakan ambil pesanan"};
            PremiumDialogs.builder(this).setTitle("Pesan cepat").setItems(texts, (d, w) -> sendText(texts[w], false))
                    .setNegativeButton("Tutup", null).show();
        });
        quick.setPadding(dp(8), 0, dp(8), 0);
        page.addView(quick, new LinearLayout.LayoutParams(-2, -2));

        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(2),0,dp(2),dp(6));
        scroll.addView(messages, new ScrollView.LayoutParams(-1,-2));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(canRead()&&lastId>readThrough){
                main.removeCallbacks(reportVisibleRead);
                main.postDelayed(reportVisibleRead,1250);
            }
        });
        chatStyle.overflow(head, quick, query -> new JSONObject(get(chatEndpoint() + query)), messages);
        page.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(dp(5),dp(4),dp(5),dp(4)); composer.setBackground(round(Color.WHITE,dp(18)));
        input = new EditText(this); input.setHint("Pesan ke driver…"); input.setMinHeight(dp(48)); input.setEnabled(false); input.setSingleLine(false); input.setMaxLines(4); input.setMinLines(1); input.setTextSize(compactScreen() ? 13 : 14); input.setTextColor(TEXT); input.setHintTextColor(Color.parseColor("#98A2B3")); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setBackground(stroke(Color.parseColor("#F8FBFF"),Color.parseColor("#DDE7F3"),dp(14))); input.setPadding(dp(12),dp(8),dp(12),dp(8));
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send = btn("Kirim");
        send.setMinWidth(dp(64));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-2, -2);
        sendParams.setMarginStart(dp(6));
        send.setEnabled(false);
        send.setOnClickListener(v -> sendText(input.getText().toString(), true));
        composer.addView(send, sendParams);
        page.addView(composer);
        applyChatInsets(page);
        MerchantResponsiveUi.onSafeSizeChanged(page, (width, height) -> {
            boolean compact = height < dp(260);
            quick.setVisibility(compact ? View.GONE : View.VISIBLE);
            input.setMaxLines(compact ? 2 : 4);
        });
    }

    private void applyChatInsets(LinearLayout page) {
        final int side = dp(Math.max(12, (screenWidthDp() - 840) / 2));
        final int baseTop = dp(8), baseBottom = dp(8);
        ViewCompat.setOnApplyWindowInsetsListener(page, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(bars.bottom, ime.bottom);
            v.setPadding(side + bars.left, baseTop + bars.top, side + bars.right, baseBottom + bottomInset);
            // Weighted message area menyusut otomatis; composer selalu berada tepat di atas keyboard.
            if (input != null && input.hasFocus() && nearBottom()) scroll.post(this::scrollToLatest);
            return insets;
        });
        MerchantWindowInsets.requestWhenAttached(page);
        scroll.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int previousHeight = oldB - oldT;
            if (previousHeight > 0 && b - t != previousHeight && input.hasFocus()
                    && messages.getHeight() - scroll.getScrollY() - previousHeight < dp(80)) {
                scroll.post(this::scrollToLatest);
            }
        });
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        focusedSince=focused?android.os.SystemClock.elapsedRealtime():0;
        main.removeCallbacks(reportVisibleRead);
        if(focused&&lastId>readThrough)main.postDelayed(reportVisibleRead,1250);
    }

    private boolean nearBottom(){
        return scroll!=null&&messages!=null&&messages.getHeight()-scroll.getScrollY()-scroll.getHeight()<dp(80);
    }

    private void scrollToLatest() {
        scroll.scrollTo(0, messages.getBottom());
    }
    private boolean canRead(){
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        return !stopped&&hasWindowFocus()&&nearBottom()&&power!=null&&power.isInteractive();
    }

    private String chatEndpoint(){
        return BASE+"getMerchantDriverChat.php?chat_ui=1&order_id="+enc(orderId)+"&order_db_id="+enc(orderDbId);
    }

    private String readParams(int through,long visible){
        return through<=0?"":"&mark_read=1&read_through_id="+through+"&visible_ms="+visible;
    }

    private void load(boolean initial) {
        if (loading) { reloadAfterCurrent=true; return; }
        if (orderId.isEmpty() && orderDbId.isEmpty()) return;
        reloadAfterCurrent = false;
        loading = true;
        int cursor=lastId;
        String receiptIds=OrderChatStyle.receiptIds(receiptViews);
        long visible=focusedSince==0?0:android.os.SystemClock.elapsedRealtime()-Math.max(focusedSince,latestShownAt);
        int mark=canRead()&&visible>=1200&&cursor>readThrough?cursor:0;
        java.util.concurrent.Future<?> readTask = MerchantNetworkExecutor.executeRead(this, "driver-chat:" + orderDbId, () -> {
            try {
                String url=chatEndpoint()+"&last_id="+cursor+"&receipt_ids="+receiptIds+readParams(mark,visible);
                JSONObject r = new JSONObject(get(url));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if(r.optBoolean("success"))readThrough=Math.max(readThrough,mark);
                    apply(r,initial);
                });
            } catch(Exception e) { if(initial) runOnUiThread(() -> status.setText("Koneksi chat belum tersedia • akan mencoba lagi")); }
            finally {
                main.post(() -> {
                    loading = false;
                    if (reloadAfterCurrent && !stopped) main.postDelayed(reload, 50L);
                });
            }
        });
        if (readTask == null) loading=false;
    }

    private void apply(JSONObject r, boolean initial) {
        String assignedDriver = safe(r.optString("driver_name")).trim();
        chatOpen=r.optBoolean("success",false)&&!r.optBoolean("ended",false)&&!assignedDriver.isEmpty();
        callDriver.setEnabled(chatOpen);callDriver.setAlpha(chatOpen?1f:.45f);quick.setEnabled(chatOpen);
        input.setEnabled(chatOpen);send.setEnabled(chatOpen&&!sending);
        if(!assignedDriver.isEmpty()){driverName=assignedDriver;driverTitle.setText(driverName);}
        if (!r.optBoolean("success",false)) { status.setText(r.optString("message","Chat belum tersedia")); return; }
        status.setText(r.optBoolean("ended",false)?"Riwayat pesanan":"Pesanan #"+first(orderId,orderDbId));
        boolean wasAtBottom=nearBottom();
        int priorId=lastId;
        JSONArray a=r.optJSONArray("messages");
        int received = a == null ? 0 : a.length();
        if (received > 0) quietPolls = 0; else quietPolls = Math.min(10, quietPolls + 1);
        if(a!=null) for(int i=0;i<a.length();i++){ JSONObject m=a.optJSONObject(i); if(m==null||m.optInt("id",0)<=lastId)continue; addMessage(m); lastId=Math.max(lastId,m.optInt("id",0)); }
        JSONArray receipts=r.optJSONArray("receipt_updates");
        if(receipts!=null)for(int i=0;i<receipts.length();i++){
            JSONObject update=receipts.optJSONObject(i);
            TextView receipt=update==null?null:receiptViews.get(update.optInt("id"));
            if(receipt!=null)chatStyle.receipt(receipt,update,true);
        }
        chatStyle.profile(r,driverName,getIntent().getStringExtra("participant_photo"));
        boolean addedCall=chatStyle.mergeCalls(messages,r);
        if (addedCall && emptyConversation!=null) messages.removeView(emptyConversation);
        if(lastId>priorId)latestShownAt=android.os.SystemClock.elapsedRealtime();
        if(!stopped&&hasWindowFocus()&&lastId>readThrough){
            main.removeCallbacks(reportVisibleRead);
            main.postDelayed(reportVisibleRead,1250);
        }
        if(initial && messages.getChildCount()==0){
            emptyConversation=tv("Belum ada pesan. Ketik pesan ke driver.",12,MUTED,false);
            emptyConversation.setGravity(Gravity.CENTER);
            emptyConversation.setPadding(dp(20),dp(40),dp(20),dp(20));
            messages.addView(emptyConversation);
        }
        if(r.optBoolean("ended",false)){ input.setEnabled(false); send.setEnabled(false); }
        if(initial||(wasAtBottom&&(lastId>priorId||addedCall)))scroll.post(this::scrollToLatest);
    }

    private void addMessage(JSONObject message){
        if (emptyConversation!=null) {
            messages.removeView(emptyConversation);
            emptyConversation=null;
        }
        boolean mine="customer".equalsIgnoreCase(message.optString("sender_type",""));
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(mine?Gravity.END:Gravity.START);
        LinearLayout bubble = chatStyle.addMessageBubble(wrapper, message, mine);
        TextView body = tv(message.optString("message", ""), 14, mine ? Color.WHITE : TEXT, false);
        body.setGravity(Gravity.START);
        body.setSingleLine(false);
        body.setHorizontallyScrolling(false);
        body.setMaxLines(Integer.MAX_VALUE);
        body.setEllipsize(null);
        bubble.addView(body, new LinearLayout.LayoutParams(-2, -2));
        TextView timestamp=tv("",10,MUTED,false);
        chatStyle.receipt(timestamp,message,mine);
        chatStyle.appendMeta(bubble,timestamp);
        if(mine)receiptViews.put(message.optInt("id",0),timestamp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(2),0,dp(2));
        messages.addView(wrapper,lp);
    }

    private void sendText(String raw, boolean fromComposer) {
        String text = safe(raw).trim();
        if (text.isEmpty() || sending || !chatOpen) return;
        if (text.codePointCount(0, text.length()) > 500) {
            toast("Pesan maksimal 500 karakter.");
            return;
        }
        sending = true;
        send.setEnabled(false);
        boolean queued = MerchantNetworkExecutor.executeWrite("chat-send:" + orderDbId + ":" + Integer.toHexString(text.hashCode()), () -> {
            try {
                JSONObject p=new JSONObject();p.put("order_id",orderId);p.put("order_db_id",orderDbId);p.put("message",text);
                JSONObject r=new JSONObject(postJson(BASE+"sendMerchantDriverChat.php",p));
                runOnUiThread(() -> {
                    if (!r.optBoolean("success", false)) {
                        toast(r.optString("message", "Pesan gagal dikirim"));
                        return;
                    }
                    if (fromComposer && raw.contentEquals(input.getText())) input.setText("");
                    if (loading || stopped) reloadAfterCurrent = true;
                    else load(false);
                });
            }catch(Exception e){runOnUiThread(()->toast("Koneksi gagal. Pesan belum dikirim."));}
            finally{runOnUiThread(()->{sending=false;send.setEnabled(chatOpen);});}
        });
        if (!queued) {
            sending = false;
            send.setEnabled(chatOpen);
            toast("Koneksi sibuk. Coba kirim lagi.");
        }
    }

    private String first(String... v){ if(v!=null)for(String s:v)if(s!=null&&!s.trim().isEmpty())return s.trim();return ""; }
    private String safe(String s){return s==null?"":s;}
}
