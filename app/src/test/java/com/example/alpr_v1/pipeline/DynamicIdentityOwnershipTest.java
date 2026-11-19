package com.example.alpr_v1.pipeline;

import android.app.Application;
import android.graphics.*;
import com.example.alpr_v1.acquisition.*;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.metrics.*;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.tracking.*;
import com.example.alpr_v1.vision.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Real engine/decoder/association/consensus flow with explicit synthetic tensor backends. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DynamicIdentityOwnershipTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    static final long S=1_000_000_000L;
    static final String LABELS="AB12345CD98760";
    final List<EngineFixture> fixtures=new ArrayList<>();
    @After public void close(){for(EngineFixture f:fixtures)f.close();}

    final class EngineFixture implements AutoCloseable {
        final VehicleTrackingCoordinator vehicles=new VehicleTrackingCoordinator();
        final MobileAlprEngine engine;
        final Bitmap image=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        String raw="AB12345";float confidence=.99f;Runnable duringMz=()->{};java.util.function.Consumer<ByteBuffer> duringMp=input->{};int mzRuns,closedBackends;String lastTrace="";
        boolean partialPlate,borderJitter;
        List<com.example.alpr_v1.ui.OverlayItem> lastGeometryOverlays=Collections.emptyList();
        EngineFixture()throws Exception {
            Canvas canvas=new Canvas(image);Paint paint=new Paint();
            for(int y=0;y<400;y+=4)for(int x=0;x<640;x+=4){paint.setColor((x/4+y/4)%2==0?Color.WHITE:Color.DKGRAY);canvas.drawRect(x,y,x+4,y+4,paint);}
            EnumMap<ModelRole,InstalledModel> active=new EnumMap<>(ModelRole.class);
            for(ModelRole role:ModelRole.values())active.put(role,model(role));
            ModelRegistry registry=new ModelRegistry(RuntimeEnvironment.getApplication(),true);
            Map<ModelRole,InstalledModel> selected=org.robolectric.util.ReflectionHelpers.getField(registry,"active");selected.clear();selected.putAll(active);
            vehicles.setNormalDynamicIdentity(true,S);
            engine=new MobileAlprEngine(registry,new AutoTuneManager(RuntimeEnvironment.getApplication()),RoiBudgetPolicy.TWO_ROI,
                    MtExecutionPolicy.LEGACY_BURST,MtFallbackPolicy.SAME_CYCLE,VehicleTrackingPolicy.TRACKED_MP,null,vehicles,
                    RuntimeEvidenceSink.NONE,(model,variant,profile,sink)->new SyntheticBackend(model.manifest().role()));
            fixtures.add(this);
        }
        InstalledModel model(ModelRole role)throws Exception {
            File root=temp.newFolder();InstalledModel base=RuntimeEvidenceFixture.model(root,"fp32","FLOAT32",null);
            JSONObject json=new JSONObject(base.manifest().rawJson());json.put("role",role.wireName()).put("model_id","synthetic-identity-"+role.wireName()).put("task",role==ModelRole.PLATE?"pose":"detect");
            json.getJSONObject("input").put("width",64).put("height",64);
            JSONArray labels=new JSONArray();if(role==ModelRole.VEHICLE)labels.put("car");else if(role==ModelRole.PLATE)labels.put("plate");else for(char c:LABELS.toCharArray())labels.put(String.valueOf(c));
            json.put("labels",labels).put("output",new JSONObject().put("decoder","ultralytics_yolo_raw_v1").put("class_count",labels.length())
                    .put("keypoint_count",role==ModelRole.PLATE?4:0).put("keypoint_dimensions",role==ModelRole.PLATE?3:0)
                    .put("tensor_layout","channels_first").put("confidence_threshold",.25).put("iou_threshold",.45));
            return new InstalledModel(ModelManifest.parse(json.toString()),root,"synthetic-identity-"+role.wireName());
        }
        PipelineResult runGeometry(int frame)throws Exception {
            engine.requestVehicleRefreshAfterZoom();
            ContinuityStamp stamp=new ContinuityStamp(0,0,0,frame, S+frame*S,SourceTimestampDomain.CAMERAX_SENSOR);
            InferenceTrace trace=new InferenceTrace(frame,stamp);trace.start("total");
            try{return engine.run(image,trace,stamp,(items,w,h,s)->lastGeometryOverlays=new ArrayList<>(items),()->false);}
            finally{lastTrace=trace.toJson().toString();}
        }
        PipelineResult run(int frame)throws Exception {
            PipelineResult geometry=runGeometry(frame);
            if(!engine.hasPendingDynamicMz())return geometry;
            InferenceTrace trace=new InferenceTrace(frame,engine.pendingDynamicMzStamp());trace.start("total");
            try{return engine.runPendingDynamicMz(trace,()->false);}finally{geometry.close();lastTrace=trace.toJson().toString();}
        }
        long current(){for(VehicleCandidate c:vehicles.latestFrame().candidates)if(!c.predicted&&c.sourceIndex>=0)return c.entityId;throw new AssertionError("No measured vehicle");}
        void proof(long id,int nextFrame){vehicles.identity().preview(S+nextFrame*S,Collections.singleton(id),true);}
        public void close(){engine.close();image.recycle();}
        final class SyntheticBackend implements InferenceBackend {
            final ModelRole role;SyntheticBackend(ModelRole role){this.role=role;}
            public int inputByteSize(){return 64*64*12;}
            public TensorInfo inputInfo(){return new TensorInfo(0,new int[]{1,64,64,3},"FLOAT32",inputByteSize(),0,0);}
            public String runtimeName(){return "SYNTHETIC_IDENTITY_TENSORS_NOT_NEURAL_INFERENCE";}public void close(){closedBackends++;}
            public InferenceRunResult run(ByteBuffer input){
                if(role==ModelRole.VEHICLE)duringMp.accept(input);
                int n=8,c=role==ModelRole.PLATE?17:role==ModelRole.CHARACTER?4+LABELS.length():5;float[] v=new float[c*n];
                if(role==ModelRole.VEHICLE){v[0]=32;v[n]=32;v[2*n]=54;v[3*n]=54;v[4*n]=.99f;}
                else if(role==ModelRole.PLATE){float right=partialPlate?32:50,top=borderJitter?29:28,bottom=borderJitter?39:40;
                    v[0]=(14+right)/2;v[n]=34;v[2*n]=right-14;v[3*n]=bottom-top;v[4*n]=.99f;
                    float[] xy={14,top,right,top,right,bottom,14,bottom};for(int k=0;k<4;k++){v[(5+3*k)*n]=xy[2*k];v[(6+3*k)*n]=xy[2*k+1];v[(7+3*k)*n]=.99f;}}
                else {mzRuns++;duringMz.run();for(int i=0;i<raw.length();i++){v[i]=7+i*7;v[n+i]=32;v[2*n+i]=4;v[3*n+i]=18;v[(4+LABELS.indexOf(raw.charAt(i)))*n+i]=confidence;}}
                ByteBuffer output=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());for(float x:v)output.putFloat(x);output.rewind();
                return new InferenceRunResult(Collections.singletonMap(0,output),Collections.singletonMap(0,new TensorInfo(0,new int[]{1,c,n},"FLOAT32",v.length*4,0,0)));
            }
        }
    }
    @Test public void debugCaptureCopiesTheActualInputBeforeBackendInvocation()throws Exception {
        EngineFixture f=new EngineFixture();File root=temp.newFolder(),folder=new File(root,"mp-diagnostics/engine-order");
        f.engine.setMpDiagnosticCapture(new MpDiagnosticCapture(root,"engine-order"));
        List<byte[]> expected=new ArrayList<>();
        f.duringMp=input->{
            try {
                ByteBuffer copy=input.asReadOnlyBuffer();copy.clear();byte[] bytes=new byte[copy.remaining()];copy.get(bytes);
                if(expected.isEmpty()) {
                    assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(new File(folder,"input.bin").toPath()));
                    expected.add(bytes);input.putFloat(0,1234f);
                }
            }catch(IOException failure){throw new AssertionError(failure);}
        };
        try(PipelineResult result=f.run(1)){assertEquals("AB12345",result.recognizedText);}
        assertEquals(1,expected.size());assertArrayEquals(expected.get(0),java.nio.file.Files.readAllBytes(new File(folder,"input.bin").toPath()));
        JSONObject metadata=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(folder,"capture.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("COMPLETE",metadata.getString("status"));assertEquals(1,metadata.getInt("vehicle_class_eligible_count"));
        assertTrue(metadata.getJSONObject("model").getString("runtime").startsWith("SYNTHETIC_"));
    }
    @Test public void actualEngineRefinesDifferentRawReadsWithinOnePhysicalOwner()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult initial=f.run(1)){assertEquals("AB12345",initial.recognizedText);}
        long id=f.current();f.raw="CD9876";f.confidence=.65f;boolean observed=false;
        for(int frame=2;frame<=20;frame++)try(PipelineResult result=f.run(frame)) {
            assertEquals(id,f.current());
            for(PlateObservation o:result.plateObservations)if(o.hasFreshMzRead()) {
                observed=true;assertEquals(id,o.entityId);assertEquals(6,o.characters.size());
            }
        }
        assertTrue(observed);assertEquals("CD9876",f.vehicles.repository().get(id).registration().text);
        assertEquals(1,f.vehicles.stats().entitiesCreated);assertEquals(1,f.vehicles.identity().ownerNumber(id));
    }
    @Test public void memoryPauseReleasesNativeBackendsAfterInferenceButCannotCloseANewerRun()throws Exception {
        for(boolean resumed:new boolean[]{false,true}) {
            EngineFixture f=new EngineFixture();
            AlprPipeline pipeline=new AlprPipeline(RuntimeEnvironment.getApplication(),new ModelRegistry(RuntimeEnvironment.getApplication(),true),
                    new com.example.alpr_v1.metrics.MetricsCollector(),new AutoTuneManager(RuntimeEnvironment.getApplication()));
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
            android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("memory-runtime-release-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            org.robolectric.util.ReflectionHelpers.setField(main,"uiPreferences",prefs);
            org.robolectric.util.ReflectionHelpers.setField(main,"pipeline",pipeline);
            org.robolectric.util.ReflectionHelpers.setField(pipeline,"engine",f.engine);
            org.robolectric.util.ReflectionHelpers.setField(main,"ordinaryMemoryPaused",true);
            java.util.concurrent.ExecutorService worker=org.robolectric.util.ReflectionHelpers.getField(main,"pipelineInferenceExecutor");
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            worker.execute(()->{entered.countDown();try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
            try {
                org.robolectric.util.ReflectionHelpers.callInstanceMethod(main,"queuePausedOrdinaryRuntimeRelease");
                assertEquals(0,f.closedBackends);
                if(resumed)org.robolectric.util.ReflectionHelpers.setField(main,"ordinaryMemoryPaused",false);
                release.countDown();worker.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(resumed?0:3,f.closedBackends);
            } finally {release.countDown();pipeline.close();worker.shutdownNow();}
        }
    }
    @Test public void partialMtCannotChangeAttachmentRenderAPlateOrEnterMz()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}
        long id=f.current(),lastMt=f.vehicles.repository().get(id).lastMtSourceTimestampNanos();
        int mz=f.mzRuns;f.partialPlate=true;
        for(int frame=2;frame<=5;frame++)try(PipelineResult ignored=f.runGeometry(frame)) {
            assertFalse(f.engine.hasPendingDynamicMz());assertEquals(mz,f.mzRuns);
            assertEquals(lastMt,f.vehicles.repository().get(id).lastMtSourceTimestampNanos());
            assertTrue(f.lastTrace.contains("PARTIAL_REFERENCE"));
            for(com.example.alpr_v1.ui.OverlayItem item:f.lastGeometryOverlays)
                assertNotEquals(com.example.alpr_v1.ui.OverlayItem.Kind.PLATE,item.kind);
        }
        assertEquals(1,f.vehicles.identity().ownerNumber(id));assertEquals(1,f.vehicles.stats().entitiesCreated);
        f.partialPlate=false;try(PipelineResult ignored=f.run(6)){}
        assertTrue(f.mzRuns>mz);assertEquals(id,f.current());
    }
    @Test public void acceptedReferenceIsTheActualFullCropQueuedForMz()throws Exception {
        EngineFixture f=new EngineFixture();int width,height;
        try(PipelineResult ignored=f.runGeometry(1)) {
            Object pending=org.robolectric.util.ReflectionHelpers.getField(f.engine,"pendingDynamicMz");assertNotNull(pending);
            Bitmap crop=org.robolectric.util.ReflectionHelpers.getField(pending,"crop");width=crop.getWidth();height=crop.getHeight();
        }
        f.borderJitter=true;
        try(PipelineResult ignored=f.runGeometry(2)) {
            assertTrue(f.lastTrace.contains("ACCEPTED_REFERENCE"));
            Object pending=org.robolectric.util.ReflectionHelpers.getField(f.engine,"pendingDynamicMz");assertNotNull(pending);
            Bitmap crop=org.robolectric.util.ReflectionHelpers.getField(pending,"crop");
            assertEquals(width,crop.getWidth());assertEquals(height,crop.getHeight());
        }
        assertEquals(0,f.mzRuns);
    }
    @Test public void actualEngineGeometryKeepsTheNumberWithoutRepeatedMatchingPlateReads()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}long id=f.current();
        for(int frame=2;frame<=6;frame++)try(PipelineResult result=f.run(frame)) {
            assertEquals(id,f.current());for(PlateObservation o:result.plateObservations)assertEquals(id,o.entityId);
        }
        assertEquals(1,f.vehicles.stats().entitiesCreated);assertEquals(1,f.vehicles.identity().ownerNumber(id));
    }
    @Test public void focusedEngineKeepsItsOwnerIndependentOfRawMzText()throws Exception {
        for(boolean same:new boolean[]{false,true}) {
            EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}long id=f.current();
            f.engine.setPersistentTargetEntityId(id);if(!same)f.raw="CD9876";
            for(int frame=2;frame<=12;frame++)try(PipelineResult result=f.run(frame)) {
                assertEquals(id,f.current());for(PlateObservation o:result.plateObservations)assertEquals(id,o.entityId);
            }
            assertEquals(1,f.vehicles.stats().entitiesCreated);
        }
    }
    @Test public void rawDisagreementWithContinuousLocalTrackingCorrectsEvidenceWithoutChangingTheVehicle()throws Exception {
        EngineFixture f=new EngineFixture();double before;
        try(PipelineResult initial=f.run(1)){before=initial.confidence;}long id=f.current();f.raw="CD9876";boolean lowered=false;
        for(int frame=2;frame<=10;frame++) {
            f.proof(id,frame);
            try(PipelineResult result=f.run(frame)) {
                for(PlateObservation o:result.plateObservations) {
                    assertEquals(id,o.entityId);
                    if(o.hasFreshMzRead()&&o.recognitionConfidence<before)lowered=true;
                }
            }
            assertTrue(f.vehicles.identity().presentation().get(id).visible);
        }
        assertTrue(lowered);assertEquals("CD9876",f.vehicles.repository().get(id).registration().text);
        assertEquals(1,f.vehicles.stats().entitiesCreated);
    }
    @Test public void lossWhileMzRunsCancelsSameSceneResultBeforeConsensusAndCallback()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}long id=f.current();
        try(PipelineResult ignored=f.run(2)){}f.proof(id,3);
        f.duringMz=()->f.vehicles.identity().preview(4*S+DynamicVehicleIdentity.COAST_NANOS+1L,Collections.emptySet(),true);
        assertNull(f.run(3));assertEquals(2,f.mzRuns);
        assertEquals("AB12345",f.vehicles.repository().get(id).registration().text);assertFalse(f.vehicles.identity().presentation().get(id).visible);
    }
    @Test public void briefTrackingGapWhileMzRunsKeepsTheSameOwner()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}long id=f.current();
        try(PipelineResult ignored=f.run(2)){}f.proof(id,3);
        f.duringMz=()->f.vehicles.identity().preview(4*S+100_000_000L,Collections.emptySet(),true);
        try(PipelineResult result=f.run(3)){assertFalse(result.plateObservations.isEmpty());assertEquals(id,result.plateObservations.get(0).entityId);}
        assertEquals(2,f.mzRuns);assertTrue(f.vehicles.identity().presentation().get(id).visible);
    }
    PlateObservation observation(long id,DynamicVehicleIdentity.Stamp owner,Bitmap bitmap) {
        return new PlateObservation(1,PlateVehicleAssociation.directValidated(id,owner.vehicleTrackId,1,"fixture"),MtWorkKind.VEHICLE_ROI,MtReason.UNKNOWN,
                1,bitmap,"AB12345",.9,.9,true,2,Collections.emptyList(),1,1,1,null,null,null,true,true,"AB12345",true,1,"single",Collections.singletonList(7),"","AB12345")
                .stampIdentityOwner(owner);
    }
    @Test public void runtimeOwnerSurvivesDtoCopiesButIsNotAnExportFieldAndFilteredCopiesCloseOnce()throws Exception {
        EngineFixture f=new EngineFixture();try(PipelineResult ignored=f.run(1)){}long a=f.current();
        DynamicVehicleIdentity.Stamp stamp=f.vehicles.identity().stamp(a,f.vehicles.repository().get(a).vehicleTrackId());
        Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);PlateObservation o=observation(a,stamp,bitmap);
        assertSame(stamp,o.withContinuityStamp(ContinuityStamp.initial(42)).identityOwner());assertSame(stamp,o.withoutFinalization().identityOwner());
        PlateObservation gallery=o.copyForGallery();assertSame(stamp,gallery.identityOwner());gallery.recyclePreview();
        assertTrue(java.lang.reflect.Modifier.isTransient(PlateObservation.class.getDeclaredField("identityOwner").getModifiers()));
        PipelineResult original=new PipelineResult("recognized","AB12345",Collections.singletonList(new PlateRecognition(o.text,.9)),Collections.emptyList(),640,400,Collections.singletonList(o));
        f.vehicles.identity().preview(3*S,Collections.singleton(a),true);f.vehicles.identity().preview(4*S,Collections.emptySet(),true);
        PipelineResult filtered=original.filterIdentityObservations(f.vehicles::acceptsObservation);
        assertEquals("",filtered.recognizedText);assertEquals("identity_verification",filtered.status);assertFalse(filtered.message.contains("AB12345"));
        filtered.close();original.close();assertTrue(bitmap.isRecycled());
    }
    @Test public void epochChangesResetTemplateAndUnassignedTrackCannotCarryThePreviousPlate() {
        PlateTrackCoordinator c=new PlateTrackCoordinator();
        PlateTrackCoordinator.Observation sample=new PlateTrackCoordinator.Observation(0,new MotionBoxTracker.Box(.1f,.2f,.4f,.3f),.9f,true);
        PlateTrackCoordinator.Decision d=c.update(Collections.singletonList(sample),1,S).get(0);c.bindEntityState(d.trackId,1,1);
        List<Detection> chars=new ArrayList<>();for(int i=0;i<5;i++)chars.add(new Detection(i,.99f,i*12,0,i*12+8,20,Collections.emptyList()));
        c.recordRecognition(d.trackId,.9f,1,chars,Arrays.asList("A","B","1","2","3"));
        c.recordRecognition(d.trackId,.9f,2,chars,Arrays.asList("A","B","1","2","3"));
        assertEquals(5,c.identityDecision(d,sample,2).expectedCharacterCount);
        c.bindEntityState(d.trackId,2,2);PlateTrackCoordinator.Decision next=c.identityDecision(d,sample,2);
        assertEquals(0,next.expectedCharacterCount);assertTrue(next.recognize);
        c.bindUnassignedIdentity(d.trackId,3);assertEquals(0,c.identityDecision(d,sample,3).expectedCharacterCount);
    }
    @Test public void canonicalizationPreservesSessionTimeUserFocusRevisionAndStaticGuard() {
        ModeController mode=new ModeController();ScanAcquisitionController c=new ScanAcquisitionController(mode,ScanAcquisitionProfile.DEFAULT);
        c.startRun(1,S);c.pickVehicle(9,S+1);TargetSession session=mode.activeSession();TargetFocusSnapshot focus=c.targetFocus();
        long updated=session.lastUpdateNanos(),started=session.startedAtNanos();c.rebindVerifiedVehicle(9,2);
        assertSame(session,mode.activeSession());assertEquals(2,session.entityId());assertEquals(focus.revision,c.targetFocus().revision);
        assertEquals(started,session.startedAtNanos());assertEquals(updated,session.lastUpdateNanos());assertEquals(focus.sessionId,c.targetFocus().sessionId);
        c.setStaticBaseline(true);c.rebindVerifiedVehicle(2,3);assertEquals(2,session.entityId());
    }
    @Test public void geometryFinishesBeforeMzAndQueuedWorkDoesNotRetainTheSourceBitmap()throws Exception {
        EngineFixture f=new EngineFixture();com.example.alpr_v1.camera.BitmapMemoryBudget budget=new com.example.alpr_v1.camera.BitmapMemoryBudget(32*1024*1024);
        f.engine.setDynamicBitmapBudget(budget);
        try(PipelineResult geometry=f.runGeometry(1)){assertEquals(0,f.mzRuns);assertTrue(f.engine.hasPendingDynamicMz());assertTrue(budget.usedBytes()>0);}
        f.image.recycle();InferenceTrace trace=new InferenceTrace(2,f.engine.pendingDynamicMzStamp());trace.start("total");
        try(PipelineResult mz=f.engine.runPendingDynamicMz(trace,()->false)) {
            assertEquals("AB12345",mz.recognizedText);assertTrue(mz.overlayItems.isEmpty());
            assertFalse(f.engine.hasPendingDynamicMz());assertTrue(budget.usedBytes()>0);
        }
        assertEquals(0,budget.usedBytes());assertEquals(1,f.mzRuns);
    }
    @Test public void newerCropReplacesTheSinglePendingJobAndReleasesItsLease()throws Exception {
        EngineFixture f=new EngineFixture();com.example.alpr_v1.camera.BitmapMemoryBudget budget=new com.example.alpr_v1.camera.BitmapMemoryBudget(32*1024*1024);
        f.engine.setDynamicBitmapBudget(budget);
        try(PipelineResult ignored=f.runGeometry(1)){}long used=budget.usedBytes();assertTrue(used>0);
        try(PipelineResult ignored=f.runGeometry(2)){}assertEquals(used,budget.usedBytes());assertEquals(used,budget.peakBytes());
        assertEquals(2,f.engine.pendingDynamicMzStamp().sourceSequence);f.engine.discardPendingDynamicMz();assertEquals(0,budget.usedBytes());
    }
    @Test public void invalidatedOwnerDropsPendingCropWithoutCallingMz()throws Exception {
        EngineFixture f=new EngineFixture();com.example.alpr_v1.camera.BitmapMemoryBudget budget=new com.example.alpr_v1.camera.BitmapMemoryBudget(32*1024*1024);
        f.engine.setDynamicBitmapBudget(budget);try(PipelineResult ignored=f.runGeometry(1)){}long id=f.current();
        f.vehicles.identity().preview(3*S,Collections.singleton(id),true);f.vehicles.identity().preview(4*S,Collections.emptySet(),true);
        InferenceTrace trace=new InferenceTrace(2,f.engine.pendingDynamicMzStamp());trace.start("total");
        assertNull(f.engine.runPendingDynamicMz(trace,()->false));assertEquals(0,f.mzRuns);assertEquals(0,budget.usedBytes());
    }
    @Test public void insufficientCropBudgetSkipsMzWithoutLeakingOrLosingGeometry()throws Exception {
        EngineFixture f=new EngineFixture();com.example.alpr_v1.camera.BitmapMemoryBudget budget=new com.example.alpr_v1.camera.BitmapMemoryBudget(1);
        f.engine.setDynamicBitmapBudget(budget);try(PipelineResult geometry=f.runGeometry(1)){assertFalse(geometry.overlayItems.isEmpty());}
        assertFalse(f.engine.hasPendingDynamicMz());assertEquals(0,f.mzRuns);assertEquals(0,budget.usedBytes());
    }
    @Test public void cancelledDispatchClosesItsCropBeforeStartingMz()throws Exception {
        EngineFixture f=new EngineFixture();com.example.alpr_v1.camera.BitmapMemoryBudget budget=new com.example.alpr_v1.camera.BitmapMemoryBudget(32*1024*1024);
        f.engine.setDynamicBitmapBudget(budget);try(PipelineResult ignored=f.runGeometry(1)){}
        InferenceTrace trace=new InferenceTrace(2,f.engine.pendingDynamicMzStamp());trace.start("total");
        assertThrows(MobileAlprEngine.ProcessingCancelledException.class,()->f.engine.runPendingDynamicMz(trace,()->true));
        assertFalse(f.engine.hasPendingDynamicMz());assertEquals(0,f.mzRuns);assertEquals(0,budget.usedBytes());
    }
    @Test public void emptyMzPublishesItsAttemptCropAndTimingWithoutInventingCharacterEvidence()throws Exception {
        EngineFixture f=new EngineFixture();f.raw="";
        try(PipelineResult result=f.run(1)) {
            assertNotNull(result);assertTrue(result.recognitions.isEmpty());assertEquals("",result.recognizedText);
            assertEquals(1,result.plateObservations.size());PlateObservation attempt=result.plateObservations.get(0);
            assertTrue(attempt.freshMzAttempted);assertFalse(attempt.freshMzSuccessful);assertFalse(attempt.hasFreshMzRead());
            assertEquals(0,attempt.observations);assertEquals(0,attempt.recognitionConfidence,0);
            assertNotNull(attempt.previewBitmap);assertNotNull(attempt.timing);assertEquals(1,attempt.mzAttemptIndex);
        }
        assertEquals(1,f.mzRuns);assertEquals(1,f.vehicles.stats().entitiesCreated);
        assertEquals(1,new org.json.JSONObject(f.lastTrace).getJSONObject("counters").getInt("mz_runs"));
    }
}
