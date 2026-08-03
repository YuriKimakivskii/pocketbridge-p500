package ua.pocketbridge.lgp500;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;

final class P500ApiClient {
    private static final int CONNECT_TIMEOUT_MS = 3500;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_JSON_CHARS = 512 * 1024;
    private static final int MAX_SCREEN_JSON_CHARS = 2 * 1024 * 1024;

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
        return request(context, "GET", "/api/p500/native/bootstrap?layout=" + DeviceLayout.apiKey(context)
                + "&role=" + AppPreferences.deviceRole(context), null, etag);
    }

    static Result bootstrap(Context context) {
        return bootstrap(context, "");
    }

    static Result status(Context context, boolean media) {
        return request(context, "GET", "/api/p500/status?media=" + (media ? "1" : "0"), null, "");
    }

    static Result monitor(Context context, boolean force) {
        return request(context, "GET", "/api/p500/monitor" + (force ? "?force=1" : ""), null, "");
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

    static Result youtubeSearch(Context context, String query) {
        JSONObject body = new JSONObject();
        try {
            body.put("q", query == null ? "" : query);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/youtube/search", body, "");
    }

    static Result mouse(Context context, String kind, int dx, int dy, int delta) {
        JSONObject body = new JSONObject();
        try {
            String event = "m";
            if ("left_click".equals(kind)) event = "l";
            else if ("right_click".equals(kind)) event = "r";
            else if ("double_click".equals(kind)) event = "d";
            else if ("scroll".equals(kind)) event = "s";
            else if ("drag_start".equals(kind)) event = "h";
            else if ("drag_end".equals(kind)) event = "u";
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

    static Result wolDiagnostics(Context context) {
        return request(context, "GET", "/api/wol/diagnostics", null, "");
    }

    static Result screenInfo(Context context) {
        return request(context, "GET", "/api/p500/screen/info", null, "");
    }

    static Result screenFrame(Context context, int monitor, int width, int quality, String frameHash) {
        try {
            String path = "/api/p500/screen/frame?monitor=" + clamp(monitor, 0, 15)
                    + "&width=" + clamp(width, 160, 800)
                    + "&quality=" + clamp(quality, 25, 80)
                    + "&hash=" + URLEncoder.encode(frameHash == null ? "" : frameHash, "UTF-8");
            return request(context, "GET", path, null, "", MAX_SCREEN_JSON_CHARS);
        } catch (Exception exception) {
            return failure(exception);
        }
    }

    static Result screenClick(Context context, int monitor, int x, int y, int width, int height,
                              String button, boolean doubleClick) {
        JSONObject body = new JSONObject();
        try {
            body.put("m", clamp(monitor, 0, 15));
            body.put("x", clamp(x, 0, 4000));
            body.put("y", clamp(y, 0, 4000));
            body.put("w", clamp(width, 1, 4000));
            body.put("h", clamp(height, 1, 4000));
            body.put("b", "right".equals(button) ? "r" : "l");
            body.put("d", doubleClick);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/screen/click", body, "");
    }

    static Result clipboardGet(Context context) {
        return request(context, "GET", "/api/p500/clipboard", null, "");
    }

    static Result clipboardSet(Context context, String text) {
        JSONObject body = new JSONObject();
        try {
            String value = text == null ? "" : text;
            if (value.length() > 8192) value = value.substring(0, 8192);
            body.put("t", value);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "PUT", "/api/p500/clipboard", body, "");
    }


    static Result filesList(Context context, String path) {
        try {
            return request(context, "GET", "/api/p500/files?path="
                    + URLEncoder.encode(path == null ? "" : path, "UTF-8"), null, "");
        } catch (Exception exception) {
            return failure(exception);
        }
    }

    static Result fileTicket(Context context, String path) {
        try {
            return request(context, "POST", "/api/p500/files/ticket?path="
                    + URLEncoder.encode(path == null ? "" : path, "UTF-8"), new JSONObject(), "");
        } catch (Exception exception) {
            return failure(exception);
        }
    }

    static Result createFolder(Context context, String path) {
        JSONObject body = new JSONObject();
        try {
            body.put("p", path == null ? "" : path);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/p500/files/folder", body, "");
    }

    static Result deleteFile(Context context, String path) {
        try {
            return request(context, "DELETE", "/api/p500/files?path="
                    + URLEncoder.encode(path == null ? "" : path, "UTF-8"), null, "");
        } catch (Exception exception) {
            return failure(exception);
        }
    }

    static Result uploadFile(Context context, Uri uri, String fileName, String folder) {
        String safeFolder = folder == null ? "" : folder.replace('\\', '/').replaceAll("^/+|/+$", "");
        String path = safeFolder.length() == 0 ? sanitizeFileName(fileName)
                : safeFolder + "/" + sanitizeFileName(fileName);
        return uploadToPath(context, uri, path);
    }

    static Result downloadTicket(Context context, String ticketUrl, String fileName) {
        return downloadTicket(context, ticketUrl, fileName, -1L);
    }

    static Result downloadTicket(Context context, String ticketUrl, String fileName, long expectedSize) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        File temporary = null;
        File destination = null;
        long started = System.currentTimeMillis();
        final long maxBytes = 100L * 1024L * 1024L;
        try {
            String value = ticketUrl == null ? "" : ticketUrl.trim();
            if (!value.startsWith("/api/files/download/")) {
                throw new IllegalArgumentException("Некоректне посилання завантаження");
            }
            if (expectedSize > maxBytes) {
                throw new IllegalArgumentException("Файл перевищує 100 МіБ");
            }
            URL url = new URL(AppPreferences.baseUrl(context) + value);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setRequestProperty("X-PocketBridge-Token", AppPreferences.token(context));
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String raw = readAll(connection.getErrorStream());
                String detail = "HTTP " + code;
                try { detail = new JSONObject(raw).optString("detail", detail); } catch (Exception ignored) { }
                return new Result(false, code, System.currentTimeMillis() - started, new JSONObject(), detail);
            }
            long declaredSize = connection.getContentLength();
            if (declaredSize > maxBytes) {
                throw new IllegalArgumentException("Файл перевищує 100 МіБ");
            }
            File directory = downloadDirectory(context);
            destination = uniqueFile(directory, sanitizeFileName(fileName));
            temporary = new File(directory, "." + destination.getName() + "." + System.currentTimeMillis() + ".part");
            input = connection.getInputStream();
            output = new FileOutputStream(temporary);
            byte[] buffer = new byte[8192];
            int count;
            long total = 0L;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                total += count;
                if (total > maxBytes) throw new IllegalArgumentException("Файл перевищує 100 МіБ");
                output.write(buffer, 0, count);
            }
            output.flush();
            output.close();
            output = null;
            if (expectedSize >= 0L && total != expectedSize) {
                throw new IllegalStateException("Файл завантажено не повністю");
            }
            if (!temporary.renameTo(destination)) {
                throw new IllegalStateException("Не вдалося завершити збереження файла");
            }
            temporary = null;
            MediaScannerConnection.scanFile(context,
                    new String[] {destination.getAbsolutePath()}, null, null);
            JSONObject data = new JSONObject();
            data.put("ok", true);
            data.put("path", destination.getAbsolutePath());
            data.put("size", total);
            return new Result(true, code, System.currentTimeMillis() - started, data,
                    "Збережено: " + destination.getAbsolutePath());
        } catch (Exception exception) {
            return new Result(false, 0, System.currentTimeMillis() - started, new JSONObject(),
                    exception.getMessage() == null ? "Не вдалося завантажити файл" : exception.getMessage());
        } finally {
            try { if (output != null) output.close(); } catch (Exception ignored) { }
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (temporary != null && temporary.exists()) temporary.delete();
            if (connection != null) connection.disconnect();
        }
    }

    static Result shareText(Context context, String text, String mode) {
        JSONObject body = new JSONObject();
        try {
            body.put("text", text == null ? "" : text);
            body.put("mode", mode == null ? "auto" : mode);
        } catch (Exception exception) {
            return failure(exception);
        }
        return request(context, "POST", "/api/share/text", body, "");
    }

    static Result uploadShared(Context context, Uri uri, String fileName) {
        return uploadToPath(context, uri, "Mobile-Share/" + sanitizeFileName(fileName));
    }

    private static Result uploadToPath(Context context, Uri uri, String path) {
        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        long started = System.currentTimeMillis();
        try {
            URL url = new URL(AppPreferences.baseUrl(context) + "/api/files/upload?rename_if_exists=1&path="
                    + URLEncoder.encode(path, "UTF-8"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setChunkedStreamingMode(8192);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("X-PocketBridge-Token", AppPreferences.token(context));
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IllegalArgumentException("Не вдалося відкрити файл");
            output = connection.getOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            long total = 0L;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                total += count;
                if (total > 100L * 1024L * 1024L) throw new IllegalArgumentException("Файл перевищує 100 МіБ");
                output.write(buffer, 0, count);
            }
            output.flush();
            output.close();
            output = null;
            int code = connection.getResponseCode();
            InputStream response = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String raw = readAll(response);
            JSONObject data = raw.length() == 0 ? new JSONObject() : new JSONObject(raw);
            boolean ok = code >= 200 && code < 300 && data.optBoolean("ok", true);
            String detail = data.optString("detail", data.optString("d", ok ? "Файл надіслано" : "HTTP " + code));
            return new Result(ok, code, System.currentTimeMillis() - started, data, detail, false, "", raw.length());
        } catch (Exception exception) {
            return new Result(false, 0, System.currentTimeMillis() - started, new JSONObject(),
                    exception.getMessage() == null ? "Не вдалося передати файл" : exception.getMessage());
        } finally {
            try { if (output != null) output.close(); } catch (Exception ignored) { }
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    private static File downloadDirectory(Context context) throws Exception {
        File publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File publicDirectory = new File(publicRoot, "PocketBridge");
        try {
            if ((publicDirectory.exists() || publicDirectory.mkdirs()) && publicDirectory.canWrite()) {
                return publicDirectory;
            }
        } catch (SecurityException ignored) { }
        File appRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (appRoot == null) appRoot = context.getFilesDir();
        File appDirectory = new File(appRoot, "PocketBridge");
        if (!appDirectory.exists() && !appDirectory.mkdirs()) {
            throw new IllegalStateException("Не вдалося створити папку завантажень");
        }
        return appDirectory;
    }

    private static File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int index = 1; index < 1000; index++) {
            candidate = new File(directory, base + " (" + index + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(directory, System.currentTimeMillis() + "-" + name);
    }

    private static String sanitizeFileName(String value) {
        String name = value == null ? "shared-file" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        while (name.endsWith(".") || name.endsWith(" ")) name = name.substring(0, name.length() - 1);
        if (name.length() == 0) name = "shared-file";
        String upperBase = name;
        int reservedDot = upperBase.indexOf('.');
        if (reservedDot >= 0) upperBase = upperBase.substring(0, reservedDot);
        upperBase = upperBase.toUpperCase(java.util.Locale.US);
        if ("CON".equals(upperBase) || "PRN".equals(upperBase)
                || "AUX".equals(upperBase) || "NUL".equals(upperBase)
                || upperBase.matches("COM[1-9]") || upperBase.matches("LPT[1-9]")) {
            name = "_" + name;
        }
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 16 ? name.substring(dot) : "";
            int baseLimit = Math.max(1, 120 - extension.length());
            name = name.substring(0, baseLimit) + extension;
        }
        return name;
    }

    private static Result request(Context context, String method, String path,
                                  JSONObject payload, String ifNoneMatch) {
        return request(context, method, path, payload, ifNoneMatch, MAX_JSON_CHARS);
    }

    private static Result request(Context context, String method, String path,
                                  JSONObject payload, String ifNoneMatch, int maxResponseChars) {
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
            connection.setRequestProperty("User-Agent", "PocketBridgeRemote/" + BuildConfig.VERSION_NAME + " NativeCore/3 " + DeviceLayout.apiKey(context));
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
            String raw = readAll(input, maxResponseChars);
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
        return readAll(input, MAX_JSON_CHARS);
    }

    private static String readAll(InputStream input, int maxChars) throws Exception {
        if (input == null) return "";
        int safeMax = Math.max(4096, maxChars);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"), 2048);
        try {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (value.length() + count > safeMax) {
                    throw new java.io.IOException("Відповідь сервера надто велика");
                }
                value.append(buffer, 0, count);
            }
            return value.toString();
        } finally {
            reader.close();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
