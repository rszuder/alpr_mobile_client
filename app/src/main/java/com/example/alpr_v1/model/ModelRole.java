package com.example.alpr_v1.model;

import java.util.Locale;

public enum ModelRole {
    VEHICLE("vehicle", "pojazdu"),
    PLATE("plate", "tablic"),
    CHARACTER("character", "znaków");

    private final String wireName;
    private final String displayName;

    ModelRole(String wireName, String displayName) {
        this.wireName = wireName;
        this.displayName = displayName;
    }

    public String wireName() {
        return wireName;
    }

    public String displayName() {
        return displayName;
    }

    public static ModelRole fromWire(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ModelRole role : values()) {
            if (role.wireName.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Nieznana rola modelu: " + value);
    }
}
