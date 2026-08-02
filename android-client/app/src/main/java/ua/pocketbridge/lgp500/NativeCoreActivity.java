package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class NativeCoreActivity extends Activity {
    private static final int PAGE_PANEL = 0;
    private static final int PAGE_TOUCH = 1;
    private static final int PAGE_KEYS = 2;
    private static final int PAGE_MORE = 3;

    private Handler handler;
    private NativeRequestQueue commandQueue;
    private NativeRequestQueue mouseQueue;
    private TextView connectionText;
    private TextView activeText;
    private TextView commandText;
    private TextView perfText;
    private Spinner profileSpinner;
    private Button[] actionButtons;
    private JSONObject[] actionTargets;
    private TextView emptyProfileText;
    private ArrayAdapter<String> profileAdapter;
    private final ArrayList<String> profileLabels = new ArrayList<String>();
    private RuntimeTuner runtimeTuner;
    private View panelPage;
    private View touchPage;
    private View keysPage;
    private View morePage;
    private View touchSurface;
    private EditText textInput;
    private JSONArray profiles = new JSONArray();
    private String profileId = "";
    private String activeConnectionKey = "";
    private boolean loadingProfiles;
    private boolean resumed;
    private boolean destroyed;
    private boolean statusInFlight;
    private boolean bootstrapInFlight;
    private int statusFailures;
    private int statusCycle;
    private int statusIntervalMs = 8000;
    private int idleIntervalMs = 20000;
    private int configuredTouchIntervalMs = 80;
    private int touchIntervalMs = 80;
    private long lastInteractionAt;
    private long lastDownAt;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private int pendingDx;
    private int pendingDy;
    private boolean mouseInFlight;
    private boolean volumeUpLongHandled;
    private boolean volumeDownLongHandled;

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() { requestStatus(); }
    };

    private final Runnable touchFlushRunnable = new Runnable() {
        @Override public void run() { flushTouchMovement(); }
    };

    private final Runnable dimRunnable = new Runnable() {
        @Override public void run() {
            DisplayController.applyBrightness(
                    NativeCoreActivity.this,
                    AppPreferences.idleBrightness(NativeCoreActivity.this));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (!AppPreferences.isConfigured(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }
        handler = new Handler();
        runtimeTuner = new RuntimeTuner(this);
        commandQueue = new NativeRequestQueue(handler, runtimeTuner.commandQueueCapacity());
        mouseQueue = new NativeRequestQueue(handler, runtimeTuner.mouseQueueCapacity());
        applyWindowPreferences();
        setContentView(R.layout.activity_native_core);
        bindViews();
        bindNavigation();
        bindTouchpad();
        bindKeyboard();
        bindMore();
        showPage(PAGE_PANEL);
        activeConnectionKey = AppPreferences.baseUrl(this) + "|" + AppPreferences.token(this);
        JSONObject cached = NativeCache.loadBootstrap(this);
        if (cached != null) renderBootstrap(cached, true);
        requestBootstrap();
        DiagnosticLog.write(this, "native_core_start", "client=" + BuildConfig.VERSION_NAME);
    }

    private void bindViews() {
        connectionText = (TextView) findViewById(R.id.core_connection);
        activeText = (TextView) findViewById(R.id.core_active);
        commandText = (TextView) findViewById(R.id.core_command_status);
        perfText = (TextView) findViewById(R.id.core_perf);
        profileSpinner = (Spinner) findViewById(R.id.core_profile_spinner);
        emptyProfileText = (TextView) findViewById(R.id.core_empty_profile);
        actionButtons = new Button[] {
                (Button) findViewById(R.id.core_action_1),
                (Button) findViewById(R.id.core_action_2),
                (Button) findViewById(R.id.core_action_3),
                (Button) findViewById(R.id.core_action_4),
                (Button) findViewById(R.id.core_action_5),
                (Button) findViewById(R.id.core_action_6)
        };
        actionTargets = new JSONObject[actionButtons.length];
        for (int i = 0; i < actionButtons.length; i++) {
            final int index = i;
            actionButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    executeTarget(actionTargets[index], false);
                }
            });
            actionButtons[i].setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) {
                    JSONObject target = actionTargets[index];
                    JSONObject longTarget = target == null ? null : target.optJSONObject("long_press");
                    if (longTarget == null) return false;
                    executeTarget(longTarget, true);
                    return true;
                }
            });
        }
        profileAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, profileLabels);
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(profileAdapter);
        panelPage = findViewById(R.id.core_panel_page);
        touchPage = findViewById(R.id.core_touch_page);
        keysPage = findViewById(R.id.core_keys_page);
        morePage = findViewById(R.id.core_more_page);
        touchSurface = findViewById(R.id.core_touch_surface);
        textInput = (EditText) findViewById(R.id.core_text_input);
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loadingProfiles || position < 0 || position >= profiles.length()) return;
                JSONObject profile = profiles.optJSONObject(position);
                if (profile == null) return;
                profileId = profile.optString("id", "");
                NativeCache.selectedProfile(NativeCoreActivity.this, profileId);
                renderButtons();
                touchActivity();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void bindNavigation() {
        bindPageButton(R.id.core_nav_panel, PAGE_PANEL);
        bindPageButton(R.id.core_nav_touch, PAGE_TOUCH);
        bindPageButton(R.id.core_nav_keys, PAGE_KEYS);
        bindPageButton(R.id.core_nav_more, PAGE_MORE);
    }

    private void bindPageButton(int id, final int page) {
        ((Button) findViewById(id)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(page); }
        });
    }

    private void bindTouchpad() {
        touchSurface.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                touchActivity();
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    lastDownAt = System.currentTimeMillis();
                    downX = lastX = event.getX();
                    downY = lastY = event.getY();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    float x = event.getX();
                    float y = event.getY();
                    int dx = Math.round(x - lastX);
                    int dy = Math.round(y - lastY);
                    lastX = x;
                    lastY = y;
                    if (Math.abs(dx) + Math.abs(dy) >= 2) {
                        synchronized (NativeCoreActivity.this) {
                            pendingDx = clamp(pendingDx + dx, -600, 600);
                            pendingDy = clamp(pendingDy + dy, -600, 600);
                        }
                        handler.removeCallbacks(touchFlushRunnable);
                        handler.postDelayed(touchFlushRunnable, touchIntervalMs);
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    float distance = Math.abs(event.getX() - downX) + Math.abs(event.getY() - downY);
                    long duration = System.currentTimeMillis() - lastDownAt;
                    if (action == MotionEvent.ACTION_UP && distance < 14f && duration < 450L) {
                        sendMouse("left_click", 0, 0, 0, false);
                    } else {
                        flushTouchMovement();
                    }
                    return true;
                }
                return true;
            }
        });
        bindMouseButton(R.id.core_left_click, "left_click", 0);
        bindMouseButton(R.id.core_right_click, "right_click", 0);
        bindMouseButton(R.id.core_double_click, "double_click", 0);
        bindMouseButton(R.id.core_scroll_up, "scroll", 3);
        bindMouseButton(R.id.core_scroll_down, "scroll", -3);
    }

    private void bindMouseButton(int id, final String kind, final int delta) {
        ((Button) findViewById(id)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                touchActivity();
                sendMouse(kind, 0, 0, delta, true);
            }
        });
    }

    private void bindKeyboard() {
        bindKey(R.id.core_key_escape, "escape");
        bindKey(R.id.core_key_tab, "tab");
        bindKey(R.id.core_key_enter, "enter");
        bindKey(R.id.core_key_backspace, "backspace");
        bindKey(R.id.core_key_left, "left");
        bindKey(R.id.core_key_up, "up");
        bindKey(R.id.core_key_down, "down");
        bindKey(R.id.core_key_right, "right");
        bindKey(R.id.core_key_ctrl_c, "ctrl_c");
        bindKey(R.id.core_key_ctrl_v, "ctrl_v");
        bindKey(R.id.core_key_ctrl_z, "ctrl_z");
        bindKey(R.id.core_key_alt_f4, "alt_f4");
        ((Button) findViewById(R.id.core_send_text)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { sendTypedText(); }
        });
        textInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    sendTypedText();
                    return true;
                }
                return false;
            }
        });
    }

    private void bindKey(int id, final String key) {
        ((Button) findViewById(id)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                touchActivity();
                commandText.setText("Надсилаю: " + key + "…");
                commandQueue.submit(new NativeRequestQueue.Request() {
                    @Override public P500ApiClient.Result run() {
                        return P500ApiClient.key(getApplicationContext(), key);
                    }
                }, commandCallback("Клавіша " + key));
            }
        });
    }

    private void sendTypedText() {
        String value = textInput.getText().toString();
        if (value.length() == 0) {
            Toast.makeText(this, "Введи текст", Toast.LENGTH_SHORT).show();
            return;
        }
        if (value.length() > 256) value = value.substring(0, 256);
        final String text = value;
        commandText.setText("Надсилаю текст…");
        commandQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.text(getApplicationContext(), text);
            }
        }, new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                renderCommandResult(result, "Текст надіслано");
                if (result.ok) {
                    textInput.setText("");
                    InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (manager != null) manager.hideSoftInputFromWindow(textInput.getWindowToken(), 0);
                }
            }
        });
    }

    private void bindMore() {
        ((Button) findViewById(R.id.core_web_tools)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(NativeCoreActivity.this, MainActivity.class);
                intent.putExtra("force_web", true);
                startActivity(intent);
            }
        });
        ((Button) findViewById(R.id.core_wake)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { wakeComputer(); }
        });
        ((Button) findViewById(R.id.core_backup_remote)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(NativeCoreActivity.this, NativeRemoteActivity.class));
            }
        });
        ((Button) findViewById(R.id.core_refresh)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                NativeCache.clear(NativeCoreActivity.this);
                requestBootstrap();
            }
        });
        ((Button) findViewById(R.id.core_settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(NativeCoreActivity.this, SettingsActivity.class));
            }
        });
        ((Button) findViewById(R.id.core_diagnostics)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exportDiagnostics(); }
        });
    }

    private void requestBootstrap() {
        if (bootstrapInFlight || destroyed) return;
        bootstrapInFlight = true;
        commandText.setText("Оновлення конфігурації…");
        final String etag = NativeCache.bootstrapEtag(this);
        commandQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.bootstrap(getApplicationContext(), etag);
            }
        }, new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                bootstrapInFlight = false;
                runtimeTuner.record(result);
                if (result.ok && result.notModified) {
                    JSONObject cached = NativeCache.loadBootstrap(NativeCoreActivity.this);
                    if (cached != null) {
                        renderBootstrap(cached, true);
                        commandText.setText("Конфігурація з кешу актуальна");
                        statusFailures = 0;
                    } else {
                        NativeCache.bootstrapEtag(NativeCoreActivity.this, "");
                        commandText.setText("Кеш відсутній, повторюю…");
                        handler.postDelayed(new Runnable() {
                            @Override public void run() { requestBootstrap(); }
                        }, 300L);
                        return;
                    }
                } else if (result.ok) {
                    NativeCache.saveBootstrap(NativeCoreActivity.this, result.data);
                    NativeCache.bootstrapEtag(NativeCoreActivity.this, result.etag);
                    renderBootstrap(result.data, false);
                    commandText.setText("Готово");
                    statusFailures = 0;
                    runtimeTuner.clearMemoryPressure();
                } else {
                    renderOffline(result);
                }
                scheduleStatus(result.ok ? 700L : 5000L);
            }
        });
    }

    private void renderBootstrap(JSONObject data, boolean cached) {
        profiles = data.optJSONArray("p");
        if (profiles == null) profiles = new JSONArray();
        statusIntervalMs = Math.max(3000, data.optInt("s", 8) * 1000);
        idleIntervalMs = Math.max(10000, data.optInt("i", 20) * 1000);
        configuredTouchIntervalMs = clamp(data.optInt("t", 80), 60, 200);
        touchIntervalMs = runtimeTuner.touchInterval(configuredTouchIntervalMs);
        renderProfiles(data.optString("d", ""));
        JSONObject initialStatus = data.optJSONObject("z");
        if (initialStatus != null) renderStatus(initialStatus, 0L);
        long cacheAgeMs = NativeCache.bootstrapAgeMs(this);
        long ageSeconds = cacheAgeMs == Long.MAX_VALUE ? -1L : cacheAgeMs / 1000L;
        perfText.setText(runtimeTuner.summary() + (cached && ageSeconds >= 0 ? " · кеш " + ageSeconds + "с" : ""));
    }

    private void renderProfiles(String defaultId) {
        loadingProfiles = true;
        profileLabels.clear();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            profileLabels.add(profile == null ? "Профіль" : profile.optString("name", "Профіль"));
        }
        profileAdapter.notifyDataSetChanged();
        String selected = NativeCache.selectedProfile(this);
        if (!profileExists(selected)) selected = defaultId;
        if (!profileExists(selected) && profiles.length() > 0) {
            JSONObject first = profiles.optJSONObject(0);
            selected = first == null ? "" : first.optString("id", "");
        }
        profileId = selected;
        int position = profilePosition(profileId);
        if (position >= 0) profileSpinner.setSelection(position);
        loadingProfiles = false;
        renderButtons();
    }

    private void renderButtons() {
        JSONObject profile = profileById(profileId);
        JSONArray buttons = profile == null ? null : profile.optJSONArray("buttons");
        int count = buttons == null ? 0 : Math.min(actionButtons.length, buttons.length());
        emptyProfileText.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < actionButtons.length; i++) {
            Button button = actionButtons[i];
            JSONObject target = i < count ? buttons.optJSONObject(i) : null;
            actionTargets[i] = target;
            if (target == null) {
                button.setVisibility(View.INVISIBLE);
                button.setText("");
                button.setLongClickable(false);
                continue;
            }
            String icon = target.optString("icon", "");
            String label = target.optString("label", "Команда");
            if (icon.length() > 0 && icon.length() <= 3) label = icon + "  " + label;
            button.setText(label);
            String style = target.optString("style", "");
            button.setBackgroundResource("danger".equals(style)
                    ? R.drawable.button_danger
                    : ("primary".equals(style) ? R.drawable.button_primary : R.drawable.button_secondary));
            button.setLongClickable(target.optJSONObject("long_press") != null);
            button.setVisibility(View.VISIBLE);
        }
    }


    private void executeTarget(final JSONObject target, boolean longPress) {
        if (target == null) return;
        touchActivity();
        if (isDangerous(target)) {
            new AlertDialog.Builder(this)
                    .setTitle("Підтвердити дію")
                    .setMessage(target.optString("label", target.optString("action", "Небезпечна команда")))
                    .setNegativeButton("Скасувати", null)
                    .setPositiveButton("Виконати", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            dispatchTarget(target, true);
                        }
                    }).show();
        } else {
            dispatchTarget(target, false);
        }
    }

    private void dispatchTarget(final JSONObject target, final boolean confirm) {
        String label = target.optString("label", target.optString("action", "Команда"));
        commandText.setText("Виконую: " + label + "…");
        commandQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.executeTarget(getApplicationContext(), target, confirm);
            }
        }, commandCallback(label));
    }

    private NativeRequestQueue.Callback commandCallback(final String label) {
        return new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                renderCommandResult(result, label);
            }
        };
    }

    private void renderCommandResult(P500ApiClient.Result result, String label) {
        runtimeTuner.record(result);
        if (result.ok) {
            FeedbackController.success(this);
            commandText.setText("OK: " + label + " · " + result.latencyMs + " мс");
            statusFailures = 0;
            scheduleStatus(900L);
        } else {
            FeedbackController.failure(this);
            commandText.setText("Помилка: " + result.detail);
            Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show();
        }
        DiagnosticLog.write(this, result.ok ? "native_core_command_ok" : "native_core_command_failed",
                label + " http=" + result.httpCode + " latency=" + result.latencyMs + " " + result.detail);
    }

    private void requestStatus() {
        handler.removeCallbacks(statusRunnable);
        if (!resumed || destroyed || statusInFlight) return;
        statusInFlight = true;
        final boolean media = (++statusCycle % runtimeTuner.mediaEvery()) == 0;
        commandQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.status(getApplicationContext(), media);
            }
        }, new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                statusInFlight = false;
                runtimeTuner.record(result);
                if (result.ok) {
                    statusFailures = 0;
                    renderStatus(result.data, result.latencyMs);
                } else {
                    statusFailures++;
                    renderOffline(result);
                }
                scheduleStatus(nextStatusDelay());
            }
        });
    }

    private void renderStatus(JSONObject data, long latencyMs) {
        int cpu = data.optInt("c", 0);
        int ram = data.optInt("r", 0);
        boolean control = data.optInt("e", 1) == 1;
        connectionText.setText("● " + AppPreferences.activePcName(this) + "   CPU " + cpu + "%   RAM " + ram + "%");
        connectionText.setTextColor(getResources().getColor(control ? R.color.status_ok : R.color.status_warn));
        JSONObject media = data.optJSONObject("m");
        if (media != null && media.optString("t", "").length() > 0) {
            activeText.setText(media.optString("t", "") + " · " + media.optString("a", ""));
        } else {
            String active = data.optString("p", "");
            if (active.length() == 0) active = data.optString("w", "—");
            activeText.setText(active);
        }
        String suggestion = data.optString("sp", "");
        if (suggestion.length() > 0 && profileExists(suggestion)
                && System.currentTimeMillis() - lastInteractionAt > 15000L) {
            profileId = suggestion;
            int position = profilePosition(profileId);
            if (position >= 0) profileSpinner.setSelection(position);
            renderButtons();
        }
        perfText.setText(runtimeTuner.summary() + " · q" + commandQueue.pending());
    }

    private void renderOffline(P500ApiClient.Result result) {
        connectionText.setText("● ПК недоступний · HTTP " + result.httpCode);
        connectionText.setTextColor(getResources().getColor(R.color.status_error));
        activeText.setText(result.detail);
        perfText.setText(runtimeTuner.summary() + " · повтор " + (nextStatusDelay() / 1000) + "с");
    }

    private void sendMouse(final String kind, final int dx, final int dy, final int delta, final boolean feedback) {
        mouseQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.mouse(getApplicationContext(), kind, dx, dy, delta);
            }
        }, feedback ? new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                runtimeTuner.record(result);
                if (result.ok) FeedbackController.success(NativeCoreActivity.this);
                else {
                    FeedbackController.failure(NativeCoreActivity.this);
                    commandText.setText("Миша: " + result.detail);
                }
            }
        } : null);
    }

    private void flushTouchMovement() {
        if (mouseInFlight || destroyed) return;
        final int dx;
        final int dy;
        synchronized (this) {
            dx = clamp(pendingDx, -300, 300);
            dy = clamp(pendingDy, -300, 300);
            pendingDx -= dx;
            pendingDy -= dy;
        }
        if (dx == 0 && dy == 0) return;
        mouseInFlight = true;
        mouseQueue.submit(new NativeRequestQueue.Request() {
            @Override public P500ApiClient.Result run() {
                return P500ApiClient.mouse(getApplicationContext(), "move", dx, dy, 0);
            }
        }, new NativeRequestQueue.Callback() {
            @Override public void complete(P500ApiClient.Result result) {
                mouseInFlight = false;
                runtimeTuner.record(result);
                touchIntervalMs = runtimeTuner.touchInterval(configuredTouchIntervalMs);
                if (!result.ok) {
                    synchronized (NativeCoreActivity.this) {
                        pendingDx = 0;
                        pendingDy = 0;
                    }
                    commandText.setText("Тачпад: " + result.detail);
                } else {
                    synchronized (NativeCoreActivity.this) {
                        if (pendingDx != 0 || pendingDy != 0) {
                            handler.postDelayed(touchFlushRunnable, touchIntervalMs);
                        }
                    }
                }
            }
        });
    }

    private void showPage(int page) {
        panelPage.setVisibility(page == PAGE_PANEL ? View.VISIBLE : View.GONE);
        touchPage.setVisibility(page == PAGE_TOUCH ? View.VISIBLE : View.GONE);
        keysPage.setVisibility(page == PAGE_KEYS ? View.VISIBLE : View.GONE);
        morePage.setVisibility(page == PAGE_MORE ? View.VISIBLE : View.GONE);
        touchActivity();
    }

    private void wakeComputer() {
        final PcProfile profile = PcProfileStore.active(this);
        if (profile == null || profile.mac == null || profile.mac.trim().length() == 0) {
            Toast.makeText(this, "У профілі не вказана MAC-адреса", Toast.LENGTH_LONG).show();
            return;
        }
        commandText.setText("Надсилаю Wake-on-LAN…");
        new Thread(new Runnable() {
            @Override public void run() {
                String detail;
                boolean ok;
                try {
                    WakeOnLan.send(profile.mac, profile.broadcast, 9);
                    detail = "Magic Packet надіслано";
                    ok = true;
                } catch (Exception exception) {
                    detail = exception.getMessage() == null ? "Wake-on-LAN не виконано" : exception.getMessage();
                    ok = false;
                }
                final String finalDetail = detail;
                final boolean finalOk = ok;
                handler.post(new Runnable() {
                    @Override public void run() {
                        commandText.setText((finalOk ? "OK: " : "Помилка: ") + finalDetail);
                        if (finalOk) FeedbackController.success(NativeCoreActivity.this);
                        else FeedbackController.failure(NativeCoreActivity.this);
                    }
                });
            }
        }, "PocketBridge-WOL").start();
    }

    private void exportDiagnostics() {
        try {
            String path = DiagnosticLog.export(this);
            Toast.makeText(this, "Діагностику збережено:\n" + path, Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void touchActivity() {
        lastInteractionAt = System.currentTimeMillis();
        handler.removeCallbacks(dimRunnable);
        DisplayController.applyBrightness(this, AppPreferences.panelBrightness(this));
        int seconds = AppPreferences.idleDimSeconds(this);
        if (resumed && seconds > 0) handler.postDelayed(dimRunnable, seconds * 1000L);
    }

    private void applyWindowPreferences() {
        if (AppPreferences.get(this).getBoolean(AppPreferences.FULLSCREEN, true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (AppPreferences.get(this).getBoolean(AppPreferences.KEEP_SCREEN_ON, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        DisplayController.applyBrightness(this, AppPreferences.panelBrightness(this));
    }

    private void scheduleStatus(long delay) {
        handler.removeCallbacks(statusRunnable);
        if (resumed && !destroyed) handler.postDelayed(statusRunnable, Math.max(500L, delay));
    }

    private long nextStatusDelay() {
        if (statusFailures > 0) {
            int shift = Math.min(2, statusFailures - 1);
            return Math.min(30000, 5000 << shift);
        }
        boolean idle = System.currentTimeMillis() - lastInteractionAt > 30000L;
        return runtimeTuner.statusDelay(statusIntervalMs, idleIntervalMs, idle);
    }

    private JSONObject profileById(String id) {
        int position = profilePosition(id);
        return position < 0 ? null : profiles.optJSONObject(position);
    }

    private boolean profileExists(String id) { return profilePosition(id) >= 0; }

    private int profilePosition(String id) {
        if (id == null || id.length() == 0) return -1;
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && id.equals(profile.optString("id", ""))) return i;
        }
        return -1;
    }

    private boolean isDangerous(JSONObject target) {
        String action = target.optString("action", "");
        return target.optBoolean("dangerous", false)
                || "shutdown".equals(action) || "restart".equals(action);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) touchActivity();
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        touchActivity();
        if (AppPreferences.hardwareKeysEnabled(this)
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpLongHandled = false;
                else volumeDownLongHandled = false;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && LauncherMode.isEnabled(this)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this) && AppPreferences.longPressMediaEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeUpLongHandled = true;
                executeSimpleAction("media_next");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeDownLongHandled = true;
                executeSimpleAction("media_previous");
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (!volumeUpLongHandled) executeSimpleAction("volume_up");
                volumeUpLongHandled = false;
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (!volumeDownLongHandled) executeSimpleAction("volume_down");
                volumeDownLongHandled = false;
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void executeSimpleAction(String action) {
        try {
            JSONObject target = new JSONObject();
            target.put("action", action);
            dispatchTarget(target, false);
        } catch (Exception ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        applyWindowPreferences();
        touchActivity();
        if (!AppPreferences.nativeCoreMode(this)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("force_web", true);
            startActivity(intent);
            finish();
            return;
        }
        String key = AppPreferences.baseUrl(this) + "|" + AppPreferences.token(this);
        if (!key.equals(activeConnectionKey)) {
            activeConnectionKey = key;
            profiles = new JSONArray();
            NativeCache.clear(this);
            requestBootstrap();
        } else {
            scheduleStatus(500L);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(statusRunnable);
        handler.removeCallbacks(touchFlushRunnable);
        handler.removeCallbacks(dimRunnable);
        super.onPause();
    }

    @Override public void onLowMemory() {
        runtimeTuner.onLowMemory();
        NativeCache.bootstrapEtag(this, "");
        handler.removeCallbacks(statusRunnable);
        statusFailures = Math.max(statusFailures, 1);
        perfText.setText(runtimeTuner.summary() + " · low memory");
        scheduleStatus(15000L);
        DiagnosticLog.write(this, "native_low_memory", runtimeTuner.summary());
        super.onLowMemory();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        if (commandQueue != null) commandQueue.close();
        if (mouseQueue != null) mouseQueue.close();
        DiagnosticLog.write(this, "native_core_stop", "");
        super.onDestroy();
    }
}
