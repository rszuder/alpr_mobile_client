package com.example.alpr_v1.domain;

import java.util.Locale;

/** Canonical comparison key; never use this value to overwrite an MZ prediction. */
public final class RegistrationTextNormalizer {
    public static final String POLICY = "uppercase_alphanumeric.v1";
    private RegistrationTextNormalizer() { }

    public static String registrationKey(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String upper = raw.toUpperCase(Locale.ROOT);
        StringBuilder key = new StringBuilder(upper.length());
        upper.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            // Python str.isalnum includes Unicode letter numbers and other numbers too.
            if (Character.isLetterOrDigit(cp) || type == Character.LETTER_NUMBER
                    || type == Character.OTHER_NUMBER) key.appendCodePoint(cp);
        });
        return key.toString();
    }
}
