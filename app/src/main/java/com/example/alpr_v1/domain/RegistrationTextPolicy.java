package com.example.alpr_v1.domain;

/** Rozdziela szybkie pokazanie odczytu od silniejszej bramki konsensusu OCR. */
public final class RegistrationTextPolicy {
    public static final int MINIMUM_DISPLAY_CHARACTERS = 4;
    public static final int MINIMUM_CONSENSUS_CHARACTERS = 5;

    private RegistrationTextPolicy() {}

    public static boolean displayable(String text) {
        if (text == null) return false;
        int characters = 0;
        for (int index = 0; index < text.length(); index++) {
            if (Character.isLetterOrDigit(text.charAt(index))) characters++;
        }
        return characters >= MINIMUM_DISPLAY_CHARACTERS;
    }

    public static boolean plausibleLength(int characters) {
        return characters >= MINIMUM_CONSENSUS_CHARACTERS;
    }
}
