package ua.pocketbridge.lgp500;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

final class AppPreferences {
    static final String FILE = "pocketbridge_remote";
    static final String HOST = "host";
    static final String PORT = "port";
    static final String TOKEN = "token";
    static final String FULLSCREEN = "fullscreen";
    static final String KEEP_SCREEN_ON = "keep_screen_on";
    static final String AUTO_RECONNECT = "auto_reconnect";
    static final String AUTO_START = "auto_start";
    static final String HARDWARE_KEYS = "hardware_keys";
    static final String LONG_PRESS_MEDIA = "long_press_media";
    static final String HAPTIC_FEEDBACK = "haptic_feedback";
    static final String SOUND_FEEDBACK = "sound_feedback";
    static final String PANEL_BRIGHTNESS = "panel_brightness";
    static final String IDLE_BRIGHTNESS = "idle_brightness";
    static final String IDLE_DIM_SECONDS = "idle_dim_seconds";
    static final String LAUNCHER_MODE = "launcher_mode";
    static final String LAST_SERVER_VERSION = "last_server_version";
    static final String LAST_HTTP_CODE = "last_http_code";
    static final String LAST_LATENCY_MS = "last_latency_ms";
    static final String LAST_SUCCESS_EPOCH = "last_success_epoch";
    static final String P500_LITE_MODE = "p500_lite_mode";
    static final String NATIVE_CORE_MODE = "native_core_mode";
    static final String ADAPTIVE_RUNTIME = "adaptive_runtime";
    static final String DEVICE_LAYOUT = "device_layout";
    static final String DEVICE_ROLE = "device_role";
    static final String REALTIME_INPUT = "realtime_input";

    private AppPreferences() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context context) {
        PcProfile profile = PcProfileStore.active(context);
        return profile != null && profile.isConfigured();
    }

    static String token(Context context) {
        PcProfile profile = PcProfileStore.active(context);
        return profile == null ? "" : profile.token;
    }

    static String activePcName(Context context) {
        PcProfile profile = PcProfileStore.active(context);
        return profile == null ? "ПК" : profile.name;
    }

    static String baseUrl(Context context) {
        PcProfile profile = PcProfileStore.active(context);
        String host = profile == null ? "" : normalizeHost(profile.host);
        int port = profile == null ? 8765 : profile.port;
        return "http://" + host + ":" + port;
    }

    static String panelUrl(Context context) {
        return p500LiteMode(context) ? litePanelUrl(context) : fullPanelUrl(context);
    }

    static String fullPanelUrl(Context context) {
        return baseUrl(context) + "/?token=" + urlEncode(token(context)) + "&pb_client=android";
    }

    static String litePanelUrl(Context context) {
        return baseUrl(context) + "/p500/?token=" + urlEncode(token(context)) + "&pb_client=android";
    }

    static boolean p500LiteMode(Context context) {
        return get(context).getBoolean(P500_LITE_MODE, !DeviceLayout.isModern(context));
    }

    static boolean nativeCoreMode(Context context) {
        return get(context).getBoolean(NATIVE_CORE_MODE, true);
    }

    static boolean adaptiveRuntime(Context context) {
        return get(context).getBoolean(ADAPTIVE_RUNTIME, true);
    }

    static String deviceRole(Context context) {
        String value = get(context).getString(DEVICE_ROLE, "auto");
        if ("station".equals(value) || "mobile".equals(value)) return value;
        return DeviceLayout.isModern(context) ? "mobile" : "station";
    }

    static boolean realtimeInput(Context context) {
        return get(context).getBoolean(REALTIME_INPUT, true);
    }

    static String healthUrl(Context context) {
        return baseUrl(context) + "/api/health?client_version=" + urlEncode(BuildConfig.VERSION_NAME);
    }

    static String sessionCheckUrl(Context context) {
        return baseUrl(context) + "/api/session/check?client_version=" + urlEncode(BuildConfig.VERSION_NAME);
    }


    static boolean fullscreen(Context context) {
        return get(context).getBoolean(FULLSCREEN, !DeviceLayout.isModern(context));
    }

    static boolean keepScreenOn(Context context) {
        return get(context).getBoolean(KEEP_SCREEN_ON, true);
    }

    static boolean hardwareKeysEnabled(Context context) {
        return get(context).getBoolean(HARDWARE_KEYS, true);
    }

    static boolean longPressMediaEnabled(Context context) {
        return get(context).getBoolean(LONG_PRESS_MEDIA, true);
    }

    static boolean hapticFeedback(Context context) {
        return get(context).getBoolean(HAPTIC_FEEDBACK, true);
    }

    static boolean soundFeedback(Context context) {
        return get(context).getBoolean(SOUND_FEEDBACK, false);
    }

    static int panelBrightness(Context context) {
        return clampPercent(get(context).getInt(PANEL_BRIGHTNESS, 90), 90);
    }

    static int idleBrightness(Context context) {
        return clampPercent(get(context).getInt(IDLE_BRIGHTNESS, 20), 20);
    }

    static int idleDimSeconds(Context context) {
        int value = get(context).getInt(IDLE_DIM_SECONDS, 60);
        return Math.max(0, Math.min(3600, value));
    }

    static void storeProbe(Context context, ServerProbe.Result result) {
        SharedPreferences.Editor editor = get(context).edit()
                .putInt(LAST_HTTP_CODE, result.httpCode)
                .putLong(LAST_LATENCY_MS, result.latencyMs);
        if (result.ok) {
            editor.putLong(LAST_SUCCESS_EPOCH, System.currentTimeMillis());
            if (result.serverVersion != null && result.serverVersion.length() > 0) {
                editor.putString(LAST_SERVER_VERSION, result.serverVersion);
            }
        }
        editor.commit();
    }

    static String lastServerVersion(Context context) {
        return get(context).getString(LAST_SERVER_VERSION, "невідомо");
    }

    static int lastHttpCode(Context context) {
        return get(context).getInt(LAST_HTTP_CODE, 0);
    }

    static long lastLatency(Context context) {
        return get(context).getLong(LAST_LATENCY_MS, 0L);
    }

    static long lastSuccess(Context context) {
        return get(context).getLong(LAST_SUCCESS_EPOCH, 0L);
    }

    static String normalizeHost(String value) {
        String host = trim(value);
        if (host.startsWith("http://")) {
            host = host.substring(7);
        } else if (host.startsWith("https://")) {
            host = host.substring(8);
        }
        int endIndex = host.length();
        int slashIndex = host.indexOf('/');
        int queryIndex = host.indexOf('?');
        int fragmentIndex = host.indexOf('#');
        if (slashIndex >= 0) {
            endIndex = Math.min(endIndex, slashIndex);
        }
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        host = trim(host.substring(0, endIndex));
        int userInfoIndex = host.lastIndexOf('@');
        if (userInfoIndex >= 0) {
            host = host.substring(userInfoIndex + 1);
        }
        if (host.startsWith("[")) {
            int bracketEnd = host.indexOf(']');
            return bracketEnd > 0 ? host.substring(0, bracketEnd + 1) : "";
        }
        int firstColon = host.indexOf(':');
        int lastColon = host.lastIndexOf(':');
        if (firstColon >= 0 && firstColon == lastColon) {
            host = host.substring(0, firstColon);
        } else if (firstColon >= 0) {
            host = "[" + host + "]";
        }
        return trim(host);
    }

    static int parsePort(String value) {
        try {
            int port = Integer.parseInt(trim(value));
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) { }
        return 8765;
    }

    static int parseBoundedInt(String value, int fallback, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(trim(value));
            return Math.max(minimum, Math.min(maximum, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampPercent(int value, int fallback) {
        if (value < 1 || value > 100) {
            return fallback;
        }
        return value;
    }

    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            return value == null ? "" : value;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
