package com.example.alpr_v1.continuity;

/** Interprets image evidence; generation ownership remains in the coordinator. */
public interface ScenePolicy {
    ContinuityAssessment assess(SceneEvidence evidence, SceneContinuityProfile profile);
}
