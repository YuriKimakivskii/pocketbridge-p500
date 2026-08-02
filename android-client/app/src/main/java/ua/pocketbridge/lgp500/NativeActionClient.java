package ua.pocketbridge.lgp500;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

final class NativeActionClient {
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 5000;

    static final class Result {
        final boolean ok;
        final int httpCode;
        final String action;
        final String detail;

        Result(boolean ok, int httpCode, String action, String detail) {
            this.ok = ok;
            this.httpCode = httpCode;
            this.action = action == null ? "" : action;
            this.detail = detail == null ? "" : detail;
        }
    }

    private NativeActionClient() { }

    static Result execute(Context context, String action) {
        return execute(context, action, false);
    }

    static Result execute(Context context, String action, boolean confirm) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(AppPreferences.baseUrl(context) + "/api/action");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-PocketBridge-Token", AppPreferences.token(context));
            connection.setRequestProperty("User-Agent", "PocketBridgeRemote/" + BuildConfig.VERSION_NAME);

            JSONObject payload = new JSONObject();
            payload.put("action", action);
            payload.put("confirm", confirm);
            byte[] body = payload.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            output.write(body);
            output.flush();
            output.close();

            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream());
            JSONObject json = response.length() == 0 ? new JSONObject() : new JSONObject(response);
            boolean ok = code >= 200 && code < 300 && json.optBoolean("ok", false);
            String detail = json.optString("detail", ok ? "Команду виконано" : "HTTP " + code);
            if (!ok && json.has("detail")) {
                detail = json.optString("detail", detail);
            }
            return new Result(ok, code, json.optString("action", action), detail);
        } catch (Exception exception) {
            String detail = exception.getMessage();
            if (detail == null || detail.trim().length() == 0) {
                detail = "Не вдалося зв’язатися з PocketBridge";
            }
            return new Result(false, 0, action, detail);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            value.append(line);
        }
        reader.close();
        return value.toString();
    }
}
