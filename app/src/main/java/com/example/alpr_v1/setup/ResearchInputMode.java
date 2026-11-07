package com.example.alpr_v1.setup;

import android.graphics.Bitmap;

/** A mode is admissible only after its producer AND Desktop coordinate contract are supported. */
public enum ResearchInputMode {
    CAMERA_FULL_FRAME, TV_RECTIFIED, TV_BBOX_CROP;
    public static final String TV_BBOX_DESKTOP_CONTRACT_STATUS="ACCEPTED_F5A21";
    public static final String TV_BLOCK_REASON="BLOCKED_DESKTOP_CONTRACT";
    public void requireSessionSupported(){
        if(this==TV_RECTIFIED)throw new IllegalStateException(TV_BLOCK_REASON);
    }
    /** Legacy admission returns the identical camera bitmap, without allocation or resampling. */
    public Bitmap cameraInputForSession(Bitmap camera){
        requireSessionSupported();
        if(this==TV_BBOX_CROP)throw new IllegalStateException("TV_BBOX_FRAME_CONTEXT_REQUIRED");
        return camera;
    }
}
