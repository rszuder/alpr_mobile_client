package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.CameraSetupReadiness.Reason;
import com.example.alpr_v1.experiment.ResearchOperationState;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi",
    shadows={F5a24CameraStartTest.ModelIo.class,F5a26SingleClickStartTest.StartIo.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class MainHeaderOperationUiTest {
    F5a26SingleClickStartTest h;
    @Before public void setup()throws Exception{h=new F5a26SingleClickStartTest();h.setup();h.normal();h.set("cameraPreviewActive",false);h.call("renderAnalysisControls");presenter().clearMissingModelsStatus();presenter().showState(LivePresentationController.State.STOPPED,"");}
    @After public void close()throws Exception{if(h!=null)h.close();}
    View view(int id){return h.f.main.findViewById(id);}
    String text(int id){return ((TextView)view(id)).getText().toString();}
    LivePresentationController presenter()throws Exception{return (LivePresentationController)h.get("livePresentation");}
    void render()throws Exception{h.call("renderAnalysisControls");MainViewfinderInsetsTest.layout(view(R.id.main));}
    @Test public void progressStaysInsideHeaderAndSuccessIsAvailableInMenu()throws Exception{
        render();View root=view(R.id.main);Rect camera=MainViewfinderInsetsTest.bounds(root,view(R.id.camera_preview));
        ResearchOperationState ops=h.f.sessions.operations;String ticket=ops.begin("SYNTHETIC-HEADER","COPYING_DOCUMENT","");
        try{
            ops.update(ticket,"COPYING_DOCUMENT",37,100,null);render();
            assertEquals("Eksportowanie dokumentu",text(R.id.live_status));
            LinearProgressIndicator progress=(LinearProgressIndicator)view(R.id.session_operation_progress);assertFalse(progress.isIndeterminate());assertEquals(37,progress.getProgress());
            Rect panel=MainViewfinderInsetsTest.bounds(root,view(R.id.session_operation_panel));assertTrue(MainViewfinderInsetsTest.bounds(root,view(R.id.normal_camera_hud)).contains(panel));assertFalse(Rect.intersects(camera,panel));
            assertEquals(camera,MainViewfinderInsetsTest.bounds(root,view(R.id.camera_preview)));
            MainViewfinderInsetsTest.image(root,"main-header-copying.png");
            ops.end(ticket,"SUCCEEDED","content://synthetic/saved",null);render();
            assertEquals(View.GONE,view(R.id.session_operation_panel).getVisibility());assertFalse(text(R.id.live_status).contains("gotowe"));
            assertEquals(camera,MainViewfinderInsetsTest.bounds(root,view(R.id.camera_preview)));
            MaterialToolbar bar=(MaterialToolbar)view(R.id.top_app_bar);assertTrue(bar.getMenu().findItem(R.id.menu_session_operation).isVisible());
            assertEquals(true,TvBboxStartUiTest.call(h.f.main,"handleMenuItem",new Class<?>[]{android.view.MenuItem.class},bar.getMenu().findItem(R.id.menu_session_operation)));
            android.app.Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains(ticket));dialog.dismiss();
            MainViewfinderInsetsTest.image(root,"main-after-save.png");
        }finally{if(ops.hasWriteWork())ops.end(ticket,"CANCELLED",null,null);}
        assertFalse(h.f.sessions.experiment.isRunning());assertEquals(0,F5a26SingleClickStartTest.StartIo.starts.get());
    }
    @Test public void liveStateAndTimerRemainPrimaryWhileProgressAndWarningUseSecondary()throws Exception{
        LivePresentationController p=presenter();ResearchOperationState ops=h.f.sessions.operations;
        p.showState(LivePresentationController.State.SEARCHING,"");String live=text(R.id.live_status);
        String ticket=ops.begin("SYNTHETIC-WARNING","BUILDING_ARCHIVE","");
        p.setOperationStatus(ResearchOperationPresentation.from(ops.presentation()),false,false);assertEquals(live,text(R.id.live_status));assertTrue(text(R.id.live_status_secondary).contains("archiwum"));
        p.showState(LivePresentationController.State.SEARCHING,"");assertTrue(text(R.id.live_status_secondary).contains("archiwum"));
        p.setTimerCompleted(true);String finished=text(R.id.live_status);assertNotEquals(live,finished);
        p.setOperationStatus(ResearchOperationPresentation.from(ops.presentation()),true,false);assertEquals(finished,text(R.id.live_status));
        p.setTimerCompleted(false);p.showUserStatus(LivePresentationController.State.SEARCHING,live,"");
        ops.end(ticket,"FAILED",null,new IllegalStateException("SYNTHETIC_TEST_ONLY"));p.setOperationStatus(ResearchOperationPresentation.from(ops.presentation()),false,true);
        assertEquals(live,text(R.id.live_status));assertFalse(text(R.id.live_status_secondary).isEmpty());
        p.setOperationStatus(null,false,false);assertEquals(live,text(R.id.live_status));assertEquals(View.GONE,view(R.id.live_status_secondary).getVisibility());
    }
    @Test public void mainCameraBlockerShowsOffAndStartingCopyWithoutRawReason()throws Exception{
        Class<?> type=Class.forName("com.example.alpr_v1.MainActivity$StartBlocker");Object blocker=Enum.valueOf((Class)type,"CAMERA");
        h.set("cameraPreparationNotice",Reason.CAMERA_NOT_BOUND.description());
        org.robolectric.util.ReflectionHelpers.setField(h.hardware.camera,"camera",null);
        for(boolean preview:new boolean[]{false,true}){
            h.set("cameraPreviewActive",preview);TvBboxStartUiTest.call(h.f.main,"renderExperimentStartStatus",new Class<?>[]{type},blocker);
            assertFalse(text(R.id.live_status).contains("CAMERA_NOT_BOUND"));assertFalse(text(R.id.live_status_secondary).contains("CAMERA_NOT_BOUND"));
            assertTrue(text(R.id.live_status),text(R.id.live_status).contains(preview?"Uruchamianie":"wyłączona"));
        }
        assertFalse(h.f.intent().pending());assertEquals(0,F5a26SingleClickStartTest.StartIo.starts.get());
    }
}
