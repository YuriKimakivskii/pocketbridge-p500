package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Opens vendor/system screens needed for reliable background operation on MIUI. */
final class SystemSettingsHelper {
    private SystemSettingsHelper() { }

    static void openBattery(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                return;
            }
        } catch (Exception ignored) { }
        openAppDetails(activity);
    }

    static void openAutostart(Activity activity) {
        String[][] candidates = new String[][] {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.miui.securitycenter", "com.miui.powercenter.PowerSettings"},
                {"com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity"}
        };
        for (int i = 0; i < candidates.length; i++) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(candidates[i][0], candidates[i][1]));
                intent.putExtra("extra_pkgname", activity.getPackageName());
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) { }
        }
        Toast.makeText(activity, "Відкрий Автозапуск і дозволь PocketBridge", Toast.LENGTH_LONG).show();
        openAppDetails(activity);
    }

    static void openAppDetails(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception exception) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
