package com.example.alpr_v1.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TargetSessionTest {
    @Test
    public void persistentSessionTracksQualityRecoveryAndAttention() {
        TargetSession session = new TargetSession(1L, 7L, TargetPurpose.USER_PICK, 100L);

        session.transitionTo(TargetSessionState.PROVISIONAL_HOLD, 110L);
        session.setCameraAttentionOwned(true, 120L);
        session.updateQuality(1.5f, -0.2f, 2, 130L);
        session.transitionTo(TargetSessionState.RECOVERING, 140L);
        session.transitionTo(TargetSessionState.LOCKED_UNIDENTIFIED, 150L);

        assertTrue(session.persistent());
        assertTrue(session.cameraAttentionOwned());
        assertEquals(1f, session.trackingQuality(), 0.0001f);
        assertEquals(0f, session.identityConfidence(), 0.0001f);
        assertEquals(0, session.recoveryLevel());
        assertEquals(150L, session.lastUpdateNanos());

        session.transitionTo(TargetSessionState.LOCKED_IDENTIFIED, 160L);
        session.transitionTo(TargetSessionState.COMPLETED, 170L);
        assertFalse(session.cameraAttentionOwned());
        assertTrue(session.state().terminal());
    }

    @Test(expected = IllegalStateException.class)
    public void shortSessionCannotEnterPersistentLockState() {
        TargetSession session = new TargetSession(
                1L, 7L, TargetPurpose.SCAN_ACQUISITION, 100L
        );
        session.transitionTo(TargetSessionState.TRACKING, 110L);

        session.transitionTo(TargetSessionState.LOCKED_IDENTIFIED, 120L);
    }

    @Test(expected = IllegalStateException.class)
    public void terminalSessionRejectsFurtherUpdates() {
        TargetSession session = new TargetSession(1L, 7L, TargetPurpose.USER_PICK, 100L);
        session.transitionTo(TargetSessionState.CANCELLED, 110L);

        session.updateQuality(0.5f, 0.5f, 0, 120L);
    }
}
