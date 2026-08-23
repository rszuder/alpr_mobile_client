package com.example.alpr_v1.camera;

import android.util.Size;

import java.util.Locale;
import java.util.Objects;

public final class CameraResolutionSelection {

    public static final String AUTO =
            "auto";

    private final boolean automatic;

    private final int width;

    private final int height;


    private CameraResolutionSelection(
            boolean automatic,
            int width,
            int height
    ) {
        this.automatic =
                automatic;

        this.width =
                Math.max(
                        0,
                        width
                );

        this.height =
                Math.max(
                        0,
                        height
                );
    }


    public static CameraResolutionSelection auto() {
        return new CameraResolutionSelection(
                true,
                0,
                0
        );
    }


    public static CameraResolutionSelection exact(
            Size size
    ) {
        if (size == null) {
            return auto();
        }

        return new CameraResolutionSelection(
                false,
                size.getWidth(),
                size.getHeight()
        );
    }


    public static CameraResolutionSelection fromWireName(
            String value
    ) {
        if (value == null) {
            return auto();
        }

        String normalized =
                value.trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()
                || AUTO.equals(normalized)) {

            return auto();
        }

        String[] parts =
                normalized.split(
                        "x"
                );

        if (parts.length != 2) {
            return auto();
        }

        try {
            int width =
                    Integer.parseInt(
                            parts[0].trim()
                    );

            int height =
                    Integer.parseInt(
                            parts[1].trim()
                    );

            if (width <= 0
                    || height <= 0) {

                return auto();
            }

            return new CameraResolutionSelection(
                    false,
                    width,
                    height
            );

        } catch (NumberFormatException ignored) {
            return auto();
        }
    }


    public boolean automatic() {
        return automatic;
    }


    public Size size() {
        if (automatic) {
            return null;
        }

        return new Size(
                width,
                height
        );
    }


    public String wireName() {
        if (automatic) {
            return AUTO;
        }

        return width
                + "x"
                + height;
    }


    @Override
    public boolean equals(
            Object other
    ) {
        if (this == other) {
            return true;
        }

        if (!(other
                instanceof CameraResolutionSelection)) {

            return false;
        }

        CameraResolutionSelection value =
                (CameraResolutionSelection)
                        other;

        return automatic
                == value.automatic
                && width
                == value.width
                && height
                == value.height;
    }


    @Override
    public int hashCode() {
        return Objects.hash(
                automatic,
                width,
                height
        );
    }
}
