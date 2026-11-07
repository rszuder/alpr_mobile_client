package com.example.alpr_v1.camera;

import java.util.Objects;

/** Exact public Camera2 route. Empty logical id means the existing default back selector. */
public final class CameraSelection {
    public final String logicalCameraId,physicalCameraId;
    public CameraSelection(String logical,String physical){
        logicalCameraId=Objects.requireNonNull(logical).trim();
        physicalCameraId=physical==null||physical.trim().isEmpty()?null:physical.trim();
        if(logicalCameraId.isEmpty()&&physicalCameraId!=null)throw new IllegalArgumentException("Physical camera needs logical id");
    }
    public static CameraSelection defaultBack(){return new CameraSelection("",null);}
    public boolean isDefault(){return logicalCameraId.isEmpty();}
    @Override public boolean equals(Object other){if(!(other instanceof CameraSelection))return false;
        CameraSelection selection=(CameraSelection)other;return logicalCameraId.equals(selection.logicalCameraId)&&Objects.equals(physicalCameraId,selection.physicalCameraId);}
    @Override public int hashCode(){return Objects.hash(logicalCameraId,physicalCameraId);}
    @Override public String toString(){return (isDefault()?"default-back":logicalCameraId)+(physicalCameraId==null?"":" / "+physicalCameraId);}
}
