package com.transiva.app;
import android.app.Activity;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.widget.*;
final class MerchantLoginUi{private MerchantLoginUi(){}
static TextView label(Activity a,String v){TextView t=new TextView(a);t.setText(v);t.setTextSize(12);t.setTextColor(Color.parseColor("#667085"));t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);t.setPadding(dp(a,2),dp(a,8),0,dp(a,6));return t;}
static GradientDrawable round(String fill,int radius,Activity a){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(a,radius));return g;}
static GradientDrawable roundStroke(String fill,String stroke,int radius,int width,Activity a){GradientDrawable g=round(fill,radius,a);g.setStroke(dp(a,width),Color.parseColor(stroke));return g;}
static GradientDrawable gradient(String start,String end,int radius,Activity a){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor(start),Color.parseColor(end)});g.setCornerRadius(dp(a,radius));return g;}
static int dp(Activity a,int v){return (int)(v*a.getResources().getDisplayMetrics().density+.5f);} }
