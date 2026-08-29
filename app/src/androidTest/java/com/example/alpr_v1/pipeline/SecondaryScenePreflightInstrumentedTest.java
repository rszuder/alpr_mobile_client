package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
import com.example.alpr_v1.domain.VehicleEntityRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class SecondaryScenePreflightInstrumentedTest {
    @Test
    public void secondaryOnlyChangeBlocksMpMtMzBeforeRepositoryMutation()
            throws Exception {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        repository.updateFromMp(
                11L, bounds(0.10f), MotionState.STATIONARY,
                new AppearanceDescriptor(null), 7_900_000_000L
        );
        repository.updateFromMp(
                12L, bounds(0.55f), MotionState.STATIONARY,
                new AppearanceDescriptor(null), 7_900_000_000L
        );

        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );
        SceneTransitionDecision decision = coordinator.observe(
                secondaryRawChange(),
                1_000_000_000L
        );
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, decision.action);

        SecondaryScenePreflightGate gate = SecondaryScenePreflightGate.from(
                decision,
                coordinator.snapshot().heavyInferenceSuspended
        );
        AtomicBoolean inferenceRan = new AtomicBoolean(false);
        Boolean result = gate.run(() -> {
            inferenceRan.set(true);
            repository.updateFromMp(
                    13L, bounds(0.30f), MotionState.STATIONARY,
                    new AppearanceDescriptor(null), 8_001_000_000L
            );
            return true;
        });

        assertTrue(gate.skipsInference());
        assertNull(result);
        assertFalse(inferenceRan.get());
        assertEquals(2, repository.size());
    }

    private static SceneEvidence secondaryRawChange() {
        return new SceneEvidence(
                1L, 8_000_000_000L, true,
                0.95f, 0.90f, 0.05f, 0.90f, 0.90f,
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
