package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.*;
import java.util.*;

/** Actual TfliteBackend and its contract checks; only native neural-network execution is replaced. */
public final class MzBackendProbe implements AutoCloseable {
    public final InstalledModel model;
    public final ModelVariant variant;
    public final ModelInputSpec spec;
    public final TfliteBackend backend;
    public byte[] received;
    public int runs;
    public RuntimeException failure;
    public MzBackendProbe(File root,int w,int h,String dtype,String layout)throws Exception{
        InstalledModel base=RuntimeEvidenceFixture.model(root,"INT8".equals(dtype)?"int8":"fp32",dtype,null);
        JSONObject json=new JSONObject(base.manifest().rawJson());
        json.getJSONObject("input").put("width",w).put("height",h).put("layout",layout);
        model=new InstalledModel(ModelManifest.parse(json.toString()),root,"synthetic-manifest-fingerprint");
        variant=model.manifest().variants().get(0);spec=variant.input(model.manifest().input());
        int scalar="FLOAT32".equals(dtype)?4:1;
        TensorInfo input=new TensorInfo(0,"NHWC".equals(layout)?new int[]{1,h,w,3}:new int[]{1,3,h,w},dtype,w*h*3*scalar,
                "FLOAT32".equals(dtype)?0:.05f,"INT8".equals(dtype)?-128:0);
        TensorInfo output=new TensorInfo(0,new int[]{1,5,2},dtype,10*scalar,"FLOAT32".equals(dtype)?0:.02f,0);
        backend=new TfliteBackend(model,variant,ExecutionProfile.tfliteCpu(2),RuntimeEvidenceSink.NONE,
                (bytes,profile,evidence)->new TfliteBackend.Driver(){
                    public List<TensorInfo> inputs(){return Collections.singletonList(input);}
                    public Map<Integer,TensorInfo> outputs(){return Collections.singletonMap(0,output);}
                    public void run(ByteBuffer tensor,Map<Integer,ByteBuffer> outputs){
                        runs++;ByteBuffer view=tensor.duplicate();received=new byte[view.remaining()];view.get(received);
                        if(failure!=null)throw failure;
                    }
                    public void close(){}
                });
    }
    public void run(ByteBuffer input){backend.run(input);}
    public void close(){backend.close();}
}
