package com.example.alpr_v1.acquisition;

import java.util.Locale;

public final class RegistrationSearchPolicy {
    private RegistrationSearchPolicy() {}
    public static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
    public static boolean possible(String target, String reading) {
        String a = normalize(target), b = normalize(reading);
        if (a.length() < 4 || b.length() < 4 || Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (a.length() >= b.length()) i++;
            if (b.length() >= a.length()) j++;
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }
}
