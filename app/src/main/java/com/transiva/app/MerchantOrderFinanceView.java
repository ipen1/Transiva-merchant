package com.transiva.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        long merchantNet = n(order, "merchant_receivable_total", "merchant_net_revenue");
        long grossup = n(order, "merchant_grossup_fee");
        long merchantDiscount = n(order, "merchant_discount_total");
        long voucher = n(order, "customer_voucher_discount", "voucher_discount");
        long customerTotal = n(order, "customer_order_total", "price", "total", "grand_total");

        // Fallback untuk server lama: total makanan tetap dipisah dari ongkir.
        if (foodTotal <= 0 && customerTotal > 0) foodTotal = Math.max(0, customerTotal - deliveryFee);
        if (merchantNet <= 0 && foodTotal > 0) merchantNet = Math.max(0, foodTotal - grossup);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 13), dp(a, 12), dp(a, 13), dp(a, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(a, 10), 0, dp(a, 8));
        card.setLayoutParams(lp);
        card.setBackground(a.round(Color.parseColor("#F8FAFC"), dp(a, 14)));

        card.addView(text(a, "Rincian Pembayaran Merchant", 14, Color.parseColor("#102A43"), true));
        card.addView(row(a, "Total menu customer (termasuk gross-up)", money(foodTotal), false));
        if (grossup > 0) card.addView(row(a, "Gross-up milik Transiva", "- " + money(grossup), false));
        if (merchantDiscount > 0) card.addView(row(a, "Diskon merchant (sudah diterapkan)", money(merchantDiscount), false));
        card.addView(divider(a));
        card.addView(row(a, "TOTAL DITERIMA MERCHANT", money(merchantNet), true));

        TextView netInfo = text(a, "✓ Total diterima merchant = harga bersih makanan/opsi tanpa gross-up dan tanpa ongkir.", 11, Color.parseColor("#137333"), true);
        netInfo.setPadding(0, dp(a, 5), 0, 0);
        card.addView(netInfo);

        if (deliveryFee > 0) {
            card.addView(divider(a));
            card.addView(row(a, "Ongkir customer", money(deliveryFee), false));
            TextView shipInfo = text(a, "Ongkir adalah hak pengantaran/driver dan tidak dihitung ke pendapatan merchant.", 11, Color.parseColor("#667085"), false);
            card.addView(shipInfo);
        }
        if (voucher > 0) card.addView(row(a, "Voucher / subsidi Transiva", "- " + money(voucher), false));
        if (customerTotal > 0) card.addView(row(a, "Total akhir customer", money(customerTotal), false));

        parent.addView(card);
    }

    private static LinearLayout row(MerchantBaseActivity a, String label, String value, boolean strong) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(a, 4), 0, dp(a, 4));
        TextView l = text(a, label, strong ? 14 : 12, strong ? Color.parseColor("#102A43") : Color.parseColor("#667085"), strong);
        TextView v = text(a, value, strong ? 15 : 12, strong ? Color.parseColor("#137333") : Color.parseColor("#344054"), strong);
        v.setGravity(Gravity.END);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(v);
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
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
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
