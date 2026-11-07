package com.example.alpr_v1.experiment;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import com.example.alpr_v1.*;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.ui.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class GallerySessionScopeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    ResearchSessionViewModel vm;
    final List<ResearchSessionStore> stores=new ArrayList<>();
    final List<CapturedPlateItem> ram=new ArrayList<>();
    final Map<MainActivity,View> contentViews=new IdentityHashMap<>();
    Application app;
    String oldApk;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();oldApk=app.getApplicationInfo().sourceDir;
        File synthetic=temp.newFile("synthetic-apk-identity.bin");Files.write(synthetic.toPath(),new byte[]{1,2,3});
        app.getApplicationInfo().sourceDir=synthetic.getPath();
        app.getSharedPreferences("f16-gallery-test",0).edit().clear().commit();
        vm=new ResearchSessionViewModel(app);idle();
    }
    void idle()throws Exception{
        Field f=ResearchSessionViewModel.class.getDeclaredField("finalizer");f.setAccessible(true);
        ((ExecutorService)f.get(vm)).submit(()->{}).get(10,TimeUnit.SECONDS);
    }
    @After public void close()throws Exception{
        vm.onCleared();
        for(ResearchSessionStore store:stores){Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);((ThreadPoolExecutor)f.get(store)).shutdownNow();}
        for(CapturedPlateItem item:ram)item.recycle();
        app.getApplicationInfo().sourceDir=oldApk;
    }
    ExperimentSession.Prepared prepared(){return vm.experiment.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),
            new ExperimentIdentity("SYNTHETIC-F16-GALLERY","SYNTHETIC",1,"synthetic=true"),null);}
    ResearchSessionStore completedA()throws Exception{
        ExperimentSession.Prepared a=prepared();ResearchSessionStore store=vm.prepare(a);stores.add(store);
        long elapsed=Math.max(store.preparedElapsedNanos,SystemClock.elapsedRealtimeNanos());
        store.startAt(10000,elapsed);assertTrue(vm.experiment.startPrepared(a,10000,elapsed));
        Bitmap image=Bitmap.createBitmap(16,8,Bitmap.Config.ARGB_8888);image.eraseColor(0xffc0c0c0);
        for(int i=0;i<7;i++){
            AcquisitionAttemptRecord r=store.beginAttempt(new ContinuityStamp(1,1,0,i+1,100+i,SourceTimestampDomain.CAMERAX_SENSOR),"synthetic",1,1,1);
            r.copyPlateCrop(image);store.submit(r);
            ram.add(new CapturedPlateItem("ram-"+i,a.sessionId,1,image.copy(Bitmap.Config.ARGB_8888,false),"ABC",.8,.7,false,
                    Collections.emptyList(),10000+i,elapsed+i,.2f,null));
        }
        image.recycle();store.closeAdmission("manual",11000,elapsed+1000);vm.experiment.finishAt(ExperimentSession.CompletionReason.MANUAL,11000,elapsed+1000);
        store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true}","","","","",""),null);
        assertEquals(7,store.writtenCropCount());return store;
    }
    ResearchSessionStore prepareB()throws Exception{ResearchSessionStore b=vm.prepare(prepared());stores.add(b);return b;}
    MainActivity activity(String selected)throws Exception{
        MainActivity a=Robolectric.buildActivity(MainActivity.class).get();a.setTheme(R.style.Theme_ALPR_v1);
        set(a,"researchSessions",vm);set(a,"experimentSession",vm.experiment);set(a,"capturedCrops",ram);
        set(a,"uiPreferences",app.getSharedPreferences("f16-gallery-test",0));set(a,"resolvedCropLimit",49);
        set(a,"experimentModeEnabled",true);set(a,"galleryResearchMode",true);
        if(selected!=null)call(a,"selectVerificationSession",new Class[]{String.class},selected);
        View content=LayoutInflater.from(a).inflate(R.layout.bottom_sheet_gallery,null,false);
        contentViews.put(a,content);
        call(a,"bindGallerySheet",new Class[]{View.class},content);
        call(a,"renderResearchVerification",new Class[0]);return a;
    }
    void render(MainActivity ui,String name)throws Exception{
        String fixtures=System.getenv("F5A16_FIXTURES");if(fixtures==null)return;
        View content=contentViews.get(ui);content.findViewById(R.id.gallery_recent_container).setVisibility(View.GONE);
        content.findViewById(R.id.gallery_research_container).setVisibility(View.VISIBLE);
        content.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));
        content.layout(0,0,360,720);Bitmap image=Bitmap.createBitmap(360,720,Bitmap.Config.ARGB_8888);
        content.draw(new android.graphics.Canvas(image));File dir=new File(new File(fixtures).getParentFile(),"renders");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();
    }
    static void set(Object object,String field,Object value)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
    static Object get(Object object,String field)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
    static Object call(Object object,String name,Class[] args,Object... values)throws Exception{Method m=object.getClass().getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(object,values);}
    @Test public void U01_U02_oldSessionSevenSurvivesActualPrepareOfB()throws Exception{
        ResearchSessionStore a=completedA(),b=prepareB();
        assertNotEquals(a.sessionId(),b.sessionId());assertEquals(a.sessionId(),vm.experiment.sessionId());
        assertEquals(7L,(long)vm.gallerySession(a.sessionId()).savedCount);assertEquals(0L,(long)vm.gallerySession(b.sessionId()).savedCount);
        assertEquals("COMPLETED",vm.gallerySession(a.sessionId()).state);assertEquals("PREPARED",vm.gallerySession(b.sessionId()).state);
    }
    @Test public void U03_actualGalleryHeaderAndRamUseSelectedCompletedSession()throws Exception{
        ResearchSessionStore a=completedA();prepareB();MainActivity ui=activity(a.sessionId());
        assertEquals("7",call(ui,"currentResearchCropCount",new Class[0]));
        String header=((TextView)get(ui,"galleryCollectionStats")).getText().toString();
        assertTrue(header.contains(a.sessionId()));assertTrue(header.contains("Zapisane obrazy tej sesji: 7"));assertTrue(header.contains("RAM: 7"));
        assertEquals(7,((List<?>)get(ui,"filteredVerificationItems")).size());
        assertTrue(((TextView)get(ui,"verificationMetrics")).getText().toString().contains(a.sessionId()));
        for(CapturedPlateItem item:ram)assertEquals(CapturedPlateItem.VerificationStatus.NOT_REVIEWED,item.verificationStatus);
        render(ui,"gallery-completed-A");
    }
    @Test public void U04_switchBetweenAAndBDoesNotRelabelOrWriteItems()throws Exception{
        ResearchSessionStore a=completedA(),b=prepareB();MainActivity ui=activity(a.sessionId());
        call(ui,"selectVerificationSession",new Class[]{String.class},b.sessionId());call(ui,"renderResearchVerification",new Class[0]);
        assertEquals("0",call(ui,"currentResearchCropCount",new Class[0]));assertTrue(((List<?>)get(ui,"filteredVerificationItems")).isEmpty());
        render(ui,"gallery-prepared-B");
        call(ui,"selectVerificationSession",new Class[]{String.class},a.sessionId());call(ui,"renderResearchVerification",new Class[0]);
        assertEquals("7",call(ui,"currentResearchCropCount",new Class[0]));assertEquals(7,ram.size());assertEquals(7,a.writtenCropCount());
    }
    @Test public void U05_clearRamDoesNotClearPersistedCountOrRestoreBitmaps()throws Exception{
        ResearchSessionStore a=completedA();prepareB();MainActivity ui=activity(a.sessionId());
        for(CapturedPlateItem i:ram)i.recycle();ram.clear();call(ui,"renderResearchVerification",new Class[0]);
        assertEquals("7",call(ui,"currentResearchCropCount",new Class[0]));assertTrue(((List<?>)get(ui,"filteredVerificationItems")).isEmpty());
        assertTrue(((TextView)get(ui,"galleryCollectionStats")).getText().toString().contains("RAM: 0"));
    }
    @Test public void U06_actualCollectionAndStaticToggleGatesRemainIntact()throws Exception{
        MainActivity ui=activity("");MaterialButton collection=new MaterialButton(ui);set(ui,"collectionToggle",collection);
        set(ui,"cameraStarted",false);call(ui,"renderCollectionControl",new Class[0]);assertFalse(collection.isEnabled());
        set(ui,"cameraStarted",true);call(ui,"renderCollectionControl",new Class[0]);assertFalse(collection.isEnabled());
        set(ui,"experimentModeEnabled",false);call(ui,"renderCollectionControl",new Class[0]);assertTrue(collection.isEnabled());
        ExperimentSession.Prepared p=prepared();vm.experiment.startPrepared(p,1000,1000);call(ui,"renderCollectionControl",new Class[0]);assertFalse(collection.isEnabled());
        vm.experiment.finishAt(ExperimentSession.CompletionReason.MANUAL,2000,2000);call(ui,"renderCollectionControl",new Class[0]);assertTrue(collection.isEnabled());
        MaterialButton toggle=new MaterialButton(ui);set(ui,"liveSceneModeToggle",toggle);set(ui,"overlayView",new DetectionOverlayView(ui,null));
        for(SceneHandlingMode mode:SceneHandlingMode.values()){
            app.getSharedPreferences("f16-gallery-test",0).edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,mode.wireName()).commit();
            set(ui,"experimentModeEnabled",false);call(ui,"renderSceneModeHudToggle",new Class[0]);assertTrue(toggle.isEnabled());
            set(ui,"experimentModeEnabled",true);call(ui,"renderSceneModeHudToggle",new Class[0]);assertFalse(toggle.isEnabled());
        }
    }
    @Test public void U07_restartReadsVerifiedArchiveCountWithoutRecreatingRam()throws Exception{
        ResearchSessionStore a=completedA();String id=a.sessionId();GallerySessionScope original=GallerySessionScope.readStored(a.directory());
        assertEquals(7L,(long)original.savedCount);assertEquals("VERIFIED_SESSION_ARCHIVE",original.countSource);
        activity(id);prepareB();MainActivity recreated=activity(null);
        assertEquals(id,call(recreated,"verificationSessionId",new Class[0]));
        vm.onCleared();vm=new ResearchSessionViewModel(app);idle();vm.refreshGallerySessions();idle();
        assertEquals(7L,(long)vm.gallerySession(id).savedCount);
        for(CapturedPlateItem item:ram)item.recycle();ram.clear();MainActivity ui=activity(null);
        assertEquals("7",call(ui,"currentResearchCropCount",new Class[0]));assertTrue(ram.isEmpty());
        assertEquals(7L,(long)GallerySessionScope.readStored(a.directory()).savedCount);
    }
    @Test public void unavailableOrDamagedCountIsNaNotZero()throws Exception{
        GallerySessionScope unknown=GallerySessionScope.unavailable("absent","UNKNOWN","Brak archiwum");
        assertNull(unknown.savedCount);assertEquals("N/A",unknown.countText());assertTrue(unknown.header(7,0,49).contains("Brak archiwum"));
        ResearchSessionStore a=completedA();File archive=new File(a.directory(),"final/"+a.sessionId()+".alprsession");
        File sidecar=new File(a.directory(),"session.json");byte[] metadata=Files.readAllBytes(sidecar.toPath());Files.delete(sidecar.toPath());
        assertEquals("7",GallerySessionScope.readStored(a.directory()).countText());Files.write(sidecar.toPath(),metadata);
        Files.write(archive.toPath(),new byte[]{1,2,3});assertEquals("N/A",GallerySessionScope.readStored(a.directory()).countText());
    }
    @Test public void productionDiagnosticSettingCannotBeArmed()throws Exception{
        assertFalse(MzInputEvidence.productionEnabled());
        View settings=LayoutInflater.from(Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity.class).get())
                .cloneInContext(new android.view.ContextThemeWrapper(app,R.style.Theme_ALPR_v1))
                .inflate(R.layout.activity_settings,null,false);
        TextView status=settings.findViewById(R.id.settings_mz_input_evidence_status);
        assertNotNull(status);assertTrue(status.getText().toString().contains(MzInputEvidence.DIAGNOSTIC_BLOCK_REASON));
        assertTrue(status.getText().toString().contains("OFF"));assertFalse(status.isClickable());
    }
}
