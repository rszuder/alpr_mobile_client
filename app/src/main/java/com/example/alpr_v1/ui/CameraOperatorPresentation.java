package com.example.alpr_v1.ui;

import android.content.Context;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.CameraSetupReadiness;

/** Operator wording only. Reason.description(), logs and START matching remain unchanged. */
public final class CameraOperatorPresentation {
    public final String primary,hint;
    private CameraOperatorPresentation(Context context,int primary,int hint){
        this.primary=context.getString(primary);this.hint=context.getString(hint);
    }
    public static CameraOperatorPresentation from(Context context,CameraSetupReadiness.Reason reason,
                                                   boolean previewActive,boolean paused,boolean startPending){
        if(paused||!previewActive&&!startPending)
            return new CameraOperatorPresentation(context,R.string.camera_operator_off,R.string.camera_operator_open_preview);
        if(reason==null)return new CameraOperatorPresentation(context,R.string.camera_operator_unknown,R.string.camera_operator_details);
        switch(reason){
            case READY:return new CameraOperatorPresentation(context,R.string.camera_operator_ready,R.string.camera_operator_check_image);
            case CAMERA_NOT_BOUND:
            case CAMERA_ROUTE_UNCONFIRMED:
            case CAMERA_SETUP_SNAPSHOT_MISSING:
                return new CameraOperatorPresentation(context,R.string.camera_operator_starting,R.string.camera_operator_wait_image);
            case ZOOM_NOT_ACKNOWLEDGED:
                return new CameraOperatorPresentation(context,R.string.camera_operator_zoom,R.string.camera_operator_wait_image);
            case NO_FRESH_CAMERA_FRAME:
                return new CameraOperatorPresentation(context,R.string.camera_operator_wait_frame,R.string.camera_operator_wait_image);
            case CAMERA_SETUP_SNAPSHOT_STALE:
            case CAMERA_SETUP_GENERATION_CHANGED:
                return new CameraOperatorPresentation(context,R.string.camera_operator_changed,R.string.camera_operator_check_image);
            case CAMERA_SETUP_GEOMETRY_MISMATCH:
                return new CameraOperatorPresentation(context,R.string.camera_operator_tv,R.string.camera_operator_tv_action);
            default:return new CameraOperatorPresentation(context,R.string.camera_operator_unknown,R.string.camera_operator_details);
        }
    }
    public String message(){return primary+"\n"+hint;}
    /** Compatibility adapter at the display boundary; never used to decide admission. */
    public static CameraSetupReadiness.Reason reason(String diagnostic){
        if(diagnostic==null)return null;
        for(CameraSetupReadiness.Reason reason:CameraSetupReadiness.Reason.values())
            if(diagnostic.equals(reason.name())||diagnostic.startsWith(reason.name()+" · ")||diagnostic.startsWith(reason.name()+":"))return reason;
        return null;
    }
    public static String readableDetail(Context context,String diagnostic){
        CameraSetupReadiness.Reason known=reason(diagnostic);
        if(known!=null)return from(context,known,true,false,true).message();
        if(diagnostic!=null&&diagnostic.startsWith("TV_BBOX_REQUIRES_FIXED_ZOOM"))return context.getString(R.string.camera_operator_fixed_zoom);
        if(diagnostic!=null&&diagnostic.matches("(?s)^[A-Z][A-Z0-9_]{2,}(?:[ :·].*)?$"))return context.getString(R.string.camera_operator_details);
        return diagnostic==null?"":diagnostic;
    }
}
