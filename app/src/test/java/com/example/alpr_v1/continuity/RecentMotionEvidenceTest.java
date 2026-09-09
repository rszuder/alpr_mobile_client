package com.example.alpr_v1.continuity;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecentMotionEvidenceTest {
    @Test public void forwardsVisualMotionAndSettlingToInferencePreflight() {
        RecentMotionEvidence recent = new RecentMotionEvidence();
        MotionExplanationEvidence motion = new MotionExplanationEvidence(true, true, false, .2f,
                false, true, .95f, .1f, 0f, 0f, true);
        recent.publish(motion, 1_000_000_000L);
        MotionExplanationEvidence received = recent.current(1_200_000_000L);
        assertSame(motion, received);
        assertTrue(received.dominantMotionEstimated);
        assertTrue(received.motionSettling);
    }

    @Test public void oldMotionCannotExplainANewSceneIndefinitely() {
        RecentMotionEvidence recent = new RecentMotionEvidence();
        recent.publish(MotionExplanationEvidence.none(), 1_000_000_000L);
        assertNull(recent.current(999_999_999L));
        assertNull(recent.current(1_000_000_000L + RecentMotionEvidence.MAXIMUM_AGE_NANOS + 1L));
        recent.reset();
        assertNull(recent.current(1_000_000_000L));
    }

    @Test public void delayedPublisherDoesNotOverwriteNewerEvidence() {
        RecentMotionEvidence recent = new RecentMotionEvidence();
        MotionExplanationEvidence newest = MotionExplanationEvidence.none();
        recent.publish(newest, 200L);
        recent.publish(new MotionExplanationEvidence(true, true, true, 1f,
                false, false, 0f, 1f, 0f, 0f), 100L);
        assertSame(newest, recent.current(250L));
    }
}
