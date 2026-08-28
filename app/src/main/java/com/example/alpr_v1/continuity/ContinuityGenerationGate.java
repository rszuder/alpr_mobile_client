package com.example.alpr_v1.continuity;

/** Pure ordering rule for rejecting stale asynchronous results. */
public final class ContinuityGenerationGate {
    public ContinuityResultDisposition evaluate(
            ContinuityStamp current,
            ContinuityStamp result
    ) {
        Contracts.required("current", current);
        Contracts.required("result", result);
        if (result.sceneGeneration != current.sceneGeneration) {
            return ContinuityResultDisposition.REJECT_ALL;
        }
        if (result.visualEpoch != current.visualEpoch) {
            return ContinuityResultDisposition.REJECT_GEOMETRY_CROP_AND_FINALIZATION;
        }
        if (result.cameraTransformGeneration != current.cameraTransformGeneration) {
            return ContinuityResultDisposition.REJECT_STALE_CAMERA_TRANSFORM;
        }
        return ContinuityResultDisposition.ACCEPT_ALL;
    }
}
