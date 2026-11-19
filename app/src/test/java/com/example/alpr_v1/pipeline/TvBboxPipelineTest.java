package com.example.alpr_v1.pipeline;

import android.app.Application;
import android.graphics.*;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Real engine, decoders, preprocessing, rectifier and archive. Only model calculations are synthetic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvBboxPipelineTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final List<Suite> suites=new ArrayList<>();
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static List<Point2> plate(int index){
        return index==0?Arrays.asList(new Point2(160,150),new Point2(280,155),new Point2(275,190),new Point2(165,185))
                :Arrays.asList(new Point2(380,160),new Point2(500,168),new Point2(495,200),new Point2(390,195));
    }
    static ContinuityStamp stamp(int n){return new ContinuityStamp(1,1,0,n,9007199254740993L+n,SourceTimestampDomain.CAMERAX_SENSOR);}
    @After public void close()throws Exception{for(Suite s:suites)s.close();}

    final class Suite implements AutoCloseable {
        final TvBboxCrop crop;
        final Bitmap camera;
        final ModelRegistry registry=new ModelRegistry(RuntimeEnvironment.getApplication());
        final Map<ModelRole,FakeBackend> backends=new EnumMap<>(ModelRole.class);
        final ResearchExecutionConfig config;
        final ResearchSessionStore store;
        String scenario;
        final boolean cropped;
        final MobileAlprEngine engine;
        final List<JSONObject> observedMt=new ArrayList<>();
        final List<byte[]> expectedMz=new ArrayList<>();
        final Set<String> checkedMzAttemptIds=new HashSet<>();
        int checkedMpTensors,checkedMtTensors;
        File archive;
        Runnable onMtRun=()->{};
        Suite(RoiBudgetPolicy policy,String scenario,boolean cropped)throws Exception{
            this(policy,scenario,cropped,false);
        }
        Suite(RoiBudgetPolicy policy,String scenario,boolean cropped,boolean f6)throws Exception{
            this(policy,scenario,cropped,f6,null,null,null);
        }
        Suite(RoiBudgetPolicy policy,String scenario,boolean cropped,boolean f6,TvBboxCrop approvedTv,Final180PhotoRoi approvedPhoto,String label)throws Exception{
            this.scenario=scenario;this.cropped=cropped;
            crop=approvedTv==null?TvBboxCropTest.selection(640,400):approvedTv;
            camera=Bitmap.createBitmap(crop.sourceWidth,crop.sourceHeight,Bitmap.Config.ARGB_8888);
            Canvas draw=new Canvas(camera);draw.drawColor(Color.rgb(25,80,140));Paint paint=new Paint();
            for(int index=0;index<2;index++){
                if(index>0&&(scenario.equals("extra-final")||scenario.equals("duplicate-final")))continue;
                List<Point2> points=plate(index);paint.setColor(Color.WHITE);draw.drawRect(points.get(0).x-5,points.get(0).y-5,points.get(1).x+5,points.get(2).y+5,paint);
                paint.setColor(Color.BLACK);for(int x=(int)points.get(0).x;x<points.get(1).x;x+=8)draw.drawRect(x,points.get(0).y,x+3,points.get(2).y,paint);
            }
            Map<ModelRole,InstalledModel> active=(Map<ModelRole,InstalledModel>)field(registry,"active");active.clear();
            for(ModelRole role:ModelRole.values())active.put(role,model(role));
            ResearchStageExecutionConfig mp=policy.usesVehicleCascade()?stage(active.get(ModelRole.VEHICLE)):ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE);
            ResearchExecutionConfig base=new ResearchExecutionConfig("roi_budget",label==null?"R"+policy.ordinal():label,policy,RecognitionProfile.BALANCED,"640x400",
                    false,false,policy.usesVehicleCascade(),true,false,false,mp,stage(active.get(ModelRole.PLATE)),stage(active.get(ModelRole.CHARACTER)))
                    .withCameraSetup(crop.cameraSetup);
            ResearchExecutionConfig selected=cropped?base.withTvBboxCrop(crop):base;
            config=f6?selected.withFinal180(approvedPhoto==null?Final180PhotoRoi.prepare(new JSONArray("[{x:.2,y:.22},{x:.82,y:.22},{x:.82,y:.72},{x:.2,y:.72}]"),crop):approvedPhoto):selected;
            if(f6){paint.setColor(Color.MAGENTA);draw.drawRect(90,70,110,90,paint);}
            store=TvBboxProducerFixture.prepare(temp.newFolder(),config);
            engine=new MobileAlprEngine(registry,new AutoTuneManager(RuntimeEnvironment.getApplication()),policy,MtExecutionPolicy.LEGACY_BURST,
                    scenario.equals("zero")?MtFallbackPolicy.DEFERRED:MtFallbackPolicy.SAME_CYCLE,VehicleTrackingPolicy.RAW_MP,config,
                    new VehicleTrackingCoordinator(),RuntimeEvidenceSink.NONE,(model,variant,profile,sink)->{
                FakeBackend backend=new FakeBackend(model.manifest().role(),this);backends.put(model.manifest().role(),backend);return backend;
            });
            engine.setStaticSceneMode(true);engine.setResearchCollector(store,1);suites.add(this);
        }
        InstalledModel model(ModelRole role)throws Exception{
            File root=temp.newFolder();InstalledModel original=RuntimeEvidenceFixture.model(root,"fp32","FLOAT32",null);
            JSONObject json=new JSONObject(original.manifest().rawJson());
            json.put("role",role.wireName()).put("model_id","synthetic-f20-"+role.wireName()).put("task",role==ModelRole.PLATE?"pose":"detect");
            json.getJSONObject("input").put("width",64).put("height",64);
            JSONArray labels=new JSONArray();if(role==ModelRole.VEHICLE)labels.put("car");else if(role==ModelRole.PLATE)labels.put("plate");else for(String c:new String[]{"A","1","2","3"})labels.put(c);
            json.put("labels",labels);json.put("output",new JSONObject().put("decoder","ultralytics_yolo_raw_v1")
                    .put("class_count",labels.length()).put("keypoint_count",role==ModelRole.PLATE?4:0)
                    .put("keypoint_dimensions",role==ModelRole.PLATE?3:0).put("tensor_layout","channels_first")
                    .put("confidence_threshold",.25).put("iou_threshold",.45));
            return new InstalledModel(ModelManifest.parse(json.toString()),root,"synthetic-f20-"+role.wireName());
        }
        ResearchStageExecutionConfig stage(InstalledModel model){return ResearchStageExecutionConfig.enabled(model.manifest().role(),model,
                model.manifest().variants().get(0),ExecutionProfile.tfliteCpu(2),registry);}
        PipelineResult run(int sequence)throws Exception{return run(sequence,stamp(sequence));}
        PipelineResult run(int sequence,ContinuityStamp stamp)throws Exception{
            String scene=store.f5aEvidence().ticket(stamp);if(!scene.isEmpty())store.captureSceneEvidence(scene,stamp,camera);
            InferenceTrace trace=new InferenceTrace(sequence,stamp);trace.start("total");
            int mtBefore=backends.containsKey(ModelRole.PLATE)?backends.get(ModelRole.PLATE).runs:0;
            try{
                if(cropped)try(TvBboxCrop.Frame input=crop.open(camera,crop.geometry,crop.cameraSetup)){
                    return engine.run(camera,trace,stamp,(a,w,h,s)->{},()->scenario.equals("cancelled")&&backends.containsKey(ModelRole.PLATE)&&backends.get(ModelRole.PLATE).runs>mtBefore,null,input);
                }else return engine.run(camera,trace,stamp,(a,w,h,s)->{},()->false);
            }finally{store.appendTelemetry("traces.jsonl",trace.toJson().toString());}
        }
        JSONObject currentMt()throws Exception{
            Object batch=field(engine,"researchBatch");List<AcquisitionAttemptRecord> records=(List<AcquisitionAttemptRecord>)field(batch,"records");
            return TvBboxProducerFixture.data(records.get(records.size()-1));
        }
        byte[] tensor(Bitmap bitmap)throws Exception{
            PreparedInput input=BitmapTensorPreprocessor.prepare(bitmap,registry.getActive(ModelRole.CHARACTER).manifest().input(),new TensorInfo(0,new int[]{1,64,64,3},"FLOAT32",64*64*12,0,0));
            byte[] result=new byte[input.buffer.limit()];input.buffer.duplicate().get(result);return result;
        }
        void checkPixels(byte[] received,int left,int top,int width,int height)throws Exception{
            Bitmap input=Bitmap.createBitmap(camera,left,top,width,height);try{assertArrayEquals(tensor(input),received);}finally{if(input!=camera)input.recycle();}
        }
        void checkActualDecodedMz(byte[] received)throws Exception{
            Object batch=field(engine,"researchBatch");List<AcquisitionAttemptRecord> records=(List<AcquisitionAttemptRecord>)field(batch,"records");
            for(AcquisitionAttemptRecord record:records){JSONObject data=TvBboxProducerFixture.data(record);
                if(data.optBoolean("mz_executed")&&checkedMzAttemptIds.add(record.attemptId)){
                    JSONArray points=data.getJSONObject("tv_bbox_local_geometry").getJSONArray("corners_px");List<Point2> full=new ArrayList<>();
                    for(int i=0;i<4;i++)full.add(new Point2((float)points.getJSONArray(i).getDouble(0)+crop.left,(float)points.getJSONArray(i).getDouble(1)+crop.top));
                    Bitmap rectified=PlateRectifier.rectify(camera,full);try{assertArrayEquals("MZ must use the actual decoded source Pose",tensor(rectified),received);}finally{rectified.recycle();}return;
                }
            }fail("MZ backend needs an actual executed detection attempt");
        }
        File finish(String name)throws Exception{
            archive=TvBboxProducerFixture.finish(store,config);
            assertEquals(cropped?"PASS":"ABSENT",TvBboxInputValidator.validate(archive));
            F6ProducerContractVerifier.validateScenes(archive);
            export(archive,name);return archive;
        }
        File finishF6(String name,String status)throws Exception{
            archive=TvBboxProducerFixture.finish(store,config);assertEquals(status,F6ProducerContractVerifier.validate(archive));
            String folder=System.getenv("F6_FIXTURES");if(folder!=null){java.nio.file.Path target=Paths.get(folder,name+".alprsession");Files.createDirectories(target.getParent());Files.copy(archive.toPath(),target,StandardCopyOption.REPLACE_EXISTING);}
            return archive;
        }
        public void close()throws Exception{engine.close();TvBboxProducerFixture.close(store);if(!camera.isRecycled())camera.recycle();}
    }
    /** Shared producer boundary for the real UI preference -> engine -> STOP -> archive regression. */
    public static File produceUiLifecycle(TvBboxCrop tv,Final180PhotoRoi photo,String label,File output)throws Exception{
        return produceUiLifecycle(tv,photo,label,output,()->{});
    }
    public static File produceUiLifecycle(TvBboxCrop tv,Final180PhotoRoi photo,String label,File output,Runnable afterFrozen)throws Exception{
        TvBboxPipelineTest fixture=new TvBboxPipelineTest();fixture.temp.create();
        try{
            Suite s=fixture.new Suite(RoiBudgetPolicy.FULL_FRAME,"no-detection",true,true,tv,photo,label);
            F6LifecycleTrace.record(s.store.sessionId(),"START_FROZEN",new JSONObject(new String(Files.readAllBytes(new File(s.store.directory(),"session.json").toPath()),StandardCharsets.UTF_8)));
            afterFrozen.run();
            PipelineResult result=s.run(1);if(result!=null)result.close();
            TvBboxProducerFixture.drain(s.store);
            F6LifecycleTrace.record(s.store.sessionId(),"FIRST_FRAME_PROCESSED",new JSONObject().put("execution",s.config.toJson())
                    .put("lifecycle_journal",new String(Files.readAllBytes(new File(s.store.directory(),Final180Evidence.FILE).toPath()),StandardCharsets.UTF_8)));
            s.store.closeAdmission("manual",System.currentTimeMillis(),System.nanoTime());
            F6LifecycleTrace.record(s.store.sessionId(),"STOP_ADMISSION_CLOSED",new JSONObject(new String(Files.readAllBytes(new File(s.store.directory(),"session.json").toPath()),StandardCharsets.UTF_8)));
            File archive=s.store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"test_only\":true,\"device_run\":\"NOT_RUN\"}","","","","",""),s.config);
            F6LifecycleTrace.record(s.store.sessionId(),"FINALIZED_EXPORT",new JSONObject(new String(Files.readAllBytes(new File(s.store.directory(),"session.json").toPath()),StandardCharsets.UTF_8)));
            assertEquals("PASS",F6ProducerContractVerifier.validate(archive));
            assertEquals("NO_DETECTION",rows(archive,"samples/attempts.jsonl").get(0).getString("mt_status"));
            Files.createDirectories(output.getParentFile().toPath());Files.copy(archive.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING);return output;
        }finally{fixture.close();fixture.temp.delete();}
    }
    static byte[] bytes(ByteBuffer buffer){ByteBuffer copy=buffer.duplicate();copy.rewind();byte[] result=new byte[copy.remaining()];copy.get(result);return result;}
    final class FakeBackend implements InferenceBackend {
        final ModelRole role;final Suite suite;int runs;final List<byte[]> received=new ArrayList<>();
        FakeBackend(ModelRole role,Suite suite){this.role=role;this.suite=suite;}
        public int inputByteSize(){return 64*64*12;}public TensorInfo inputInfo(){return new TensorInfo(0,new int[]{1,64,64,3},"FLOAT32",inputByteSize(),0,0);}
        public String runtimeName(){return "SYNTHETIC_MODEL_CALCULATION_ONLY";}public void close(){}
        public InferenceRunResult run(ByteBuffer input){
            runs++;byte[] got=bytes(input);received.add(got);
            if(role==ModelRole.PLATE)suite.onMtRun.run();
            if(role==ModelRole.PLATE&&suite.scenario.equals("mt-error"))throw new IllegalStateException("SYNTHETIC_MT_FAILURE");
            if(role==ModelRole.CHARACTER&&suite.scenario.equals("mz-error"))throw new IllegalStateException("SYNTHETIC_MZ_FAILURE");
            if(role==ModelRole.PLATE&&suite.scenario.equals("mt-fatal"))throw new OutOfMemoryError("SYNTHETIC_NO_ALLOCATION");
            if(role==ModelRole.CHARACTER&&suite.scenario.equals("mz-fatal"))throw new OutOfMemoryError("SYNTHETIC_NO_ALLOCATION");
            int channels=role==ModelRole.PLATE?17:role==ModelRole.CHARACTER?8:5,anchors=4;
            float[] values=new float[channels*anchors];
            try {
                if(role==ModelRole.VEHICLE){
                    int l=suite.cropped?suite.crop.left:0,t=suite.cropped?suite.crop.top:0;
                    int w=suite.cropped?suite.crop.width():640,h=suite.cropped?suite.crop.height():400;
                    suite.checkPixels(got,l,t,w,h);suite.checkedMpTensors++;
                    if(!suite.scenario.equals("zero"))for(int i=0;i<2;i++){
                        float left=100+i*240,top=75,right=300+i*240,bottom=325;
                        encode(values,channels,anchors,i,left-l,top-t,right-l,bottom-t,w,h,0,null);
                    }
                }else if(role==ModelRole.PLATE){
                    JSONObject audit=suite.currentMt();suite.observedMt.add(new JSONObject(audit.toString()));
                    int l=audit.getInt("roi_left"),t=audit.getInt("roi_top"),r=audit.getInt("roi_right"),b=audit.getInt("roi_bottom");
                    suite.checkPixels(got,l,t,r-l,b-t);suite.checkedMtTensors++;
                    boolean empty=suite.scenario.equals("no-detection")||suite.scenario.equals("fallback")&&runs<=2;
                    if(!empty)for(int i=0;i<2;i++){
                        if((suite.scenario.equals("one")||suite.scenario.equals("no-mz"))&&i>0)continue;
                        List<Point2> points=new ArrayList<>(plate(i));
                        if(suite.scenario.equals("no-mz"))points=Arrays.asList(new Point2(170,160),new Point2(180,160),new Point2(180,170),new Point2(170,170));
                        if(suite.scenario.equals("duplicate-final"))points=Arrays.asList(new Point2(i==0?160:222,150),new Point2(i==0?212:280,150),new Point2(i==0?212:280,190),new Point2(i==0?160:222,190));
                        boolean inside=true;for(Point2 p:points)inside&=p.x>=l&&p.x<=r&&p.y>=t&&p.y<=b;
                        if(!inside)continue;
                        List<Point2> local=new ArrayList<>();for(Point2 p:points)local.add(new Point2(p.x-l,p.y-t,1));
                        encode(values,channels,anchors,i,points.get(0).x-l,points.get(0).y-t,points.get(1).x-l,points.get(2).y-t,r-l,b-t,0,local);
                        if(suite.scenario.equals("no-mz")){
                            // A valid small quad, low keypoint confidence and an oversized box use the existing MZ quality gate.
                            encode(values,channels,anchors,i,0,0,r-l,b-t,r-l,b-t,0,local);
                            for(int k=0;k<4;k++)values[(7+k*3)*anchors+i]=0;
                        }
                        Bitmap rectified=PlateRectifier.rectify(suite.camera,points);try{suite.expectedMz.add(suite.tensor(rectified));}finally{rectified.recycle();}
                    }
                }else {
                    if(suite.config.final180PhotoRoi()!=null)suite.checkActualDecodedMz(got);
                    else{boolean match=false;for(byte[] expected:suite.expectedMz)match|=Arrays.equals(expected,got);assertTrue("MZ must use full source and translated Pose",match);}
                    for(int i=0;i<(suite.scenario.equals("no-read")?0:4);i++){
                        float cx=8+i*14,cy=32;values[0*anchors+i]=cx;values[1*anchors+i]=cy;values[2*anchors+i]=7;values[3*anchors+i]=10;values[(4+i)*anchors+i]=.95f;
                    }
                }
            }catch(Exception error){throw new AssertionError(error);}
            ByteBuffer output=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder());for(float value:values)output.putFloat(value);output.rewind();
            return new InferenceRunResult(Collections.singletonMap(0,output),Collections.singletonMap(0,new TensorInfo(0,new int[]{1,channels,anchors},"FLOAT32",values.length*4,0,0)));
        }
    }
    static void encode(float[] out,int channels,int anchors,int index,float left,float top,float right,float bottom,int w,int h,int classId,List<Point2> points){
        float scale=Math.min(64f/w,64f/h),padX=(64-w*scale)/2,padY=(64-h*scale)/2;
        out[index]=((left+right)/2)*scale+padX;out[anchors+index]=((top+bottom)/2)*scale+padY;
        out[2*anchors+index]=(right-left)*scale;out[3*anchors+index]=(bottom-top)*scale;out[(4+classId)*anchors+index]=.95f;
        if(points!=null)for(int i=0;i<4;i++){out[(5+i*3)*anchors+index]=points.get(i).x*scale+padX;out[(6+i*3)*anchors+index]=points.get(i).y*scale+padY;out[(7+i*3)*anchors+index]=.99f;}
    }
    static void export(File archive,String name)throws Exception{
        String folder=System.getenv("F5A20_FIXTURES");if(folder==null)return;java.nio.file.Path target=Paths.get(folder,name+".alprsession");Files.createDirectories(target.getParent());Files.copy(archive.toPath(),target,StandardCopyOption.REPLACE_EXISTING);
    }
    static List<JSONObject> rows(File archive,String entry)throws Exception{
        try(ZipFile zip=new ZipFile(archive)){List<JSONObject> rows=new ArrayList<>();String text=new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8);
            for(String line:text.split("\n"))if(!line.isEmpty())rows.add(new JSONObject(line));return rows;}
    }
    @Test public void R0UsesWholeTvWithoutMpAndPreservesPoseAndMzPixels()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true);PipelineResult result=s.run(1);assertNotNull(result);
        assertFalse(s.backends.containsKey(ModelRole.VEHICLE));assertEquals(1,s.backends.get(ModelRole.PLATE).runs);
        assertEquals(s.crop.left,s.observedMt.get(0).getInt("roi_left"));assertEquals(s.crop.right,s.observedMt.get(0).getInt("roi_right"));
        assertTrue("MZ must actually execute",s.backends.get(ModelRole.CHARACTER).runs>0);result.close();
        File archive=s.finish("tv-r0");List<JSONObject> a=rows(archive,"samples/attempts.jsonl");assertEquals(1,a.size());
        String pose=a.get(0).getString("mt_pose_corners_norm");assertTrue(pose.startsWith("0.25:"));
        assertEquals("source_frame_normalized_v1",a.get(0).getString("mt_pose_coordinate_space"));
    }
    @Test public void f6PhotoRoiUsesExactPixelsAndKeepsOuterTvAndSourceEvidence()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true,true);PipelineResult result=s.run(1);if(result!=null)result.close();
        File archive=s.finishF6("r0-positive","PASS");JSONObject a=rows(archive,"samples/attempts.jsonl").get(0);
        assertEquals(s.config.final180PhotoRoi().left,a.getInt("roi_left"));assertEquals(s.config.final180PhotoRoi().top,a.getInt("roi_top"));
        assertEquals(1,s.checkedMtTensors);assertFalse(s.backends.containsKey(ModelRole.VEHICLE));
        List<JSONObject> events=rows(archive,Final180Evidence.FILE);JSONObject start=events.stream().filter(e->"MT_STARTED".equals(e.optString("event"))).findFirst().get();
        assertEquals(a.getString("mt_invocation_id"),start.getString("mt_invocation_id"));assertEquals(stamp(1).sourceTimestampNanos,start.getJSONObject("source_stamp").getLong("source_timestamp_nanos"));
        JSONObject frame=rows(archive,TvBboxEvidence.INDEX).get(0);try(ZipFile zip=new ZipFile(archive)){
            for(String name:new String[]{"source_image","crop_image"}){byte[] image=zip.getInputStream(zip.getEntry(frame.getJSONObject(name).getString("entry"))).readAllBytes();Bitmap b=BitmapFactory.decodeByteArray(image,0,image.length);
                int x="source_image".equals(name)?100:100-s.crop.left,y="source_image".equals(name)?80:80-s.crop.top;
                int color=b.getPixel(x,y);assertTrue(Color.red(color)>180&&Color.blue(color)>180&&Color.green(color)<80);b.recycle();}
        }
    }
    @Test public void f6ExportsZeroMultipleNoReadAndBackendErrorsWithoutFiltering()throws Exception{
        for(String scenario:new String[]{"no-detection","two","no-read","no-mz","extra-final","duplicate-final","mt-error","mz-error","mt-fatal","mz-fatal","cancelled"}){
            Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,scenario,true,true);
            if(scenario.endsWith("error"))assertThrows(IllegalStateException.class,()->s.run(1));
            else if(scenario.endsWith("fatal"))assertThrows(OutOfMemoryError.class,()->s.run(1));
            else if(scenario.equals("cancelled"))assertThrows(MobileAlprEngine.ProcessingCancelledException.class,()->s.run(1));
            else{PipelineResult result=s.run(1);if(result!=null)result.close();}
            File archive=s.finishF6("r0-"+scenario,scenario.endsWith("error")||scenario.endsWith("fatal")||scenario.equals("cancelled")?"PARTIAL":"PASS");
            List<JSONObject> attempts=rows(archive,"samples/attempts.jsonl"),roles=rows(archive,F5aEvidence.ROLES);
            if(scenario.equals("no-detection")){assertEquals(1,attempts.size());assertEquals(0,roles.get(0).getJSONArray("final_attempt_ids").length());assertEquals(0,attempts.get(0).getInt("mt_detection_count"));}
            if(scenario.equals("two")){assertEquals(2,attempts.size());assertEquals(2,roles.get(0).getJSONArray("final_attempt_ids").length());}
            if(scenario.equals("extra-final")||scenario.equals("duplicate-final"))assertEquals(2,roles.get(0).getJSONArray("final_attempt_ids").length());
            if(scenario.equals("no-mz")){assertEquals(1,roles.get(0).getJSONArray("final_attempt_ids").length());assertEquals("NOT_RUN",attempts.get(0).getString("mz_status"));assertEquals(0,s.backends.get(ModelRole.CHARACTER).runs);}
            if(scenario.equals("no-read"))for(JSONObject a:attempts){assertEquals("NO_CHARACTERS",a.getString("mz_status"));assertEquals("",a.getString("prediction"));}
        }
    }
    @Test public void f6ManySceneStopExportRetainsActualMtMzLinksOverVirtualTwentyMinutes()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"two",true,true,null,null,"R0_FINAL");
        JSONObject original=new JSONObject(new String(Files.readAllBytes(new File(s.store.directory(),"session.json").toPath()),StandardCharsets.UTF_8));
        long start=original.getLong("started_elapsed_nanos"),wall=java.time.Instant.parse(original.getString("started_at")).toEpochMilli();
        for(int n=1;n<=120;n++){
            final int current=n;
            ContinuityStamp source=new ContinuityStamp(n,n,0,n,9007199254740993L+n*10_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
            s.engine.hardResetScene("SYNTHETIC_TEST_ONLY_scene_change");
            s.store.f5aEvidence().boundary("SYNTHETIC_20MIN_VIRTUAL",n,"TEST_ONLY",source,wall+n*10000L,start+n*10_000_000_000L);
            s.scenario=new String[]{"two","one","no-detection","no-read","no-mz"}[n%5];
            s.onMtRun=n%17==0?()->s.store.f5aEvidence().boundary("SYNTHETIC_LATE",current+1,"TEST_ONLY_next_scene",
                    new ContinuityStamp(current+1,current+1,0,current+1,source.sourceTimestampNanos+1,SourceTimestampDomain.CAMERAX_SENSOR),wall+current*10000L,start+current*10_000_000_000L):()->{};
            if(n%17==0){s.scenario="cancelled";assertThrows(MobileAlprEngine.ProcessingCancelledException.class,()->s.run(current,source));}
            else{PipelineResult result=s.run(n,source);if(result!=null)result.close();}
            TvBboxProducerFixture.drain(s.store);
        }
        s.store.closeAdmission("synthetic_completed_20min_virtual_NOT_THERMAL",wall+1_200_000L,start+1_200_000_000_000L);
        File archive=s.store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"device_run\":\"NOT_RUN\",\"virtual_duration_seconds\":1200}","","","","",""),s.config);
        assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));
        assertEquals(120,s.backends.get(ModelRole.PLATE).runs);assertTrue(s.backends.get(ModelRole.CHARACTER).runs>60);
        List<JSONObject> roles=rows(archive,F5aEvidence.ROLES);assertEquals(120,roles.size());
        assertTrue(rows(archive,F5aEvidence.SCENES).size()>=120);
        assertTrue(roles.stream().anyMatch(r->r.optBoolean("stale_or_cancelled")));
        JSONObject meta;try(ZipFile zip=new ZipFile(archive)){meta=new JSONObject(new String(zip.getInputStream(zip.getEntry("session.json")).readAllBytes(),StandardCharsets.UTF_8));}
        assertEquals(1_200_000_000_000L,meta.getLong("finished_elapsed_nanos")-meta.getLong("started_elapsed_nanos"));
        assertFalse(meta.getBoolean("collection_complete"));
        String folder=System.getenv("F6_STATE_FIXTURES");if(folder!=null){Files.createDirectories(Paths.get(folder));Files.copy(archive.toPath(),Paths.get(folder,"synthetic-20min-120scenes-mt-mz-PARTIAL.alprsession"));}
    }
    @Test public void f6DelayedResultKeepsTheOriginalSourceAcrossSceneBoundary()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true,true);
        s.onMtRun=()->s.store.f5aEvidence().boundary("synthetic",2,"synthetic-next-photo",new ContinuityStamp(2,2,0,2,9007199254741000L,SourceTimestampDomain.CAMERAX_SENSOR),System.currentTimeMillis(),System.nanoTime());
        PipelineResult r=s.run(1);if(r!=null)r.close();File archive=s.finishF6("r0-delayed-old-frame","PARTIAL");
        JSONObject a=rows(archive,"samples/attempts.jsonl").get(0);assertEquals(1,a.getLong("scene_generation"));
        for(JSONObject event:rows(archive,Final180Evidence.FILE))if(!event.isNull("mt_invocation_id"))assertEquals(1,event.getJSONObject("source_stamp").getLong("scene_generation"));
    }
    @Test public void f6PassiveLoggingDoesNotChangeTensorsOrModelCallCounts()throws Exception{
        Suite on=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true,true),off=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true,true);
        // Test-only removal of the passive sink; both engines retain the identical approved photo input domain.
        Field f=ResearchSessionStore.class.getDeclaredField("final180");f.setAccessible(true);f.set(off.store,null);
        PipelineResult a=on.run(1),b=off.run(1);assertEquals(a.status,b.status);assertEquals(a.recognitions.size(),b.recognitions.size());
        for(ModelRole role:new ModelRole[]{ModelRole.PLATE,ModelRole.CHARACTER}){
            assertEquals(on.backends.get(role).runs,off.backends.get(role).runs);
            for(int i=0;i<on.backends.get(role).received.size();i++)assertArrayEquals(on.backends.get(role).received.get(i),off.backends.get(role).received.get(i));
        }
        a.close();b.close();TvBboxProducerFixture.drain(off.store);on.finishF6("r0-logging-equivalence","PASS");
    }
    @Test public void f6RejectsContradictoryFramesOffsetsAndAttemptIdentities()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true,true);PipelineResult r=s.run(1);if(r!=null)r.close();File good=s.finishF6("r0-validator-reference","PASS");
        for(String fault:new String[]{"image-sha","payload-missing","source-stamp","unknown-invocation","duplicate-index","missing-offset","double-offset","roi-offset","wrong-space","dimensions","snapshot-digest","false-homography"}){
            File bad=mutate(good,fault,s.crop);assertThrows("F6 rejects "+fault,Exception.class,()->Final180ArchiveValidator.validate(bad));
        }
    }
    @Test public void R1UsesMpCropAndAtMostOneRoi()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.ONE_ROI,"one",true);PipelineResult result=s.run(1);if(result!=null)result.close();
        assertEquals(1,s.checkedMpTensors);assertEquals(1,s.backends.get(ModelRole.PLATE).runs);assertEquals(1,s.observedMt.size());s.finish("tv-r1");
    }
    @Test public void R2UsesTwoRoisAndDistinctPlateIdentities()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.TWO_ROI,"two",true);PipelineResult result=s.run(1);if(result!=null)result.close();
        assertEquals(1,s.checkedMpTensors);assertEquals(2,s.backends.get(ModelRole.PLATE).runs);
        for(JSONObject roi:s.observedMt){assertTrue(roi.getInt("roi_left")>=s.crop.left);assertTrue(roi.getInt("roi_top")>=s.crop.top);
            assertTrue(roi.getInt("roi_right")<=s.crop.right);assertTrue(roi.getInt("roi_bottom")<=s.crop.bottom);}
        File archive=s.finish("tv-r2-two-roi");List<JSONObject> attempts=rows(archive,"samples/attempts.jsonl");Set<String> calls=new HashSet<>();
        for(JSONObject a:attempts)calls.add(a.getString("mt_invocation_id"));assertEquals(2,calls.size());
    }
    @Test public void zeroRoiPreservesFullFrameWithoutInventingMt()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.TWO_ROI,"zero",true);PipelineResult result=s.run(1);if(result!=null)result.close();
        assertEquals(0,s.backends.get(ModelRole.PLATE).runs);assertEquals(0,s.backends.get(ModelRole.CHARACTER).runs);
        File archive=s.finish("tv-zero-roi");assertTrue(rows(archive,"samples/attempts.jsonl").isEmpty());
        JSONObject frame=rows(archive,TvBboxEvidence.INDEX).get(0);assertEquals(0,frame.getJSONArray("attempt_links").length());assertEquals("WRITTEN",frame.getJSONObject("source_image").getString("status"));
    }
    @Test public void twoEmptyRoisFallbackToTvOnlyAndFindMultiplePlates()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.TWO_ROI,"fallback",true);PipelineResult result=s.run(1);if(result!=null)result.close();
        assertEquals(3,s.backends.get(ModelRole.PLATE).runs);JSONObject last=s.observedMt.get(2);
        assertEquals(s.crop.left,last.getInt("roi_left"));assertEquals(s.crop.top,last.getInt("roi_top"));assertEquals(s.crop.right,last.getInt("roi_right"));
        File archive=s.finish("tv-r2-fallback-multiple");List<JSONObject> roles=rows(archive,"samples/mt_detection_roles.jsonl");assertEquals(3,roles.size());
    }
    @Test public void noDetectionAndCancellationRetainInputEvidence()throws Exception{
        Suite empty=new Suite(RoiBudgetPolicy.FULL_FRAME,"no-detection",true);PipelineResult result=empty.run(1);if(result!=null)result.close();
        File archive=empty.finish("tv-no-detection");assertEquals("NO_DETECTION",rows(archive,"samples/attempts.jsonl").get(0).getString("mt_status"));
        Suite cancelled=new Suite(RoiBudgetPolicy.FULL_FRAME,"cancelled",true);assertThrows(MobileAlprEngine.ProcessingCancelledException.class,()->cancelled.run(1));
        File stopped=cancelled.finish("tv-cancelled");assertTrue(rows(stopped,TvBboxEvidence.INDEX).get(0).getBoolean("stale_or_cancelled"));
    }
    @Test public void fullFrameReferenceDoesNotGainTvMetadataOrCropItsInputs()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",false);PipelineResult result=s.run(1);if(result!=null)result.close();
        assertEquals(0,s.observedMt.get(0).getInt("roi_left"));assertEquals(640,s.observedMt.get(0).getInt("roi_right"));
        File archive=s.finish("legacy-full-frame");try(ZipFile zip=new ZipFile(archive)){
            assertNull(zip.getEntry(TvBboxEvidence.INDEX));JSONObject session=new JSONObject(new String(zip.getInputStream(zip.getEntry("session.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertFalse(session.has("input_mode"));assertFalse(session.has("tv_bbox_crop"));
        }
    }

    @Test public void corruptContractFixturesRejectMissingDoubleOffsetsAndFalseProvenance()throws Exception{
        Suite s=new Suite(RoiBudgetPolicy.FULL_FRAME,"one",true);PipelineResult result=s.run(1);if(result!=null)result.close();
        File good=s.finish("tv-valid-for-negatives");
        for(String fault:new String[]{"image-sha","payload-missing","source-stamp","unknown-invocation","duplicate-index",
                "missing-offset","double-offset","roi-offset","wrong-space","dimensions","snapshot-digest","false-homography"}){
            File bad=mutate(good,fault,s.crop);assertThrows("Reject "+fault,Exception.class,()->TvBboxInputValidator.validate(bad));export(bad,"negative-"+fault);
        }
    }
    File mutate(File original,String fault,TvBboxCrop crop)throws Exception{
        Map<String,byte[]> entries=new LinkedHashMap<>();try(ZipFile zip=new ZipFile(original)){
            Enumeration<? extends ZipEntry> all=zip.entries();while(all.hasMoreElements()){ZipEntry e=all.nextElement();entries.put(e.getName(),zip.getInputStream(e).readAllBytes());}
        }
        JSONObject row=new JSONObject(new String(entries.get(TvBboxEvidence.INDEX),StandardCharsets.UTF_8).trim());
        String attemptsText=new String(entries.get("samples/attempts.jsonl"),StandardCharsets.UTF_8).trim();JSONObject attempt=new JSONObject(attemptsText);
        switch(fault){
            case "image-sha":row.getJSONObject("crop_image").put("sha256",String.join("",Collections.nCopies(64,"0")));break;
            case "payload-missing":entries.remove(row.getJSONObject("crop_image").getString("entry"));break;
            case "source-stamp":row.getJSONObject("source_stamp").put("source_timestamp_nanos",17L);break;
            case "unknown-invocation":row.getJSONArray("attempt_links").getJSONObject(0).put("mt_invocation_id","nonexistent");break;
            case "missing-offset":case "double-offset":
                int sign="missing-offset".equals(fault)?-1:1;
                for(String k:new String[]{"plate_left","plate_top","plate_right","plate_bottom"})attempt.put(k,attempt.getDouble(k)+sign*(k.equals("plate_left")||k.equals("plate_right")?crop.left:crop.top));break;
            case "roi-offset":attempt.put("roi_left",attempt.getInt("roi_left")+crop.left);break;
            case "wrong-space":row.getJSONObject("crop_image").put("coordinate_space",TvBboxCrop.CAMERA_SPACE);break;
            case "dimensions":row.getJSONObject("source_image").put("width_px",42);break;
            case "snapshot-digest":row.put("tv_bbox_crop_digest_sha256","bad");break;
            case "false-homography":row.put("H_TV","APPLIED");break;
        }
        String index=row+"\n";if("duplicate-index".equals(fault))index+=index;
        entries.put(TvBboxEvidence.INDEX,index.getBytes(StandardCharsets.UTF_8));entries.put("samples/attempts.jsonl",(attempt+"\n").getBytes(StandardCharsets.UTF_8));
        JSONObject manifest=new JSONObject(new String(entries.remove("manifest.json"),StandardCharsets.UTF_8));JSONObject hashes=new JSONObject();
        for(Map.Entry<String,byte[]> e:entries.entrySet())hashes.put(e.getKey(),sha(e.getValue()));
        manifest.put("entry_sha256",hashes).put("entries_before_manifest",entries.size());entries.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        File bad=temp.newFile(fault+".alprsession");try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(bad))){
            for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
        }return bad;
    }
    static String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
