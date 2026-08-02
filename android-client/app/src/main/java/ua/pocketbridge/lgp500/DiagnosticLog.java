package ua.pocketbridge.lgp500;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLog {
    private static final String FILE_NAME = "pocketbridge.log";
    private static final long MAX_BYTES = 128L * 1024L;

    private DiagnosticLog() { }

    static synchronized void write(Context context, String event, String detail) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try {
            if (file.exists() && file.length() >= MAX_BYTES) {
                File previous = new File(context.getFilesDir(), FILE_NAME + ".old");
                if (previous.exists()) {
                    previous.delete();
                }
                file.renameTo(previous);
            }
            FileOutputStream output = context.openFileOutput(FILE_NAME, Context.MODE_APPEND);
            try {
                String line = timestamp() + "\t" + safe(event) + "\t" + safe(detail) + "\n";
                output.write(line.getBytes("UTF-8"));
            } finally {
                output.close();
            }
        } catch (IOException ignored) { }
    }

    static synchronized String export(Context context) throws IOException {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new IOException("SD-карта недоступна для запису");
        }
        File directory = new File(Environment.getExternalStorageDirectory(), "PocketBridge");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Не вдалося створити папку PocketBridge");
        }
        File target = new File(directory, "pocketbridge-diagnostics.txt");
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, false));
        try {
            String header =
                    "PocketBridge Remote diagnostics\n" +
                    "Exported: " + timestamp() + "\n" +
                    "Client version: " + BuildConfig.VERSION_NAME + "\n" +
                    "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                    "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                    "Server: " + AppPreferences.baseUrl(context) + "\n" +
                    "Last server version: " + AppPreferences.lastServerVersion(context) + "\n" +
                    "Last HTTP code: " + AppPreferences.lastHttpCode(context) + "\n" +
                    "Last latency: " + AppPreferences.lastLatency(context) + " ms\n" +
                    "Last success: " + AppPreferences.lastSuccess(context) + "\n" +
                    "\nLog:\n";
            output.write(header.getBytes("UTF-8"));
            copyIfExists(new File(context.getFilesDir(), FILE_NAME + ".old"), output);
            copyIfExists(new File(context.getFilesDir(), FILE_NAME), output);
        } finally {
            output.close();
        }
        return target.getAbsolutePath();
    }

    static synchronized String recent(Context context, int maxChars) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return "Журнал ще порожній";
        }
        try {
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_BYTES) {
                    break;
                }
            }
            input.close();
            String value = output.toString("UTF-8");
            if (value.length() > maxChars) {
                return value.substring(value.length() - maxChars);
            }
            return value;
        } catch (IOException exception) {
            return "Не вдалося прочитати журнал: " + exception.getMessage();
        }
    }

    private static void copyIfExists(File source, BufferedOutputStream output) throws IOException {
        if (!source.exists()) {
            return;
        }
        BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
        try {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
