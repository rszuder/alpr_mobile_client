package com.example.alpr_v1.domain;

/**
 * Znormalizowany kadr roboczy kolejki Scan.
 *
 * <p>Nie ogranicza wejścia modeli ani trackingu. Określa wyłącznie, kiedy
 * śledzony na pełnej klatce pojazd może zostać dopuszczony do akwizycji Scan.
 */
public final class ScanAcquisitionViewport {
    public static final NormalizedBounds BOUNDS =
            new NormalizedBounds(0.05f, 0.16f, 0.95f, 0.84f);

    private ScanAcquisitionViewport() {
    }

    public static boolean accepts(NormalizedBounds candidate) {
        if (candidate == null || !candidate.valid()) return false;
        float centerX = candidate.centerX();
        float centerY = candidate.centerY();
        return centerX >= BOUNDS.left
                && centerX <= BOUNDS.right
                && centerY >= BOUNDS.top
                && centerY <= BOUNDS.bottom
                && intersectionRatio(candidate) >= 0.45f;
    }

    public static float intersectionRatio(NormalizedBounds candidate) {
        if (candidate == null || candidate.area() <= 0f) return 0f;
        float width = Math.max(0f,
                Math.min(candidate.right, BOUNDS.right)
                        - Math.max(candidate.left, BOUNDS.left));
        float height = Math.max(0f,
                Math.min(candidate.bottom, BOUNDS.bottom)
                        - Math.max(candidate.top, BOUNDS.top));
        return Math.min(1f, width * height / candidate.area());
    }
}
