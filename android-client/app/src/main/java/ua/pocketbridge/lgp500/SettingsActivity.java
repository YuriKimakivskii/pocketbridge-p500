package ua.pocketbridge.lgp500;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private Spinner profileSpinner;
    private Spinner deviceLayoutSpinner;
    private Spinner deviceRoleSpinner;
    private EditText profileNameInput;
    private EditText hostInput;
    private EditText portInput;
    private EditText tokenInput;
    private EditText macInput;
    private EditText broadcastInput;
    private EditText pairingCodeInput;
    private CheckBox fullscreenCheck;
    private CheckBox p500LiteCheck;
    private CheckBox nativeCoreCheck;
    private CheckBox adaptiveRuntimeCheck;
    private CheckBox realtimeInputCheck;
    private CheckBox screenOnCheck;
    private CheckBox reconnectCheck;
    private CheckBox autostartCheck;
    private CheckBox hardwareKeysCheck;
    private CheckBox longPressMediaCheck;
    private CheckBox hapticCheck;
    private CheckBox soundCheck;
    private CheckBox launcherModeCheck;
    private EditText panelBrightnessInput;
    private EditText idleBrightnessInput;
    private EditText idleTimeoutInput;
    private Button testButton;
    private Button discoverButton;
    private Button pairButton;
    private Button wakeButton;
    private Button wolDiagnosticsButton;
    private TextView diagnosticsSummary;
    private List<PcProfile> profiles = new ArrayList<PcProfile>();
    private boolean loadingProfile;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        loadGlobalValues();
        refreshProfiles(null);
        renderDiagnostics();
    }

    private void bindViews() {
        profileSpinner = (Spinner) findViewById(R.id.profile_spinner);
        deviceLayoutSpinner = (Spinner) findViewById(R.id.device_layout_spinner);
        deviceRoleSpinner = (Spinner) findViewById(R.id.device_role_spinner);
        profileNameInput = (EditText) findViewById(R.id.profile_name_input);
        hostInput = (EditText) findViewById(R.id.host_input);
        portInput = (EditText) findViewById(R.id.port_input);
        tokenInput = (EditText) findViewById(R.id.token_input);
        macInput = (EditText) findViewById(R.id.mac_input);
        broadcastInput = (EditText) findViewById(R.id.broadcast_input);
        pairingCodeInput = (EditText) findViewById(R.id.pairing_code_input);
        fullscreenCheck = (CheckBox) findViewById(R.id.fullscreen_check);
        p500LiteCheck = (CheckBox) findViewById(R.id.p500_lite_check);
        nativeCoreCheck = (CheckBox) findViewById(R.id.native_core_check);
        adaptiveRuntimeCheck = (CheckBox) findViewById(R.id.adaptive_runtime_check);
        realtimeInputCheck = (CheckBox) findViewById(R.id.realtime_input_check);
        screenOnCheck = (CheckBox) findViewById(R.id.screen_on_check);
        reconnectCheck = (CheckBox) findViewById(R.id.reconnect_check);
        autostartCheck = (CheckBox) findViewById(R.id.autostart_check);
        hardwareKeysCheck = (CheckBox) findViewById(R.id.hardware_keys_check);
        longPressMediaCheck = (CheckBox) findViewById(R.id.long_press_media_check);
        hapticCheck = (CheckBox) findViewById(R.id.haptic_feedback_check);
        soundCheck = (CheckBox) findViewById(R.id.sound_feedback_check);
        launcherModeCheck = (CheckBox) findViewById(R.id.launcher_mode_check);
        panelBrightnessInput = (EditText) findViewById(R.id.panel_brightness_input);
        idleBrightnessInput = (EditText) findViewById(R.id.idle_brightness_input);
        idleTimeoutInput = (EditText) findViewById(R.id.idle_timeout_input);
        testButton = (Button) findViewById(R.id.test_button);
        discoverButton = (Button) findViewById(R.id.discover_button);
        pairButton = (Button) findViewById(R.id.pair_button);
        wakeButton = (Button) findViewById(R.id.wake_button);
        wolDiagnosticsButton = (Button) findViewById(R.id.wol_diagnostics_button);
        diagnosticsSummary = (TextView) findViewById(R.id.diagnostics_summary);

        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[] {"Авто", "LG Optimus One P500", "Redmi Note 9 Pro / сучасний HD"});
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceLayoutSpinner.setAdapter(deviceAdapter);
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[] {"Автоматично", "Стаціонарна панель", "Мобільний пульт"});
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceRoleSpinner.setAdapter(roleAdapter);

        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!loadingProfile && position >= 0 && position < profiles.size()) {
                    PcProfileStore.setActive(SettingsActivity.this, profiles.get(position).id);
                    showProfile(profiles.get(position));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        ((Button) findViewById(R.id.new_profile_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { createProfile(); }
        });
        ((Button) findViewById(R.id.delete_profile_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { deleteProfile(); }
        });
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { discoverServers(); }
        });
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { pairDevice(); }
        });
        wakeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { wakeComputer(); }
        });
        wolDiagnosticsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { runWolDiagnostics(); }
        });
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { testConnection(); }
        });
        ((Button) findViewById(R.id.save_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { saveAndConnect(); }
        });
        ((Button) findViewById(R.id.open_native_remote_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, NativeCoreActivity.class));
            }
        });
        ((Button) findViewById(R.id.export_diagnostics_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exportDiagnostics(); }
        });
        ((Button) findViewById(R.id.open_autostart_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { SystemSettingsHelper.openAutostart(SettingsActivity.this); }
        });
        ((Button) findViewById(R.id.open_battery_button)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { SystemSettingsHelper.openBattery(SettingsActivity.this); }
        });
    }

    private void loadGlobalValues() {
        SharedPreferences preferences = AppPreferences.get(this);
        fullscreenCheck.setChecked(AppPreferences.fullscreen(this));
        String deviceMode = preferences.getString(AppPreferences.DEVICE_LAYOUT, DeviceLayout.AUTO);
        deviceLayoutSpinner.setSelection(DeviceLayout.P500.equals(deviceMode) ? 1 : (DeviceLayout.MODERN.equals(deviceMode) ? 2 : 0));
        String deviceRole = preferences.getString(AppPreferences.DEVICE_ROLE, "auto");
        deviceRoleSpinner.setSelection("station".equals(deviceRole) ? 1 : ("mobile".equals(deviceRole) ? 2 : 0));
        boolean modernAutoMigration = !preferences.contains(AppPreferences.DEVICE_LAYOUT) && DeviceLayout.isModern(this);
        p500LiteCheck.setChecked(modernAutoMigration ? false
                : preferences.getBoolean(AppPreferences.P500_LITE_MODE, !DeviceLayout.isModern(this)));
        nativeCoreCheck.setChecked(preferences.getBoolean(AppPreferences.NATIVE_CORE_MODE, true));
        adaptiveRuntimeCheck.setChecked(preferences.getBoolean(AppPreferences.ADAPTIVE_RUNTIME, true));
        realtimeInputCheck.setChecked(preferences.getBoolean(AppPreferences.REALTIME_INPUT, true));
        screenOnCheck.setChecked(preferences.getBoolean(AppPreferences.KEEP_SCREEN_ON, true));
        reconnectCheck.setChecked(preferences.getBoolean(AppPreferences.AUTO_RECONNECT, true));
        autostartCheck.setChecked(preferences.getBoolean(AppPreferences.AUTO_START, false));
        hardwareKeysCheck.setChecked(preferences.getBoolean(AppPreferences.HARDWARE_KEYS, true));
        longPressMediaCheck.setChecked(preferences.getBoolean(AppPreferences.LONG_PRESS_MEDIA, true));
        hapticCheck.setChecked(preferences.getBoolean(AppPreferences.HAPTIC_FEEDBACK, true));
        soundCheck.setChecked(preferences.getBoolean(AppPreferences.SOUND_FEEDBACK, false));
        launcherModeCheck.setChecked(preferences.getBoolean(AppPreferences.LAUNCHER_MODE, false));
        panelBrightnessInput.setText(String.valueOf(AppPreferences.panelBrightness(this)));
        idleBrightnessInput.setText(String.valueOf(AppPreferences.idleBrightness(this)));
        idleTimeoutInput.setText(String.valueOf(AppPreferences.idleDimSeconds(this)));
    }

    private void refreshProfiles(String selectId) {
        profiles = PcProfileStore.list(this);
        if (profiles.isEmpty()) {
            PcProfile blank = new PcProfile(PcProfileStore.newId(), "Основний ПК", "", 8765, "", "", "255.255.255.255");
            PcProfileStore.save(this, blank, true);
            profiles = PcProfileStore.list(this);
        }
        PcProfile active = PcProfileStore.active(this);
        if (selectId == null && active != null) {
            selectId = active.id;
        }
        loadingProfile = true;
        ArrayAdapter<PcProfile> adapter = new ArrayAdapter<PcProfile>(this, android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(selectId)) {
                selected = i;
                break;
            }
        }
        profileSpinner.setSelection(selected);
        loadingProfile = false;
        PcProfileStore.setActive(this, profiles.get(selected).id);
        showProfile(profiles.get(selected));
    }

    private void showProfile(PcProfile profile) {
        profileNameInput.setText(profile.name);
        hostInput.setText(profile.host);
        portInput.setText(String.valueOf(profile.port));
        tokenInput.setText(profile.token);
        macInput.setText(profile.mac);
        broadcastInput.setText(profile.broadcast);
    }

    private PcProfile currentProfile(boolean requireToken) {
        String name = profileNameInput.getText().toString().trim();
        String host = AppPreferences.normalizeHost(hostInput.getText().toString());
        String token = tokenInput.getText().toString().trim();
        int port = AppPreferences.parsePort(portInput.getText().toString());
        if (TextUtils.isEmpty(name)) {
            profileNameInput.setError("Вкажи назву ПК");
            return null;
        }
        if (TextUtils.isEmpty(host)) {
            hostInput.setError("Вкажи IP-адресу ПК або скористайся пошуком");
            return null;
        }
        if (requireToken && TextUtils.isEmpty(token)) {
            tokenInput.setError("Введи код парування або токен");
            return null;
        }
        PcProfile selected = selectedProfile();
        String id = selected == null ? PcProfileStore.newId() : selected.id;
        return new PcProfile(id, name, host, port, token,
                macInput.getText().toString().trim(),
                broadcastInput.getText().toString().trim());
    }

    private PcProfile selectedProfile() {
        int position = profileSpinner.getSelectedItemPosition();
        return position >= 0 && position < profiles.size() ? profiles.get(position) : null;
    }

    private void saveGlobalValues() {
        int panelBrightness = AppPreferences.parseBoundedInt(
                panelBrightnessInput.getText().toString(), 90, 1, 100);
        int idleBrightness = AppPreferences.parseBoundedInt(
                idleBrightnessInput.getText().toString(), 20, 1, 100);
        int idleSeconds = AppPreferences.parseBoundedInt(
                idleTimeoutInput.getText().toString(), 60, 0, 3600);
        String deviceMode = DeviceLayout.AUTO;
        int devicePosition = deviceLayoutSpinner.getSelectedItemPosition();
        if (devicePosition == 1) deviceMode = DeviceLayout.P500;
        else if (devicePosition == 2) deviceMode = DeviceLayout.MODERN;
        String deviceRole = selectedDeviceRole();
        AppPreferences.get(this).edit()
                .putString(AppPreferences.DEVICE_LAYOUT, deviceMode)
                .putString(AppPreferences.DEVICE_ROLE, deviceRole)
                .putBoolean(AppPreferences.P500_LITE_MODE, p500LiteCheck.isChecked())
                .putBoolean(AppPreferences.NATIVE_CORE_MODE, nativeCoreCheck.isChecked())
                .putBoolean(AppPreferences.ADAPTIVE_RUNTIME, adaptiveRuntimeCheck.isChecked())
                .putBoolean(AppPreferences.REALTIME_INPUT, realtimeInputCheck.isChecked())
                .putBoolean(AppPreferences.FULLSCREEN, fullscreenCheck.isChecked())
                .putBoolean(AppPreferences.KEEP_SCREEN_ON, screenOnCheck.isChecked())
                .putBoolean(AppPreferences.AUTO_RECONNECT, reconnectCheck.isChecked())
                .putBoolean(AppPreferences.AUTO_START, autostartCheck.isChecked())
                .putBoolean(AppPreferences.HARDWARE_KEYS, hardwareKeysCheck.isChecked())
                .putBoolean(AppPreferences.LONG_PRESS_MEDIA, longPressMediaCheck.isChecked())
                .putBoolean(AppPreferences.HAPTIC_FEEDBACK, hapticCheck.isChecked())
                .putBoolean(AppPreferences.SOUND_FEEDBACK, soundCheck.isChecked())
                .putBoolean(AppPreferences.LAUNCHER_MODE, launcherModeCheck.isChecked())
                .putInt(AppPreferences.PANEL_BRIGHTNESS, panelBrightness)
                .putInt(AppPreferences.IDLE_BRIGHTNESS, idleBrightness)
                .putInt(AppPreferences.IDLE_DIM_SECONDS, idleSeconds)
                .commit();
        LauncherMode.setEnabled(this, launcherModeCheck.isChecked());
    }


    private String selectedDeviceRole() {
        int rolePosition = deviceRoleSpinner.getSelectedItemPosition();
        if (rolePosition == 1) return "station";
        if (rolePosition == 2) return "mobile";
        return DeviceLayout.isModern(this) ? "mobile" : "station";
    }

    private void saveAndConnect() {
        PcProfile profile = currentProfile(true);
        if (profile == null) { return; }
        saveGlobalValues();
        PcProfileStore.save(this, profile, true);
        DiagnosticLog.write(this, "profile_saved", profile.name + " " + profile.host);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void createProfile() {
        PcProfile blank = new PcProfile(PcProfileStore.newId(), "Новий ПК", "", 8765, "", "", "255.255.255.255");
        PcProfileStore.save(this, blank, true);
        refreshProfiles(blank.id);
    }

    private void deleteProfile() {
        final PcProfile profile = selectedProfile();
        if (profile == null) { return; }
        new AlertDialog.Builder(this)
                .setTitle("Видалити профіль?")
                .setMessage(profile.name)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Видалити", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        PcProfileStore.remove(SettingsActivity.this, profile.id);
                        refreshProfiles(null);
                    }
                }).show();
    }

    private void discoverServers() {
        discoverButton.setEnabled(false);
        discoverButton.setText("Шукаю…");
        new AsyncTask<Void, Void, List<DiscoveryClient.Server>>() {
            private String error = "";
            @Override protected List<DiscoveryClient.Server> doInBackground(Void... values) {
                try { return DiscoveryClient.discover(DiscoveryClient.DEFAULT_PORT, 2200); }
                catch (Exception exception) { error = exception.getMessage(); return new ArrayList<DiscoveryClient.Server>(); }
            }
            @Override protected void onPostExecute(final List<DiscoveryClient.Server> servers) {
                if (destroyed) { return; }
                discoverButton.setEnabled(true);
                discoverButton.setText(R.string.discover_pc);
                if (servers.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            error.length() > 0 ? error : "PocketBridge у мережі не знайдено",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                CharSequence[] labels = new CharSequence[servers.size()];
                for (int i = 0; i < servers.size(); i++) { labels[i] = servers.get(i).toString(); }
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Знайдені ПК")
                        .setItems(labels, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                DiscoveryClient.Server server = servers.get(which);
                                hostInput.setText(server.host);
                                portInput.setText(String.valueOf(server.port));
                                profileNameInput.setText(server.name);
                                Toast.makeText(SettingsActivity.this,
                                        server.pairingEnabled ? "Введи код парування" : "Парування на сервері вимкнено",
                                        Toast.LENGTH_LONG).show();
                            }
                        }).show();
            }
        }.execute();
    }

    private void pairDevice() {
        final PcProfile profile = currentProfile(false);
        final String code = pairingCodeInput.getText().toString().trim();
        if (profile == null) { return; }
        if (!code.matches("[0-9]{6}")) {
            pairingCodeInput.setError("Потрібен 6-значний код із редактора на ПК");
            return;
        }
        pairButton.setEnabled(false);
        pairButton.setText("Парування…");
        new AsyncTask<Void, Void, PairingClient.Result>() {
            @Override protected PairingClient.Result doInBackground(Void... values) {
                return PairingClient.claim(profile.host, profile.port, code, profile.name, selectedDeviceRole());
            }
            @Override protected void onPostExecute(PairingClient.Result result) {
                if (destroyed) { return; }
                pairButton.setEnabled(true);
                pairButton.setText(R.string.pair_device);
                if (!result.ok) {
                    Toast.makeText(SettingsActivity.this, result.detail, Toast.LENGTH_LONG).show();
                    return;
                }
                profile.token = result.token;
                tokenInput.setText(result.token);
                pairingCodeInput.setText("");
                PcProfileStore.save(SettingsActivity.this, profile, true);
                refreshProfiles(profile.id);
                DiagnosticLog.write(SettingsActivity.this, "paired", result.deviceId);
                Toast.makeText(SettingsActivity.this, "Парування завершено", Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    private void wakeComputer() {
        final PcProfile profile = currentProfile(false);
        if (profile == null) { return; }
        wakeButton.setEnabled(false);
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... values) {
                try {
                    WakeOnLan.Result result = WakeOnLan.send(
                            getApplicationContext(), profile.mac, profile.broadcast, 9);
                    return result.summary();
                } catch (Exception exception) {
                    return exception.getMessage() == null ? "Не вдалося надіслати Wake-on-LAN" : exception.getMessage();
                }
            }
            @Override protected void onPostExecute(String result) {
                if (destroyed) { return; }
                wakeButton.setEnabled(true);
                Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    private void runWolDiagnostics() {
        PcProfile profile = currentProfile(true);
        if (profile == null) return;
        PcProfileStore.save(this, profile, true);
        wolDiagnosticsButton.setEnabled(false);
        wolDiagnosticsButton.setText("Перевіряю…");
        new AsyncTask<Void, Void, P500ApiClient.Result>() {
            @Override protected P500ApiClient.Result doInBackground(Void... values) {
                return P500ApiClient.wolDiagnostics(getApplicationContext());
            }
            @Override protected void onPostExecute(P500ApiClient.Result result) {
                if (destroyed) return;
                wolDiagnosticsButton.setEnabled(true);
                wolDiagnosticsButton.setText("Діагностика Wake-on-LAN");
                StringBuilder message = new StringBuilder();
                message.append(result.detail);
                if (result.ok) {
                    message.append("\n\nS3: ").append(result.data.optBoolean("sleep_s3", false) ? "так" : "ні");
                    message.append("\nEthernet wake armed: ").append(result.data.optBoolean("ethernet_wake_armed", false) ? "так" : "ні");
                    org.json.JSONArray adapters = result.data.optJSONArray("ethernet");
                    if (adapters != null && adapters.length() > 0) {
                        JSONObject adapter = adapters.optJSONObject(0);
                        if (adapter != null) {
                            message.append("\nАдаптер: ").append(adapter.optString("description", adapter.optString("name", "")));
                            message.append("\nMAC: ").append(adapter.optString("mac", ""));
                            message.append("\nЛінк: ").append(adapter.optString("status", ""));
                        }
                    }
                    org.json.JSONArray tips = result.data.optJSONArray("recommendations");
                    if (tips != null && tips.length() > 0) {
                        message.append("\n\nРекомендації:");
                        for (int i = 0; i < tips.length(); i++) message.append("\n• ").append(tips.optString(i));
                    }
                }
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Wake-on-LAN")
                        .setMessage(message.toString())
                        .setPositiveButton("Гаразд", null)
                        .show();
            }
        }.execute();
    }

    private void testConnection() {
        PcProfile profile = currentProfile(true);
        if (profile == null) { return; }
        PcProfileStore.save(this, profile, true);
        testButton.setEnabled(false);
        testButton.setText("Перевіряю…");
        new HealthCheckTask().execute();
    }

    private final class HealthCheckTask extends AsyncTask<Void, Void, ServerProbe.Result> {
        @Override protected ServerProbe.Result doInBackground(Void... values) {
            return ServerProbe.check(getApplicationContext());
        }
        @Override protected void onPostExecute(ServerProbe.Result result) {
            if (destroyed) { return; }
            testButton.setEnabled(true);
            testButton.setText(R.string.test_connection);
            AppPreferences.storeProbe(SettingsActivity.this, result);
            DiagnosticLog.write(SettingsActivity.this,
                    result.ok ? "connection_test_ok" : "connection_test_failed",
                    "http=" + result.httpCode + " latency=" + result.latencyMs + "ms " + result.detail);
            renderDiagnostics();
            Toast.makeText(SettingsActivity.this, result.detail, Toast.LENGTH_LONG).show();
        }
    }

    private void renderDiagnostics() {
        StringBuilder value = new StringBuilder();
        value.append("APK ").append(BuildConfig.VERSION_NAME);
        value.append(" · ").append(AppPreferences.activePcName(this));
        value.append("\nСервер ").append(AppPreferences.lastServerVersion(this));
        int code = AppPreferences.lastHttpCode(this);
        if (code > 0) {
            value.append(" · HTTP ").append(code).append(" · ").append(AppPreferences.lastLatency(this)).append(" мс");
        }
        value.append("\n").append(DiagnosticLog.recent(this, 900));
        diagnosticsSummary.setText(value.toString());
    }

    private void exportDiagnostics() {
        try {
            String path = DiagnosticLog.export(this);
            Toast.makeText(this, "Діагностику збережено:\n" + path, Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

}
