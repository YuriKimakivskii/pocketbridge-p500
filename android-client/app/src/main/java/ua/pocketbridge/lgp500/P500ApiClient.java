package ua.pocketbridge.lgp500;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;

final class P500ApiClient {
    private static final int CONNECT_TIMEOUT_MS = 3500;
    private static final int READ_TIMEOUT_MS = 5000;

    static final class Result {
        final boolean ok;
        final int httpCode;
        final long latencyMs;
        final JSONObject data;
        final String detail;
        final boolean notModified;
        final String etag;
        final int responseBytes;

        Result(boolean ok, int httpCode, long latencyMs, JSONObject data, String detail) {
            this(ok, httpCode, latencyMs, data, detail, false, "", 0);
        }

        Result(boolean ok, int httpCode, long latencyMs, JSONObject data, String detail,
               boolean notModified, String etag, int responseBytes) {
            this.ok = ok;
            this.httpCode = httpCode;
            this.latencyMs = latencyMs;
            this.data = data == null ? new JSONObject() : data;
            this.detail = detail == null ? "" : detail;
            this.notModified = notModified;
            this.etag = etag == null ? "" : etag;
            this.responseBytes = Math.max(0, responseBytes);
        }
    }

    private P500ApiClient() { }

    static Result bootstrap(Context context, String etag) {
        return request(context, "GET", "/api/p500/native/bootstrap", null, etag);
    }

    static Result bootstrap(Context context) {
        return bootstrap(context, "");
    }

    static Result status(Context context, boolean media) {
        return request(context, "GET", "/api/p500/status?media=" + (media ? "1" : "0"), null, "");
    }

    static Result executeTarget(Context context, JSONObject target, boolean confirm) {
        JSONObject body = new JSONObject();
        try {
            String macroId = target.optString("macro_id", "");
            String programId = target.optString("program_id", "");
            if (macroId.length() > 0) body.put("m", macroId);
            else if (programId.length() > 0) body.put("p", programId);
            else body.put("a", target.optString("action", ""));
            body.put("c", confirm);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/cmd", body, "");
    }

    static Result mouse(Context context, String kind, int dx, int dy, int delta) {
        JSONObject body = new JSONObject();
        try {
            String event = "m";
            if ("left_click".equals(kind)) event = "l";
            else if ("right_click".equals(kind)) event = "r";
            else if ("double_click".equals(kind)) event = "d";
            else if ("scroll".equals(kind)) event = "s";
            body.put("e", event);
            body.put("x", clamp(dx, -300, 300));
            body.put("y", clamp(dy, -300, 300));
            body.put("d", clamp(delta, -10, 10));
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/input", body, "");
    }

    static Result key(Context context, String key) {
        JSONObject body = new JSONObject();
        try {
            body.put("e", "k");
            body.put("v", key);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/input", body, "");
    }

    static Result text(Context context, String text) {
        JSONObject body = new JSONObject();
        try {
            body.put("e", "t");
            body.put("v", text);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/input", body, "");
    }

    private static Result request(Context context, String method, String path,
                                  JSONObject payload, String ifNoneMatch) {
        HttpURLConnection connection = null;
        long started = System.currentTimeMillis();
        try {
            URL url = new URL(AppPreferences.baseUrl(context) + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("X-PocketBridge-Token", AppPreferences.token(context));
            connection.setRequestProperty("User-Agent", "PocketBridgeRemote/" + BuildConfig.VERSION_NAME + " NativeCore/2");
            if (ifNoneMatch != null && ifNoneMatch.length() > 0) {
                connection.setRequestProperty("If-None-Match", ifNoneMatch);
            }
            if (payload != null) {
                byte[] body = payload.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream output = connection.getOutputStream();
                output.write(body);
                output.flush();
                output.close();
            }
            int code = connection.getResponseCode();
            String responseEtag = connection.getHeaderField("ETag");
            long latency = System.currentTimeMillis() - started;
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return new Result(true, code, latency, new JSONObject(), "Кеш актуальний",
                        true, responseEtag, 0);
            }
            InputStream input = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            if (input != null && "gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                input = new GZIPInputStream(input);
            }
            String raw = readAll(input);
            int responseBytes = raw.length() == 0 ? 0 : raw.getBytes("UTF-8").length;
            JSONObject data = raw.length() == 0 ? new JSONObject() : new JSONObject(raw);
            boolean ok = code >= 200 && code < 300 && data.optBoolean("ok", true);
            String detail = data.optString("detail", data.optString("d", ok ? "OK" : "HTTP " + code));
            return new Result(ok, code, latency, data, detail, false, responseEtag, responseBytes);
        } catch (Exception exception) {
            String detail = exception.getMessage();
            if (detail == null || detail.trim().length() == 0) {
                detail = "Не вдалося зв’язатися з PocketBridge";
            }
            return new Result(false, 0, System.currentTimeMillis() - started,
                    new JSONObject(), detail, false, "", 0);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Result failure(Exception exception) {
        String detail = exception.getMessage();
        return new Result(false, 0, 0L, new JSONObject(),
                detail == null ? "Некоректна команда" : detail);
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"), 2048);
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[2048];
        int count;
        while ((count = reader.read(buffer)) >= 0) value.append(buffer, 0, count);
        reader.close();
        return value.toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
