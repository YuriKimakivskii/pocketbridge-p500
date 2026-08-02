package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class NativeRemoteActivity extends Activity {
    private Handler handler;
    private TextView statusText;
    private boolean volumeUpLongHandled;
    private boolean volumeDownLongHandled;
    private boolean resumed;
    private boolean destroyed;

    private final Runnable dimRunnable = new Runnable() {
        @Override
        public void run() {
            DisplayController.applyBrightness(
                    NativeRemoteActivity.this,
                    AppPreferences.idleBrightness(NativeRemoteActivity.this));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        handler = new Handler();
        applyWindowPreferences();
        setContentView(R.layout.activity_native_remote);
        statusText = (TextView) findViewById(R.id.native_status);
        bindAction(R.id.native_volume_down, "volume_down");
        bindAction(R.id.native_play_pause, "media_play_pause");
        bindAction(R.id.native_volume_up, "volume_up");
        bindAction(R.id.native_previous, "media_previous");
        bindAction(R.id.native_mute, "volume_mute");
        bindAction(R.id.native_next, "media_next");
        bindAction(R.id.native_desktop, "show_desktop");
        bindAction(R.id.native_alt_tab, "alt_tab");
        bindAction(R.id.native_lock, "lock");

        ((Button) findViewById(R.id.native_wake)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { wakeComputer(); }
        });
        ((Button) findViewById(R.id.native_panel)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(NativeRemoteActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });
        ((Button) findViewById(R.id.native_settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(NativeRemoteActivity.this, SettingsActivity.class));
            }
        });
        renderReady();
        resetIdleTimer();
    }

    private void bindAction(int buttonId, final String action) {
        ((Button) findViewById(buttonId)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { executeAction(action); }
        });
    }

    private void executeAction(final String action) {
        statusText.setText("Виконую: " + labelFor(action) + "…");
        new AsyncTask<Void, Void, NativeActionClient.Result>() {
            @Override protected NativeActionClient.Result doInBackground(Void... values) {
                return NativeActionClient.execute(getApplicationContext(), action);
            }
            @Override protected void onPostExecute(NativeActionClient.Result result) {
                if (destroyed) { return; }
                if (result.ok) {
                    FeedbackController.success(NativeRemoteActivity.this);
                    statusText.setText("OK: " + result.detail);
                } else {
                    FeedbackController.failure(NativeRemoteActivity.this);
                    statusText.setText("ПОМИЛКА: " + result.detail);
                    Toast.makeText(NativeRemoteActivity.this, result.detail, Toast.LENGTH_LONG).show();
                }
                DiagnosticLog.write(NativeRemoteActivity.this,
                        result.ok ? "native_action_ok" : "native_action_failed",
                        action + " http=" + result.httpCode + " " + result.detail);
            }
        }.execute();
    }

    private String labelFor(String action) {
        if ("volume_down".equals(action)) return "Гучність −";
        if ("volume_up".equals(action)) return "Гучність +";
        if ("volume_mute".equals(action)) return "Без звуку";
        if ("media_play_pause".equals(action)) return "Play / Pause";
        if ("media_previous".equals(action)) return "Попередній трек";
        if ("media_next".equals(action)) return "Наступний трек";
        if ("show_desktop".equals(action)) return "Робочий стіл";
        if ("alt_tab".equals(action)) return "Alt + Tab";
        if ("lock".equals(action)) return "Блокування ПК";
        return action;
    }

    private void wakeComputer() {
        final PcProfile profile = PcProfileStore.active(this);
        if (profile == null || profile.mac == null || profile.mac.trim().length() == 0) {
            FeedbackController.failure(this);
            Toast.makeText(this, "У профілі не вказана MAC-адреса", Toast.LENGTH_LONG).show();
            return;
        }
        statusText.setText("Надсилаю Wake-on-LAN…");
        new AsyncTask<Void, Void, String>() {
            private boolean ok;
            @Override protected String doInBackground(Void... values) {
                try {
                    WakeOnLan.send(profile.mac, profile.broadcast, 9);
                    ok = true;
                    return "Magic Packet надіслано для " + profile.name;
                } catch (Exception exception) {
                    return exception.getMessage() == null ? "Wake-on-LAN не виконано" : exception.getMessage();
                }
            }
            @Override protected void onPostExecute(String detail) {
                if (destroyed) { return; }
                if (ok) FeedbackController.success(NativeRemoteActivity.this);
                else FeedbackController.failure(NativeRemoteActivity.this);
                statusText.setText((ok ? "OK: " : "ПОМИЛКА: ") + detail);
                Toast.makeText(NativeRemoteActivity.this, detail, Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    private void renderReady() {
        statusText.setText(AppPreferences.activePcName(this) + " · " + AppPreferences.baseUrl(this));
    }

    private void applyWindowPreferences() {
        if (AppPreferences.get(this).getBoolean(AppPreferences.FULLSCREEN, true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (AppPreferences.get(this).getBoolean(AppPreferences.KEEP_SCREEN_ON, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        DisplayController.applyBrightness(this, AppPreferences.panelBrightness(this));
    }

    private void resetIdleTimer() {
        handler.removeCallbacks(dimRunnable);
        DisplayController.applyBrightness(this, AppPreferences.panelBrightness(this));
        int seconds = AppPreferences.idleDimSeconds(this);
        if (resumed && seconds > 0) {
            handler.postDelayed(dimRunnable, seconds * 1000L);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            resetIdleTimer();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        resetIdleTimer();
        if (AppPreferences.hardwareKeysEnabled(this)
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpLongHandled = false;
                else volumeDownLongHandled = false;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this) && AppPreferences.longPressMediaEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeUpLongHandled = true;
                executeAction("media_next");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeDownLongHandled = true;
                executeAction("media_previous");
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (!volumeUpLongHandled) executeAction("volume_up");
                volumeUpLongHandled = false;
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (!volumeDownLongHandled) executeAction("volume_down");
                volumeDownLongHandled = false;
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        applyWindowPreferences();
        renderReady();
        resetIdleTimer();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(dimRunnable);
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        destroyed = true;
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
