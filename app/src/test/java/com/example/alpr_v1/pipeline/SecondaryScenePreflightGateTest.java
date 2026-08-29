package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.MotionExplanationEvidence;
import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneEvidence;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.TargetContinuityEvidence;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.MotionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SecondaryScenePreflightGateTest {
    @Test
    public void dynamicSecondaryChangeCannotMutateRepositoryBeforeReacquire() {
        assertPreflightBlocksDomainMutation(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneTransitionAction.SOFT_REACQUIRE
        );
    }

    @Test
    public void strictSecondaryChangeCannotMutateRepositoryBeforeHardReset() {
        assertPreflightBlocksDomainMutation(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                SceneTransitionAction.HARD_RESET
        );
    }

    private static void assertPreflightBlocksDomainMutation(
            SceneHandlingMode mode,
            SceneTransitionAction expectedAction
    ) {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        repository.updateFromMp(
                11L, bounds(0.10f), MotionState.STATIONARY,
                new AppearanceDescriptor(null), 7_900_000_000L
        );
        repository.updateFromMp(
                12L, bounds(0.55f), MotionState.STATIONARY,
                new AppearanceDescriptor(null), 7_900_000_000L
        );
        assertEquals(2, repository.size());

        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                mode, SceneContinuityProfile.INITIAL
        );
        SceneTransitionDecision decision = coordinator.observe(
                secondaryRawChange(),
                1_000_000_000L
        );
        assertEquals(expectedAction, decision.action);

        SecondaryScenePreflightGate gate = SecondaryScenePreflightGate.from(
                decision,
                coordinator.snapshot().heavyInferenceSuspended
        );
        AtomicBoolean mpMtMzRan = new AtomicBoolean(false);
        Boolean mutationResult;
        try {
            mutationResult = gate.run(() -> {
                mpMtMzRan.set(true);
                VehicleEntity created = repository.updateFromMp(
                        13L, bounds(0.30f), MotionState.STATIONARY,
                        new AppearanceDescriptor(null), 8_001_000_000L
                );
                repository.attachPlate(
                        created.entityId(),
                        71L,
                        null,
                        null,
                        8_001_000_000L
                );
                return true;
            });
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        assertTrue(gate.skipsInference());
        assertNull(mutationResult);
        assertFalse(mpMtMzRan.get());
        assertEquals(2, repository.size());
        for (VehicleEntity entity : repository.activeEntities()) {
            assertNull(entity.plateTrackId());
        }
    }

    private static SceneEvidence secondaryRawChange() {
        return new SceneEvidence(
                1L,
                8_000_000_000L,
                true,
                0.95f,
                0.90f,
                0.05f,
                0.90f,
                0.90f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, false, false, false
        );
    }

    private static NormalizedBounds bounds(float left) {
        return new NormalizedBounds(left, 0.2f, left + 0.25f, 0.7f);
    }
}
