package com.example.alpr_v1.pipeline;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AutoZoomTargetLockTest {
    @Test
    public void selectsTargetByMotionGeometryAndAppearance() {
        AutoZoomTargetLock lock = new AutoZoomTargetLock();
        lock.begin(box(0.40f, 0.45f, 0.60f, 0.55f), descriptor(1f, 0f));

        AutoZoomTargetLock.Candidate distractor = candidate(
                0,
                box(0.56f, 0.45f, 0.76f, 0.55f),
                0.98f,
                descriptor(0f, 1f)
        );
        AutoZoomTargetLock.Candidate target = candidate(
                1,
                box(0.41f, 0.45f, 0.61f, 0.55f),
                0.75f,
                descriptor(1f, 0f)
        );

        AutoZoomTargetLock.Selection selection =
                lock.select(Arrays.asList(distractor, target));

        assertEquals(1, selection.candidate.sourceIndex);
        assertEquals(AutoZoomTargetLock.State.LOCKED, selection.state);
    }

    @Test
    public void refusesAmbiguousCandidatesInsteadOfSwitchingIdentity() {
        AutoZoomTargetLock lock = new AutoZoomTargetLock();
        lock.begin(box(0.40f, 0.45f, 0.60f, 0.55f), null);

        AutoZoomTargetLock.Selection selection = lock.select(Arrays.asList(
                candidate(0, box(0.39f, 0.45f, 0.59f, 0.55f), 0.82f, null),
                candidate(1, box(0.41f, 0.45f, 0.61f, 0.55f), 0.82f, null)
        ));

        assertNull(selection.candidate);
        assertEquals(AutoZoomTargetLock.State.UNCERTAIN, selection.state);
    }

    @Test
    public void expandsSearchWhileUncertainAndEventuallyMarksTargetLost() {
        AutoZoomTargetLock lock = new AutoZoomTargetLock();
        lock.begin(box(0.47f, 0.48f, 0.53f, 0.52f), null);
        float initialWidth = lock.searchBox().width();

        lock.select(Collections.emptyList());
        float uncertainWidth = lock.searchBox().width();
        lock.select(Collections.emptyList());
        AutoZoomTargetLock.Selection lost = lock.select(Collections.emptyList());

        assertTrue(uncertainWidth > initialWidth);
        assertEquals(AutoZoomTargetLock.State.LOST, lost.state);
    }

    @Test
    public void appearanceDescriptorKeepsStableTemplateNormalized() {
        float[] blended = PlateAppearanceDescriptor.blend(
                descriptor(1f, 0f),
                descriptor(0f, 1f),
                0.25f
        );

        assertTrue(PlateAppearanceDescriptor.similarity(
                descriptor(1f, 0f), blended
        ) > PlateAppearanceDescriptor.similarity(
                descriptor(0f, 1f), blended
        ));
    }

    private static AutoZoomTargetLock.Candidate candidate(
            int index,
            AutoZoomTargetLock.Box box,
            float confidence,
            float[] appearance
    ) {
        return new AutoZoomTargetLock.Candidate(
                index, box, confidence, true, appearance
        );
    }

    private static AutoZoomTargetLock.Box box(
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new AutoZoomTargetLock.Box(left, top, right, bottom);
    }

    private static float[] descriptor(float first, float second) {
        float norm = (float) Math.sqrt(first * first + second * second);
        return new float[]{first / norm, second / norm};
    }
}
