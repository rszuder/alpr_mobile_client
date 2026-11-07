package com.example.alpr_v1.experiment;

import android.graphics.Bitmap;
import java.io.*;
import java.util.concurrent.*;
import com.example.alpr_v1.setup.TvBboxCrop;
import com.example.alpr_v1.continuity.ContinuityStamp;

/** Test-only access to the actual Android producer, never an operator START. */
public final class TvBboxProducerFixture {
    public static ResearchSessionStore prepare(File root,ResearchExecutionConfig config)throws Exception{
        ResearchSessionStore s=prepareOnly(root,config);
        s.startAt(System.currentTimeMillis(),System.nanoTime());return s;
    }
    public static ResearchSessionStore prepareOnly(File root,ResearchExecutionConfig config)throws Exception{
        ExperimentSession.Prepared p=new ExperimentSession().prepare("roi_budget",config.variant,TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("SYNTHETIC-F5A20-TV-BBOX","SYNTHETIC",1,"synthetic=true; DEVICE_RUN=NOT_RUN"),config);
        return prepareFor(root,p);
    }
    public static ResearchSessionStore prepareFor(File root,ExperimentSession.Prepared p)throws Exception{
        ResearchSessionStore s=ResearchSessionStore.prepare(root,p,256,64,0,(bitmap,target)->{
            try(FileOutputStream stream=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,stream))throw new IOException("JPEG");}
        },System::nanoTime);
        return s;
    }
    public static void drain(ResearchSessionStore store)throws Exception{
        java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);
        ThreadPoolExecutor writer=(ThreadPoolExecutor)f.get(store);
        for(int i=0;i<3;i++){
            FutureTask<Void> barrier=new FutureTask<>(()->null);
            if(writer.isShutdown()||!writer.getQueue().offer(barrier,10,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic writer barrier unavailable");
            barrier.get(10,TimeUnit.SECONDS);
        }
    }
    public static File finish(ResearchSessionStore store,ResearchExecutionConfig config)throws Exception{
        drain(store);store.closeAdmission("synthetic_completed",System.currentTimeMillis(),System.nanoTime());
        return store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"device_run\":\"NOT_RUN\"}","","","","",""),config);
    }
    public static void close(ResearchSessionStore store)throws Exception{
        java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(store)).shutdownNow();
    }
    public static org.json.JSONObject data(AcquisitionAttemptRecord record)throws Exception{return record.data;}
}
