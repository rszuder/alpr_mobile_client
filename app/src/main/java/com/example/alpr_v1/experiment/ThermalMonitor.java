package com.example.alpr_v1.experiment;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.SystemClock;

public final class ThermalMonitor {

    public static final class Snapshot {

        public final double batteryTemperatureC;

        public final int thermalStatus;

        public final long capturedElapsedMillis;


        private Snapshot(
                double batteryTemperatureC,
                int thermalStatus,
                long capturedElapsedMillis
        ) {
            this.batteryTemperatureC =
                    batteryTemperatureC;

            this.thermalStatus =
                    thermalStatus;

            this.capturedElapsedMillis =
                    capturedElapsedMillis;
        }


        public boolean available() {
            return !Double.isNaN(
                    batteryTemperatureC
            )
                    && thermalStatus >= 0;
        }
    }


    private final Context context;


    public ThermalMonitor(
            Context context
    ) {
        this.context =
                context.getApplicationContext();
    }


    public Snapshot read() {

        Intent batteryState =
                context.registerReceiver(
                        null,
                        new IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                        )
                );


        int temperatureTenths =
                batteryState == null
                        ? Integer.MIN_VALUE
                        : batteryState.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE,
                        Integer.MIN_VALUE
                );


        double batteryTemperatureC =
                temperatureTenths
                        == Integer.MIN_VALUE

                        ? Double.NaN

                        : temperatureTenths
                          / 10.0;


        PowerManager power =
                (PowerManager)
                        context.getSystemService(
                                Context.POWER_SERVICE
                        );


        int thermalStatus =
                power == null
                        ? -1
                        : power.getCurrentThermalStatus();


        return new Snapshot(
                batteryTemperatureC,
                thermalStatus,
                SystemClock.elapsedRealtime()
        );
    }


    public static String statusLabel(
            int status
    ) {
        switch (status) {

            case PowerManager.THERMAL_STATUS_NONE:
                return "NONE";

            case PowerManager.THERMAL_STATUS_LIGHT:
                return "LIGHT";

            case PowerManager.THERMAL_STATUS_MODERATE:
                return "MODERATE";

            case PowerManager.THERMAL_STATUS_SEVERE:
                return "SEVERE";

            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "CRITICAL";

            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "EMERGENCY";

            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "SHUTDOWN";

            default:
                return "?";
        }
    }
}
