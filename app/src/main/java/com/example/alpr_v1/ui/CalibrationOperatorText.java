package com.example.alpr_v1.ui;

import android.content.Context;
import com.example.alpr_v1.R;

/** Operator copy only. Original exceptions, diagnostics and export reason codes stay intact. */
public final class CalibrationOperatorText {
    private CalibrationOperatorText() {}
    public static String preparationError(Context context,String diagnostic) {
        if(diagnostic!=null&&(diagnostic.contains("F6_PHOTO_ROI_REQUIRED")||diagnostic.contains("F6_PHOTO_ROI_STALE")
                ||diagnostic.contains("F6_PHOTO_ROI_INVALID")))return context.getString(R.string.tv_precision_f6_required);
        if(CameraOperatorPresentation.reason(diagnostic)!=null)return CameraOperatorPresentation.readableDetail(context,diagnostic);
        return diagnostic;
    }
    public static String preparationDetail(Context context,String diagnostic) {
        if(CameraOperatorPresentation.reason(diagnostic)!=null)return "";
        if(java.util.Objects.equals(diagnostic,preparationError(context,diagnostic)))return "";
        String saved=context.getSharedPreferences("alpr_ui",0).getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
        if(saved.isEmpty())return context.getString(R.string.tv_precision_f6_missing_detail);
        return context.getString(R.string.tv_precision_f6_check_detail);
    }
    public static String preparationMessage(Context context,String diagnostic){
        String detail=preparationDetail(context,diagnostic),primary=preparationError(context,diagnostic);
        return detail.isEmpty()?primary:primary+"\n"+detail;
    }
}
