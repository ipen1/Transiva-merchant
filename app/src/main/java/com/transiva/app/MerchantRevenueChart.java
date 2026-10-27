package com.transiva.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;

/** Fourteen daily bars in one bounded viewport. Touch selects a day; zero revenue stays zero. */
final class MerchantRevenueChart extends View {
 interface Selection {void select(JSONObject day);}
 private JSONArray days=new JSONArray();private int selected=-1;private Selection listener;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 MerchantRevenueChart(Context c){super(c);setMinimumHeight(dp(160));setContentDescription("Grafik pendapatan harian. Rincian seluruh hari tersedia di bawah grafik.");}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void setDays(JSONArray days,Selection listener){this.days=days==null?new JSONArray():days;this.listener=listener;selected=this.days.length()-1;invalidate();if(selected>=0&&listener!=null)listener.select(this.days.optJSONObject(selected));}
 @Override protected void onDraw(Canvas c){super.onDraw(c);boolean dark=MerchantAppSettings.isDarkMode(getContext());int n=days.length();if(n==0)return;float left=dp(3),right=getWidth()-dp(3),top=dp(12),bottom=getHeight()-dp(27),slot=(right-left)/n;long max=1;for(int i=0;i<n;i++){JSONObject d=days.optJSONObject(i);if(d!=null)max=Math.max(max,d.optLong("revenue"));}
  p.setStrokeWidth(dp(1));p.setColor(dark?0xFF2B4056:0xFFE2ECF7);for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;c.drawLine(left,y,right,y,p);}
  for(int i=0;i<n;i++){JSONObject day=days.optJSONObject(i);long amount=day==null?0:Math.max(0,day.optLong("revenue"));float height=amount==0?dp(2):(bottom-top)*amount/(float)max;float width=Math.max(dp(2),slot*.58f),x=left+slot*i+(slot-width)/2;p.setColor(amount==0?(dark?0xFF344B63:0xFFD9E6F4):(i==selected?0xFF17B6EE:0xFF147DFF));c.drawRoundRect(x,bottom-height,x+width,bottom,dp(4),dp(4),p);}
  p.setColor(dark?0xFFA9B8CC:0xFF60758E);p.setTextSize(dp(10));p.setTypeface(Typeface.DEFAULT);int[] marks={0,n/2,n-1};for(int i:marks){JSONObject d=days.optJSONObject(i);if(d==null)continue;String label=MerchantReportUi.date(d.optString("date"),false);p.setTextAlign(i==0?Paint.Align.LEFT:i==n-1?Paint.Align.RIGHT:Paint.Align.CENTER);c.drawText(label,i==0?left:i==n-1?right:left+slot*(i+.5f),getHeight()-dp(5),p);}
 }
 @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;if(event.getActionMasked()==MotionEvent.ACTION_UP){int n=days.length();if(n>0){selected=Math.min(n-1,Math.max(0,(int)(event.getX()/Math.max(1,getWidth())*n)));if(listener!=null)listener.select(days.optJSONObject(selected));invalidate();performClick();}return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}
