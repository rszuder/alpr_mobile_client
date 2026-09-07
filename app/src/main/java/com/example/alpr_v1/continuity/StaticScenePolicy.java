package com.example.alpr_v1.continuity;

/** Static samples never recover identity across a confirmed image boundary. */
public final class StaticScenePolicy implements ScenePolicy {
    @Override public ContinuityAssessment assess(SceneEvidence evidence, SceneContinuityProfile profile) {
        boolean cut = evidence.rawVisualChange && !evidence.motion.cameraTransformInProgress;
        return new ContinuityAssessment(cut ? VisualChangeClassification.CONTINUITY_BREAK
                : VisualChangeClassification.NONE, 0f, 0f, 0f, cut ? 1f : 0f,
                false, false, !cut, cut ? "static_scene_boundary" : "static_same_sample");
    }
}
