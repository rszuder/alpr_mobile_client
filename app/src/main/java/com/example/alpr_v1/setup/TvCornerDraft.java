package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.List;

/** Visible editing handles only. This geometry is never a confirmed calibration or model Pose. */
public final class TvCornerDraft {
    private TvCornerDraft(){}
    public static List<Point2> create(CameraPreviewGeometry geometry,float density){
        if(geometry==null)throw new IllegalArgumentException("Brak aktualnego przeliczenia kamery");
        double[] u=geometry.usableViewRect();double l=Double.POSITIVE_INFINITY,t=l,r=Double.NEGATIVE_INFINITY,b=r;
        for(double[] n:new double[][]{{0,0},{1,0},{1,1},{0,1}}){double[] p=geometry.normalizedToView(n[0],n[1]);l=Math.min(l,p[0]);t=Math.min(t,p[1]);r=Math.max(r,p[0]);b=Math.max(b,p[1]);}
        l=Math.max(l,u[0]);t=Math.max(t,u[1]);r=Math.min(r,u[2]);b=Math.min(b,u[3]);
        double x=Math.min((r-l)/4,Math.max(24*density,(r-l)*.1));
        double y=Math.min((b-t)/4,Math.max(24*density,(b-t)*.1));
        double[][] visual={{l+x,t+y},{r-x,t+y},{r-x,b-y},{l+x,b-y}};
        Point2[] ordered=new Point2[4];int[] order=geometry.mirrored?new int[]{1,0,3,2}:new int[]{0,1,2,3};
        for(int i=0;i<4;i++){double[] p=geometry.viewToNormalized(visual[i][0],visual[i][1]);ordered[order[i]]=new Point2((float)p[0],(float)p[1]);}
        List<Point2> result=new ArrayList<>(java.util.Arrays.asList(ordered));String error=TvSetupCalibration.validation(result,geometry,density);
        if(error!=null)throw new IllegalArgumentException(error);return result;
    }
}
