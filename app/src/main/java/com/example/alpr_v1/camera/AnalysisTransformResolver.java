package com.example.alpr_v1.camera;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import com.example.alpr_v1.setup.CameraPreviewGeometry;

/** Read-only compatibility with CameraX 1.4.0 CPU rotation metadata. Never changes image pixels. */
public final class AnalysisTransformResolver {
    private AnalysisTransformResolver(){}
    public static final class RawGeometry {
        public final int width,height,rotation;
        public final Rect crop;
        public final boolean cpuRotation;
        public RawGeometry(int width,int height,Rect crop,int rotation,boolean cpuRotation){
            this.width=width;this.height=height;this.crop=new Rect(crop);this.rotation=rotation;this.cpuRotation=cpuRotation;
        }
    }
    public static final class Result {
        public final double[] effective;
        public final boolean corrected;
        Result(double[] effective,boolean corrected){this.effective=effective;this.corrected=corrected;}
    }
    public static Result resolve(double[] reported,int width,int height,Rect crop,int remaining,RawGeometry raw,Rect active){
        if(raw==null||!raw.cpuRotation||raw.rotation==0)return new Result(reported.clone(),false);
        if(active==null||active.isEmpty())throw new IllegalArgumentException("CPU_ROTATION_SENSOR_METADATA_MISSING");
        int expectedW=raw.rotation%180==0?raw.width:raw.height,expectedH=raw.rotation%180==0?raw.height:raw.width;
        double[] rotation=CameraPreviewGeometry.bufferToSource(new double[]{0,0,raw.width,raw.height},raw.rotation);
        double[] l=CameraPreviewGeometry.map(rotation,raw.crop.left,raw.crop.top),r=CameraPreviewGeometry.map(rotation,raw.crop.right,raw.crop.bottom);
        Rect expectedCrop=new Rect((int)Math.round(Math.min(l[0],r[0])),(int)Math.round(Math.min(l[1],r[1])),
                (int)Math.round(Math.max(l[0],r[0])),(int)Math.round(Math.max(l[1],r[1])));
        if(remaining!=0||width!=expectedW||height!=expectedH||!expectedCrop.equals(crop))
            throw new IllegalArgumentException("CPU_ROTATION_PIXELS_METADATA_MISMATCH");
        // Same raw sensor domain as CameraUseCaseAdapter, using public characteristics/resolution.
        Matrix bufferToSensor=new Matrix();bufferToSensor.setRectToRect(new RectF(0,0,raw.width,raw.height),new RectF(active),Matrix.ScaleToFit.CENTER);
        Matrix sensorToRaw=new Matrix();if(!bufferToSensor.invert(sensorToRaw))throw new IllegalArgumentException("Invalid raw sensor mapping");
        float[] values=new float[9];sensorToRaw.getValues(values);double[] sensor=new double[9];for(int i=0;i<9;i++)sensor[i]=values[i];
        double[] correct=CameraPreviewGeometry.multiply(rotation,sensor);
        if(samePixels(reported,correct,active))return new Result(reported.clone(),false);
        double[] sdk140=CameraPreviewGeometry.multiply(sensor,rotation);
        if(!samePixels(reported,sdk140,active))throw new IllegalArgumentException("CPU_ROTATION_UNRECOGNIZED_MATRIX");
        return new Result(correct,true);
    }
    private static boolean samePixels(double[] a,double[] b,Rect sensor){
        for(double[] point:new double[][]{{sensor.left,sensor.top},{sensor.right,sensor.top},{sensor.right,sensor.bottom},{sensor.left,sensor.bottom}}){
            double[] x=CameraPreviewGeometry.map(a,point[0],point[1]),y=CameraPreviewGeometry.map(b,point[0],point[1]);
            if(Math.hypot(x[0]-y[0],x[1]-y[1])>.01)return false;
        }return true;
    }
}
