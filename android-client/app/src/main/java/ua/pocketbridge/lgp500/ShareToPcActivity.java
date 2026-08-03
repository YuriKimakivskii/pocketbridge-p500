package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

/** Android share target: forwards text, links, and one file to the active PocketBridge PC. */
public class ShareToPcActivity extends Activity {
    private boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AppPreferences.isConfigured(this)) {
            Toast.makeText(this, "Спочатку налаштуй і спаруй PocketBridge", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }
        final Uri stream = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        final CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (stream != null) {
            confirmFile(stream);
        } else if (text != null && text.toString().trim().length() > 0) {
            chooseTextMode(text.toString().trim());
        } else {
            Toast.makeText(this, "Немає даних для надсилання", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void chooseTextMode(final String text) {
        final boolean url = text.toLowerCase().startsWith("http://") || text.toLowerCase().startsWith("https://");
        CharSequence[] options = url
                ? new CharSequence[] {"Відкрити посилання на ПК", "Скопіювати в буфер Windows"}
                : new CharSequence[] {"Скопіювати в буфер Windows"};
        new AlertDialog.Builder(this)
                .setTitle("Поділитися через PocketBridge")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        sendText(text, url && which == 0 ? "open" : "copy");
                    }
                })
                .setNegativeButton("Скасувати", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { finish(); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { finish(); }
                })
                .show();
    }

    private void confirmFile(final Uri stream) {
        final String name = displayName(stream);
        new AlertDialog.Builder(this)
                .setTitle("Надіслати файл на ПК?")
                .setMessage(name + "\nФайл буде збережено у shared\\Mobile-Share")
                .setNegativeButton("Скасувати", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { finish(); }
                })
                .setPositiveButton("Надіслати", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { sendFile(stream, name); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { finish(); }
                })
                .show();
    }

    private void sendText(final String text, final String mode) {
        Toast.makeText(this, "Надсилання…", Toast.LENGTH_SHORT).show();
        new AsyncTask<Void, Void, P500ApiClient.Result>() {
            @Override protected P500ApiClient.Result doInBackground(Void... values) {
                return P500ApiClient.shareText(getApplicationContext(), text, mode);
            }
            @Override protected void onPostExecute(P500ApiClient.Result result) {
                if (destroyed) return;
                Toast.makeText(ShareToPcActivity.this, result.detail, Toast.LENGTH_LONG).show();
                finish();
            }
        }.execute();
    }

    private void sendFile(final Uri uri, final String name) {
        Toast.makeText(this, "Передавання файла…", Toast.LENGTH_SHORT).show();
        new AsyncTask<Void, Void, P500ApiClient.Result>() {
            @Override protected P500ApiClient.Result doInBackground(Void... values) {
                return P500ApiClient.uploadShared(getApplicationContext(), uri, name);
            }
            @Override protected void onPostExecute(P500ApiClient.Result result) {
                if (destroyed) return;
                Toast.makeText(ShareToPcActivity.this, result.detail, Toast.LENGTH_LONG).show();
                finish();
            }
        }.execute();
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && value.trim().length() > 0) return value.trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String value = uri.getLastPathSegment();
        return value == null || value.length() == 0 ? "shared-file" : value;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
