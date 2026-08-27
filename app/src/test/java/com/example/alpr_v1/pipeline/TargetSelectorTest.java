package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class TargetSelectorTest {
    private final TargetSelector selector = new TargetSelector();

    @Test
    public void ranksCandidatesBeforeLock() {
        TargetSelector.Candidate weakEdge = candidate(
                10L, 0.05f, 0.10f, 0.01f, 0.35f, 0.30f, 1
        );
        TargetSelector.Candidate strongCenter = candidate(
                20L, 0.50f, 0.52f, 0.07f, 0.92f, 0.85f, 4
        );

        TargetSelector.Selection result = selector.select(
                Arrays.asList(weakEdge, strongCenter),
                0L,
                false
        );

        assertTrue(result.found());
        assertEquals(20L, result.candidate.trackId);
        assertEquals("ranked_candidate", result.reason);
    }

    @Test
    public void stickyLockKeepsIdentityEvenWhenAnotherCandidateScoresHigher() {
        TargetSelector.Candidate locked = candidate(
                31L, 0.18f, 0.20f, 0.02f, 0.55f, 0.50f, 8
        );
        TargetSelector.Candidate challenger = candidate(
                32L, 0.50f, 0.50f, 0.08f, 0.98f, 0.95f, 8
        );

        TargetSelector.Selection result = selector.select(
                Arrays.asList(locked, challenger),
                31L,
                true
        );

        assertTrue(result.found());
        assertEquals(31L, result.candidate.trackId);
        assertEquals("sticky_target_kept", result.reason);
    }

    @Test
    public void stickyLockDoesNotFallThroughToForeignPlate() {
        TargetSelector.Candidate foreign = candidate(
                42L, 0.50f, 0.50f, 0.08f, 0.99f, 0.99f, 10
        );

        TargetSelector.Selection result = selector.select(
                Collections.singletonList(foreign),
                41L,
                true
        );

        assertFalse(result.found());
        assertEquals("sticky_target_missing", result.reason);
    }

    @Test
    public void noCandidatesProducesExplicitReason() {
        TargetSelector.Selection result = selector.select(
                Collections.emptyList(),
                0L,
                false
        );

        assertFalse(result.found());
        assertEquals("no_candidate", result.reason);
    }

    @Test
    public void reassociatesChangedTrackIdUsingGeometryAndAppearance() {
        TargetSelector.Reference reference = new TargetSelector.Reference(
                0.35f, 0.40f, 0.60f, 0.52f, new float[]{1f, 0f, 0f}
        );
        TargetSelector.Candidate samePlate = associatedCandidate(
                52L, 0.36f, 0.40f, 0.61f, 0.52f,
                new float[]{1f, 0f, 0f}
        );
        TargetSelector.Candidate lookalike = associatedCandidate(
                53L, 0.36f, 0.40f, 0.61f, 0.52f,
                new float[]{-1f, 0f, 0f}
        );

        TargetSelector.Association association = selector.associate(
                reference,
                Arrays.asList(lookalike, samePlate)
        );

        assertTrue(association.matched());
        assertEquals(52L, association.candidate.trackId);
        assertEquals("appearance_geometry_match", association.reason);
    }

    @Test
    public void refusesAmbiguousReassociation() {
        TargetSelector.Reference reference = new TargetSelector.Reference(
                0.35f, 0.40f, 0.60f, 0.52f, new float[]{1f, 0f}
        );
        TargetSelector.Candidate first = associatedCandidate(
                61L, 0.36f, 0.40f, 0.61f, 0.52f, new float[]{1f, 0f}
        );
        TargetSelector.Candidate second = associatedCandidate(
                62L, 0.36f, 0.40f, 0.61f, 0.52f, new float[]{1f, 0f}
        );

        TargetSelector.Association association = selector.associate(
                reference,
                Arrays.asList(first, second)
        );

        assertFalse(association.matched());
        assertEquals("association_ambiguous", association.reason);
    }

    @Test
    public void refusesForeignAppearanceEvenAtSameGeometry() {
        TargetSelector.Reference reference = new TargetSelector.Reference(
                0.35f, 0.40f, 0.60f, 0.52f, new float[]{1f, 0f, 0f}
        );
        TargetSelector.Candidate foreign = associatedCandidate(
                71L, 0.35f, 0.40f, 0.60f, 0.52f,
                new float[]{-1f, 0f, 0f}
        );

        TargetSelector.Association association = selector.associate(
                reference,
                Collections.singletonList(foreign)
        );

        assertFalse(association.matched());
        assertEquals("association_too_weak", association.reason);
    }

    private static TargetSelector.Candidate candidate(
            long trackId,
            float centerX,
            float centerY,
            float area,
            float quality,
            float sharpness,
            int age
    ) {
        return new TargetSelector.Candidate(
                trackId,
                centerX,
                centerY,
                area,
                quality,
                sharpness,
                age
        );
    }

    private static TargetSelector.Candidate associatedCandidate(
            long trackId,
            float left,
            float top,
            float right,
            float bottom,
            float[] appearance
    ) {
        return new TargetSelector.Candidate(
                trackId,
                left,
                top,
                right,
                bottom,
                0.9f,
                0.8f,
                4,
                appearance
        );
    }
}
