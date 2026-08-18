package com.example.alpr_v1.metrics;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class DeviceProfile {
    public final String manufacturer;
    public final String model;
    public final String device;
    public final int sdk;
    public final String[] abis;
    public final int cpuCores;
    public final long totalMemoryBytes;
    public final long availableMemoryBytes;
    public final boolean lowRamDevice;
    public final boolean lowMemoryNow;
    public final int thermalStatus;

    private DeviceProfile(
            String manufacturer,
            String model,
            String device,
            int sdk,
            String[] abis,
            int cpuCores,
            long totalMemoryBytes,
            long availableMemoryBytes,
            boolean lowRamDevice,
            boolean lowMemoryNow,
            int thermalStatus
    ) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.device = device;
        this.sdk = sdk;
        this.abis = abis;
        this.cpuCores = cpuCores;
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.lowRamDevice = lowRamDevice;
        this.lowMemoryNow = lowMemoryNow;
        this.thermalStatus = thermalStatus;
    }

    public static DeviceProfile capture(Context context) {
        ActivityManager activity = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activity.getMemoryInfo(memory);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return new DeviceProfile(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.DEVICE,
                Build.VERSION.SDK_INT,
                Build.SUPPORTED_ABIS.clone(),
                Runtime.getRuntime().availableProcessors(),
                memory.totalMem,
                memory.availMem,
                activity.isLowRamDevice(),
                memory.lowMemory,
                power.getCurrentThermalStatus()
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("manufacturer", manufacturer);
        json.put("model", model);
        json.put("device", device);
        json.put("android_sdk", sdk);
        json.put("abis", new JSONArray(abis));
        json.put("cpu_cores", cpuCores);
        json.put("total_memory_bytes", totalMemoryBytes);
        json.put("available_memory_bytes", availableMemoryBytes);
        json.put("low_ram_device", lowRamDevice);
        json.put("low_memory", lowMemoryNow);
        json.put("thermal_status", thermalStatus);
        return json;
    }
}
