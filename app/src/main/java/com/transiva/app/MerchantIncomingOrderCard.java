package com.transiva.app;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class MerchantIncomingOrderCard {
    interface Decision {
        void submit(String status,String reason);
    }

    private MerchantIncomingOrderCard() {}

    static LinearLayout build(MerchantBaseActivity a,JSONObject order,boolean enabled,Decision decision,Runnable expiredRefresh) {
        LinearLayout card=new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(12));
        card.setBackground(a.stroke(Color.WHITE,0xFFB6D8F9,a.dp(16)));
        card.setElevation(a.dp(1));
        String display=order.optString("order_id",order.optString("id"));
        String id=order.optString("id",display);
        LinearLayout outlet=a.row();
        outlet.setGravity(Gravity.CENTER_VERTICAL);
        outlet.addView(a.tv(order.optString("restaurant_name"),14,a.BLUE,true),new LinearLayout.LayoutParams(0,-2,1));
        String suffix=display.length()>8?display.substring(display.length()-8):display;
        TextView number=a.tv("#"+suffix,12,a.MUTED,true);
        number.setContentDescription("Nomor pesanan "+display);
        outlet.addView(number,new LinearLayout.LayoutParams(-2,-2));
        card.addView(outlet);
        card.addView(a.tv("Pesanan baru",21,a.NAVY,true));
        MerchantCountdownView timer=addTimer(a,card,order);
        String customer=order.optString("customer_name",order.optString("customer_username"));
        if (!customer.isEmpty()) card.addView(a.tv("Untuk "+customer,14,a.TEXT,true));
        JSONArray items=order.optJSONArray("items");
        if (items==null || items.length()==0) {
            card.addView(a.tv("Rincian menu belum tersedia. Buka Aktivitas untuk memeriksa pesanan.",14,a.TEXT,false));
            enabled=false;
        } else {
            for (int i=0;i<items.length();i++) {
                JSONObject item=items.optJSONObject(i);
                if (item==null) continue;
                String name=item.optString("name",item.optString("menu_name","Menu"));
                int qty=Math.max(1,item.optInt("qty",item.optInt("quantity",1)));
                addTinted(a,card,a.tv(qty+" × "+name,21,a.TEXT,true),0xFFE8ECFF,0xFF263B62);
                JSONArray choices=item.optJSONArray("selected_options");
                boolean shown=false;
                if (choices!=null) for (int j=0;j<choices.length();j++) {
                    JSONObject option=choices.optJSONObject(j);
                    if (option==null || option.optString("name").isEmpty()) continue;
                    String nameLabel=option.optString("type").contains("topping")?"Topping":option.optString("label","Pilihan");
                    TextView choice=a.tv(nameLabel+" · "+option.optString("name"),17,
                            MerchantAppSettings.isDarkMode(a)?0xFFFFD697:0xFF693900,true);
                    addTinted(a,card,choice,0xFFFFEFCB,0xFF493321);
                    shown=true;
                }
                String legacy=item.optString("variant",item.optString("variant_name",item.optString("option",item.optString("options"))));
                if (!shown && !legacy.isEmpty()) {
                    TextView choice=a.tv("Pilihan · "+legacy,17,
                            MerchantAppSettings.isDarkMode(a)?0xFFFFD697:0xFF693900,true);
                    addTinted(a,card,choice,0xFFFFEFCB,0xFF493321);
                }
                String itemNote=item.optString("note",item.optString("item_note"));
                if (!itemNote.isEmpty()) card.addView(a.tv("Catatan menu · "+itemNote,15,a.TEXT,false));
            }
        }
        String note=order.optString("customer_note");
        if (!note.isEmpty()) card.addView(a.tv("Catatan pelanggan · "+note,15,a.TEXT,false));
        MerchantOrderFinanceView.attach(a,card,order);
        LinearLayout buttons=a.actionRow();
        Button accept=a.btn("✓  Terima pesanan");
        Button reject=rejectButton(a);
        accept.setEnabled(enabled && !expired(order));
        reject.setEnabled(enabled && !expired(order));
        timer.onComplete(()->{
            accept.setEnabled(false);
            reject.setEnabled(false);
            expiredRefresh.run();
        });
        accept.setOnClickListener(v->decision.submit("merchant_accepted",""));
        reject.setOnClickListener(v->showReject(a,reason->decision.submit("merchant_rejected",reason)));
        buttons.addView(accept,a.actionLp());
        buttons.addView(reject,a.actionLp());
        card.addView(buttons);
        String driver=order.optString("driver_name",order.optString("driver"));
        if (!driver.isEmpty()) MerchantDriverCommunication.attach(a,card,order,id,display,driver);
        return card;
    }

    static Button rejectButton(MerchantBaseActivity a) {
        boolean dark=MerchantAppSettings.isDarkMode(a);
        Button button=a.outlineBtn("Tolak");
        button.setTextColor(dark?0xFFFFA9BA:0xFF96284E);
        button.setBackground(a.stroke(dark?0xFF262030:Color.WHITE,0xFFCE7994,a.dp(12)));
        return button;
    }

    static void showReject(MerchantBaseActivity a,java.util.function.Consumer<String> decision) {
        String[] reasons={"Menu habis","Restoran terlalu ramai","Restoran akan segera tutup","Pesanan tidak dapat dibuat","Lainnya"};
        PremiumDialogs.builder(a).setTitle("Alasan tolak pesanan")
                .setItems(reasons,(dialog,which)->{
                    if (which<reasons.length-1) {
                        PremiumDialogs.builder(a).setTitle("Tolak pesanan?")
                                .setMessage(reasons[which]).setNegativeButton("Kembali",null)
                                .setPositiveButton("Tolak pesanan",(d,w)->decision.accept(reasons[which])).show();
                        return;
                    }
                    android.widget.EditText field=new android.widget.EditText(a);
                    field.setHint("Tulis alasan penolakan");
                    field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(250)});
                    android.app.AlertDialog custom=PremiumDialogs.builder(a).setTitle("Alasan penolakan")
                            .setView(field).setNegativeButton("Kembali",null)
                            .setPositiveButton("Tolak pesanan",null).create();
                    custom.setOnShowListener(d->{
                        PremiumDialogs.applyPremiumStyle(custom);
                        custom.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                            String text=field.getText().toString().trim();
                            if (text.isEmpty()) field.setError("Isi alasan penolakan.");
                            else { custom.dismiss(); decision.accept(text); }
                        });
                    });
                    custom.show();
                }).setNegativeButton("Kembali",null).show();
    }

    private static long deadline(JSONObject order) {
        long created=order.optLong("merchant_response_epoch")*1000L;
        if (created<=0) created=MerchantServerClock.createdAt(order);
        return created>0?created+300000L:0;
    }

    static boolean expired(JSONObject order) {
        long until=deadline(order);
        return until>0 && MerchantServerClock.now()>=until;
    }

    static MerchantCountdownView addTimer(MerchantBaseActivity a,LinearLayout box,JSONObject order) {
        MerchantCountdownView timer=new MerchantCountdownView(a,deadline(order),300000L,
                "Sisa konfirmasi","Waktu konfirmasi habis");
        timer.setTextSize(15);
        addTinted(a,box,timer,0xFFFFEDC3,0xFF423220);
        return timer;
    }

    static void addTinted(MerchantBaseActivity a,LinearLayout box,TextView value,int light,int dark) {
        value.setPadding(a.dp(10),a.dp(6),a.dp(10),a.dp(6));
        value.setBackground(a.round(MerchantAppSettings.isDarkMode(a)?dark:light,a.dp(10)));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        space.topMargin=a.dp(4);
        space.bottomMargin=a.dp(3);
        box.addView(value,space);
    }
}
