package ua.pocketbridge.lgp500;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherMode {
    private static final String ALIAS = "ua.pocketbridge.lgp500.HomeAlias";

    private LauncherMode() { }

    static void setEnabled(Context context, boolean enabled) {
        ComponentName component = new ComponentName(context, ALIAS);
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        context.getPackageManager().setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP);
    }

    static boolean isEnabled(Context context) {
        ComponentName component = new ComponentName(context, ALIAS);
        int state = context.getPackageManager().getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return true;
        }
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            return false;
        }
        return AppPreferences.get(context).getBoolean(AppPreferences.LAUNCHER_MODE, false);
    }
}
