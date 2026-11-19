package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import android.util.Log;
import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.inference.InferenceRunResult;
import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.vision.PreparedInput;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit debug request: one streamed bundle, no retained frame/tensor copies. */
public final class MpDiagnosticCapture {
    public static final String INTENT_EXTRA="debug_mp_capture";
    private static final String TAG="ALPR_MP_DIAGNOSTIC";
    private static final long MAX_BUNDLE_BYTES=20L*1024*1024;
    private static final int METADATA_BYTES=16*1024;
    private final File directory;
    private final String requestId;
    private final long dataBudget;
    private final AtomicBoolean claimed=new AtomicBoolean();

    public MpDiagnosticCapture(File filesDirectory,String requestId)throws IOException {
        this(filesDirectory,requestId,MAX_BUNDLE_BYTES-METADATA_BYTES);
    }
    MpDiagnosticCapture(File filesDirectory,String requestId,long dataBudget)throws IOException {
        if(!BuildConfig.DEBUG)throw new IllegalStateException("MP diagnostics require a debug build");
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid MP capture request");
        if(dataBudget<=0||dataBudget>MAX_BUNDLE_BYTES-METADATA_BYTES)throw new IllegalArgumentException("Invalid MP capture budget");
        File root=new File(filesDirectory,"mp-diagnostics").getCanonicalFile();
        directory=new File(root,requestId).getCanonicalFile();
        if(!directory.getParentFile().equals(root))throw new IllegalArgumentException("MP capture outside diagnostic root");
        this.requestId=requestId;this.dataBudget=dataBudget;
    }
    boolean pending(){return !claimed.get();}

    Session begin(Bitmap source,long frameId,ContinuityStamp stamp,JSONObject model) {
        if(!BuildConfig.DEBUG||!claimed.compareAndSet(false,true))return null;
        try {
            if(source==null||source.isRecycled()||(long)source.getWidth()*source.getHeight()>2_073_600L)
                throw new IOException("Invalid or oversized MP source bitmap");
            if(directory.exists()||!directory.mkdirs())throw new IOException("MP diagnostic destination already exists or cannot be created");
            Session session=new Session(frameId,stamp,model);
            session.guarded(()->session.png("source.png",source,"before_preprocessing"));
            return session;
        } catch(Exception|OutOfMemoryError failure) {
            Log.e(TAG,"request="+requestId+" status=FAILED_TO_BEGIN",failure);return null;
        }
    }

    final class Session implements AutoCloseable {
        private final JSONObject metadata=new JSONObject(),files=new JSONObject();
        private long remaining=dataBudget,ioNanos;
        private boolean failed,complete,closed,inputSaved,letterboxSaved;
        private int outputCount;
        Session(long frameId,ContinuityStamp stamp,JSONObject model)throws Exception {
            metadata.put("schema","alpr.mp-diagnostic.v1").put("request_id",requestId)
                    .put("frame_id",frameId).put("started_at_ms",System.currentTimeMillis())
                    .put("source_sequence",stamp.sourceSequence).put("source_timestamp_ns",stamp.sourceTimestampNanos)
                    .put("source_timestamp_domain",stamp.sourceTimestampDomain.name())
                    .put("scene_generation",stamp.sceneGeneration).put("visual_epoch",stamp.visualEpoch)
                    .put("camera_transform_generation",stamp.cameraTransformGeneration)
                    .put("model",model).put("files",files)
                    .put("app_built_at_utc",BuildConfig.BUILT_AT_UTC).put("git_commit",BuildConfig.GIT_COMMIT)
                    .put("git_dirty",BuildConfig.GIT_DIRTY).put("max_bundle_bytes",dataBudget+METADATA_BYTES)
                    .put("scope","Actual MP source, prepared input captured before backend.run, and raw outputs before decoding. Diagnostic I/O is not a performance or recognition acceptance test.");
        }
        long ioNanos(){return ioNanos;}
        void letterboxed(Bitmap image){guarded(()->{png("letterbox.png",image,"after_tensor_packing_before_backend");letterboxSaved=true;});}
        void input(PreparedInput input,TensorInfo info) {
            guarded(()->{
                metadata.put("preprocessing",new JSONObject().put("scale",input.scale).put("pad_x",input.padX)
                        .put("pad_y",input.padY).put("source_width",input.sourceWidth).put("source_height",input.sourceHeight));
                tensor("input.bin",input.buffer,info,"before_backend_run");inputSaved=true;
            });
        }
        void output(InferenceRunResult result) {
            guarded(()->{
                for(Integer index:new TreeSet<>(result.outputs().keySet())) {
                    tensor("output-"+index+".bin",result.outputs().get(index),result.tensorInfo().get(index),"before_decoder");
                    outputCount++;
                }
            });
        }
        void complete(int decodedCount,int vehicleClassEligible) {
            guarded(()->{
                if(!inputSaved||!letterboxSaved||outputCount==0)throw new IOException("Incomplete MP diagnostic stages");
                metadata.put("decoded_count",decodedCount).put("vehicle_class_eligible_count",vehicleClassEligible);
                complete=true;
            });
        }
        private void guarded(Action action) {
            if(failed||closed)return;
            long start=System.nanoTime();
            try {action.run();}
            catch(Exception|OutOfMemoryError failure) {
                failed=true;
                try {metadata.put("error",failure.getClass().getSimpleName()+": "+failure.getMessage());}catch(Exception ignored){}
                Log.e(TAG,"request="+requestId+" status=CAPTURE_FAILED",failure);
            } finally {ioNanos+=Math.max(0,System.nanoTime()-start);}
        }
        private void png(String name,Bitmap image,String stage)throws Exception {
            BudgetStream stream=new BudgetStream(name);
            try(BudgetStream ignored=stream) {
                if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("PNG compression failed");
            }
            files.put(name,stream.description().put("width",image.getWidth()).put("height",image.getHeight()).put("stage",stage));
        }
        private void tensor(String name,ByteBuffer buffer,TensorInfo info,String stage)throws Exception {
            if(buffer==null||info==null||buffer.capacity()!=info.byteSize)throw new IOException("Tensor size does not match actual buffer");
            ByteBuffer bytes=buffer.asReadOnlyBuffer().order(buffer.order());bytes.clear();
            BudgetStream stream=new BudgetStream(name);
            try(BudgetStream ignored=stream) {
                byte[] chunk=new byte[16*1024];
                while(bytes.hasRemaining()){int n=Math.min(chunk.length,bytes.remaining());bytes.get(chunk,0,n);stream.write(chunk,0,n);}
            }
            JSONArray shape=new JSONArray();for(int dimension:info.shape)shape.put(dimension);
            JSONObject description=stream.description().put("shape",shape).put("data_type",info.dataType)
                    .put("byte_order",buffer.order().toString()).put("stage",stage)
                    .put("quantization_scale",info.quantizationScale).put("quantization_zero_point",info.quantizationZeroPoint);
            if("FLOAT32".equals(info.dataType))description.put("float_statistics",floatStatistics(buffer));
            files.put(name,description);
        }
        private JSONObject floatStatistics(ByteBuffer original)throws Exception {
            ByteBuffer values=original.asReadOnlyBuffer().order(original.order());values.clear();
            long finite=0,nan=0,infinite=0;double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            while(values.remaining()>=4){float v=values.getFloat();if(Float.isNaN(v))nan++;else if(Float.isInfinite(v))infinite++;
                else {finite++;sum+=v;min=Math.min(min,v);max=Math.max(max,v);}}
            return new JSONObject().put("finite_count",finite).put("nan_count",nan).put("infinite_count",infinite)
                    .put("minimum",finite==0?JSONObject.NULL:min).put("maximum",finite==0?JSONObject.NULL:max)
                    .put("mean",finite==0?JSONObject.NULL:sum/finite);
        }
        private final class BudgetStream extends OutputStream {
            final OutputStream output;
            final MessageDigest digest;
            long bytes;
            BudgetStream(String name)throws Exception {
                digest=MessageDigest.getInstance("SHA-256");
                output=new BufferedOutputStream(new FileOutputStream(new File(directory,name)),16*1024);
            }
            public void write(int value)throws IOException {byte[] one={(byte)value};write(one,0,1);}
            public void write(byte[] values,int offset,int length)throws IOException {
                if(length>remaining)throw new IOException("MP diagnostic byte budget exhausted");
                output.write(values,offset,length);digest.update(values,offset,length);remaining-=length;bytes+=length;
            }
            public void flush()throws IOException {output.flush();}
            public void close()throws IOException {output.close();}
            JSONObject description()throws Exception {return new JSONObject().put("bytes",bytes).put("sha256",hex(digest.digest()));}
        }
        public void close() {
            if(closed)return;closed=true;
            long started=System.nanoTime();
            try {
                String status=failed?"FAILED":complete?"COMPLETE":"INCOMPLETE";
                metadata.put("status",status).put("finished_at_ms",System.currentTimeMillis()).put("diagnostic_io_ns_before_metadata_commit",ioNanos);
                byte[] encoded=metadata.toString(2).getBytes(StandardCharsets.UTF_8);
                if(encoded.length>METADATA_BYTES)throw new IOException("MP metadata budget exhausted");
                File pending=new File(directory,"capture.json.tmp");
                try(FileOutputStream stream=new FileOutputStream(pending)){stream.write(encoded);}
                if(!pending.renameTo(new File(directory,"capture.json")))throw new IOException("Cannot commit MP diagnostic metadata");
                Log.i(TAG,"request="+requestId+" status="+status+" frame="+metadata.optLong("frame_id")+" files="+directory.getName());
            } catch(Exception|OutOfMemoryError failure){Log.e(TAG,"request="+requestId+" status=METADATA_FAILED",failure);}
            finally {ioNanos+=Math.max(0,System.nanoTime()-started);}
        }
    }
    private interface Action {void run()throws Exception;}
    static String hex(byte[] digest){StringBuilder result=new StringBuilder();for(byte value:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();}
}
