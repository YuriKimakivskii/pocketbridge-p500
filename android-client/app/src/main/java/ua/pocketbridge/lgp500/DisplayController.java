package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.view.WindowManager;

final class DisplayController {
    private DisplayController() { }

    static void applyBrightness(Activity activity, int percent) {
        int clamped = Math.max(1, Math.min(100, percent));
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = clamped / 100.0f;
        activity.getWindow().setAttributes(attributes);
    }
}
