package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.ui.OverlayItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public final class TargetStateMachineInstrumentedTest {
    @Test
    public void appearanceAndGeometryPreserveLockAcrossTrackIdChange() {
        TargetStateMachine machine = new TargetStateMachine();
        OverlayItem original = plate(301L, 0.35f, 0.40f, 0.60f, 0.52f);
        float[] appearance = new float[]{1f, 0f, 0f};
        Map<Long, float[]> originalAppearance = new HashMap<>();
        originalAppearance.put(301L, appearance);

        machine.onMtAnchor(Collections.singletonList(original), originalAppearance);
        machine.onMtAnchor(Collections.singletonList(original), originalAppearance);
        machine.onMtAnchor(Collections.singletonList(original), originalAppearance);

        OverlayItem reassigned = plate(909L, 0.36f, 0.40f, 0.61f, 0.52f);
        Map<Long, float[]> reassignedAppearance = new HashMap<>();
        reassignedAppearance.put(909L, appearance);
        TargetSnapshot recovered = machine.onMtAnchor(
                Collections.singletonList(reassigned),
                reassignedAppearance
        );

        assertEquals(TargetSnapshot.State.LOCKED, recovered.state);
        assertEquals(909L, recovered.trackId);
        assertEquals(909L, recovered.lockedTrackId);
        assertEquals(1, recovered.lockReassociations);
        assertEquals(0, recovered.lockSwitches);
        assertEquals(
                "lock_reassociated_appearance_geometry",
                recovered.transitionReason
        );
    }

    @Test
    public void lockedTargetCannotBeReplacedBeforeLost() {
        TargetStateMachine machine = new TargetStateMachine();
        OverlayItem first = plate(101L, 0.40f, 0.42f, 0.60f, 0.50f);
        OverlayItem challenger = plate(202L, 0.45f, 0.45f, 0.70f, 0.55f);

        machine.onMtAnchor(Collections.singletonList(first));
        machine.onMtAnchor(Collections.singletonList(first));
        TargetSnapshot locked = machine.onMtAnchor(Collections.singletonList(first));

        assertEquals(TargetSnapshot.State.LOCKED, locked.state);
        assertEquals(101L, locked.trackId);
        assertEquals(101L, locked.lockedTrackId);

        TargetSnapshot protectedTarget = machine.onMtAnchor(
                Collections.singletonList(challenger)
        );

        assertEquals(TargetSnapshot.State.DEGRADED, protectedTarget.state);
        assertEquals(101L, protectedTarget.trackId);
        assertEquals(101L, protectedTarget.lockedTrackId);
        assertEquals(
                "mt_anchor_locked_target_missing_association_too_weak",
                protectedTarget.transitionReason
        );

        TargetSnapshot lost = machine.onTrackingLost();
        assertEquals(TargetSnapshot.State.LOST, lost.state);
        assertEquals(0L, lost.lockedTrackId);
        assertEquals(1, lost.lockLosses);

        machine.onMtAnchor(Collections.singletonList(challenger));
        machine.onMtAnchor(Collections.singletonList(challenger));
        TargetSnapshot relocked = machine.onMtAnchor(Collections.singletonList(challenger));

        assertEquals(TargetSnapshot.State.LOCKED, relocked.state);
        assertEquals(202L, relocked.trackId);
        assertEquals(1, relocked.lockSwitches);
        assertEquals(2L, relocked.lockRevision);
    }

    @Test
    public void disabledLockNeverAcquiresOrTracksATarget() {
        TargetStateMachine machine = new TargetStateMachine();
        machine.setEnabled(false);
        OverlayItem candidate = plate(101L, 0.40f, 0.42f, 0.60f, 0.50f);

        TargetSnapshot first = machine.onMtAnchor(Collections.singletonList(candidate));
        TargetSnapshot second = machine.onMtAnchor(Collections.singletonList(candidate));
        TargetSnapshot third = machine.onMtAnchor(Collections.singletonList(candidate));

        assertEquals(TargetSnapshot.State.SEARCHING, first.state);
        assertEquals(TargetSnapshot.State.SEARCHING, second.state);
        assertEquals(TargetSnapshot.State.SEARCHING, third.state);
        assertEquals(0L, third.trackId);
        assertEquals(0L, third.lockedTrackId);
    }

    private static OverlayItem plate(
            long trackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                new RectF(left, top, right, bottom),
                Collections.emptyList(),
                "",
                trackId,
                false
        );
    }
}
