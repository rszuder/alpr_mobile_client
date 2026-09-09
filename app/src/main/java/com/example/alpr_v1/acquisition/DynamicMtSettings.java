package com.example.alpr_v1.acquisition;

import android.content.SharedPreferences;

public final class DynamicMtSettings {
    public static final String ENTER_WIDTH = "dynamic_mt_enter_width";
    public static final String ENTER_HEIGHT = "dynamic_mt_enter_height";
    public static final String KEEP_WIDTH = "dynamic_mt_keep_width";
    public static final String KEEP_HEIGHT = "dynamic_mt_keep_height";
    public static final String PRIMARY_TOP_PERCENT = "dynamic_mt_primary_top_percent";
    private DynamicMtSettings() {}

    public static DynamicMtConfig read(SharedPreferences preferences) {
        try {
            return new DynamicMtConfig(new DynamicVehicleSizeGate.Config(
                    preferences.getInt(ENTER_WIDTH, 120), preferences.getInt(ENTER_HEIGHT, 80),
                    preferences.getInt(KEEP_WIDTH, 100), preferences.getInt(KEEP_HEIGHT, 64)),
                    preferences.getInt(PRIMARY_TOP_PERCENT, 35) / 100f);
        } catch (IllegalArgumentException | ClassCastException invalid) {
            return DynamicMtConfig.INITIAL;
        }
    }

    public static void save(SharedPreferences preferences, DynamicMtConfig config) {
        preferences.edit().putInt(ENTER_WIDTH, config.size.enterWidth)
                .putInt(ENTER_HEIGHT, config.size.enterHeight).putInt(KEEP_WIDTH, config.size.keepWidth)
                .putInt(KEEP_HEIGHT, config.size.keepHeight)
                .putInt(PRIMARY_TOP_PERCENT, Math.round(config.primaryTopFraction * 100f)).apply();
    }
}
