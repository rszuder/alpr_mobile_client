package com.example.alpr_v1.capture;

/** Wyznacza limit galerii z ustawienia użytkownika i budżetu pamięci. */
public final class CropCapacityPolicy {
    public static final String AUTO = "auto";
    private static final long ESTIMATED_ITEM_BYTES = 160L * 1024L;

    private CropCapacityPolicy() {}

    public static int resolve(String setting, long maximumHeapBytes, boolean lowRamDevice) {
        if (setting != null && !AUTO.equalsIgnoreCase(setting)) {
            try {
                return clamp(Integer.parseInt(setting), 5, 100);
            } catch (NumberFormatException ignored) {
                // Nieprawidłowa preferencja wraca do trybu automatycznego.
            }
        }
        long budget = Math.max(ESTIMATED_ITEM_BYTES * 10L, maximumHeapBytes * 3L / 100L);
        int hardwareLimit = lowRamDevice ? 25 : 100;
        return clamp((int) (budget / ESTIMATED_ITEM_BYTES), 10, hardwareLimit);
    }

    public static String normalizeSetting(String value) {
        if (value == null || AUTO.equalsIgnoreCase(value)) return AUTO;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed == 10 || parsed == 25 || parsed == 50 || parsed == 100) {
                return String.valueOf(parsed);
            }
        } catch (NumberFormatException ignored) {
            // Powrót do auto poniżej.
        }
        return AUTO;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
