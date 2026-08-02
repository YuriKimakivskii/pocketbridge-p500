package ua.pocketbridge.lgp500;

import android.os.Build;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

final class PairingClient {
    static final class Result {
        final boolean ok;
        final String detail;
        final String token;
        final String deviceId;

        Result(boolean ok, String detail, String token, String deviceId) {
            this.ok = ok;
            this.detail = detail;
            this.token = token;
            this.deviceId = deviceId;
        }
    }

    private PairingClient() { }

    static Result claim(String host, int port, String code, String name) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + host + ":" + port + "/api/pairing/claim");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            JSONObject body = new JSONObject();
            body.put("code", code);
            body.put("device_name", name);
            body.put("client_version", BuildConfig.VERSION_NAME);
            body.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            byte[] encoded = body.toString().getBytes("UTF-8");
            OutputStream output = connection.getOutputStream();
            output.write(encoded);
            output.flush();
            output.close();
            int status = connection.getResponseCode();
            InputStream raw = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(raw);
            JSONObject payload = new JSONObject(response.length() == 0 ? "{}" : response);
            if (status >= 200 && status < 300) {
                return new Result(true, "Парування завершено",
                        payload.optString("token", ""), payload.optString("device_id", ""));
            }
            return new Result(false, payload.optString("detail", "HTTP " + status), "", "");
        } catch (Exception exception) {
            return new Result(false, exception.getMessage() == null ? "Помилка парування" : exception.getMessage(), "", "");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream raw) throws Exception {
        if (raw == null) {
            return "";
        }
        BufferedInputStream input = new BufferedInputStream(raw);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1 && output.size() < 32768) {
            output.write(buffer, 0, count);
        }
        input.close();
        return output.toString("UTF-8");
    }
}
