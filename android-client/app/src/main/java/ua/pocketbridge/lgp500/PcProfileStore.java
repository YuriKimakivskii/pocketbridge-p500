package ua.pocketbridge.lgp500;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;

final class PcProfileStore {
    private static final String PROFILES = "pc_profiles_v1";
    private static final String ACTIVE_ID = "active_pc_profile";

    private PcProfileStore() { }

    static List<PcProfile> list(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        String raw = preferences.getString(PROFILES, "");
        ArrayList<PcProfile> result = new ArrayList<PcProfile>();
        if (raw != null && raw.length() > 0) {
            try {
                JSONArray values = new JSONArray(raw);
                for (int i = 0; i < values.length(); i++) {
                    org.json.JSONObject item = values.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    PcProfile profile = PcProfile.fromJson(item);
                    if (profile.id.length() > 0) {
                        result.add(profile);
                    }
                }
            } catch (Exception ignored) { }
        }
        if (result.isEmpty()) {
            String host = AppPreferences.normalizeHost(preferences.getString(AppPreferences.HOST, ""));
            String token = preferences.getString(AppPreferences.TOKEN, "");
            if (host.length() > 0 || token.length() > 0) {
                PcProfile migrated = new PcProfile(
                        newId(), "Основний ПК", host,
                        AppPreferences.parsePort(preferences.getString(AppPreferences.PORT, "8765")),
                        token, "", "255.255.255.255");
                result.add(migrated);
                saveAll(context, result, migrated.id);
            }
        }
        return result;
    }

    static PcProfile active(Context context) {
        List<PcProfile> profiles = list(context);
        if (profiles.isEmpty()) {
            return null;
        }
        String activeId = AppPreferences.get(context).getString(ACTIVE_ID, "");
        for (PcProfile profile : profiles) {
            if (profile.id.equals(activeId)) {
                return profile;
            }
        }
        setActive(context, profiles.get(0).id);
        return profiles.get(0);
    }

    static void save(Context context, PcProfile profile, boolean makeActive) {
        List<PcProfile> profiles = list(context);
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            profiles.add(profile);
        }
        saveAll(context, profiles, makeActive ? profile.id : null);
    }

    static void remove(Context context, String id) {
        List<PcProfile> profiles = list(context);
        ArrayList<PcProfile> kept = new ArrayList<PcProfile>();
        for (PcProfile profile : profiles) {
            if (!profile.id.equals(id)) {
                kept.add(profile);
            }
        }
        String activeId = kept.isEmpty() ? "" : kept.get(0).id;
        saveAll(context, kept, activeId);
    }

    static void setActive(Context context, String id) {
        AppPreferences.get(context).edit().putString(ACTIVE_ID, id).commit();
        PcProfile profile = activeWithoutRecursion(context, id);
        if (profile != null) {
            AppPreferences.get(context).edit()
                    .putString(AppPreferences.HOST, profile.host)
                    .putString(AppPreferences.PORT, String.valueOf(profile.port))
                    .putString(AppPreferences.TOKEN, profile.token)
                    .commit();
        }
    }

    static String newId() {
        return "pc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static PcProfile activeWithoutRecursion(Context context, String id) {
        List<PcProfile> profiles = list(context);
        for (PcProfile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    private static void saveAll(Context context, List<PcProfile> profiles, String activeId) {
        JSONArray values = new JSONArray();
        for (PcProfile profile : profiles) {
            try {
                values.put(profile.toJson());
            } catch (JSONException ignored) { }
        }
        SharedPreferences.Editor editor = AppPreferences.get(context).edit()
                .putString(PROFILES, values.toString());
        if (activeId != null) {
            editor.putString(ACTIVE_ID, activeId);
        }
        editor.commit();
        if (activeId != null) {
            for (PcProfile profile : profiles) {
                if (profile.id.equals(activeId)) {
                    AppPreferences.get(context).edit()
                            .putString(AppPreferences.HOST, profile.host)
                            .putString(AppPreferences.PORT, String.valueOf(profile.port))
                            .putString(AppPreferences.TOKEN, profile.token)
                            .commit();
                    break;
                }
            }
        }
    }
}
