package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int MENU_RELOAD = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_NATIVE_REMOTE = 3;
    private static final int MENU_DIAGNOSTICS = 4;
    private static final int MENU_EXIT = 5;
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;
    private static final int HEARTBEAT_FAILURE_LIMIT = 2;

    private WebView webView;
    private LinearLayout offlinePanel;
    private TextView offlineDetail;
    private Handler handler;
    private boolean pageLoaded;
    private boolean pageLoading;
    private boolean loadFailed;
    private boolean destroyed;
    private boolean resumed;
    private boolean receiverRegistered;
    private boolean heartbeatRunning;
    private boolean authenticationFailed;
    private int heartbeatFailures;
    private String currentPanelUrl;
    private String lastFailureDetail = "";
    private boolean volumeUpLongHandled;
    private boolean volumeDownLongHandled;
    private boolean forceFullUi;

    private final Runnable dimRunnable = new Runnable() {
        @Override
        public void run() {
            DisplayController.applyBrightness(
                    MainActivity.this,
                    AppPreferences.idleBrightness(MainActivity.this));
        }
    };

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !resumed || authenticationFailed || pageLoaded || pageLoading || !autoReconnectEnabled()) {
                return;
            }
            loadPanel();
        }
    };

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            startHeartbeat();
        }
    };

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (destroyed || !resumed || webView == null) {
                return;
            }
            if (isNetworkConnected()) {
                DiagnosticLog.write(MainActivity.this, "network", "connected");
                authenticationFailed = false;
                if ((!pageLoaded && !pageLoading) || offlinePanel.getVisibility() == View.VISIBLE) {
                    loadPanel();
                } else {
                    startHeartbeat();
                }
            } else {
                DiagnosticLog.write(MainActivity.this, "network", "disconnected");
                pageLoaded = false;
                pageLoading = false;
                showOfflinePanel("Wi-Fi або мережеве підключення відсутнє");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        handler = new Handler();

        if (!AppPreferences.isConfigured(this)) {
            openSettings(true);
            return;
        }

        forceFullUi = getIntent().getBooleanExtra("full_ui", false);
        if (AppPreferences.nativeCoreMode(this)
                && !getIntent().getBooleanExtra("force_web", false)) {
            startActivity(new Intent(this, NativeCoreActivity.class));
            finish();
            return;
        }

        applyWindowPreferences();
        setContentView(R.layout.activity_main);
        bindViews();
        configureWebView();
        DiagnosticLog.write(this, "app_start", "client=" + BuildConfig.VERSION_NAME);
        loadPanel();
    }

    private void bindViews() {
        webView = (WebView) findViewById(R.id.web_view);
        offlinePanel = (LinearLayout) findViewById(R.id.offline_panel);
        offlineDetail = (TextView) findViewById(R.id.offline_detail);
        Button wakeButton = (Button) findViewById(R.id.wake_offline_button);
        Button retryButton = (Button) findViewById(R.id.retry_button);
        Button settingsButton = (Button) findViewById(R.id.settings_button);
        Button nativeButton = (Button) findViewById(R.id.native_remote_button);
        Button diagnosticsButton = (Button) findViewById(R.id.diagnostics_button);

        wakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wakeActiveComputer();
            }
        });
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                authenticationFailed = false;
                loadPanel();
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSettings(false);
            }
        });
        nativeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNativeRemote();
            }
        });
        diagnosticsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportDiagnostics();
            }
        });
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        settings.setAppCacheEnabled(false);
        boolean liteWeb = AppPreferences.p500LiteMode(this) && !forceFullUi;
        settings.setCacheMode(liteWeb
                ? WebSettings.LOAD_CACHE_ELSE_NETWORK : WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString()
                + " PocketBridgeRemote/" + BuildConfig.VERSION_NAME
                + (liteWeb ? " P500Lite/1" : " FullUI/2"));

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimeType, long contentLength) {
                if (!isAllowedUrl(url)) {
                    DiagnosticLog.write(MainActivity.this, "blocked_download", String.valueOf(url));
                    Toast.makeText(MainActivity.this, "Завантаження заблоковано", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("User-Agent", userAgent == null ? "PocketBridge" : userAgent);
                    request.setTitle(fileName);
                    request.setDescription("PocketBridge");
                    request.setShowRunningNotification(true);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (manager == null) {
                        throw new IllegalStateException("DownloadManager недоступний");
                    }
                    manager.enqueue(request);
                    DiagnosticLog.write(MainActivity.this, "download_started", fileName);
                    Toast.makeText(MainActivity.this, "Завантаження: " + fileName, Toast.LENGTH_LONG).show();
                } catch (RuntimeException exception) {
                    DiagnosticLog.write(MainActivity.this, "download_failed", exception.toString());
                    Toast.makeText(MainActivity.this, "Не вдалося завантажити файл", Toast.LENGTH_LONG).show();
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isAllowedUrl(url)) {
                    view.loadUrl(url);
                } else {
                    DiagnosticLog.write(MainActivity.this, "blocked_url", String.valueOf(url));
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageLoaded = false;
                pageLoading = true;
                loadFailed = false;
                hideOfflinePanel();
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoading = false;
                if (!loadFailed) {
                    pageLoaded = true;
                    handler.removeCallbacks(reconnectRunnable);
                    hideOfflinePanel();
                    startHeartbeat();
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl == null || currentPanelUrl == null
                        || sameWithoutFragment(failingUrl, currentPanelUrl)) {
                    pageLoaded = false;
                    pageLoading = false;
                    loadFailed = true;
                    lastFailureDetail = description == null ? "Помилка WebView " + errorCode : description;
                    DiagnosticLog.write(MainActivity.this, "webview_error",
                            errorCode + " " + lastFailureDetail);
                    showOfflinePanel(lastFailureDetail);
                    scheduleReconnect();
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });
    }

    private boolean sameWithoutFragment(String first, String second) {
        int firstHash = first.indexOf('#');
        int secondHash = second.indexOf('#');
        String left = firstHash >= 0 ? first.substring(0, firstHash) : first;
        String right = secondHash >= 0 ? second.substring(0, secondHash) : second;
        return left.equals(right);
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            Uri requested = Uri.parse(url);
            Uri base = Uri.parse(AppPreferences.baseUrl(this));
            String requestedHost = requested.getHost();
            String baseHost = base.getHost();
            return "http".equalsIgnoreCase(requested.getScheme())
                    && requestedHost != null && baseHost != null
                    && requestedHost.equalsIgnoreCase(baseHost)
                    && requested.getPort() == base.getPort();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void loadPanel() {
        if (!AppPreferences.isConfigured(this) || webView == null) {
            openSettings(true);
            return;
        }
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(heartbeatRunnable);
        pageLoaded = false;
        pageLoading = false;
        loadFailed = false;
        currentPanelUrl = forceFullUi
                ? AppPreferences.fullPanelUrl(this)
                : AppPreferences.panelUrl(this);
        if (!isNetworkConnected()) {
            showOfflinePanel("Wi-Fi або мережеве підключення відсутнє");
            scheduleReconnect();
            return;
        }
        hideOfflinePanel();
        DiagnosticLog.write(this, "load_panel", AppPreferences.baseUrl(this));
        pageLoading = true;
        webView.loadUrl(currentPanelUrl);
    }

    private void startHeartbeat() {
        if (destroyed || !resumed || heartbeatRunning || pageLoading || !AppPreferences.isConfigured(this)
                || !isNetworkConnected() || authenticationFailed) {
            return;
        }
        handler.removeCallbacks(heartbeatRunnable);
        new HeartbeatTask().execute();
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        if (!destroyed && resumed && !authenticationFailed) {
            handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        }
    }

    private final class HeartbeatTask extends AsyncTask<Void, Void, ServerProbe.Result> {
        @Override
        protected void onPreExecute() {
            heartbeatRunning = true;
        }

        @Override
        protected ServerProbe.Result doInBackground(Void... values) {
            return ServerProbe.check(getApplicationContext());
        }

        @Override
        protected void onPostExecute(ServerProbe.Result result) {
            heartbeatRunning = false;
            if (destroyed) {
                return;
            }
            String previousVersion = AppPreferences.lastServerVersion(MainActivity.this);
            AppPreferences.storeProbe(MainActivity.this, result);
            if (!resumed) {
                return;
            }
            if (result.ok) {
                heartbeatFailures = 0;
                authenticationFailed = false;
                DiagnosticLog.write(MainActivity.this, "heartbeat_ok",
                        "http=" + result.httpCode + " latency=" + result.latencyMs
                                + "ms server=" + result.serverVersion);
                if (result.serverVersion.length() > 0
                        && !"невідомо".equals(previousVersion)
                        && !result.serverVersion.equals(previousVersion)) {
                    DiagnosticLog.write(MainActivity.this, "server_updated",
                            previousVersion + " -> " + result.serverVersion);
                    webView.clearCache(true);
                    webView.reload();
                } else if (!pageLoaded) {
                    loadPanel();
                    return;
                }
                if (result.upgradeRecommended) {
                    lastFailureDetail = "Рекомендовано оновити APK";
                }
            } else {
                heartbeatFailures += 1;
                lastFailureDetail = result.detail;
                DiagnosticLog.write(MainActivity.this, "heartbeat_failed",
                        "http=" + result.httpCode + " " + result.detail);
                if (result.httpCode == 401 || result.httpCode == 429) {
                    authenticationFailed = true;
                    pageLoaded = false;
                    pageLoading = false;
                    showOfflinePanel(result.detail);
                    return;
                }
                if (heartbeatFailures >= HEARTBEAT_FAILURE_LIMIT) {
                    pageLoaded = false;
                    pageLoading = false;
                    showOfflinePanel(result.detail);
                    scheduleReconnect();
                }
            }
            scheduleHeartbeat();
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showOfflinePanel(String detail) {
        if (offlinePanel == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append((detail == null || detail.length() == 0)
                ? getString(R.string.offline_hint)
                : detail + "\n\n" + getString(R.string.offline_hint));
        message.append("\n\nСервер: ").append(AppPreferences.baseUrl(this));
        message.append("\nAPK: ").append(BuildConfig.VERSION_NAME);
        message.append(" · сервер: ").append(AppPreferences.lastServerVersion(this));
        int code = AppPreferences.lastHttpCode(this);
        if (code > 0) {
            message.append("\nHTTP: ").append(code)
                    .append(" · ").append(AppPreferences.lastLatency(this)).append(" мс");
        }
        long lastSuccess = AppPreferences.lastSuccess(this);
        if (lastSuccess > 0) {
            String formatted = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
                    .format(new Date(lastSuccess));
            message.append("\nОстаннє з’єднання: ").append(formatted);
        }
        offlineDetail.setText(message.toString());
        offlinePanel.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void hideOfflinePanel() {
        if (offlinePanel == null) {
            return;
        }
        offlinePanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable);
        if (!destroyed && resumed && autoReconnectEnabled() && !authenticationFailed) {
            handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    private boolean autoReconnectEnabled() {
        return AppPreferences.get(this).getBoolean(AppPreferences.AUTO_RECONNECT, true);
    }

    private void applyWindowPreferences() {
        SharedPreferences preferences = AppPreferences.get(this);
        if (preferences.getBoolean(AppPreferences.FULLSCREEN, true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (preferences.getBoolean(AppPreferences.KEEP_SCREEN_ON, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        DisplayController.applyBrightness(this, AppPreferences.panelBrightness(this));
        resetIdleTimer();
    }

    private void wakeActiveComputer() {
        final PcProfile profile = PcProfileStore.active(this);
        if (profile == null || profile.mac == null || profile.mac.trim().length() == 0) {
            Toast.makeText(this, "У налаштуваннях ПК не вказана MAC-адреса", Toast.LENGTH_LONG).show();
            return;
        }
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... values) {
                try {
                    WakeOnLan.Result result = WakeOnLan.send(
                            getApplicationContext(), profile.mac, profile.broadcast, 9);
                    return result.summary();
                } catch (Exception exception) {
                    return exception.getMessage() == null
                            ? "Wake-on-LAN не виконано" : exception.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (destroyed || !resumed) {
                    return;
                }
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                scheduleReconnect();
            }
        }.execute();
    }

    private void openNativeRemote() {
        startActivity(new Intent(this, NativeCoreActivity.class));
    }

    private void executeNativeAction(final String action) {
        new AsyncTask<Void, Void, NativeActionClient.Result>() {
            @Override
            protected NativeActionClient.Result doInBackground(Void... values) {
                return NativeActionClient.execute(getApplicationContext(), action);
            }

            @Override
            protected void onPostExecute(NativeActionClient.Result result) {
                if (destroyed || !resumed) {
                    return;
                }
                if (result.ok) {
                    FeedbackController.success(MainActivity.this);
                } else {
                    FeedbackController.failure(MainActivity.this);
                    Toast.makeText(MainActivity.this, result.detail, Toast.LENGTH_LONG).show();
                }
                DiagnosticLog.write(MainActivity.this,
                        result.ok ? "hardware_action_ok" : "hardware_action_failed",
                        action + " http=" + result.httpCode + " " + result.detail);
            }
        }.execute();
    }

    private void resetIdleTimer() {
        if (handler == null) {
            return;
        }
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

    private void exportDiagnostics() {
        try {
            String path = DiagnosticLog.export(this);
            Toast.makeText(this, "Діагностику збережено:\n" + path, Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openSettings(boolean finishCurrent) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        if (finishCurrent) {
            finish();
        }
    }

    private void registerNetworkReceiver() {
        if (!receiverRegistered) {
            registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            receiverRegistered = true;
        }
    }

    private void unregisterNetworkReceiver() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(networkReceiver);
            } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        registerNetworkReceiver();
        if (webView != null) {
            applyWindowPreferences();
            resetIdleTimer();
            String updatedUrl = forceFullUi
                    ? AppPreferences.fullPanelUrl(this)
                    : AppPreferences.panelUrl(this);
            if (currentPanelUrl == null || !currentPanelUrl.equals(updatedUrl)) {
                authenticationFailed = false;
                loadPanel();
            } else if (!pageLoaded && !pageLoading) {
                scheduleReconnect();
            } else {
                startHeartbeat();
            }
            if (Build.VERSION.SDK_INT >= 11) {
                webView.onResume();
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(heartbeatRunnable);
        handler.removeCallbacks(dimRunnable);
        unregisterNetworkReceiver();
        if (webView != null && Build.VERSION.SDK_INT >= 11) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        resumed = false;
        unregisterNetworkReceiver();
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
        }
        DiagnosticLog.write(this, "app_stop", lastFailureDetail);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_RELOAD, 0, R.string.menu_reload);
        menu.add(0, MENU_SETTINGS, 1, R.string.menu_settings);
        menu.add(0, MENU_NATIVE_REMOTE, 2, R.string.menu_native_remote);
        menu.add(0, MENU_DIAGNOSTICS, 3, R.string.menu_diagnostics);
        menu.add(0, MENU_EXIT, 4, R.string.menu_exit);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RELOAD:
                authenticationFailed = false;
                webView.clearCache(false);
                loadPanel();
                return true;
            case MENU_SETTINGS:
                openSettings(false);
                return true;
            case MENU_NATIVE_REMOTE:
                openNativeRemote();
                return true;
            case MENU_DIAGNOSTICS:
                exportDiagnostics();
                return true;
            case MENU_EXIT:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        resetIdleTimer();
        if (AppPreferences.hardwareKeysEnabled(this)
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    volumeUpLongHandled = false;
                } else {
                    volumeDownLongHandled = false;
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && LauncherMode.isEnabled(this)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this) && AppPreferences.longPressMediaEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeUpLongHandled = true;
                executeNativeAction("media_next");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeDownLongHandled = true;
                executeNativeAction("media_previous");
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (AppPreferences.hardwareKeysEnabled(this)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (!volumeUpLongHandled) {
                    executeNativeAction("volume_up");
                }
                volumeUpLongHandled = false;
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (!volumeDownLongHandled) {
                    executeNativeAction("volume_down");
                }
                volumeDownLongHandled = false;
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
}
