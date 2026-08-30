package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import org.junit.Test;

public final class TargetSnapshotSourceStampTest {
    @Test
    public void withStateChangesRuntimeTimeButPreservesSourceIdentity() {
        ContinuityStamp source = new ContinuityStamp(
                2L, 7L, 4L,
                81L, 8_000_000_000L,
                SourceTimestampDomain.PREVIEW_INHERITED_CAMERA
        );
        TargetSnapshot before = TargetSnapshot.searching()
                .withContinuityStamp(source);

        TargetSnapshot after = before.withState(TargetSnapshot.State.DEGRADED);

        assertEquals(81L, after.sourceSequence);
        assertEquals(8_000_000_000L, after.sourceTimestampNanos);
        assertEquals(
                SourceTimestampDomain.PREVIEW_INHERITED_CAMERA,
                after.sourceTimestampDomain
        );
        assertEquals(2L, after.sceneGeneration);
        assertTrue(after.updatedAtRuntimeNanos >= before.updatedAtRuntimeNanos);
    }

    @Test
    public void searchingTargetDoesNotUseRuntimeAsSourceTimestamp() {
        TargetSnapshot searching = TargetSnapshot.searching();

        assertEquals(0L, searching.sourceSequence);
        assertEquals(0L, searching.sourceTimestampNanos);
        assertEquals(SourceTimestampDomain.UNKNOWN, searching.sourceTimestampDomain);
        assertTrue(searching.updatedAtRuntimeNanos > 0L);
    }
}
