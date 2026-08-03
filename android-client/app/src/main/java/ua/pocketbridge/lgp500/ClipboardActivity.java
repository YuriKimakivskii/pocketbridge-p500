package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClipboardActivity extends Activity {
    private EditText textInput;
    private TextView historyText;
    private TextView statusText;
    private boolean requestInFlight;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_clipboard);
        textInput = (EditText) findViewById(R.id.clipboard_text);
        historyText = (TextView) findViewById(R.id.clipboard_history);
        statusText = (TextView) findViewById(R.id.clipboard_status);
        bindActions();
        readFromPc();
    }

    private void bindActions() {
        ((Button) findViewById(R.id.clipboard_read_pc)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { readFromPc(); }
        });
        ((Button) findViewById(R.id.clipboard_send_pc)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { sendToPc(); }
        });
        ((Button) findViewById(R.id.clipboard_from_phone)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { readPhoneClipboard(); }
        });
        ((Button) findViewById(R.id.clipboard_to_phone)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { writePhoneClipboard(); }
        });
        ((Button) findViewById(R.id.clipboard_clear)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                textInput.setText("");
                statusText.setText("Поле очищено");
            }
        });
        ((Button) findViewById(R.id.clipboard_close)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
    }

    private void readFromPc() {
        if (requestInFlight || destroyed) return;
        requestInFlight = true;
        statusText.setText("Зчитування буфера ПК…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.clipboardGet(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (!result.ok) {
                            renderError(result.detail);
                            return;
                        }
                        JSONObject data = result.data;
                        if (data.optInt("a", 0) != 1) {
                            renderError(data.optString("d", "Буфер ПК недоступний"));
                            return;
                        }
                        textInput.setText(data.optString("t", ""));
                        textInput.setSelection(textInput.getText().length());
                        renderHistory(data.optJSONArray("h"));
                        statusText.setText("Буфер ПК зчитано · " + result.latencyMs + " мс");
                    }
                });
            }
        }, "pb-clipboard-read").start();
    }

    private void sendToPc() {
        if (requestInFlight || destroyed) return;
        String value = textInput.getText().toString();
        if (value.length() > 8192) value = value.substring(0, 8192);
        final String text = value;
        requestInFlight = true;
        statusText.setText("Оновлення буфера ПК…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.clipboardSet(getApplicationContext(), text);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestInFlight = false;
                        if (destroyed) return;
                        if (result.ok) {
                            FeedbackController.success(ClipboardActivity.this);
                            statusText.setText("Текст у буфері ПК · " + result.latencyMs + " мс");
                            readFromPc();
                        } else {
                            FeedbackController.failure(ClipboardActivity.this);
                            renderError(result.detail);
                        }
                    }
                });
            }
        }, "pb-clipboard-write").start();
    }

    @SuppressWarnings("deprecation")
    private void readPhoneClipboard() {
        android.text.ClipboardManager manager = (android.text.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence value = manager == null ? null : manager.getText();
        textInput.setText(value == null ? "" : value.toString());
        textInput.setSelection(textInput.getText().length());
        statusText.setText(value == null ? "Буфер телефона порожній" : "Зчитано з телефона");
    }

    @SuppressWarnings("deprecation")
    private void writePhoneClipboard() {
        android.text.ClipboardManager manager = (android.text.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            renderError("Буфер телефона недоступний");
            return;
        }
        manager.setText(textInput.getText().toString());
        Toast.makeText(this, "Скопійовано в буфер телефона", Toast.LENGTH_SHORT).show();
        statusText.setText("Текст у буфері телефона");
    }

    private void renderHistory(JSONArray history) {
        if (history == null || history.length() == 0) {
            historyText.setText("Історія: —");
            return;
        }
        StringBuilder value = new StringBuilder("Історія:");
        for (int i = 0; i < history.length() && i < 4; i++) {
            String item = history.optString(i, "").replace('\n', ' ').replace('\r', ' ').trim();
            if (item.length() > 70) item = item.substring(0, 70) + "…";
            if (item.length() > 0) value.append("\n• ").append(item);
        }
        historyText.setText(value.toString());
    }

    private void renderError(String detail) {
        String value = detail == null || detail.length() == 0 ? "Операція недоступна" : detail;
        statusText.setText(value);
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
