package ua.pocketbridge.lgp500;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FileManagerActivity extends Activity {
    private static final int REQUEST_PICK_FILE = 4101;
    private static final int REQUEST_STORAGE = 4102;

    private TextView pathText;
    private TextView statusText;
    private LinearLayout listContainer;
    private Button upButton;
    private String currentPath = "";
    private String parentPath = "";
    private boolean requestInFlight;
    private boolean destroyed;
    private boolean uploadAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceLayout.applyOrientation(this);
        setContentView(R.layout.activity_file_manager);
        pathText = (TextView) findViewById(R.id.files_path);
        statusText = (TextView) findViewById(R.id.files_status);
        listContainer = (LinearLayout) findViewById(R.id.files_list);
        upButton = (Button) findViewById(R.id.files_up);

        upButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { load(parentPath); }
        });
        ((Button) findViewById(R.id.files_refresh)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { load(currentPath); }
        });
        ((Button) findViewById(R.id.files_upload)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { pickUpload(); }
        });
        ((Button) findViewById(R.id.files_new_folder)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showCreateFolder(); }
        });

        if (getIntent() != null && getIntent().getBooleanExtra("open_upload", false)) {
            uploadAfterPermission = true;
        }
        load("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uploadAfterPermission) {
            uploadAfterPermission = false;
            pickUpload();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void load(final String path) {
        if (requestInFlight) return;
        requestInFlight = true;
        statusText.setText("Завантаження списку…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.filesList(getApplicationContext(), path);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (!result.ok) {
                            renderError(result.detail);
                            return;
                        }
                        renderList(result.data, result.latencyMs);
                    }
                });
            }
        }, "pb-files-list").start();
    }

    private void renderList(JSONObject data, long latencyMs) {
        if (data.optInt("a", 0) != 1) {
            currentPath = "";
            parentPath = "";
            pathText.setText("Спільні файли");
            upButton.setEnabled(false);
            listContainer.removeAllViews();
            renderEmpty(data.optString("d", "Файловий обмін вимкнено"));
            statusText.setText("Файловий обмін недоступний");
            return;
        }
        currentPath = data.optString("p", "");
        parentPath = data.optString("u", "");
        pathText.setText(currentPath.length() == 0 ? "Спільні файли" : "/" + currentPath);
        upButton.setEnabled(currentPath.length() > 0);
        listContainer.removeAllViews();
        JSONArray items = data.optJSONArray("i");
        int count = items == null ? 0 : items.length();
        if (count == 0) {
            renderEmpty("Папка порожня");
        } else {
            for (int index = 0; index < count; index++) {
                JSONObject item = items.optJSONObject(index);
                if (item != null) addItem(item);
            }
        }
        statusText.setText(count + " об’єктів · " + latencyMs + " мс · максимум "
                + data.optInt("x", 20) + " МіБ");
    }

    private void addItem(final JSONObject item) {
        final boolean folder = "d".equals(item.optString("t", "f"));
        final String name = item.optString("n", "Без назви");
        final String path = item.optString("p", "");
        Button button = new Button(this);
        button.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        button.setTextColor(Color.WHITE);
        button.setTextSize(DeviceLayout.isModern(this) ? 14f : 11f);
        button.setBackgroundResource(folder ? R.drawable.button_primary : R.drawable.button_secondary);
        button.setPadding(dp(10), dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DeviceLayout.isModern(this) ? dp(58) : dp(46));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setText((folder ? "[ПАПКА]  " : "[ФАЙЛ]  ") + name
                + (folder ? "" : "\n" + formatSize(item.optLong("s", 0L))));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (folder) load(path);
                else confirmDownload(path, name);
            }
        });
        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                confirmDelete(path, name, folder);
                return true;
            }
        });
        listContainer.addView(button);
    }

    private void renderEmpty(String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextColor(Color.LTGRAY);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(dp(10), dp(24), dp(10), dp(24));
        listContainer.addView(empty);
    }

    private void confirmDownload(final String path, final String name) {
        new AlertDialog.Builder(this)
                .setTitle("Завантажити файл")
                .setMessage(name + "\n\nФайл буде збережено у Download/PocketBridge.")
                .setPositiveButton("Завантажити", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ensureStorageThenDownload(path, name);
                    }
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void ensureStorageThenDownload(String path, String name) {
        if (Build.VERSION.SDK_INT >= 23 && !StoragePermissionApi23.hasWritePermission(this)) {
            pendingDownloadPath = path;
            pendingDownloadName = name;
            StoragePermissionApi23.requestWritePermission(this, REQUEST_STORAGE);
            return;
        }
        download(path, name);
    }

    private String pendingDownloadPath = "";
    private String pendingDownloadName = "";

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            String path = pendingDownloadPath;
            String name = pendingDownloadName;
            pendingDownloadPath = "";
            pendingDownloadName = "";
            if (path.length() > 0) {
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Збереження буде виконано у папку застосунку", Toast.LENGTH_SHORT).show();
                }
                download(path, name);
            }
        }
    }

    private void download(final String path, final String name) {
        if (requestInFlight) return;
        requestInFlight = true;
        statusText.setText("Створення посилання…");
        new Thread(new Runnable() {
            @Override public void run() {
                P500ApiClient.Result ticket = P500ApiClient.fileTicket(getApplicationContext(), path);
                final P500ApiClient.Result result;
                if (!ticket.ok) result = ticket;
                else result = P500ApiClient.downloadTicket(
                        getApplicationContext(), ticket.data.optString("u", ""), name,
                        ticket.data.optLong("s", -1L));
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (result.ok) {
                            FeedbackController.success(FileManagerActivity.this);
                            statusText.setText(result.detail);
                            Toast.makeText(FileManagerActivity.this, result.detail, Toast.LENGTH_LONG).show();
                        } else renderError(result.detail);
                    }
                });
            }
        }, "pb-file-download").start();
    }

    private void pickUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Вибрати файл для ПК"), REQUEST_PICK_FILE);
        } catch (Exception exception) {
            renderError("Системний вибір файлів недоступний");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_FILE || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        final String name = displayName(uri);
        if (requestInFlight) return;
        requestInFlight = true;
        statusText.setText("Передавання " + name + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.uploadFile(
                        getApplicationContext(), uri, name, currentPath);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (result.ok) {
                            FeedbackController.success(FileManagerActivity.this);
                            Toast.makeText(FileManagerActivity.this, result.detail, Toast.LENGTH_SHORT).show();
                            load(currentPath);
                        } else renderError(result.detail);
                    }
                });
            }
        }, "pb-file-upload").start();
    }

    private void showCreateFolder() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Назва папки");
        new AlertDialog.Builder(this)
                .setTitle("Нова папка")
                .setView(input)
                .setPositiveButton("Створити", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        String path = currentPath.length() == 0 ? name : currentPath + "/" + name;
                        createFolder(path);
                    }
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void createFolder(final String path) {
        if (requestInFlight) return;
        requestInFlight = true;
        statusText.setText("Створення папки…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.createFolder(getApplicationContext(), path);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (result.ok) load(currentPath);
                        else renderError(result.detail);
                    }
                });
            }
        }, "pb-folder-create").start();
    }

    private void confirmDelete(final String path, String name, boolean folder) {
        new AlertDialog.Builder(this)
                .setTitle(folder ? "Видалити папку" : "Видалити файл")
                .setMessage(name + (folder ? "\n\nПапка має бути порожньою." : ""))
                .setPositiveButton("Видалити", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { delete(path); }
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void delete(final String path) {
        if (requestInFlight) return;
        requestInFlight = true;
        statusText.setText("Видалення…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.deleteFile(getApplicationContext(), path);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (result.ok) {
                            FeedbackController.success(FileManagerActivity.this);
                            load(currentPath);
                        } else renderError(result.detail);
                    }
                });
            }
        }, "pb-file-delete").start();
    }

    private String displayName(Uri uri) {
        String result = "mobile-file";
        Cursor cursor = null;
        try {
            ContentResolver resolver = getContentResolver();
            cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) result = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (result == null || result.length() == 0 || "mobile-file".equals(result)) {
            String segment = uri.getLastPathSegment();
            if (segment != null && segment.length() > 0) result = segment;
        }
        return result == null || result.length() == 0 ? "mobile-file" : result;
    }

    private void renderError(String detail) {
        FeedbackController.failure(this);
        String value = detail == null || detail.length() == 0 ? "Помилка файлового менеджера" : detail;
        statusText.setText(value);
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }


    private static final class StoragePermissionApi23 {
        private StoragePermissionApi23() { }

        static boolean hasWritePermission(Activity activity) {
            return activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }

        static void requestWritePermission(Activity activity, int requestCode) {
            activity.requestPermissions(
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " Б";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.US, "%.1f КіБ", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.US, "%.1f МіБ", mib);
        return String.format(Locale.US, "%.2f ГіБ", mib / 1024.0);
    }
}
