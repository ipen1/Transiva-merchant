package com.transiva.app;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Shared compact report components. Values always come from the existing server reports. */
final class MerchantReportUi {
 final MerchantBaseActivity a;
 MerchantReportUi(MerchantBaseActivity a){this.a=a;}
 LinearLayout box(){LinearLayout b=new LinearLayout(a);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));b.setBackground(a.stroke(Color.WHITE,0xFFE1EAF5,a.dp(18)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=a.dp(10);b.setLayoutParams(p);return b;}
 TextView heading(LinearLayout root,String title,Runnable refresh){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(a.title(title),new LinearLayout.LayoutParams(0,-2,1));TextView reload=a.tv("↻",26,a.BLUE,true);reload.setGravity(Gravity.CENTER);reload.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,26);reload.setContentDescription("Perbarui "+title);reload.setBackground(a.round(a.SOFT_BLUE,a.dp(14)));reload.setOnClickListener(v->refresh.run());row.addView(reload,new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));root.addView(row);return reload;}
 TextView metric(LinearLayout row,String name,String initial){LinearLayout b=box();b.addView(a.tv(name,11,a.MUTED,false));TextView value=a.tv(initial,17,a.NAVY,true);value.setPadding(0,a.dp(4),0,0);b.addView(value);LinearLayout.LayoutParams lp=a.actionLp();lp.height=row.getOrientation()==LinearLayout.HORIZONTAL?-1:-2;lp.setMargins(a.dp(3),0,a.dp(3),a.dp(8));row.addView(b,lp);return value;}
 TextView section(LinearLayout root,String label){TextView t=a.label(label);t.setPadding(a.dp(2),a.dp(6),a.dp(2),a.dp(8));root.addView(t);return t;}
 void line(LinearLayout parent,String label,String value,boolean strong){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,a.dp(6),0,a.dp(6));TextView left=a.tv(label,12,a.MUTED,false);left.setPadding(0,0,a.dp(8),0);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));row.addView(a.tv(value,13,a.NAVY,strong));parent.addView(row);}
 static String payment(String raw){String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);if(s.equals("cash")||s.equals("tunai"))return "Tunai";if(Arrays.asList("balance","saldo","wallet","transpay","transiva_pay","transivapay").contains(s))return "Transiva Pay";return s.isEmpty()?"Metode belum tersedia":raw;}
 static String date(String raw,boolean withTime){try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);Date d=in.parse(raw.length()==10?raw+" 00:00:00":raw);return new SimpleDateFormat(withTime?"dd MMM · HH.mm":"dd MMM",new Locale("id","ID")).format(d);}catch(Exception ignored){return raw==null?"":raw;}}
}
