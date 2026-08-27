package com.example.alpr_v1.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModeControllerTest {
    @Test
    public void modeAcceptsOnlyItsOwnPurposes() {
        ModeController controller = new ModeController();
        TargetSession scan = controller.startSession(
                10L, TargetPurpose.SCAN_ACQUISITION, 100L
        );

        assertFalse(scan.persistent());
        assertSame(scan, controller.activeSession());
        controller.finishSession(scan.sessionId(), TargetSessionState.COMPLETED, 110L);
        assertNull(controller.activeSession());
    }

    @Test(expected = IllegalStateException.class)
    public void modeRejectsIncompatiblePurpose() {
        new ModeController().startSession(10L, TargetPurpose.USER_PICK, 100L);
    }

    @Test
    public void modeChangeCancelsIncompatibleSession() {
        ModeController controller = new ModeController(ApplicationMode.PICK_ACQUIRE_LOCK);
        TargetSession pick = controller.startSession(10L, TargetPurpose.USER_PICK, 100L);
        pick.setCameraAttentionOwned(true, 110L);

        ModeController.ModeChange change = controller.switchMode(
                ApplicationMode.SCAN_ACQUIRE, 120L
        );

        assertEquals(ApplicationMode.PICK_ACQUIRE_LOCK, change.previousMode);
        assertEquals(ApplicationMode.SCAN_ACQUIRE, change.currentMode);
        assertEquals(Long.valueOf(pick.sessionId()), change.cancelledSessionId);
        assertEquals(TargetSessionState.CANCELLED, pick.state());
        assertFalse(pick.cameraAttentionOwned());
        assertNull(controller.activeSession());
    }

    @Test
    public void searchVerificationPromotesToPersistentPursuitOfSameEntity() {
        ModeController controller = new ModeController(
                ApplicationMode.SEARCH_VERIFY_PURSUIT
        );
        TargetSession verification = controller.startSession(
                91L, TargetPurpose.SEARCH_VERIFICATION, 100L
        );

        TargetSession pursuit = controller.promoteSearchToPursuit(120L);

        assertEquals(TargetSessionState.COMPLETED, verification.state());
        assertEquals(91L, pursuit.entityId());
        assertEquals(TargetPurpose.SEARCH_PURSUIT, pursuit.purpose());
        assertTrue(pursuit.persistent());
        assertTrue(pursuit.sessionId() > verification.sessionId());
        assertSame(pursuit, controller.activeSession());
    }
}
