package ua.pocketbridge.lgp500;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Small API-10-compatible runtime governor for LG P500-class hardware.
 * It adapts network cadence without services, wakelocks or background timers.
 */
final class RuntimeTuner {
    static final int TIER_ECO = 0;
    static final int TIER_BALANCED = 1;
    static final int TIER_FAST = 2;

    private final Context context;
    private final int memoryClassMb;
    private final boolean modernDevice;
    private int tier;
    private boolean memoryPressure;
    private long latencyEwmaMs;
    private int consecutiveFailures;
    private int lastResponseBytes;
    private long lastBatteryCheckAt;
    private boolean lowBatteryCached;

    RuntimeTuner(Context context) {
        this.context = context.getApplicationContext();
        ActivityManager manager = (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE);
        memoryClassMb = manager == null ? 24 : Math.max(16, manager.getMemoryClass());
        modernDevice = DeviceLayout.isModern(this.context);
        tier = modernDevice ? TIER_FAST : (memoryClassMb <= 24 ? TIER_ECO : (memoryClassMb <= 40 ? TIER_BALANCED : TIER_FAST));
        recomputeTier();
    }

    void record(P500ApiClient.Result result) {
        if (result == null) return;
        lastResponseBytes = Math.max(0, result.responseBytes);
        if (result.ok) {
            consecutiveFailures = 0;
            if (latencyEwmaMs <= 0) latencyEwmaMs = result.latencyMs;
            else latencyEwmaMs = (latencyEwmaMs * 3L + result.latencyMs) / 4L;
        } else {
            consecutiveFailures = Math.min(8, consecutiveFailures + 1);
        }
        recomputeTier();
    }

    void onLowMemory() {
        memoryPressure = true;
        tier = TIER_ECO;
    }

    void clearMemoryPressure() {
        memoryPressure = false;
        recomputeTier();
    }

    int commandQueueCapacity() {
        return modernDevice ? (tier == TIER_ECO ? 10 : (tier == TIER_BALANCED ? 16 : 24)) : (tier == TIER_ECO ? 6 : (tier == TIER_BALANCED ? 9 : 12));
    }

    int mouseQueueCapacity() {
        return modernDevice ? (tier == TIER_ECO ? 4 : (tier == TIER_BALANCED ? 6 : 8)) : (tier == TIER_ECO ? 2 : (tier == TIER_BALANCED ? 3 : 4));
    }

    int touchInterval(int configuredMs) {
        if (modernDevice) {
            int base = clamp(configuredMs, 18, 120);
            if (tier == TIER_ECO) return Math.max(base, 40);
            if (tier == TIER_BALANCED) return Math.max(28, Math.min(base, 40));
            return Math.max(18, Math.min(base, 28));
        }
        int base = clamp(configuredMs, 35, 160);
        if (tier == TIER_ECO) return Math.max(base, 70);
        if (tier == TIER_BALANCED) return Math.max(base, 45);
        return Math.max(35, Math.min(base, 45));
    }

    long statusDelay(int activeMs, int idleMs, boolean idle) {
        long base = idle ? Math.max(10000, idleMs) : Math.max(3000, activeMs);
        if (tier == TIER_ECO) return Math.min(60000L, Math.round(base * 1.6d));
        if (tier == TIER_BALANCED) return Math.min(45000L, Math.round(base * 1.15d));
        if (modernDevice && !idle) return Math.max(2000L, Math.round(base * 0.65d));
        return base;
    }

    int mediaEvery() {
        return tier == TIER_ECO ? 8 : (tier == TIER_BALANCED ? 6 : 4);
    }

    String tierName() {
        return tier == TIER_ECO ? "ECO" : (tier == TIER_BALANCED ? "BAL" : "FAST");
    }

    String summary() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = Math.max(0L, (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L));
        long maxMb = Math.max(1L, runtime.maxMemory() / (1024L * 1024L));
        String latency = latencyEwmaMs > 0 ? String.valueOf(latencyEwmaMs) + "мс" : "—";
        return (modernDevice ? "REDMI " : "P500 ") + tierName() + " · heap " + usedMb + "/" + maxMb + "MB · " + latency
                + " · " + lastResponseBytes + "B";
    }

    private void recomputeTier() {
        if (!AppPreferences.adaptiveRuntime(context)) {
            tier = TIER_BALANCED;
            return;
        }
        if (memoryPressure || isLowBattery() || consecutiveFailures >= 2 || latencyEwmaMs >= 650L) {
            tier = TIER_ECO;
            return;
        }
        if (memoryClassMb <= 24 || latencyEwmaMs >= 250L || consecutiveFailures == 1) {
            tier = TIER_BALANCED;
            return;
        }
        tier = memoryClassMb >= 48 && latencyEwmaMs > 0 && latencyEwmaMs < 180L
                ? TIER_FAST : TIER_BALANCED;
    }

    private boolean isLowBattery() {
        long now = System.currentTimeMillis();
        if (now - lastBatteryCheckAt < 30000L) return lowBatteryCached;
        lastBatteryCheckAt = now;
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                lowBatteryCached = false;
                return false;
            }
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int percent = scale > 0 && level >= 0 ? (level * 100 / scale) : 100;
            lowBatteryCached = !charging && percent <= 20;
            return lowBatteryCached;
        } catch (Exception ignored) {
            lowBatteryCached = false;
            return false;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
