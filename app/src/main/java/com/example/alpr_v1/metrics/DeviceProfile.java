package com.example.alpr_v1.metrics;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class DeviceProfile {
    public final String manufacturer;
    public final String model;
    public final String device;
    public final String androidVersion;
    public final String appVersion;
    public final int sdk;
    public final String[] abis;
    public final int cpuCores;
    public final long totalMemoryBytes;
    public final long availableMemoryBytes;
    public final boolean lowRamDevice;
    public final boolean lowMemoryNow;
    public final int thermalStatus;
    public final int batteryPercent;
    public final boolean charging;
    public final double batteryTemperatureC;

    private DeviceProfile(
            String manufacturer,
            String model,
            String device,
            String androidVersion,
            String appVersion,
            int sdk,
            String[] abis,
            int cpuCores,
            long totalMemoryBytes,
            long availableMemoryBytes,
            boolean lowRamDevice,
            boolean lowMemoryNow,
            int thermalStatus,
            int batteryPercent,
            boolean charging,
            double batteryTemperatureC
    ) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.device = device;
        this.androidVersion = androidVersion;
        this.appVersion = appVersion;
        this.sdk = sdk;
        this.abis = abis;
        this.cpuCores = cpuCores;
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.lowRamDevice = lowRamDevice;
        this.lowMemoryNow = lowMemoryNow;
        this.thermalStatus = thermalStatus;
        this.batteryPercent = batteryPercent;
        this.charging = charging;
        this.batteryTemperatureC = batteryTemperatureC;
    }

    public static DeviceProfile capture(Context context) {
        ActivityManager activity = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activity.getMemoryInfo(memory);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        Intent batteryState = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        int temperatureTenths = batteryState == null
                ? 0
                : batteryState.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        String appVersion;
        try {
            String detected = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            appVersion = detected == null || detected.trim().isEmpty() ? "unknown" : detected;
        } catch (Exception ignored) {
            appVersion = "unknown";
        }
        return new DeviceProfile(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.DEVICE,
                Build.VERSION.RELEASE,
                appVersion,
                Build.VERSION.SDK_INT,
                Build.SUPPORTED_ABIS.clone(),
                Runtime.getRuntime().availableProcessors(),
                memory.totalMem,
                memory.availMem,
                activity.isLowRamDevice(),
                memory.lowMemory,
                power.getCurrentThermalStatus(),
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                battery.isCharging(),
                temperatureTenths / 10.0
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("manufacturer", manufacturer);
        json.put("model", model);
        json.put("device", device);
        json.put("name", manufacturer + " " + model);
        json.put("device_name", manufacturer + " " + model);
        json.put("android_version", androidVersion);
        json.put("app_version", appVersion);
        json.put("android_sdk", sdk);
        json.put("abis", new JSONArray(abis));
        json.put("cpu_cores", cpuCores);
        json.put("total_memory_bytes", totalMemoryBytes);
        json.put("available_memory_bytes", availableMemoryBytes);
        json.put("low_ram_device", lowRamDevice);
        json.put("low_memory", lowMemoryNow);
        json.put("thermal_status", thermalStatus);
        json.put("battery_percent", batteryPercent);
        json.put("charging", charging);
        json.put("battery_temperature_c", batteryTemperatureC);
        return json;
    }
}
