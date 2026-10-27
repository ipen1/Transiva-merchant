package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

/** Starts only after attachment; removes callbacks when hidden/detached. No network polling. */
final class MerchantCountdownView extends TextView {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final long target,duration;
    private final String prefix,finished;
    private long displayed=-1;
    private Runnable completion;
    private boolean completed;
    MerchantCountdownView(Context context,long target,long duration,String prefix,String finished){
        super(context);this.target=target;this.duration=Math.max(1,duration);this.prefix=prefix;this.finished=finished;
        setTextSize(16);setTypeface(Typeface.create("monospace",Typeface.BOLD));setMinHeight(dp(36));
        setPadding(0,dp(6),0,dp(6));tick(false);
    }
    void onComplete(Runnable action){completion=action;}
    private final Runnable update=()->tick(true);
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restart();}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(update);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(handler!=null){handler.removeCallbacks(update);if(visibility==View.VISIBLE&&isAttachedToWindow())restart();}}
    private void restart(){handler.removeCallbacks(update);tick(true);}
    private void tick(boolean schedule){
        long left=target<=0?0:Math.max(0,target-MerchantServerClock.now());
        long seconds=(left+999)/1000;
        if(seconds!=displayed){displayed=seconds;setText(target<=0?"Waktu belum tersedia":seconds>0?String.format(Locale.US,"%s %02d:%02d",prefix,seconds/60,seconds%60):finished);}
        boolean dark=MerchantAppSettings.isDarkMode(getContext());
        float remaining=Math.max(0f,Math.min(1f,left/(float)duration));
        int green=Color.parseColor(dark?"#68DAA1":"#16734C"),amber=Color.parseColor(dark?"#FFD48A":"#946200"),red=Color.parseColor(dark?"#FF9994":"#BF3535");
        int color=remaining>.5f?blend(amber,green,(remaining-.5f)*2):blend(red,amber,remaining*2);
        if(target<=0)color=dark?MerchantAppSettings.DARK_MUTED:Color.parseColor("#667085");
        if(getCurrentTextColor()!=color)setTextColor(color);
        if(target>0&&left==0&&!completed&&schedule){completed=true;if(completion!=null)handler.post(completion);}
        if(schedule&&left>0&&isAttachedToWindow()&&getWindowVisibility()==View.VISIBLE)handler.postDelayed(update,Math.max(1,(left-1)%1000+1));
    }
    private static int blend(int from,int to,float mix){return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*mix),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*mix),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*mix));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
