package ua.pocketbridge.lgp500;

import org.json.JSONException;
import org.json.JSONObject;

final class PcProfile {
    String id;
    String name;
    String host;
    int port;
    String token;
    String mac;
    String broadcast;

    PcProfile(String id, String name, String host, int port, String token, String mac, String broadcast) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.token = token;
        this.mac = mac;
        this.broadcast = broadcast;
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", id);
        value.put("name", name);
        value.put("host", host);
        value.put("port", port);
        value.put("token", token);
        value.put("mac", mac);
        value.put("broadcast", broadcast);
        return value;
    }

    static PcProfile fromJson(JSONObject value) {
        return new PcProfile(
                value.optString("id", ""),
                value.optString("name", "ПК"),
                value.optString("host", ""),
                value.optInt("port", 8765),
                value.optString("token", ""),
                value.optString("mac", ""),
                value.optString("broadcast", "255.255.255.255"));
    }

    boolean isConfigured() {
        return host != null && host.trim().length() > 0
                && token != null && token.trim().length() > 0;
    }

    @Override
    public String toString() {
        return name + (host.length() > 0 ? " · " + host : "");
    }
}
