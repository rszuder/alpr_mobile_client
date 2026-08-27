package com.example.alpr_v1.domain;

public final class PlateTextConsensus {
    public static final PlateTextConsensus EMPTY =
            new PlateTextConsensus("", 0f, 0, false);

    public final String text;
    public final float confidence;
    public final int observations;
    public final boolean stable;

    public PlateTextConsensus(
            String text,
            float confidence,
            int observations,
            boolean stable
    ) {
        this.text = normalize(text);
        this.confidence = Float.isFinite(confidence)
                ? Math.max(0f, Math.min(1f, confidence)) : 0f;
        this.observations = Math.max(0, observations);
        this.stable = stable && !this.text.isEmpty();
    }

    public boolean available() {
        return !text.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }
}
