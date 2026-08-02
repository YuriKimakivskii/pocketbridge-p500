package ua.pocketbridge.lgp500;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class NativeCache {
    private static final String BOOTSTRAP_PREFIX = "native_bootstrap_";
    private static final String PROFILE_PREFIX = "native_profile_";
    private static final String ETAG_PREFIX = "native_etag_";
    private static final String SAVED_PREFIX = "native_saved_";
    private static final int MAX_BOOTSTRAP_CHARS = 48000;

    private NativeCache() { }

    static JSONObject loadBootstrap(Context context) {
        String raw = AppPreferences.get(context).getString(BOOTSTRAP_PREFIX + activeId(context), "");
        if (raw.length() == 0) return null;
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return null; }
    }

    static void saveBootstrap(Context context, JSONObject value) {
        if (value == null) return;
        String raw = value.toString();
        if (raw.length() > MAX_BOOTSTRAP_CHARS) return;
        AppPreferences.get(context).edit()
                .putString(BOOTSTRAP_PREFIX + activeId(context), raw)
                .putLong(SAVED_PREFIX + activeId(context), System.currentTimeMillis())
                .apply();
    }


    static String bootstrapEtag(Context context) {
        return AppPreferences.get(context).getString(ETAG_PREFIX + activeId(context), "");
    }

    static void bootstrapEtag(Context context, String value) {
        AppPreferences.get(context).edit()
                .putString(ETAG_PREFIX + activeId(context), value == null ? "" : value)
                .apply();
    }

    static long bootstrapAgeMs(Context context) {
        long saved = AppPreferences.get(context).getLong(SAVED_PREFIX + activeId(context), 0L);
        return saved <= 0L ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - saved);
    }

    static String selectedProfile(Context context) {
        return AppPreferences.get(context).getString(PROFILE_PREFIX + activeId(context), "");
    }

    static void selectedProfile(Context context, String value) {
        AppPreferences.get(context).edit()
                .putString(PROFILE_PREFIX + activeId(context), value == null ? "" : value)
                .apply();
    }

    static void clear(Context context) {
        SharedPreferences.Editor editor = AppPreferences.get(context).edit();
        editor.remove(BOOTSTRAP_PREFIX + activeId(context));
        editor.remove(PROFILE_PREFIX + activeId(context));
        editor.remove(ETAG_PREFIX + activeId(context));
        editor.remove(SAVED_PREFIX + activeId(context));
        editor.apply();
    }

    private static String activeId(Context context) {
        PcProfile profile = PcProfileStore.active(context);
        return profile == null || profile.id == null ? "none" : profile.id;
    }
}
