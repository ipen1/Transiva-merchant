package com.transiva.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit paired foreground/background colors, also inside the story window. */
final class KabarMitraUi {
 final Activity a; final int bg,ink,muted,blue=Color.rgb(11,120,255); final boolean dark;
 KabarMitraUi(Activity a){this.a=a;dark=MerchantAppSettings.isDarkMode(a);bg=Color.parseColor(dark?"#122235":"#FFFFFF");ink=Color.parseColor(dark?"#EEF5FF":"#133152");muted=Color.parseColor(dark?"#A9B8CC":"#60758E");}
 int dp(int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 LinearLayout box(){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(14));d.setStroke(dp(1),Color.parseColor(dark?"#2D435C":"#DAE8F8"));v.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(9));v.setLayoutParams(p);return v;}
 TextView text(String s,int size,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setPadding(0,dp(4),0,dp(4));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 Button button(String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blue,Color.rgb(25,157,234)});d.setCornerRadius(dp(14));b.setBackground(d);b.setMinHeight(dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(4));b.setLayoutParams(p);return b;}
 void image(ImageView v,String path){com.bumptech.glide.Glide.with(a).load(imageUrl(path)).into(v);}
 String imageUrl(String path){if(path.startsWith("https://"))return path;if(path.startsWith("http://"))return path.replaceFirst("http://","https://");return "https://transiva.my.id/"+(path.startsWith("uploads/mitra_stories/")?"server/":"")+path.replaceFirst("^/","");}
 void storyBar(LinearLayout parent,JSONArray stories,long serverTime){
  parent.addView(text("Kabar hari ini",18,true));
  if(stories.length()==0){parent.addView(text("Belum ada story aktif dari toko yang kamu ikuti.",13,false));return;}
  HorizontalScrollView sc=new HorizontalScrollView(a);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(a);sc.addView(row);Set<Integer> seen=new HashSet<>();long builtAt=SystemClock.elapsedRealtime();
  for(int i=0;i<stories.length();i++){JSONObject s=stories.optJSONObject(i);if(s==null||s.optLong("expires_at")<=serverTime||!seen.add(s.optInt("restaurant_id")))continue;
   int rid=s.optInt("restaurant_id");LinearLayout tile=new LinearLayout(a);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(5),dp(6),dp(5),dp(6));
   FrameLayout ring=new FrameLayout(a);GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blue,Color.rgb(20,200,218)});d.setCornerRadius(dp(34));ring.setBackground(d);ring.setPadding(dp(3),dp(3),dp(3),dp(3));
   ImageView img=new ImageView(a);img.setScaleType(ImageView.ScaleType.CENTER_CROP);GradientDrawable mask=new GradientDrawable();mask.setColor(bg);mask.setCornerRadius(dp(32));img.setBackground(mask);img.setClipToOutline(true);ring.addView(img,new FrameLayout.LayoutParams(-1,-1));image(img,s.optString("image"));tile.addView(ring,new LinearLayout.LayoutParams(dp(66),dp(66)));
   TextView name=text(s.optString("name","Mitra"),11,true);name.setMaxLines(2);name.setGravity(Gravity.CENTER);tile.addView(name,new LinearLayout.LayoutParams(dp(76),-2));tile.setOnClickListener(v->viewer(stories,rid,serverTime+(SystemClock.elapsedRealtime()-builtAt)/1000));row.addView(tile);
  }parent.addView(sc,new LinearLayout.LayoutParams(-1,-2));
 }
 static final class Avatar extends FrameLayout {
  final ImageView photo;
  Avatar(KabarMitraUi ui,String path,int size,boolean story){super(ui.a);setPadding(ui.dp(2),ui.dp(2),ui.dp(2),ui.dp(2));GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setColor(ui.bg);ring.setStroke(ui.dp(story?2:1),story?ui.blue:0xFF51647A);setBackground(ring);photo=new ImageView(ui.a);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(ui.bg);photo.setBackground(mask);photo.setClipToOutline(true);addView(photo,new FrameLayout.LayoutParams(-1,-1));if(path!=null&&!path.isEmpty())ui.image(photo,path);}
 }
 Avatar avatar(String path,int size,boolean story){return new Avatar(this,path,size,story);}
 void viewer(JSONArray all,int rid,long serverTime){new MerchantStoryViewer(this,all,rid,serverTime).open();}
}
