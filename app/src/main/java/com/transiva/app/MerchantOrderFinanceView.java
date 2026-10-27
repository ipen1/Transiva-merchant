package com.transiva.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Tampilan finance khusus merchant TransFood.
 * Ongkir dan gross-up sengaja tidak pernah digabung sebagai pendapatan merchant.
 */
final class MerchantOrderFinanceView {
    private MerchantOrderFinanceView() {}

    static void attach(MerchantBaseActivity a, LinearLayout parent, JSONObject order) {
        long foodTotal = n(order, "customer_food_total_with_grossup", "customer_food_total", "food_total");
        long deliveryFee = n(order, "customer_delivery_fee", "delivery_fee");
        long serverMerchantNet = n(order, "merchant_receivable_total", "merchant_net_revenue");
        long grossup = n(order, "merchant_grossup_fee");
        long merchantDiscount = n(order, "merchant_discount_total");
        long voucher = n(order, "customer_voucher_discount", "voucher_discount");
        long customerTotal = n(order, "customer_order_total", "price", "total", "grand_total");

        // Sumber kebenaran merchant adalah snapshot harga bersih setiap item, bukan grand total customer.
        // Ini mencegah gross-up ikut masuk lagi ke pendapatan merchant bila field agregat server lama keliru.
        long itemMerchantNet = merchantNetFromItems(order);
        long itemCustomerFood = customerFoodFromItems(order);
        long itemGrossup = grossupFromItems(order);

        if (foodTotal <= 0 && itemCustomerFood > 0) foodTotal = itemCustomerFood;
        if (foodTotal <= 0 && customerTotal > 0) foodTotal = Math.max(0, customerTotal - deliveryFee);
        if (grossup <= 0 && itemGrossup > 0) grossup = itemGrossup;

        long merchantNet;
        if (itemMerchantNet > 0) {
            merchantNet = itemMerchantNet;
        } else if (foodTotal > 0 && grossup > 0) {
            merchantNet = Math.max(0, foodTotal - grossup);
        } else {
            merchantNet = Math.max(0, serverMerchantNet);
        }

        // Guard terakhir: gross-up tidak boleh pernah menjadi hak merchant.
        if (foodTotal > 0 && grossup > 0) {
            merchantNet = Math.min(merchantNet, Math.max(0, foodTotal - grossup));
        }
        if (grossup <= 0 && foodTotal > merchantNet && merchantNet > 0) {
            grossup = foodTotal - merchantNet;
        }

        boolean dark=MerchantAppSettings.isDarkMode(a);
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,6));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,8),0,dp(a,6));card.setLayoutParams(lp);
        card.setBackground(a.round(Color.parseColor(dark?"#17334E":"#EDF5FF"),dp(a,12)));
        card.addView(row(a,"Pendapatan toko",money(merchantNet),true));
        TextView explanation=text(a,"Bersih untuk toko, tanpa biaya layanan dan ongkir.",11,a.MUTED,false);card.addView(explanation);
        LinearLayout details=new LinearLayout(a);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(android.view.View.GONE);
        details.addView(row(a,"Harga menu di aplikasi",money(foodTotal),false));
        if(grossup>0)details.addView(row(a,"Biaya layanan harga menu","− "+money(grossup),false));
        if(merchantDiscount>0)details.addView(row(a,"Diskon toko (sudah dihitung)",money(merchantDiscount),false));
        if(deliveryFee>0)details.addView(row(a,"Ongkir untuk kurir",money(deliveryFee),false));
        if(voucher>0)details.addView(row(a,"Voucher Transiva","− "+money(voucher),false));
        if(customerTotal>0)details.addView(row(a,"Total pembayaran pelanggan",money(customerTotal),false));
        TextView toggle=text(a,"Lihat rincian pembayaran ▾",12,dark?Color.parseColor("#79BCFF"):a.BLUE,true);
        toggle.setMinHeight(dp(a,48));toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setOnClickListener(v->{boolean show=details.getVisibility()!=android.view.View.VISIBLE;details.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);toggle.setText(show?"Tutup rincian pembayaran ▴":"Lihat rincian pembayaran ▾");});
        card.addView(toggle);card.addView(details);
        parent.addView(card);
    }

    private static LinearLayout row(MerchantBaseActivity a, String label, String value, boolean strong) {
        LinearLayout r = new LinearLayout(a);
        boolean stack=a.stackControls();r.setOrientation(stack?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(a,4),0,dp(a,4));
        TextView l=text(a,label,strong?13:12,a.MUTED,strong);
        TextView v=text(a,value,strong?18:12,strong?a.NAVY:a.TEXT,strong);
        v.setGravity(stack?Gravity.START:Gravity.END);
        r.addView(l,new LinearLayout.LayoutParams(stack?-1:0,-2,stack?0:1));
        // Give long currency amounts a bounded share so labels can wrap on small screens.
        r.addView(v,new LinearLayout.LayoutParams(stack?-1:0,-2,stack?0:1));
        return r;
    }

    private static TextView divider(MerchantBaseActivity a) {
        TextView d = new TextView(a);
        d.setBackgroundColor(Color.parseColor("#E4E7EC"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 1));
        lp.setMargins(0, dp(a, 7), 0, dp(a, 7));
        d.setLayoutParams(lp);
        return d;
    }

    private static TextView text(MerchantBaseActivity a, String s, int sp, int color, boolean bold) {
        return a.tv(s,sp,color,bold);
    }

    private static long merchantNetFromItems(JSONObject order) {
        JSONArray items = order.optJSONArray("items");
        if (items == null || items.length() == 0) return 0;
        long total = 0;
        boolean found = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            int qty = Math.max(1, it.optInt("qty", it.optInt("quantity", 1)));
            long merchantBase = itemN(it, "merchant_price", "merchant_unit_price", "net_merchant_price");
            long optionTotal = itemN(it, "option_total", "merchant_option_total");
            if (merchantBase > 0 || optionTotal > 0) {
                total += Math.max(0, merchantBase + optionTotal) * qty;
                found = true;
            }
        }
        return found ? total : 0;
    }

    private static long customerFoodFromItems(JSONObject order) {
        JSONArray items = order.optJSONArray("items");
        if (items == null || items.length() == 0) return 0;
        long total = 0;
        boolean found = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            int qty = Math.max(1, it.optInt("qty", it.optInt("quantity", 1)));
            long subtotal = itemN(it, "subtotal", "customer_subtotal");
            if (subtotal > 0) {
                total += subtotal;
                found = true;
            } else {
                long unit = itemN(it, "price", "unit_price", "customer_unit_price");
                if (unit > 0) {
                    total += unit * qty;
                    found = true;
                }
            }
        }
        return found ? total : 0;
    }

    private static long grossupFromItems(JSONObject order) {
        JSONArray items = order.optJSONArray("items");
        if (items == null || items.length() == 0) return 0;
        long total = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            int qty = Math.max(1, it.optInt("qty", it.optInt("quantity", 1)));
            long explicit = itemN(it, "grossup_fee", "gross_up", "grossup");
            if (explicit > 0) {
                total += explicit * qty;
                continue;
            }
            long unit = itemN(it, "price", "unit_price", "customer_unit_price");
            long merchantBase = itemN(it, "merchant_price", "merchant_unit_price", "net_merchant_price");
            long optionTotal = itemN(it, "option_total", "merchant_option_total");
            if (unit > 0 && merchantBase > 0) {
                total += Math.max(0, unit - merchantBase - optionTotal) * qty;
            }
        }
        return total;
    }

    private static long itemN(JSONObject o, String... keys) {
        for (String key : keys) {
            if (!o.has(key) || o.isNull(key)) continue;
            try {
                if (o.opt(key) instanceof Number) return Math.max(0, Math.round(((Number) o.opt(key)).doubleValue()));
                String raw = o.optString(key, "").trim().replace(".", "").replace(",", ".");
                if (!raw.isEmpty()) return Math.max(0, Math.round(Double.parseDouble(raw)));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static long n(JSONObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.isNull(key)) {
                long v = o.optLong(key, Long.MIN_VALUE);
                if (v != Long.MIN_VALUE) return Math.max(0, v);
                try { return Math.max(0, Math.round(Double.parseDouble(o.optString(key, "0")))); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static String money(long n) {
        return "Rp " + NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(Math.max(0, n));
    }

    private static int dp(MerchantBaseActivity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
