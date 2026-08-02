package ua.pocketbridge.lgp500;

import android.content.Context;
import android.os.SystemClock;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

final class ServerProbe {
    static final int CONNECT_TIMEOUT_MS = 4000;
    static final int READ_TIMEOUT_MS = 4000;

    static final class Result {
        final boolean ok;
        final int httpCode;
        final long latencyMs;
        final String detail;
        final String serverVersion;
        final int apiVersion;
        final boolean compatible;
        final boolean upgradeRecommended;

        Result(boolean ok, int httpCode, long latencyMs, String detail,
               String serverVersion, int apiVersion, boolean compatible,
               boolean upgradeRecommended) {
            this.ok = ok;
            this.httpCode = httpCode;
            this.latencyMs = latencyMs;
            this.detail = detail;
            this.serverVersion = serverVersion;
            this.apiVersion = apiVersion;
            this.compatible = compatible;
            this.upgradeRecommended = upgradeRecommended;
        }
    }

    private ServerProbe() { }

    static Result check(Context context) {
        HttpURLConnection connection = null;
        BufferedInputStream input = null;
        long started = SystemClock.elapsedRealtime();
        int code = 0;
        try {
            URL url = new URL(AppPreferences.sessionCheckUrl(context));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-PocketBridge-Token", AppPreferences.token(context));
            connection.setRequestProperty("Cache-Control", "no-cache");
            code = connection.getResponseCode();
            InputStream rawInput = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (rawInput != null) {
                input = new BufferedInputStream(rawInput);
            }
            String body = readBody(input);
            long latency = SystemClock.elapsedRealtime() - started;
            if (code < 200 || code >= 300) {
                String detail = code == 401
                        ? "Токен відхилено сервером"
                        : code == 429
                        ? "Забагато невдалих спроб. Зачекай і повтори"
                        : "Сервер відповів кодом " + code;
                return new Result(false, code, latency, detail, "", 0, false, false);
            }
            JSONObject payload = new JSONObject(body.length() == 0 ? "{}" : body);
            boolean compatible = payload.optBoolean("compatible", true);
            String serverVersion = payload.optString("version", "");
            int apiVersion = payload.optInt("api_version", 0);
            boolean upgradeRecommended = payload.optBoolean("upgrade_recommended", false);
            String detail = compatible
                    ? "З’єднання встановлено"
                    : "Версія APK несумісна із сервером";
            return new Result(
                    compatible,
                    code,
                    latency,
                    detail,
                    serverVersion,
                    apiVersion,
                    compatible,
                    upgradeRecommended);
        } catch (Exception exception) {
            long latency = SystemClock.elapsedRealtime() - started;
            String message = exception.getMessage();
            return new Result(
                    false,
                    code,
                    latency,
                    "Немає з’єднання" + (message == null ? "" : ": " + message),
                    "",
                    0,
                    false,
                    false);
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) { }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(BufferedInputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1 && output.size() < 32768) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }
}
