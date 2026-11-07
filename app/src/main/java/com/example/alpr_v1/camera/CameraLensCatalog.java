package com.example.alpr_v1.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Enumerates only routes exposed by public CameraX/Camera2 APIs, never OEM camera numbers. */
@ExperimentalCamera2Interop
public final class CameraLensCatalog {
    private CameraLensCatalog(){}
    public static List<CameraSetupControl.Choice> read(Context context,List<CameraInfo> infos){
        List<CameraSetupControl.Choice> result=new ArrayList<>();Set<CameraSelection> seen=new HashSet<>();
        CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        if(manager==null)return result;
        Set<String> standalone=new HashSet<>();for(CameraInfo info:infos)try{standalone.add(Camera2CameraInfo.from(info).getCameraId());}catch(RuntimeException ignored){}
        for(CameraInfo info:infos)try{
            String logical=Camera2CameraInfo.from(info).getCameraId();CameraCharacteristics characteristics=manager.getCameraCharacteristics(logical);
            Integer facing=characteristics.get(CameraCharacteristics.LENS_FACING);
            if(facing==null||facing!=CameraCharacteristics.LENS_FACING_BACK)continue;
            append(result,seen,new CameraSelection(logical,null),characteristics);
            for(String physical:characteristics.getPhysicalCameraIds())
                if(!physical.equals(logical)&&!standalone.contains(physical))append(result,seen,new CameraSelection(logical,physical),manager.getCameraCharacteristics(physical));
        }catch(Exception inaccessible){/* Other public routes remain available. */}
        return result;
    }
    private static void append(List<CameraSetupControl.Choice> choices,Set<CameraSelection> seen,CameraSelection selection,CameraCharacteristics info){
        StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(map==null||map.getOutputSizes(ImageFormat.YUV_420_888)==null||map.getOutputSizes(ImageFormat.YUV_420_888).length==0
                ||map.getOutputSizes(ImageFormat.PRIVATE)==null||map.getOutputSizes(ImageFormat.PRIVATE).length==0||!seen.add(selection))return;
        float[] focal=info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        String detail="Kamera "+selection+(focal==null||focal.length==0?"":String.format(Locale.getDefault()," · %.2f mm",focal[0]));
        choices.add(new CameraSetupControl.Choice(selection,(choices.isEmpty()?"Domyślny tylny":"Tylny obiektyw "+(choices.size()+1)),detail));
    }
}
