package com.example.alpr_v1.experiment;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.JSONObject;

/** Optional host-test diagnostic trace of actual controller/store states; never an archive producer. */
public final class F6LifecycleTrace {
    private F6LifecycleTrace(){}
    public static synchronized void record(String caseId,String stage,JSONObject actual)throws Exception{
        String path=System.getenv("F6_LIFECYCLE_TRACE");if(path==null)return;
        JSONObject row=new JSONObject().put("synthetic",true).put("device_run","NOT_RUN").put("case_id",caseId).put("stage",stage)
                .put("time_domain","ANDROID_ELAPSED_REALTIME").put("time_unit","ns").put("elapsed_nanos",android.os.SystemClock.elapsedRealtimeNanos()).put("actual_state",actual);
        Files.write(Paths.get(path),(row+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
