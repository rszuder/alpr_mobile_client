package com.example.alpr_v1.experiment;

/**
 * Opcjonalny warunek termiczny rozpoczęcia eksperymentu.
 *
 * ThermalConfig nie uruchamia eksperymentu.
 * Określa jedynie, kiedy urządzenie jest gotowe do startu.
 */
public final class ThermalConfig {

    public static final double DEFAULT_MAX_BATTERY_TEMPERATURE_C =
            32.0;

    public static final int DEFAULT_MAX_THERMAL_STATUS =
            0;

    public static final int DEFAULT_STABILIZATION_SECONDS =
            5;

    private final boolean enabled;

    private final double maxBatteryTemperatureC;

    private final int maxThermalStatus;

    private final int stabilizationSeconds;


    private ThermalConfig(
            boolean enabled,
            double maxBatteryTemperatureC,
            int maxThermalStatus,
            int stabilizationSeconds
    ) {
        this.enabled =
                enabled;

        this.maxBatteryTemperatureC =
                maxBatteryTemperatureC;

        this.maxThermalStatus =
                Math.max(
                        0,
                        maxThermalStatus
                );

        this.stabilizationSeconds =
                Math.max(
                        1,
                        stabilizationSeconds
                );
    }


    public static ThermalConfig of(
            boolean enabled,
            double maxBatteryTemperatureC,
            int maxThermalStatus,
            int stabilizationSeconds
    ) {
        return new ThermalConfig(
                enabled,
                maxBatteryTemperatureC,
                maxThermalStatus,
                stabilizationSeconds
        );
    }


    public static ThermalConfig disabled() {
        return new ThermalConfig(
                false,
                DEFAULT_MAX_BATTERY_TEMPERATURE_C,
                DEFAULT_MAX_THERMAL_STATUS,
                DEFAULT_STABILIZATION_SECONDS
        );
    }


    public boolean enabled() {
        return enabled;
    }


    public double maxBatteryTemperatureC() {
        return maxBatteryTemperatureC;
    }


    public int maxThermalStatus() {
        return maxThermalStatus;
    }


    public int stabilizationSeconds() {
        return stabilizationSeconds;
    }


    public long stabilizationMillis() {
        return stabilizationSeconds
                * 1000L;
    }


    public boolean accepts(
            ThermalMonitor.Snapshot snapshot
    ) {
        if (!enabled) {
            return true;
        }

        if (snapshot == null
                || !snapshot.available()) {
            return false;
        }

        return snapshot.batteryTemperatureC
                <= maxBatteryTemperatureC

                && snapshot.thermalStatus
                <= maxThermalStatus;
    }
}