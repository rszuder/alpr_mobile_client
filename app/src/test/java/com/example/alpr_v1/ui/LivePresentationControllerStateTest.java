package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LivePresentationControllerStateTest {
    @Test
    public void missingModelsOverrideAllActivePipelineStates() {
        for (LivePresentationController.State requested
                : LivePresentationController.State.values()) {
            LivePresentationController.State expected =
                    requested == LivePresentationController.State.STOPPED
                            || requested == LivePresentationController.State.ERROR
                            ? requested
                            : LivePresentationController.State.SETUP_REQUIRED;
            assertEquals(
                    requested.name(),
                    expected,
                    LivePresentationController.resolveStateForMissingModels(
                            requested,
                            true
                    )
            );
        }
    }

    @Test
    public void availableModelsPreserveRequestedState() {
        for (LivePresentationController.State requested
                : LivePresentationController.State.values()) {
            assertEquals(
                    requested.name(),
                    requested,
                    LivePresentationController.resolveStateForMissingModels(
                            requested,
                            false
                    )
            );
        }
    }
}
