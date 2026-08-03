package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteScreenActivity extends Activity {
    private Handler handler;
    private ImageView imageView;
    private TextView placeholder;
    private TextView statusText;
    private Spinner monitorSpinner;
    private Button autoButton;
    private Button leftButton;
    private Button doubleButton;
    private Button rightButton;
    private final ArrayList<Integer> monitorIds = new ArrayList<Integer>();
    private boolean resumed;
    private boolean destroyed;
    private boolean infoInFlight;
    private boolean frameInFlight;
    private boolean clickInFlight;
    private boolean autoEnabled;
    private boolean clickEnabled;
    private int monitorIndex;
    private int frameMonitorIndex = -1;
    private int frameWidth;
    private int frameHeight;
    private int requestWidth = 480;
    private int requestQuality = 45;
    private String frameHash = "";
    private String clickMode = "left";
    private Bitmap currentBitmap;
    private long lastTouchAt;

    private final Runnable autoRunnable = new Runnable() {
        @Override public void run() {
            if (resumed && autoEnabled) requestFrame(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (AppPreferences.fullscreen(this)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (AppPreferences.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        setContentView(R.layout.activity_remote_screen);
        handler = new Handler();
        bindViews();
        bindActions();
        setClickMode("left");
    }

    private void bindViews() {
        imageView = (ImageView) findViewById(R.id.screen_image);
        placeholder = (TextView) findViewById(R.id.screen_placeholder);
        statusText = (TextView) findViewById(R.id.screen_status);
        monitorSpinner = (Spinner) findViewById(R.id.screen_monitor_spinner);
        autoButton = (Button) findViewById(R.id.screen_auto);
        leftButton = (Button) findViewById(R.id.screen_mode_left);
        doubleButton = (Button) findViewById(R.id.screen_mode_double);
        rightButton = (Button) findViewById(R.id.screen_mode_right);
    }

    private void bindActions() {
        ((Button) findViewById(R.id.screen_refresh)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestFrame(true); }
        });
        autoButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                autoEnabled = !autoEnabled;
                renderAutoState();
                handler.removeCallbacks(autoRunnable);
                if (autoEnabled) requestFrame(false);
            }
        });
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { setClickMode("left"); }
        });
        doubleButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { setClickMode("double"); }
        });
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { setClickMode("right"); }
        });
        ((Button) findViewById(R.id.screen_touchpad)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(RemoteScreenActivity.this, NativeCoreActivity.class);
                intent.putExtra("open_page", "touch");
                startActivity(intent);
                finish();
            }
        });
        ((Button) findViewById(R.id.screen_close)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        monitorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < monitorIds.size()) {
                    int selected = monitorIds.get(position).intValue();
                    if (monitorIndex != selected || frameWidth == 0) {
                        monitorIndex = selected;
                        frameMonitorIndex = -1;
                        frameHash = "";
                        frameWidth = 0;
                        frameHeight = 0;
                        placeholder.setVisibility(View.VISIBLE);
                        placeholder.setText("Завантаження монітора " + (selected + 1) + "…");
                        requestFrame(true);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) return true;
                if (System.currentTimeMillis() - lastTouchAt < 180L) return true;
                lastTouchAt = System.currentTimeMillis();
                sendClick(event.getX(), event.getY());
                return true;
            }
        });
    }

    private void requestInfo() {
        if (infoInFlight || destroyed) return;
        infoInFlight = true;
        statusText.setText("Перевірка доступу до екрана…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.screenInfo(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        infoInFlight = false;
                        if (destroyed) return;
                        if (!result.ok) {
                            renderError(result.detail);
                            return;
                        }
                        renderInfo(result.data);
                    }
                });
            }
        }, "pb-screen-info").start();
    }

    private void renderInfo(JSONObject data) {
        if (data.optInt("a", 0) != 1) {
            clickEnabled = false;
            placeholder.setVisibility(View.VISIBLE);
            placeholder.setText(data.optString("d", "Перегляд екрана недоступний"));
            statusText.setText(data.optString("d", "Екран недоступний"));
            return;
        }
        clickEnabled = data.optInt("c", 0) == 1;
        requestWidth = data.optInt("w", DeviceLayout.isModern(this) ? 720 : 360);
        requestQuality = data.optInt("q", DeviceLayout.isModern(this) ? 55 : 35);
        if (DeviceLayout.isModern(this)) {
            requestWidth = Math.max(480, Math.min(800, requestWidth));
            requestQuality = Math.max(40, Math.min(65, requestQuality));
        } else {
            requestWidth = Math.max(240, Math.min(480, requestWidth));
            requestQuality = Math.max(25, Math.min(45, requestQuality));
        }
        JSONArray monitors = data.optJSONArray("m");
        ArrayList<String> labels = new ArrayList<String>();
        monitorIds.clear();
        if (monitors != null) {
            for (int i = 0; i < monitors.length(); i++) {
                JSONObject item = monitors.optJSONObject(i);
                if (item == null) continue;
                int index = item.optInt("i", i);
                monitorIds.add(Integer.valueOf(index));
                labels.add("Монітор " + (index + 1) + " · " + item.optInt("w", 0) + "×" + item.optInt("h", 0));
            }
        }
        if (labels.size() == 0) {
            labels.add("Монітор 1");
            monitorIds.add(Integer.valueOf(0));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monitorSpinner.setAdapter(adapter);
        leftButton.setEnabled(clickEnabled);
        doubleButton.setEnabled(clickEnabled);
        rightButton.setEnabled(clickEnabled);
        statusText.setText(clickEnabled ? "Дотик по знімку виконує вибраний клік" : "Клік вимкнено на сервері");
        requestFrame(true);
    }

    private void requestFrame(final boolean force) {
        if (frameInFlight || destroyed || !resumed) return;
        frameInFlight = true;
        if (force) statusText.setText("Оновлення знімка…");
        final int requestedMonitor = monitorIndex;
        final String previous = force || frameMonitorIndex != requestedMonitor ? "" : frameHash;
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.screenFrame(
                        getApplicationContext(), requestedMonitor, requestWidth, requestQuality, previous);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        frameInFlight = false;
                        if (destroyed || !resumed) return;
                        if (requestedMonitor != monitorIndex) {
                            requestFrame(true);
                            return;
                        }
                        if (!result.ok) {
                            renderError(result.detail);
                            scheduleAuto();
                            return;
                        }
                        renderFrame(result, requestedMonitor);
                        scheduleAuto();
                    }
                });
            }
        }, "pb-screen-frame").start();
    }

    private void renderFrame(P500ApiClient.Result result, int requestedMonitor) {
        frameMonitorIndex = requestedMonitor;
        JSONObject data = result.data;
        frameHash = data.optString("h", frameHash);
        frameWidth = data.optInt("w", frameWidth);
        frameHeight = data.optInt("g", frameHeight);
        if (data.optInt("u", 1) == 0) {
            statusText.setText("Без змін · " + result.latencyMs + " мс");
            return;
        }
        String encoded = data.optString("i", "");
        if (encoded.length() == 0) {
            renderError("Сервер повернув порожній знімок");
            return;
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new IllegalArgumentException("Не вдалося декодувати JPEG");
            final Bitmap old = currentBitmap;
            currentBitmap = bitmap;
            imageView.setImageBitmap(bitmap);
            placeholder.setVisibility(View.GONE);
            statusText.setText(frameWidth + "×" + frameHeight + " · " + result.latencyMs + " мс"
                    + (data.optInt("z", 0) == 1 ? " · кеш" : ""));
            if (old != null && old != bitmap) {
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        try { if (!old.isRecycled()) old.recycle(); } catch (Exception ignored) { }
                    }
                }, 700L);
            }
        } catch (OutOfMemoryError error) {
            renderError("Недостатньо пам’яті для знімка. Зменш якість або ширину кадру");
            try { System.gc(); } catch (Exception ignored) { }
        } catch (Exception exception) {
            renderError(exception.getMessage() == null ? "Помилка JPEG" : exception.getMessage());
        }
    }

    private void sendClick(float viewX, float viewY) {
        if (!clickEnabled) {
            Toast.makeText(this, "Клік по екрану вимкнено на сервері", Toast.LENGTH_SHORT).show();
            return;
        }
        if (clickInFlight || frameMonitorIndex < 0 || frameMonitorIndex != monitorIndex
                || frameWidth <= 0 || frameHeight <= 0 || currentBitmap == null) return;
        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;
        float scale = Math.min(viewWidth / (float) frameWidth, viewHeight / (float) frameHeight);
        if (scale <= 0f) return;
        float renderedWidth = frameWidth * scale;
        float renderedHeight = frameHeight * scale;
        float left = (viewWidth - renderedWidth) / 2f;
        float top = (viewHeight - renderedHeight) / 2f;
        if (viewX < left || viewY < top || viewX > left + renderedWidth || viewY > top + renderedHeight) return;
        final int sourceX = clamp(Math.round((viewX - left) / scale), 0, Math.max(0, frameWidth - 1));
        final int sourceY = clamp(Math.round((viewY - top) / scale), 0, Math.max(0, frameHeight - 1));
        final String mode = clickMode;
        final int clickMonitor = frameMonitorIndex;
        final int clickWidth = frameWidth;
        final int clickHeight = frameHeight;
        clickInFlight = true;
        statusText.setText("Клік " + sourceX + ", " + sourceY + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                final P500ApiClient.Result result = P500ApiClient.screenClick(
                        getApplicationContext(), clickMonitor, sourceX, sourceY,
                        clickWidth, clickHeight, "right".equals(mode) ? "right" : "left",
                        "double".equals(mode));
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        clickInFlight = false;
                        if (destroyed) return;
                        if (result.ok) {
                            FeedbackController.success(RemoteScreenActivity.this);
                            statusText.setText("Клік виконано · " + result.latencyMs + " мс");
                            handler.postDelayed(new Runnable() {
                                @Override public void run() { requestFrame(true); }
                            }, 220L);
                        } else {
                            FeedbackController.failure(RemoteScreenActivity.this);
                            renderError(result.detail);
                        }
                    }
                });
            }
        }, "pb-screen-click").start();
    }

    private void setClickMode(String mode) {
        clickMode = mode;
        leftButton.setBackgroundResource("left".equals(mode) ? R.drawable.button_primary : R.drawable.button_secondary);
        doubleButton.setBackgroundResource("double".equals(mode) ? R.drawable.button_primary : R.drawable.button_secondary);
        rightButton.setBackgroundResource("right".equals(mode) ? R.drawable.button_primary : R.drawable.button_secondary);
    }

    private void renderAutoState() {
        autoButton.setText(autoEnabled ? "Авто: ON" : "Авто: OFF");
        autoButton.setBackgroundResource(autoEnabled ? R.drawable.button_primary : R.drawable.button_secondary);
    }

    private void scheduleAuto() {
        handler.removeCallbacks(autoRunnable);
        if (resumed && autoEnabled) {
            handler.postDelayed(autoRunnable, DeviceLayout.isModern(this) ? 900L : 2200L);
        }
    }

    private void renderError(String detail) {
        String value = detail == null || detail.length() == 0 ? "Екран недоступний" : detail;
        statusText.setText(value);
        if (currentBitmap == null) {
            placeholder.setVisibility(View.VISIBLE);
            placeholder.setText(value);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        requestInfo();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(autoRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (currentBitmap != null) {
            try { if (!currentBitmap.isRecycled()) currentBitmap.recycle(); } catch (Exception ignored) { }
            currentBitmap = null;
        }
        super.onDestroy();
    }
}
