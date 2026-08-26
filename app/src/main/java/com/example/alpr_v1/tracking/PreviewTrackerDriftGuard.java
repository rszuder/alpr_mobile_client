package com.example.alpr_v1.tracking;

/** Łączy ruch klatka-do-klatki z niezależnym pomiarem względem ostatniego MT. */
final class PreviewTrackerDriftGuard {
    private static final int MINIMUM_SUPPORT = 3;
    private static final int MAXIMUM_INCREMENTAL_STEP = 8;
    private static final int MAXIMUM_REFERENCE_DISAGREEMENT = 4;
    private static final int MAXIMUM_FALLBACK_OFFSET = 24;

    static final class Motion {
        final boolean valid;
        final int dx;
        final int dy;
        final int support;

        Motion(boolean valid, int dx, int dy, int support) {
            this.valid = valid;
            this.dx = dx;
            this.dy = dy;
            this.support = support;
        }
    }

    static final class Decision {
        final boolean valid;
        final int absoluteDx;
        final int absoluteDy;
        final boolean anchored;
        final int support;

        private Decision(
                boolean valid,
                int absoluteDx,
                int absoluteDy,
                boolean anchored,
                int support
        ) {
            this.valid = valid;
            this.absoluteDx = absoluteDx;
            this.absoluteDy = absoluteDy;
            this.anchored = anchored;
            this.support = support;
        }

        static Decision invalid() {
            return new Decision(false, 0, 0, false, 0);
        }
    }

    private PreviewTrackerDriftGuard() {}

    static Decision reconcile(
            int currentAbsoluteDx,
            int currentAbsoluteDy,
            Motion incremental,
            Motion anchored
    ) {
        boolean incrementalValid = strong(incremental);
        boolean anchorValid = strong(anchored);

        if (anchorValid) {
            if (incrementalValid) {
                int incrementalAbsoluteDx = currentAbsoluteDx + incremental.dx;
                int incrementalAbsoluteDy = currentAbsoluteDy + incremental.dy;
                if (Math.abs(incrementalAbsoluteDx - anchored.dx)
                        > MAXIMUM_REFERENCE_DISAGREEMENT
                        || Math.abs(incrementalAbsoluteDy - anchored.dy)
                        > MAXIMUM_REFERENCE_DISAGREEMENT) {
                    return Decision.invalid();
                }
            }
            return new Decision(
                    true,
                    anchored.dx,
                    anchored.dy,
                    true,
                    anchored.support
            );
        }

        if (!incrementalValid
                || incremental.dx * incremental.dx + incremental.dy * incremental.dy
                > MAXIMUM_INCREMENTAL_STEP * MAXIMUM_INCREMENTAL_STEP) {
            return Decision.invalid();
        }

        int absoluteDx = currentAbsoluteDx + incremental.dx;
        int absoluteDy = currentAbsoluteDy + incremental.dy;
        if (Math.abs(absoluteDx) > MAXIMUM_FALLBACK_OFFSET
                || Math.abs(absoluteDy) > MAXIMUM_FALLBACK_OFFSET) {
            return Decision.invalid();
        }

        return new Decision(
                true,
                absoluteDx,
                absoluteDy,
                false,
                incremental.support
        );
    }

    private static boolean strong(Motion motion) {
        return motion != null
                && motion.valid
                && motion.support >= MINIMUM_SUPPORT;
    }
}
