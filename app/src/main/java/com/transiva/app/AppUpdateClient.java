package com.transiva.app;

import android.content.Context;
import android.os.Build;

/** Version helper. APK delivery is owned by Google Play/build pipeline, never by the app itself. */
public final class AppUpdateClient {
    private AppUpdateClient() {}
    public static int installedVersionCode(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 28) return (int)Math.min(Integer.MAX_VALUE, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode());
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }
    public static String installedVersionName(Context c) {
        try { String v=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; return v==null?"-":v; }
        catch(Exception e){ return "-"; }
    }
}
