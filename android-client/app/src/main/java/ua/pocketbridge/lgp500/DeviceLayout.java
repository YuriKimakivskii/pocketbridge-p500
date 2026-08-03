package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.util.DisplayMetrics;

/** Selects the UI/runtime profile without dropping Android 2.3 support. */
final class DeviceLayout {
    static final String AUTO = "auto";
    static final String P500 = "p500";
    static final String MODERN = "modern";

    private DeviceLayout() { }

    static String preference(Context context) {
        String value = AppPreferences.get(context).getString(AppPreferences.DEVICE_LAYOUT, AUTO);
        if (!P500.equals(value) && !MODERN.equals(value)) return AUTO;
        return value;
    }

    static boolean isModern(Context context) {
        String selected = preference(context);
        if (MODERN.equals(selected)) return true;
        if (P500.equals(selected)) return false;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density <= 0f ? 1f : metrics.density;
        float widthDp = metrics.widthPixels / density;
        float heightDp = metrics.heightPixels / density;
        float shortDp = Math.min(widthDp, heightDp);
        float longDp = Math.max(widthDp, heightDp);
        return Build.VERSION.SDK_INT >= 19 && shortDp >= 360f && longDp >= 640f;
    }

    static String apiKey(Context context) {
        return isModern(context) ? MODERN : P500;
    }

    static int nativeCoreLayout(Context context) {
        return isModern(context) ? R.layout.activity_native_core_modern : R.layout.activity_native_core;
    }

    static void applyOrientation(Activity activity) {
        if (isModern(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    static String displayName(Context context) {
        return isModern(context) ? "Redmi / HD" : "LG P500";
    }
}
