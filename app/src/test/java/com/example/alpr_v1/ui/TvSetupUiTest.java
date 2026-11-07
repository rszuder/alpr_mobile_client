package com.example.alpr_v1.ui;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.PhoneOrientationEstimator;
import com.example.alpr_v1.camera.GeometryProviderFixture;
import com.example.alpr_v1.camera.CameraGeometryProvider;
import com.example.alpr_v1.camera.CameraSetupControl;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.camera.CameraSelection;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.vision.Point2;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowDialog;
import java.time.Duration;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Real application XML/controller/touch hierarchy; only camera acquisition is synthetic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvSetupUiTest {
    public static class HarnessActivity extends AppCompatActivity {
        TvSetupController setup;
        PreviewView preview;
        TvCalibrationOverlayView overlay;
        boolean editable=true;int startRequests,resultRequests,settingsRequests,settingsBlocked,collectionRequests,previewRequests,diagnosticsRequests;
        @Override public void onCreate(Bundle saved) {
            setTheme(R.style.Theme_ALPR_v1);
            super.onCreate(saved);
            setContentView(R.layout.activity_main);
            CameraWindowUi.install(getWindow(),findViewById(R.id.main));
            findViewById(R.id.recognition_hint).setVisibility(View.GONE);
            findViewById(R.id.export_report_button).setVisibility(View.GONE);
            preview=findViewById(R.id.camera_preview);
            overlay=findViewById(R.id.tv_setup_overlay);
            setup=new TvSetupController(this,preview,findViewById(R.id.detection_overlay),()->editable,()->{},(name,json)->{});
            findViewById(R.id.analysis_start_button).setOnClickListener(v->startRequests++);
            findViewById(R.id.gallery_open_button).setOnClickListener(v->resultRequests++);
            findViewById(R.id.collection_toggle).setOnClickListener(v->collectionRequests++);
            findViewById(R.id.camera_preview_button).setOnClickListener(v->previewRequests++);
            ((MaterialToolbar)findViewById(R.id.top_app_bar)).setOnMenuItemClickListener(item->{if(item.getItemId()==R.id.menu_settings){if(editable)settingsRequests++;else settingsBlocked++;return true;}if(item.getItemId()==R.id.menu_diagnostics){diagnosticsRequests++;return true;}return TvSetupNavigation.select(item.getItemId(),setup);});
            setup.setControlsRefresh(this::renderMenu);renderMenu();
        }
        void renderMenu(){TvSetupMenuState.render(((MaterialToolbar)findViewById(R.id.top_app_bar)).getMenu(),true,editable,setup.editing()?setup.mode():null,setup.restoring());}
        @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)CameraWindowUi.showSystemBars(getWindow());}
    }
    private ActivityController<HarnessActivity> activityController;
    private HarnessActivity activity;
    private int width=360,height=800;
    private double zoom=1;
    private boolean portraitSource;
    private int sourceWidthOverride,sourceHeightOverride;
    private CameraPreviewGeometry geometry;
    private GeometryProviderFixture publisher;
    private FakeCameraSetup fakeCamera;
    private final class FakeCameraSetup implements CameraSetupControl {
        final CameraSelection normal=new CameraSelection("synthetic-camera",null),wide=new CameraSelection("synthetic-wide",null);
        CameraSelection requested=normal,hardware=normal;float requestedZoom=1;long revision;int binding=1;
        boolean loading;String failure="";java.util.function.Consumer<State> listener=state->{};
        public State state(){return new State(java.util.Arrays.asList(new Choice(normal,"Tylny testowy","Kamera syntetyczna 1"),new Choice(wide,"Szerszy testowy","Kamera syntetyczna 2")),requested,requestedZoom,.5f,4,
                loading||!failure.isEmpty()?null:new CameraSetupSnapshot(hardware,(float)zoom),loading,failure,revision);}
        public void setListener(java.util.function.Consumer<State> value){listener=value;listener.accept(state());}
        public void apply(CameraSelection selection,float ratio){requested=selection;requestedZoom=ratio;loading=true;revision++;listener.accept(state());}
        void complete(){if(!requested.equals(hardware))binding++;hardware=requested;zoom=requestedZoom;loading=false;failure="";listener.accept(state());}
    }
    private void installCameraControls(){fakeCamera=new FakeCameraSetup();activity.setup.setCameraControls(fakeCamera);}

    @After public void destroy(){if(activityController!=null)activityController.pause().stop().destroy();}
    private void create(){
        create(1f,false);
    }
    private void create(float fontScale,boolean night){
        fakeCamera=null;zoom=1;
        RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");
        Configuration configuration=new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        configuration.fontScale=fontScale;
        configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(night?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(configuration,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        RuntimeEnvironment.getApplication().getSharedPreferences("alpr_ui",0).edit().clear().commit();
        activityController=Robolectric.buildActivity(HarnessActivity.class).setup().visible();activity=activityController.get();settle();
    }
    private void layout(){
        View decor=activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        decor.layout(0,0,width,height);
    }
    private CameraPreviewGeometry cameraGeometry(){
        int w=activity.preview.getWidth(),h=activity.preview.getHeight();
        int sourceW=sourceWidthOverride>0?sourceWidthOverride:portraitSource?960:1920,sourceH=sourceHeightOverride>0?sourceHeightOverride:portraitSource?1280:1080;
        int bw=portraitSource?sourceH:sourceW,bh=portraitSource?sourceW:sourceH;
        double scale=Math.min(w/(double)sourceW,h/(double)sourceH);
        return new CameraPreviewGeometry(sourceW,sourceH,bw,bh,new double[]{0,0,bw,bh},portraitSource?90:0,fakeCamera==null?"synthetic-camera":fakeCamera.hardware.logicalCameraId,"BACK",zoom,fakeCamera==null?1:fakeCamera.binding,1,w,h,
                new double[]{scale,0,(w-sourceW*scale)/2,0,scale,(h-sourceH*scale)/2,0,0,1},new double[]{0,Math.max(0,activity.findViewById(R.id.top_app_bar).getBottom()-activity.preview.getTop()),w,h},0);
    }
    private void settle(){for(int i=0;i<5;i++){
        layout();geometry=activity.preview.getWidth()>0&&activity.preview.getHeight()>0?cameraGeometry():null;
        activity.setup.onGeometry(geometry,null,geometry==null?"PREVIEW_NOT_MEASURED":"CAMERA_METADATA_UNAVAILABLE");
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(20));
    }layout();assertTrue("Preview did not recover after setup layout",activity.preview.getWidth()>0&&activity.preview.getHeight()>0);
        geometry=cameraGeometry();activity.setup.onGeometry(geometry,null,"CAMERA_METADATA_UNAVAILABLE");
        activity.findViewById(R.id.main).getViewTreeObserver().dispatchOnPreDraw();}
    private void open(){activity.setup.open(false);settle();
        if(activity.setup.phase()==TvSetupController.Phase.CAMERA_SETUP){assertFalse(activity.overlay.editing());
            assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());activity.findViewById(R.id.tv_setup_next).performClick();settle();}
        assertEquals("Editor @ "+width+"x"+height+" font="+activity.getResources().getConfiguration().fontScale+" error="+((TextView)activity.findViewById(R.id.tv_setup_error)).getText(),TvSetupController.Phase.EDITING,activity.setup.phase());}
    private void event(int action,float x,float y){
        int[] origin=new int[2];activity.overlay.getLocationInWindow(origin);
        long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x+origin[0],y+origin[1],0);
        activity.dispatchTouchEvent(event);event.recycle();
    }
    private void tap(double nx,double ny){double[] p=geometry.normalizedToView(nx,ny);
        assertTrue("Synthetic tap outside usable pixels @ "+width+"x"+height+" y="+p[1]+" usable="+java.util.Arrays.toString(geometry.usableViewRect()),geometry.visible(nx,ny));
        assertEquals(geometry.previewHeight,activity.overlay.getHeight());
        event(MotionEvent.ACTION_DOWN,(float)p[0],(float)p[1]);event(MotionEvent.ACTION_UP,(float)p[0],(float)p[1]);settle();
        assertEquals("Tap escaped editor @ "+width+"x"+height+" ("+p[0]+","+p[1]+") overlay="+activity.overlay.getHeight()+" root="+activity.findViewById(R.id.main).getHeight(),TvSetupController.Phase.EDITING,activity.setup.phase());
    }
    private void fourCorners(){
        int[] choices={R.id.tv_corner_lg,R.id.tv_corner_pg,R.id.tv_corner_pd,R.id.tv_corner_ld};double[][] quad={{.12,.18},{.83,.24},{.76,.70},{.19,.65}};
        for(int i=0;i<4;i++)dragCornerTo(i,quad[i][0],quad[i][1]);
    }
    private void installPublisher(){publisher=new GeometryProviderFixture(activity.preview,activity);
        publisher.provider.setListener(activity.setup::onGeometryState);
        activity.setup.setGeometryRefresh(publisher.provider::refresh);publisher.bind();}
    private void settlePublisher(){for(int i=0;i<6;i++){
        layout();publisher.frame();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(20));
        activity.findViewById(R.id.main).getViewTreeObserver().dispatchOnPreDraw();
    }layout();geometry=publisher.provider.latestState().geometry;}
    private void publisherTap(double x,double y){double[] p=publisher.provider.latestState().geometry.normalizedToView(x,y);
        event(MotionEvent.ACTION_DOWN,(float)p[0],(float)p[1]);event(MotionEvent.ACTION_UP,(float)p[0],(float)p[1]);settlePublisher();}
    private void enterPublisherCorners(){if(activity.setup.choosingCamera()){
        assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());activity.findViewById(R.id.tv_setup_next).performClick();settlePublisher();}}
    private void publisherFourCorners(){enterPublisherCorners();
        int[] choices={R.id.tv_corner_lg,R.id.tv_corner_pg,R.id.tv_corner_pd,R.id.tv_corner_ld};double[][] quad={{.12,.18},{.83,.24},{.76,.70},{.19,.65}};
        for(int i=0;i<4;i++){
            activity.findViewById(choices[i]).performClick();CameraPreviewGeometry g=publisher.provider.latestState().geometry;
            Point2 point=activity.overlay.corners().get(i);double[] from=g.normalizedToView(point.x,point.y),to=g.normalizedToView(quad[i][0],quad[i][1]);
            event(MotionEvent.ACTION_DOWN,(float)from[0],(float)from[1]);event(MotionEvent.ACTION_MOVE,(float)to[0],(float)to[1]);event(MotionEvent.ACTION_UP,(float)to[0],(float)to[1]);settlePublisher();
        }
    }

    @Test public void productionPublisherEnablesFourTapsAndSavesWithoutDistanceMetadata() throws Exception {
        create();installPublisher();publisher.failDistanceMetadata=true;activity.setup.open(false);settlePublisher();
        assertEquals(CameraGeometryProvider.Availability.READY,publisher.provider.latestState().availability);
        assertNull(publisher.provider.latestState().distanceMetadata);
        enterPublisherCorners();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertTrue(activity.overlay.editing());
        publisherFourCorners();assertEquals(4,activity.overlay.placedCount());assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        activity.findViewById(R.id.tv_setup_confirm).performClick();settlePublisher();
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
    }
    @Test public void productionPublisherDelayedTransformHasBoundedWaitAndRetryWorks(){
        create();installPublisher();publisher.transformAvailable=false;activity.setup.open(false);settlePublisher();
        assertEquals(TvSetupController.Phase.WAITING,activity.setup.phase());
        assertTrue(((TextView)activity.findViewById(R.id.tv_setup_status)).getText().toString().contains("Dopasow"));
        for(int i=0;i<40;i++){publisher.frame();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100));}
        assertEquals(TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
        assertTrue(activity.findViewById(R.id.tv_setup_retry).isShown());
        publisher.transformAvailable=true;settlePublisher();activity.findViewById(R.id.tv_setup_retry).performClick();settlePublisher();
        enterPublisherCorners();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());publisherFourCorners();
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
    }
    @Test public void distanceFormIsAvailableWhenCameraMappingIsInvalid(){
        create();installPublisher();publisher.viewOffsetX=20;activity.setup.open(true);settlePublisher();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));settlePublisher();
        assertTrue(activity.findViewById(R.id.tv_input_diagonal).isShown());
        assertNotEquals("Camera failure took over the calculator",TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
        assertTrue(((TextView)activity.findViewById(R.id.tv_distance_result)).getText().toString().contains("Nie można"));
    }
    private void systemInsets(boolean visible,int navHeight,int cutout){
        int types=WindowInsetsCompat.Type.systemBars();
        Insets bars=Insets.of(0,24,0,navHeight);
        ViewCompat.dispatchApplyWindowInsets(activity.findViewById(R.id.main),new WindowInsetsCompat.Builder()
                .setInsetsIgnoringVisibility(types,bars).setInsets(types,visible?bars:Insets.NONE).setVisible(types,visible)
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(0,cutout,0,0)).build());
        layout();
    }
    private void keyboardInsets(boolean visible,int bottom){
        int bars=WindowInsetsCompat.Type.systemBars();
        WindowInsetsCompat insets=android.os.Build.VERSION.SDK_INT<30?new WindowInsetsCompat.Builder()
                .setStableInsets(Insets.of(0,24,0,48)).setSystemWindowInsets(Insets.of(0,24,0,visible?bottom:48)).build():new WindowInsetsCompat.Builder()
                .setInsetsIgnoringVisibility(bars,Insets.of(0,24,0,48)).setInsets(bars,Insets.of(0,24,0,48))
                .setVisible(bars,true).setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,bottom))
                .setVisible(WindowInsetsCompat.Type.ime(),visible).build();
        if(android.os.Build.VERSION.SDK_INT<30){
            // The OS keeps these values between layout passes. A one-off dispatch would be
            // replaced by Robolectric's real (closed-keyboard) ViewRoot on requestApplyInsets.
            Object viewRoot=org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.findViewById(R.id.main),"getViewRootImpl");
            Rect content=new Rect(0,24,0,visible?bottom:48),stable=new Rect(0,24,0,48);
            for(String field:new String[]{"mPendingContentInsets","mPendingVisibleInsets","mDispatchContentInsets"}){
                Rect value=org.robolectric.util.ReflectionHelpers.getField(viewRoot,field);value.set(content);}
            for(String field:new String[]{"mPendingStableInsets","mDispatchStableInsets"}){
                Rect value=org.robolectric.util.ReflectionHelpers.getField(viewRoot,field);value.set(stable);}
            Object attach=org.robolectric.util.ReflectionHelpers.getField(viewRoot,"mAttachInfo");
            for(String field:new String[]{"mContentInsets","mVisibleInsets"}){
                Rect value=org.robolectric.util.ReflectionHelpers.getField(attach,field);value.set(content);}
            ((Rect)org.robolectric.util.ReflectionHelpers.getField(attach,"mStableInsets")).set(stable);
            org.robolectric.util.ReflectionHelpers.setField(viewRoot,"mLastWindowInsets",insets.toWindowInsets());
        }
        ViewCompat.dispatchApplyWindowInsets(activity.findViewById(R.id.main),insets);
        settle();
    }
    private void focusedInputVisible(int id,int keyboardTop){
        EditText edit=activity.findViewById(id);View box=(View)edit.getParent().getParent();
        assertTrue(box instanceof com.google.android.material.textfield.TextInputLayout);
        Rect visible=new Rect();assertTrue(box.getGlobalVisibleRect(visible));
        assertEquals("Label and value must both be visible",box.getHeight(),visible.height());
        String info="sdk="+android.os.Build.VERSION.SDK_INT+" input="+visible+" keyboardTop="+keyboardTop
                +" ime="+org.robolectric.util.ReflectionHelpers.getField(activity.setup,"imeBottom")
                +" window="+org.robolectric.util.ReflectionHelpers.getField(activity.setup,"windowBottomWithoutIme")
                +" panel="+activity.findViewById(R.id.tv_setup_panel).getTop()+"-"+activity.findViewById(R.id.tv_setup_panel).getBottom()
                +" margin="+((android.view.ViewGroup.MarginLayoutParams)activity.findViewById(R.id.tv_setup_panel).getLayoutParams()).bottomMargin;
        assertTrue("Input covered by keyboard: "+info,visible.bottom<=keyboardTop);
        assertTrue("Numeric input lost focus: "+info,edit.hasFocus());
    }
    @Test @Config(sdk={29,36}) public void keyboardKeepsNumericFieldAndLabelVisibleAndRestoresPreview(){
        for(int[] size:new int[][]{{360,800},{800,360}})for(float font:new float[]{1,1.5f,2}){
            width=size[0];height=size[1];create(font,false);activity.setup.open(true);settle();keyboardInsets(false,0);
            Rect preview=previewBounds();EditText edit=activity.findViewById(R.id.tv_input_diagonal);edit.setText("55");edit.requestFocus();
            int bottom=height>width?380:180;keyboardInsets(true,bottom);focusedInputVisible(R.id.tv_input_diagonal,height-bottom);
            assertEquals("Typing must not reframe the bound camera",preview,previewBounds());
            edit.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);settle();focusedInputVisible(R.id.tv_input_margin,height-bottom);
            assertFalse(activity.findViewById(R.id.tv_setup_actions).isShown());
            keyboardInsets(false,0);assertEquals(preview,previewBounds());assertTrue(activity.findViewById(R.id.tv_setup_cancel).isShown());
            assertEquals("55",edit.getText().toString());
            activityController.pause().stop().destroy();activityController=null;
        }
    }
    @Test public void alreadyResizedWindowDoesNotSubtractTheKeyboardTwice(){
        create();activity.setup.open(true);settle();systemInsets(true,48,0);settle();
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).requestFocus();keyboardInsets(true,380);
        height=420;settle();keyboardInsets(true,380);focusedInputVisible(R.id.tv_input_diagonal,420);
        assertTrue(activity.findViewById(R.id.tv_setup_body_scroll).getHeight()>200);
        height=800;keyboardInsets(false,0);
    }
    @Test public void outlineVisibilityIsIndependentOfValidityAndSurvivesEditingAndRestart() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));assertTrue(activity.overlay.isShown());
        String before=activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1","");
        activity.setup.toggleOutline();settle();assertFalse(activity.overlay.isShown());assertFalse(activity.setup.outlineVisible());
        assertEquals(before,activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
        open();assertTrue(activity.overlay.isShown());activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertFalse(activity.overlay.isShown());activity.setup.toggleOutline();assertTrue(activity.overlay.isShown());
        zoom=2;settle();assertEquals("STALE",activity.setup.snapshot(1).getString("status"));assertFalse(activity.overlay.isShown());
        assertTrue(activity.setup.outlineVisible());assertTrue(((TextView)activity.findViewById(R.id.tv_frame_status)).getText().toString().contains("NIEAKTUALNY"));
        activity.setup.toggleOutline();activityController.recreate();activity=activityController.get();settle();assertFalse(activity.setup.outlineVisible());
    }
    @Test public void resettingSavedCornersKeepsCommittedCalibrationUntilConfirmation() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        String before=activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1","");
        open();List<Point2> saved=activity.overlay.corners();activity.findViewById(R.id.tv_setup_clear).performClick();settle();
        assertEquals(4,activity.overlay.placedCount());assertNotEquals(saved.get(0).x,activity.overlay.corners().get(0).x,1e-6);
        assertEquals(before,activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));assertEquals(before,activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
    }
    private Rect previewBounds(){Rect bounds=new Rect();activity.preview.getGlobalVisibleRect(bounds);return bounds;}
    private void overflowSelect(int id){
        Rect before=previewBounds();MaterialToolbar toolbar=activity.findViewById(R.id.top_app_bar);toolbar.showOverflowMenu();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(30));
        assertTrue(toolbar.isOverflowMenuShowing());
        assertEquals("Overflow alone resized camera content",before,previewBounds());
        android.widget.PopupWindow popup=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
        android.widget.ListView list=popupList(popup.getContentView());assertNotNull(list);
        int position=-1;for(int i=0;i<list.getAdapter().getCount();i++)if(((android.view.MenuItem)list.getAdapter().getItem(i)).getItemId()==id)position=i;
        assertTrue(position>=0);View row=list.getChildAt(position-list.getFirstVisiblePosition());assertNotNull(row);
        float x=row.getWidth()/2f,y=row.getTop()+row.getHeight()/2f;long down=SystemClock.uptimeMillis();
        MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);list.dispatchTouchEvent(press);press.recycle();
        MotionEvent release=MotionEvent.obtain(down,down+40,MotionEvent.ACTION_UP,x,y,0);list.dispatchTouchEvent(release);release.recycle();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(150));layout();assertFalse(toolbar.isOverflowMenuShowing());
    }
    private void productionReadinessMenuTick(){
        MaterialToolbar toolbar=activity.findViewById(R.id.top_app_bar);
        TvSetupMenuState.render(toolbar.getMenu(),true,true,activity.setup.editing()?activity.setup.mode():null,activity.setup.restoring());
    }
    private android.widget.ListView popupList(View view){
        if(view instanceof android.widget.ListView)return (android.widget.ListView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){android.widget.ListView result=popupList(group.getChildAt(i));if(result!=null)return result;}}
        return null;
    }
    private void distancePopupTouch(boolean readinessTicks){
        create();MaterialToolbar toolbar=activity.findViewById(R.id.top_app_bar);
        toolbar.showOverflowMenu();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(50));
        android.widget.PopupWindow popup=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
        android.widget.ListView list=popupList(popup.getContentView());assertNotNull(list);
        android.database.DataSetObserver observer=new android.database.DataSetObserver(){@Override public void onChanged(){popupChanges++;}};
        list.getAdapter().registerDataSetObserver(observer);popupChanges=0;
        int position=-1;for(int i=0;i<list.getAdapter().getCount();i++){
            Object item=list.getAdapter().getItem(i);if(item instanceof android.view.MenuItem&&((android.view.MenuItem)item).getItemId()==R.id.menu_tv_distance)position=i;}
        assertTrue(position>=0);View row=list.getChildAt(position-list.getFirstVisiblePosition());assertNotNull(row);
        float x=row.getLeft()+row.getWidth()/2f,y=row.getTop()+row.getHeight()/2f;long down=SystemClock.uptimeMillis();
        MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);list.dispatchTouchEvent(press);press.recycle();
        if(readinessTicks){productionReadinessMenuTick();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(260));productionReadinessMenuTick();}
        assertEquals("Unchanged readiness rebuilt popup adapter during touch",0,popupChanges);
        MotionEvent release=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);list.dispatchTouchEvent(release);release.recycle();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(200));layout();
        assertEquals(TvSetupController.Mode.DISTANCE,activity.setup.mode());
        assertTrue(activity.findViewById(R.id.tv_input_diagonal).isShown());assertFalse(toolbar.isOverflowMenuShowing());
    }
    private int popupChanges;
    @Test public void actualPopupTouchOpensDistanceWithoutManualDismiss(){distancePopupTouch(false);}
    @Test public void steadyReadinessTicksDoNotInvalidatePopupDuringPress(){distancePopupTouch(true);}
    @Test public void realOverflowFocusAndTransientBarsDoNotResizePreviewOrRebindCamera(){
        create();installPublisher();settlePublisher();
        for(int navigationHeight:new int[]{24,48}){
            systemInsets(true,navigationHeight,32);settlePublisher();Rect before=previewBounds();
            int binding=publisher.provider.latestState().geometry.bindingGeneration;
            MaterialToolbar toolbar=activity.findViewById(R.id.top_app_bar);
            assertTrue(toolbar.showOverflowMenu());ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(30));
            assertTrue(toolbar.isOverflowMenuShowing());activity.onWindowFocusChanged(false);
            systemInsets(false,navigationHeight,32);settlePublisher();assertEquals(before,previewBounds());
            toolbar.dismissPopupMenus();activity.onWindowFocusChanged(true);systemInsets(true,navigationHeight,32);settlePublisher();
            assertEquals(before,previewBounds());assertEquals(binding,publisher.provider.latestState().geometry.bindingGeneration);
            assertEquals(32,activity.findViewById(R.id.main).getPaddingTop());
        }
    }
    @Test public void overflowDistanceAndSettingsCalibrationUseSameTypedRoutingAndKeepDraft() {
        create();installPublisher();publisher.viewOffsetX=20;
        overflowSelect(R.id.menu_tv_distance);settlePublisher();
        assertEquals(TvSetupController.Mode.DISTANCE,activity.setup.mode());
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");
        ((EditText)activity.findViewById(R.id.tv_input_margin)).setText("7");
        assertTrue(activity.findViewById(R.id.tv_input_diagonal).isShown());
        activity.setup.openRequested();assertEquals("55",((EditText)activity.findViewById(R.id.tv_input_diagonal)).getText().toString());
        publisher.viewOffsetX=0;settlePublisher();
        TvSetupNavigation.request(activity.getSharedPreferences("alpr_ui",0),TvSetupController.Mode.CALIBRATION);
        activity.setup.openRequested();settlePublisher();publisherFourCorners();
        List<Point2> draft=activity.overlay.corners();
        overflowSelect(R.id.menu_tv_distance);settlePublisher();
        activity.setup.open(TvSetupController.Mode.DISTANCE);settlePublisher();
        assertEquals("55",((EditText)activity.findViewById(R.id.tv_input_diagonal)).getText().toString());
        assertEquals("7",((EditText)activity.findViewById(R.id.tv_input_margin)).getText().toString());
        overflowSelect(R.id.menu_tv_calibrate);settlePublisher();
        for(int i=0;i<4;i++){assertEquals(draft.get(i).x,activity.overlay.corners().get(i).x,1e-6);assertEquals(draft.get(i).y,activity.overlay.corners().get(i).y,1e-6);}
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        activity.findViewById(R.id.tv_setup_cancel).performClick();settlePublisher();
        assertEquals(activity.getString(R.string.app_name),((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getTitle());
    }
    @Test public void calculatorOpensBeforeAnyCameraFrameAndRecoversWithoutRetry(){
        create();installPublisher();publisher.transformAvailable=false;overflowSelect(R.id.menu_tv_distance);layout();
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(5));layout();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
        assertTrue(activity.findViewById(R.id.tv_input_diagonal).isShown());
        assertFalse(activity.findViewById(R.id.tv_setup_retry).isShown());assertFalse(activity.overlay.editing());
        assertTrue(((TextView)activity.findViewById(R.id.tv_distance_result)).getText().toString().contains("Nie można wiarygodnie"));
        publisher.transformAvailable=true;settlePublisher();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
        assertTrue(((TextView)activity.findViewById(R.id.tv_distance_result)).getText().toString().contains("Camera2"));
    }
    @Test public void actualCameraXSdkPublisherEnablesFourCornersAndConfirmation() throws Exception {
        create();installPublisher();publisher.configureSdk(new Rect(0,0,4000,3000),new android.util.Size(160,122),new android.util.Size(1920,1080),90);
        overflowSelect(R.id.menu_tv_calibrate);settlePublisher();
        assertEquals(publisher.provider.latestState().detail,CameraGeometryProvider.Availability.READY,publisher.provider.latestState().availability);
        publisherFourCorners();assertEquals(4,activity.overlay.placedCount());
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        activity.findViewById(R.id.tv_setup_confirm).performClick();settlePublisher();
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        File target=new File("build/reports/tv-setup-ui/sdk-calibration-snapshot.json");target.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(target)){out.write(activity.setup.snapshot(1).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Test public void calculatorCancelRestoresConfirmedCalibrationWithoutChangingIt() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        String stored=activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1","");
        overflowSelect(R.id.menu_tv_distance);settle();
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        assertEquals(stored,activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
    }
    @Test public void visibleSystemBarsLeaveCornersAndPrimaryActionsUsableAtSmallAndLargeFonts(){
        for(int[] size:new int[][]{{320,568},{360,800}})for(float font:new float[]{1,2}){
            width=size[0];height=size[1];create(font,true);systemInsets(true,48,24);open();precisionControls();
            for(int id:new int[]{R.id.tv_corner_lg,R.id.tv_corner_pg,R.id.tv_corner_pd,R.id.tv_corner_ld,R.id.tv_setup_cancel,R.id.tv_setup_confirm})fullyVisible(id,true);
            assertTrue("System bars left no usable camera area: "+getName(R.id.camera_preview)+" usable="+java.util.Arrays.toString(geometry.usableViewRect())+" preview="+activity.preview.getWidth()+"x"+activity.preview.getHeight(),geometry.usableViewRect()[3]-geometry.usableViewRect()[1]>=90);
            fourCorners();assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
            activityController.pause().stop().destroy();activityController=null;
        }
    }
    @Test public void productionPublisherBurstsCannotKeepSpinnerAliveForTenSeconds(){
        create();installPublisher();activity.setup.open(false);settlePublisher();
        for(int i=0;i<100;i++){
            publisher.transformAvailable=true;publisher.frame();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(1));
            publisher.transformAvailable=false;publisher.frame();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(99));
        }
        assertEquals(TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
        assertFalse(activity.findViewById(R.id.tv_setup_waiting).isShown());
        activity.findViewById(R.id.tv_setup_cancel).performClick();layout();assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
    }
    @Test public void publisherErrorRecoveryActionsFitSmallScreenAndLargeFont(){
        for(float font:new float[]{1f,2f}){
            width=320;height=568;create(font,true);installPublisher();publisher.transformAvailable=false;
            activity.setup.open(false);settlePublisher();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));settlePublisher();
            assertEquals(TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
            fullyVisible(R.id.tv_setup_retry,true);fullyVisible(R.id.tv_setup_cancel,true);fullyVisible(R.id.tv_setup_error,false);
            assertFalse(activity.findViewById(R.id.tv_corner_choices).isShown());
            activity.findViewById(R.id.tv_setup_cancel).performClick();layout();assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
            activityController.pause().stop().destroy();activityController=null;
        }
    }

    @Test public void draftHandlesDoNotConfirmUntilOperatorSavesThroughActualWindow() throws Exception {
        create();open();assertTrue(activity.overlay.isShown());
        assertEquals(4,activity.overlay.placedCount());assertEquals("UNCALIBRATED",activity.setup.snapshot(1).getString("status"));
        assertTrue(((TextView)activity.findViewById(R.id.tv_setup_status)).getText().toString().contains("DRAFT"));
        assertFalse(activity.getSharedPreferences("alpr_ui",0).contains("tv_setup_calibration_v1"));
        fourCorners();assertEquals(4,activity.overlay.placedCount());
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        assertTrue(activity.getSharedPreferences("alpr_ui",0).contains("tv_setup_calibration_v1"));
    }

    @Test public void cornerChoiceDragAndGestureCancellationRetainSourcePoints(){
        create();open();fourCorners();
        activity.findViewById(R.id.tv_corner_lg).performClick();assertEquals(0,activity.overlay.selectedCorner());
        List<Point2> before=activity.overlay.corners();double[] p=geometry.normalizedToView(before.get(0).x,before.get(0).y);
        event(MotionEvent.ACTION_DOWN,(float)p[0],(float)p[1]);event(MotionEvent.ACTION_MOVE,(float)p[0]+15,(float)p[1]+8);
        assertTrue(activity.overlay.isDragging());assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        event(MotionEvent.ACTION_CANCEL,(float)p[0]+15,(float)p[1]+8);
        assertEquals(before.get(0).x,activity.overlay.corners().get(0).x,1e-6);
        assertEquals(before.get(0).y,activity.overlay.corners().get(0).y,1e-6);
        event(MotionEvent.ACTION_DOWN,(float)p[0],(float)p[1]);event(MotionEvent.ACTION_UP,(float)p[0]+10,(float)p[1]+5);settle();
        assertTrue(activity.overlay.corners().get(0).x>before.get(0).x);
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
    }

    @Test public void compassAndMetadataTicksCannotFlashHudOrEraseDraft(){
        create();open();precisionControls();tap(.12,.18);activity.findViewById(R.id.tv_corner_pg).performClick();tap(.83,.24);
        List<Point2> draft=activity.overlay.corners();
        PhoneOrientationHud hud=new PhoneOrientationHud(activity.findViewById(R.id.phone_orientation_panel));
        for(int i=0;i<40;i++){
            hud.render(PhoneOrientationEstimator.Snapshot.unavailable());
            activity.findViewById(R.id.live_status_strip).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.live_model_info).setVisibility(View.VISIBLE);
            assertFalse(activity.findViewById(R.id.phone_orientation_panel).isShown());
            assertFalse(activity.findViewById(R.id.live_status_strip).isShown());
            activity.setup.onGeometry(geometry,null,"Missing FOV metadata "+i);
            activity.setup.open(false);activity.setup.maintainPreparationUi();
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(250));layout();
            assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
            assertEquals(4,activity.overlay.placedCount());
            assertEquals(draft.get(0).x,activity.overlay.corners().get(0).x,0);
            assertFalse(activity.findViewById(R.id.phone_orientation_panel).isShown());
            assertTrue(activity.findViewById(R.id.tv_corner_choices).isShown());
        }
    }

    @Test public void closePreservesConfirmedCalibrationAndNormalLayoutWhileSavingDraft() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        String stored=activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1","");
        int previewHeight=activity.preview.getHeight();String saved=activity.setup.snapshot(1).getJSONArray("corners_norm").toString();
        open();assertEquals(4,activity.overlay.placedCount());activity.findViewById(R.id.tv_corner_lg).performClick();tap(.18,.20);
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());assertEquals(previewHeight,activity.preview.getHeight());
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));assertEquals(saved,activity.setup.snapshot(1).getJSONArray("corners_norm").toString());
        assertEquals(stored,activity.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
        assertTrue(activity.findViewById(R.id.normal_camera_hud).isShown());
        assertTrue(activity.getSharedPreferences("alpr_ui",0).contains(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE));
    }

    @Test public void sourceChangeRequiresExplicitRestartWithoutSilentDraftLoss() throws Exception {
        create();open();fourCorners();zoom=2;settle();
        assertEquals(TvSetupController.Phase.REVIEW,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
        assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());assertFalse(activity.overlay.editing());
        activity.findViewById(R.id.tv_setup_retry).performClick();settle();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
        fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        assertEquals(2,activity.setup.snapshot(1).getJSONObject("source_geometry").getDouble("zoom_ratio"),0);
    }

    @Test public void missingGeometryHasVisibleRecoveryAndCancelAlwaysExits(){
        create();activity.setup.onGeometry(null,null,"No transform");activity.setup.open(false);layout();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));layout();
        assertEquals(TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
        assertTrue(activity.findViewById(R.id.tv_setup_retry).isShown());
        assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        settle();activity.findViewById(R.id.tv_setup_retry).performClick();
        // Recovery changes the preview layout; old coordinates cannot enable the next step.
        assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());settle();
        assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());
        assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());activity.findViewById(R.id.tv_setup_next).performClick();settle();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
        tap(.12,.18);assertEquals(4,activity.overlay.placedCount());
        activity.setup.onGeometry(null,null,"Camera unavailable again");
        activity.findViewById(R.id.tv_setup_cancel).performClick();layout();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
    }

    @Test public void cancelTimeoutReturnsToNormalUiEvenWithoutNewCameraFrames(){
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_cancel).performClick();layout();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));layout();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        assertFalse(activity.findViewById(R.id.tv_setup_panel).isShown());
    }

    @Test public void transientTransformLossRecoversWithoutClearingDraftOrManualRetry(){
        create();open();precisionControls();tap(.12,.18);activity.findViewById(R.id.tv_corner_pg).performClick();tap(.83,.24);List<Point2> before=activity.overlay.corners();
        int previewHeight=activity.preview.getHeight();
        activity.setup.onGeometry(null,null,"PREVIEW_TRANSFORM_UNAVAILABLE");layout();
        assertEquals(TvSetupController.Phase.WAITING,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
        assertFalse(activity.overlay.editing());settle();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
        assertEquals(before.get(0).x,activity.overlay.corners().get(0).x,0);assertEquals(previewHeight,activity.preview.getHeight());
        activity.findViewById(R.id.tv_corner_pd).performClick();tap(.76,.70);activity.findViewById(R.id.tv_corner_ld).performClick();tap(.19,.65);assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
    }

    @Test public void repeatedTransientGeometryCannotRenewOpeningDeadlineForever(){
        create();open();
        for(int i=0;i<50;i++){
            activity.setup.onGeometry(geometry,null,"FOV unavailable");
            activity.setup.onGeometry(null,null,"PREVIEW_TRANSFORM_UNAVAILABLE");
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100));
        }
        assertEquals("Alternating geometry kept the editor waiting beyond its attempt budget",
                TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());
        assertTrue(activity.findViewById(R.id.tv_setup_retry).isShown());
    }

    @Test public void clearAndBackNeverInventOrPersistCalibration() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_clear).performClick();settle();
        assertEquals(4,activity.overlay.placedCount());assertEquals("UNCALIBRATED",activity.setup.snapshot(1).getString("status"));
        tap(.12,.18);activity.getOnBackPressedDispatcher().onBackPressed();settle();
        assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());activity.getOnBackPressedDispatcher().onBackPressed();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        assertEquals("UNCALIBRATED",activity.setup.snapshot(1).getString("status"));
        assertFalse(activity.getSharedPreferences("alpr_ui",0).contains("tv_setup_calibration_v1"));
    }

    @Test public void calculatorKeepsTypedValuesAcrossUnitAndRatioSwitches(){
        create();activity.setup.open(true);settle();
        assertEquals(activity.getString(R.string.tv_distance_title),((TextView)activity.findViewById(R.id.tv_setup_title)).getText().toString());
        assertFalse(activity.findViewById(R.id.tv_camera_lens).isShown());assertFalse(activity.findViewById(R.id.tv_corner_choices).isShown());
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");
        ((EditText)activity.findViewById(R.id.tv_input_margin)).setText("7");
        activity.findViewById(R.id.tv_units_size).performClick();
        ((EditText)activity.findViewById(R.id.tv_input_width)).setText("120");
        activity.findViewById(R.id.tv_ratio_classic).performClick();
        activity.findViewById(R.id.tv_units_diagonal).performClick();settle();
        assertEquals("55",((TextView)activity.findViewById(R.id.tv_input_diagonal)).getText().toString());
        assertEquals("120",((TextView)activity.findViewById(R.id.tv_input_width)).getText().toString());
        assertEquals("7",((TextView)activity.findViewById(R.id.tv_input_margin)).getText().toString());
        assertEquals("4",((TextView)activity.findViewById(R.id.tv_input_aspect_w)).getText().toString());
        assertFalse(activity.overlay.editing());assertFalse(activity.findViewById(R.id.tv_setup_confirm).isShown());
        assertTrue(((TextView)activity.findViewById(R.id.tv_distance_result)).getText().toString().contains("Nie można wiarygodnie"));
    }
    private void chooseLens(int index){
        com.google.android.material.chip.ChipGroup choices=activity.findViewById(R.id.tv_ref_lens_choices);
        assertTrue(choices.isShown());choices.getChildAt(index).performClick();
    }
    private void dragZoomControl(){
        android.widget.ScrollView scroll=activity.findViewById(R.id.tv_camera_controls_scroll);scroll.fullScroll(View.FOCUS_DOWN);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(50));layout();
        android.widget.SeekBar seek=activity.findViewById(R.id.tv_camera_zoom);
        seek.requestRectangleOnScreen(new Rect(0,0,seek.getWidth(),seek.getHeight()),true);layout();
        float x=seek.getPaddingLeft()+(seek.getWidth()-seek.getPaddingLeft()-seek.getPaddingRight())*.7f,y=seek.getHeight()/2f;
        long stamp=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(stamp,stamp,MotionEvent.ACTION_DOWN,x,y,0);seek.dispatchTouchEvent(down);down.recycle();
        MotionEvent up=MotionEvent.obtain(stamp,stamp+40,MotionEvent.ACTION_UP,x,y,0);seek.dispatchTouchEvent(up);up.recycle();settle();
    }
    @Test public void lensAndZoomAreConfirmedBeforeCornersAndSavedTogether() throws Exception {
        create();installCameraControls();overflowSelect(R.id.menu_tv_calibrate);settle();
        assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());assertFalse(activity.overlay.editing());
        assertTrue(activity.findViewById(R.id.tv_ref_lens_choices).isShown());
        chooseLens(1);ShadowLooper.shadowMainLooper().idle();settle();
        assertEquals(fakeCamera.wide,fakeCamera.requested);assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());
        fakeCamera.complete();settle();assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());
        dragZoomControl();assertTrue(fakeCamera.requestedZoom>1);assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());
        fakeCamera.complete();settle();activity.findViewById(R.id.tv_setup_next).performClick();settle();fourCorners();
        activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        CameraSetupSnapshot saved=CameraSetupSnapshot.restore(activity.getSharedPreferences("alpr_ui",0).getString(CameraSetupSnapshot.PREFERENCE,""));
        assertNotNull(saved);assertTrue(saved.same(fakeCamera.state().applied));assertEquals(fakeCamera.wide,saved.selection);
    }
    @Test public void cancellingLensChangeRollsBackCameraAndRequiresCalibrationCheck() throws Exception {
        create();installCameraControls();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        String saved=activity.getSharedPreferences("alpr_ui",0).getString(CameraSetupSnapshot.PREFERENCE,"");
        overflowSelect(R.id.menu_tv_calibrate);settle();
        assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());activity.getOnBackPressedDispatcher().onBackPressed();settle();
        chooseLens(1);ShadowLooper.shadowMainLooper().idle();fakeCamera.complete();settle();
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());assertEquals(fakeCamera.normal,fakeCamera.requested);
        fakeCamera.complete();settle();assertEquals("STALE",activity.setup.snapshot(1).getString("status"));
        assertEquals(saved,activity.getSharedPreferences("alpr_ui",0).getString(CameraSetupSnapshot.PREFERENCE,""));
    }
    @Test public void returningToFramePreservesDraftUntilZoomChanges(){
        create();installCameraControls();open();fourCorners();
        activity.getOnBackPressedDispatcher().onBackPressed();settle();assertEquals(4,activity.overlay.placedCount());
        assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());
        activity.findViewById(R.id.tv_setup_next).performClick();settle();assertEquals(4,activity.overlay.placedCount());
        activity.getOnBackPressedDispatcher().onBackPressed();settle();dragZoomControl();fakeCamera.complete();settle();
        assertEquals(0,activity.overlay.placedCount());assertFalse(activity.overlay.editing());assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());
    }
    @Test public void failedLensDoesNotGateStandaloneDistanceForm(){
        create();installCameraControls();fakeCamera.failure="Obiektyw niedostępny";fakeCamera.listener.accept(fakeCamera.state());
        overflowSelect(R.id.menu_tv_distance);settle();ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(4));
        assertEquals(TvSetupController.Mode.DISTANCE,activity.setup.mode());assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
        assertTrue(activity.findViewById(R.id.tv_input_diagonal).isShown());assertFalse(activity.findViewById(R.id.tv_setup_next).isShown());
    }
    @Test public void unavailableSavedLensIsNotSilentlyReplacedBySpinnerInitialization(){
        create();installCameraControls();CameraSelection missing=new CameraSelection("removed-lens",null);
        fakeCamera.requested=missing;fakeCamera.loading=true;fakeCamera.failure="Obiektyw niedostępny";fakeCamera.listener.accept(fakeCamera.state());
        overflowSelect(R.id.menu_tv_calibrate);settle();assertEquals(missing,fakeCamera.requested);
        android.widget.Spinner spinner=activity.findViewById(R.id.tv_camera_lens);
        assertTrue(spinner.getSelectedItem().toString().contains("niedostępny"));assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());
        spinner.setSelection(1);ShadowLooper.shadowMainLooper().idle();assertEquals(fakeCamera.normal,fakeCamera.requested);
        fakeCamera.complete();settle();assertTrue(activity.findViewById(R.id.tv_setup_next).isEnabled());
    }
    @Test public void landscapeKeepsLiveFrameBesideScrollableSetupAndFixedActions() throws Exception {
        for(float font:new float[]{1,2}){
            width=800;height=360;create(font,true);installCameraControls();overflowSelect(R.id.menu_tv_calibrate);settle();
            fullyVisible(R.id.tv_setup_next,true);fullyVisible(R.id.tv_setup_cancel,true);
            assertTrue(activity.preview.getRight()<=activity.findViewById(R.id.tv_setup_panel).getLeft());
            activity.findViewById(R.id.tv_setup_next).performClick();settle();fourCorners();
            fullyVisible(R.id.tv_setup_confirm,true);assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
            activity.findViewById(R.id.tv_setup_confirm).performClick();settle();assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
            assertEquals("READY",activity.setup.snapshot(1).getString("status"));
            activityController.pause().stop().destroy();activityController=null;
        }
    }

    private void fullyVisible(int id,boolean touchTarget){
        View view=activity.findViewById(id);Rect bounds=new Rect();assertTrue("Not visible: "+getName(id),view.getGlobalVisibleRect(bounds));
        assertEquals("Clipped width: "+getName(id),view.getWidth(),bounds.width());
        assertEquals("Clipped height: "+getName(id),view.getHeight(),bounds.height());
        if(touchTarget){assertTrue(getName(id)+" width<48",view.getWidth()>=48);assertTrue(getName(id)+" height<48",view.getHeight()>=48);}
        if(view instanceof TextView&&((TextView)view).getText().length()>0){TextView text=(TextView)view;assertNotNull(text.getLayout());
            for(int i=0;i<text.getLineCount();i++)assertEquals("Ellipsized: "+getName(id),0,text.getLayout().getEllipsisCount(i));
            assertTrue("Clipped text: "+getName(id)+" layout="+text.getLayout().getHeight()+" view="+view.getHeight()+" padding="+(view.getPaddingTop()+view.getPaddingBottom())+" width="+view.getWidth()+" size="+text.getTextSize()+" lines="+text.getLayout().getLineCount()+" text="+text.getText(),text.getLayout().getHeight()<=view.getHeight()-view.getPaddingTop()-view.getPaddingBottom());}
    }
    private String getName(int id){return activity.getResources().getResourceEntryName(id)+" @ "+width+"x"+height+" font="+activity.getResources().getConfiguration().fontScale;}

    @Test public void controlsRemainReadableAtThreeSizesFourFontScalesAndBothThemes(){
        for(int[] size:new int[][]{{320,568},{360,800},{411,915}})for(float font:new float[]{1f,1.3f,1.5f,2f})for(boolean night:new boolean[]{false,true}){
            width=size[0];height=size[1];create(font,night);open();
            int stablePreviewHeight=activity.preview.getHeight();precisionControls();
            for(int id:new int[]{R.id.tv_corner_lg,R.id.tv_corner_pg,R.id.tv_corner_pd,R.id.tv_corner_ld,R.id.tv_setup_cancel,R.id.tv_setup_confirm})fullyVisible(id,true);
            fullyVisible(R.id.tv_ref_section_badge,false);
            scrollTo(R.id.tv_setup_clear);fullyVisible(R.id.tv_setup_clear,true);
            if(font>1.2f||height<=640){scrollTo(R.id.tv_setup_details);fullyVisible(R.id.tv_setup_details,true);}else{scrollTo(R.id.tv_setup_coverage);fullyVisible(R.id.tv_setup_coverage,false);}
            assertTrue("No useful camera area: "+getName(R.id.camera_preview)+" usable="+java.util.Arrays.toString(geometry.usableViewRect()),geometry.usableViewRect()[3]-geometry.usableViewRect()[1]>=90);
            fourCorners();assertTrue(getName(R.id.tv_setup_confirm)+" phase="+activity.setup.phase()+" points="+activity.overlay.placedCount()+" geometryH="+geometry.previewHeight+" previewH="+activity.preview.getHeight()+" dragging="+activity.overlay.isDragging()+" error="+((TextView)activity.findViewById(R.id.tv_setup_error)).getText(),activity.findViewById(R.id.tv_setup_confirm).isEnabled());
            assertEquals("Corner progress resized camera: "+getName(R.id.camera_preview),stablePreviewHeight,activity.preview.getHeight());
            activity.findViewById(R.id.tv_setup_cancel).performClick();settle();activity.setup.open(true);settle();
            fullyVisible(R.id.tv_setup_cancel,true);fullyVisible(R.id.tv_setup_status,false);fullyVisible(R.id.tv_setup_body_scroll,false);
            assertTrue("Calculator left no useful camera area: "+getName(R.id.camera_preview),geometry.usableViewRect()[3]-geometry.usableViewRect()[1]>=90);
            activityController.pause().stop().destroy();activityController=null;
        }
    }
    @Test public void largeFontRetainsOptionalCoverageAndStateDetails(){
        width=320;height=568;create(2f,true);open();fourCorners();
        activity.findViewById(R.id.tv_setup_details).performClick();
        android.app.Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());
        String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(text.contains("NIESKALIBROWANY"));assertTrue(text.contains("Zajętość"));assertTrue(text.contains("L "));
        dialog.dismiss();assertEquals(4,activity.overlay.placedCount());
    }

    /** Native Android renderer, synthetic source frame, never a device/CameraX acceptance claim. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderActualAndroidPanelAndTouchOverlay() throws Exception {
        create();systemInsets(true,48,24);settle();syntheticFrame();capture("14-visible-bars-before-menu");
        Rect beforeMenu=previewBounds();MaterialToolbar menu=activity.findViewById(R.id.top_app_bar);
        assertTrue(menu.showOverflowMenu());ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));
        android.widget.PopupWindow popup=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
        assertNotNull(popup);captureView("15-actual-overflow-menu",popup.getContentView());assertEquals(beforeMenu,previewBounds());menu.dismissPopupMenus();
        open();syntheticFrame();capture("01-four-taps-start");
        fourCorners();syntheticFrame();capture("02-four-corners-ready");
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();activity.setup.open(true);settle();
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");settle();syntheticFrame();capture("03-distance-fields");
        ((android.widget.ScrollView)activity.findViewById(R.id.tv_setup_body_scroll)).fullScroll(View.FOCUS_DOWN);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(50));capture("05-distance-result-unavailable");
        activityController.pause().stop().destroy();activityController=null;
        width=320;height=568;create(2f,true);open();fourCorners();syntheticFrame();capture("04-small-screen-font-200");
        activityController.pause().stop().destroy();activityController=null;
        width=360;height=800;create();installPublisher();publisher.transformAvailable=false;activity.setup.open(false);settlePublisher();
        geometry=cameraGeometry();syntheticFrame();capture("06-publisher-waiting-transform");
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));settlePublisher();capture("07-publisher-transform-error");
        publisher.transformAvailable=true;settlePublisher();activity.findViewById(R.id.tv_setup_retry).performClick();settlePublisher();
        publisherFourCorners();syntheticFrame();capture("08-publisher-four-corners");
        activityController.pause().stop().destroy();activityController=null;
        width=320;height=568;create(2f,true);installPublisher();publisher.transformAvailable=false;activity.setup.open(false);settlePublisher();
        geometry=cameraGeometry();syntheticFrame();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));settlePublisher();
        fullyVisible(R.id.tv_setup_retry,true);fullyVisible(R.id.tv_setup_cancel,true);capture("09-small-screen-error-actions");
        activityController.pause().stop().destroy();activityController=null;
        width=360;height=800;create();installPublisher();publisher.viewOffsetX=20;
        overflowSelect(R.id.menu_tv_distance);settlePublisher();
        ((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));geometry=cameraGeometry();syntheticFrame();
        capture("10-calculator-invalid-geometry-fields");
        ((android.widget.ScrollView)activity.findViewById(R.id.tv_setup_body_scroll)).fullScroll(View.FOCUS_DOWN);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(50));capture("11-calculator-invalid-geometry-na");
        overflowSelect(R.id.menu_tv_calibrate);settlePublisher();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));settlePublisher();
        assertTrue(activity.findViewById(R.id.tv_setup_details).isShown());activity.findViewById(R.id.tv_setup_details).performClick();
        android.app.Dialog details=ShadowDialog.getLatestDialog();assertTrue(details.isShowing());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));
        captureView("12-geometry-matrix-details",details.getWindow().getDecorView());details.dismiss();
        activityController.pause().stop().destroy();activityController=null;
        create();installPublisher();publisher.configureSdk(new Rect(0,0,4000,3000),new android.util.Size(160,122),new android.util.Size(1920,1080),90);
        overflowSelect(R.id.menu_tv_calibrate);settlePublisher();publisherFourCorners();syntheticFrame();capture("13-sdk-viewport-four-corners");
        activityController.pause().stop().destroy();activityController=null;
        width=320;height=568;create(2f,true);systemInsets(true,48,24);open();fourCorners();syntheticFrame();capture("16-small-screen-visible-bars");
        activityController.pause().stop().destroy();activityController=null;
        width=360;height=800;create();installCameraControls();overflowSelect(R.id.menu_tv_calibrate);settle();syntheticFrame();capture("20-camera-step-one");
        chooseLens(1);ShadowLooper.shadowMainLooper().idle();fakeCamera.complete();settle();
        dragZoomControl();fakeCamera.complete();settle();syntheticFrame();capture("21-confirmed-lens-and-zoom");
        activity.findViewById(R.id.tv_setup_next).performClick();settle();fourCorners();syntheticFrame();capture("22-corners-after-camera-setup");
        activityController.pause().stop().destroy();activityController=null;
        width=800;height=360;create();installCameraControls();overflowSelect(R.id.menu_tv_calibrate);settle();syntheticFrame();capture("23-landscape-camera-setup");
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void render14bDraftOutlineAndKeyboardPriority() throws Exception {
        create();systemInsets(true,48,0);open();syntheticFrame();capture("30-14b-draft-at-entry");fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();syntheticFrame();capture("31-14b-outline-on");
        activity.setup.toggleOutline();capture("32-14b-outline-off");activity.setup.toggleOutline();zoom=2;settle();syntheticFrame();capture("33-14b-stale-outline");
        activity.setup.open(true);settle();((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");activity.findViewById(R.id.tv_input_diagonal).requestFocus();keyboardInsets(true,380);captureIme("34-14b-numeric-ime-portrait",380);
        keyboardInsets(false,0);activityController.pause().stop().destroy();activityController=null;
        width=800;height=360;create();systemInsets(true,48,0);activity.setup.open(true);settle();((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");activity.findViewById(R.id.tv_input_diagonal).requestFocus();keyboardInsets(true,180);captureIme("35-14b-numeric-ime-landscape",180);
    }
    private void captureIme(String name,int imeHeight) throws Exception {
        View root=activity.findViewById(R.id.main);Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);root.draw(canvas);
        Paint paint=new Paint();paint.setColor(0xff28313e);canvas.drawRect(0,height-imeHeight,width,height,paint);paint.setColor(Color.WHITE);paint.setTextSize(16);canvas.drawText("SYNTHETIC IME · "+imeHeight+" px",16,height-imeHeight+32,paint);
        File dir=new File("build/reports/tv-setup-ui");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    private void dragReferenceCorners(){
        double[][] target={{.12,.18},{.83,.24},{.76,.70},{.19,.65}};
        for(int i=0;i<4;i++){Point2 old=activity.overlay.corners().get(i);double[] from=geometry.normalizedToView(old.x,old.y),to=geometry.normalizedToView(target[i][0],target[i][1]);
            event(MotionEvent.ACTION_DOWN,(float)from[0],(float)from[1]);event(MotionEvent.ACTION_MOVE,(float)to[0],(float)to[1]);event(MotionEvent.ACTION_UP,(float)to[0],(float)to[1]);settle();}
    }
    private final class MainUiSnapshot {
        final int[] ids={R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.normal_camera_hud,R.id.control_panel};
        final android.view.ViewParent[] parents=new android.view.ViewParent[ids.length];
        final Rect frame=previewBounds();
        final com.google.android.material.button.MaterialButton start=activity.findViewById(R.id.analysis_start_button);
        final int radius=start.getCornerRadius(),iconSize=start.getIconSize(),gravity=start.getIconGravity();
        final float textSize=start.getTextSize();
        final android.content.res.ColorStateList tint=start.getBackgroundTintList();
        final android.graphics.drawable.Drawable background=activity.findViewById(R.id.main).getBackground();
        final int backgroundColor=((android.graphics.drawable.ColorDrawable)background).getColor();
        MainUiSnapshot(){for(int i=0;i<ids.length;i++)parents[i]=activity.findViewById(ids[i]).getParent();}
        void assertParents(){for(int i=0;i<ids.length;i++)assertSame("Main control was reparented",parents[i],activity.findViewById(ids[i]).getParent());}
        void assertRestored(boolean identicalFrame){
            assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());assertParents();
            for(int id:ids)assertTrue("Missing main control: "+getName(id),activity.findViewById(id).isShown());
            assertFalse(activity.findViewById(R.id.tv_ref_header).isShown());assertFalse(activity.findViewById(R.id.tv_ref_frame).isShown());
            assertFalse(activity.findViewById(R.id.tv_ref_lens_choices).isShown());assertFalse(activity.findViewById(R.id.tv_setup_panel).isShown());
            android.view.Menu menu=((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getMenu();
            for(int id:new int[]{R.id.menu_settings,R.id.menu_diagnostics,R.id.menu_tv_outline})assertTrue("Main menu action disappeared",menu.findItem(id).isVisible());
            assertSame(start,activity.findViewById(R.id.analysis_start_button));assertEquals(textSize,start.getTextSize(),0);
            assertEquals(radius,start.getCornerRadius());assertEquals(iconSize,start.getIconSize());assertEquals(gravity,start.getIconGravity());assertSame(tint,start.getBackgroundTintList());
            assertSame(background,activity.findViewById(R.id.main).getBackground());
            assertEquals(backgroundColor,((android.graphics.drawable.ColorDrawable)activity.findViewById(R.id.main).getBackground()).getColor());
            if(identicalFrame)assertEquals(frame,previewBounds());
        }
    }
    private void precisionControls(){
        if(!activity.findViewById(R.id.tv_corner_choices).isShown()){activity.findViewById(R.id.tv_ref_precise_toggle).performClick();settle();}
        scrollTo(R.id.tv_corner_choices);
    }
    private void scrollTo(int id){
        View view=activity.findViewById(id);view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);
        ShadowLooper.shadowMainLooper().idle();layout();
    }
    @Test public void calibrationNeverReparentsOrRestylesMainControls(){
        portraitSource=true;create();installCameraControls();MainUiSnapshot main=new MainUiSnapshot();main.assertRestored(true);
        activity.setup.open(false);settle();main.assertParents();assertTrue(activity.findViewById(R.id.tv_ref_header).isShown());
        assertFalse(activity.findViewById(R.id.control_panel).isShown());assertFalse(activity.findViewById(R.id.normal_camera_hud).isShown());
        activity.findViewById(R.id.tv_setup_next).performClick();settle();main.assertParents();
        Rect editorFrame=previewBounds();precisionControls();assertEquals(editorFrame,previewBounds());
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();main.assertRestored(true);
        activity.findViewById(R.id.analysis_start_button).performClick();assertEquals(1,activity.startRequests);
        activity.findViewById(R.id.collection_toggle).setEnabled(true);activity.findViewById(R.id.collection_toggle).performClick();assertEquals(1,activity.collectionRequests);
        activity.findViewById(R.id.camera_preview_button).performClick();assertEquals(1,activity.previewRequests);
        activity.findViewById(R.id.gallery_open_button).performClick();assertEquals(1,activity.resultRequests);
        ((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getMenu().performIdentifierAction(R.id.menu_settings,0);assertEquals(1,activity.settingsRequests);
        ((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getMenu().performIdentifierAction(R.id.menu_diagnostics,0);assertEquals(1,activity.diagnosticsRequests);
    }
    @Test public void calibrationFourHandlesCanBeDraggedSavedAndShownFromOriginalToolbar() throws Exception {
        portraitSource=true;create();MainUiSnapshot main=new MainUiSnapshot();open();
        assertEquals(4,activity.overlay.placedCount());assertFalse(activity.findViewById(R.id.tv_corner_choices).isShown());
        assertTrue(activity.findViewById(R.id.tv_ref_instruction_card).isShown());dragReferenceCorners();
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        main.assertRestored(false);assertEquals("READY",activity.setup.snapshot(1).getString("status"));assertTrue(activity.overlay.isShown());
        ((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getMenu().performIdentifierAction(R.id.menu_tv_outline,0);settle();assertFalse(activity.overlay.isShown());
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        activity.setup.open(true);settle();activity.findViewById(R.id.tv_setup_cancel).performClick();settle();main.assertRestored(false);
        assertEquals("READY",activity.setup.snapshot(1).getString("status"));
    }
    @Test public void calibrationExitPathsAlwaysReturnOriginalMainUi(){
        for(int exit=0;exit<6;exit++){
            create();MainUiSnapshot main=new MainUiSnapshot();open();
            if(exit==0)activity.findViewById(R.id.tv_setup_cancel).performClick();
            if(exit==1){activity.getOnBackPressedDispatcher().onBackPressed();settle();activity.getOnBackPressedDispatcher().onBackPressed();}
            if(exit==2)activity.setup.onBackground();
            if(exit==3){zoom=2;settle();assertEquals(TvSetupController.Phase.REVIEW,activity.setup.phase());activity.findViewById(R.id.tv_setup_cancel).performClick();}
            if(exit==4){activity.setup.onGeometry(null,null,"SYNTHETIC_TRANSFORM_LOSS");ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));assertEquals(TvSetupController.Phase.CAMERA_ERROR,activity.setup.phase());activity.findViewById(R.id.tv_setup_cancel).performClick();}
            if(exit==5){activity.findViewById(R.id.tv_setup_cancel).performClick();activity.setup.onGeometry(null,null,"SYNTHETIC_TRANSFORM_LOSS");ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3100));}
            settle();main.assertRestored(true);
            activity.setup.open(true);settle();activity.findViewById(R.id.tv_setup_cancel).performClick();settle();main.assertRestored(true);
            activityController.pause().stop().destroy();activityController=null;
        }
    }
    @Test public void referencePresentationIsAbsentFromCalculatorAndCannotBypassStartLock(){
        create();MainUiSnapshot main=new MainUiSnapshot();
        com.google.android.material.button.MaterialButton cancel=activity.findViewById(R.id.tv_setup_cancel);
        android.content.res.ColorStateList cancelTint=cancel.getBackgroundTintList();
        float titleSize=((TextView)activity.findViewById(R.id.tv_setup_title)).getTextSize();
        activity.setup.open(true);settle();
        assertFalse(activity.findViewById(R.id.tv_ref_header).isShown());assertFalse(activity.findViewById(R.id.tv_ref_frame).isShown());
        activity.setup.open(false);settle();assertTrue(activity.findViewById(R.id.tv_ref_frame).isShown());
        activity.setup.open(true);settle();assertFalse(activity.findViewById(R.id.tv_ref_frame).isShown());assertTrue(activity.findViewById(R.id.tv_distance_scroll).isShown());
        assertSame(cancelTint,cancel.getBackgroundTintList());assertEquals(titleSize,((TextView)activity.findViewById(R.id.tv_setup_title)).getTextSize(),0);
        assertEquals(main.backgroundColor,((android.graphics.drawable.ColorDrawable)activity.findViewById(R.id.main).getBackground()).getColor());
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();main.assertRestored(true);
        activity.editable=false;activity.setup.open(false);activity.setup.open(true);settle();main.assertRestored(true);
        ((MaterialToolbar)activity.findViewById(R.id.top_app_bar)).getMenu().performIdentifierAction(R.id.menu_settings,0);assertEquals(1,activity.settingsBlocked);
    }
    @Test public void calibrationUsesActualSdkGeometryAndSavesAfterRestoringMainFrame() throws Exception {
        create();installPublisher();publisher.configureSdk(new Rect(0,0,4000,3000),new android.util.Size(160,122),new android.util.Size(1920,1080),90);
        settlePublisher();MainUiSnapshot main=new MainUiSnapshot();activity.setup.open(false);settlePublisher();enterPublisherCorners();
        assertEquals(4,activity.overlay.placedCount());double[][] target={{.12,.18},{.83,.24},{.76,.70},{.19,.65}};
        for(int i=0;i<4;i++){CameraPreviewGeometry g=publisher.provider.latestState().geometry;Point2 p=activity.overlay.corners().get(i);double[] a=g.normalizedToView(p.x,p.y),b=g.normalizedToView(target[i][0],target[i][1]);
            event(MotionEvent.ACTION_DOWN,(float)a[0],(float)a[1]);event(MotionEvent.ACTION_MOVE,(float)b[0],(float)b[1]);event(MotionEvent.ACTION_UP,(float)b[0],(float)b[1]);settlePublisher();}
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());activity.findViewById(R.id.tv_setup_confirm).performClick();settlePublisher();
        main.assertRestored(false);assertEquals("READY",activity.setup.snapshot(1).getString("status"));assertEquals(CameraGeometryProvider.Availability.READY,publisher.provider.latestState().availability);
    }
    @Test public void lensChipsWorkOnlyDuringCameraStepAndCannotApplyFromMainScreen() throws Exception {
        portraitSource=true;create();installCameraControls();MainUiSnapshot main=new MainUiSnapshot();activity.setup.open(false);settle();
        com.google.android.material.chip.ChipGroup chips=activity.findViewById(R.id.tv_ref_lens_choices);assertEquals(2,chips.getChildCount());
        chips.getChildAt(1).performClick();assertEquals(fakeCamera.wide,fakeCamera.requested);assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());
        fakeCamera.complete();settle();activity.findViewById(R.id.tv_setup_next).performClick();settle();dragReferenceCorners();
        activity.findViewById(R.id.tv_setup_confirm).performClick();settle();main.assertRestored(false);assertEquals("READY",activity.setup.snapshot(1).getString("status"));
        long revision=fakeCamera.revision;chips.getChildAt(0).performClick();assertEquals(revision,fakeCamera.revision);
        activity.editable=false;activity.setup.open(false);settle();chips.getChildAt(0).performClick();assertEquals(revision,fakeCamera.revision);main.assertRestored(false);
    }
    @Test public void defaultBackRouteCanStillRequestZoomForTheAppliedCamera(){
        create();installCameraControls();fakeCamera.requested=CameraSelection.defaultBack();fakeCamera.listener.accept(fakeCamera.state());
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());
        assertEquals(fakeCamera.normal,fakeCamera.state().applied.selection);
        long revision=fakeCamera.revision;dragZoomControl();
        assertEquals(revision+1,fakeCamera.revision);assertTrue(fakeCamera.requested.isDefault());assertTrue(fakeCamera.requestedZoom>1);
        assertFalse(activity.findViewById(R.id.tv_setup_next).isEnabled());
    }
    @Test @Config(sdk={29,36}) public void calculatorRetainsImePriorityAfterLeavingCalibration(){
        for(int[] size:new int[][]{{360,800},{800,360}}){width=size[0];height=size[1];portraitSource=true;create();open();activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
            activity.setup.open(true);settle();keyboardInsets(false,0);EditText input=activity.findViewById(R.id.tv_input_diagonal);input.setText("55");input.requestFocus();
            int bottom=height>width?380:180;keyboardInsets(true,bottom);focusedInputVisible(R.id.tv_input_diagonal,height-bottom);
            assertFalse(activity.findViewById(R.id.tv_ref_header).isShown());assertFalse(activity.findViewById(R.id.tv_ref_frame).isShown());
            keyboardInsets(false,0);assertEquals("55",input.getText().toString());
            activityController.pause().stop().destroy();activityController=null;}
    }
    @Test public void calibrationPrimaryActionsFitCompactAndLargeFontLayouts(){
        for(int[] size:new int[][]{{320,568},{360,800},{800,360}})for(float font:new float[]{1f,2f}){
            width=size[0];height=size[1];portraitSource=true;create(font,false);systemInsets(true,48,0);open();
            assertEquals(4,activity.overlay.placedCount());fullyVisible(R.id.tv_setup_cancel,true);fullyVisible(R.id.tv_setup_confirm,true);referenceHeadingReadable();
            for(Point2 point:activity.overlay.corners())assertTrue(geometry.visible(point.x,point.y));
            activity.findViewById(R.id.tv_setup_cancel).performClick();settle();assertFalse(activity.findViewById(R.id.tv_ref_header).isShown());
            assertTrue(activity.findViewById(R.id.analysis_start_button).isShown());
            activityController.pause().stop().destroy();activityController=null;
        }
    }
    private void referenceHeadingReadable(){
        if(activity.findViewById(R.id.tv_corner_coordinate_header).isShown()){
            assertTrue(activity.findViewById(R.id.tv_corner_coordinates).isShown());assertTrue(activity.findViewById(R.id.tv_corner_coordinates).getHeight()>0);return;
        }
        TextView text=activity.findViewById(R.id.tv_ref_section_title);assertNotNull(text.getLayout());
        for(int i=0;i<text.getLayout().getLineCount();i++)assertEquals("Section heading was truncated: "+text.getText(),0,text.getLayout().getEllipsisCount(i));
        assertTrue("Section heading clipped vertically",text.getHeight()>=text.getLayout().getHeight());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderCalibrationOnlyAndRestoredMainComposition() throws Exception {
        portraitSource=true;create();installCameraControls();systemInsets(true,48,0);referenceFrame();capture("60-original-main-before-calibration");
        activity.setup.open(false);settle();referenceFrame();capture("61-calibration-only-set-frame");
        activity.findViewById(R.id.tv_setup_next).performClick();settle();referenceFrame();capture("62-calibration-only-four-handles");dragReferenceCorners();referenceFrame();capture("63-calibration-only-adjusted");
        activity.findViewById(R.id.tv_setup_confirm).performClick();settle();referenceFrame();capture("64-original-main-ready");
        activity.setup.toggleOutline();settle();referenceFrame();capture("65-original-main-outline-off");
        activity.setup.open(true);settle();((EditText)activity.findViewById(R.id.tv_input_diagonal)).setText("55");activity.findViewById(R.id.tv_input_diagonal).requestFocus();keyboardInsets(true,380);captureIme("66-original-calculator-ime",380);
        activityController.pause().stop().destroy();activityController=null;
        width=800;height=360;create();systemInsets(true,48,0);referenceFrame();capture("67-original-landscape-main");open();referenceFrame();capture("68-calibration-only-landscape");
        activityController.pause().stop().destroy();activityController=null;
        width=320;height=568;create(2f,false);systemInsets(true,48,0);referenceFrame();capture("69-original-main-font-200");open();referenceFrame();capture("70-calibration-only-font-200");
    }
    private void dragCornerTo(int visual,double x,double y){
        activity.overlay.selectCorner(visual);int source=geometry.mirrored?new int[]{1,0,3,2}[visual]:visual;
        Point2 point=activity.overlay.corners().get(source);double[] from=geometry.normalizedToView(point.x,point.y),to=geometry.normalizedToView(x,y);
        event(MotionEvent.ACTION_DOWN,(float)from[0],(float)from[1]);event(MotionEvent.ACTION_MOVE,(float)to[0],(float)to[1]);event(MotionEvent.ACTION_UP,(float)to[0],(float)to[1]);settle();
    }
    private void touchButton(int id,int action){
        View button=activity.findViewById(id);int[] at=new int[2];button.getLocationInWindow(at);long time=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(time,time,action,at[0]+button.getWidth()/2f,at[1]+button.getHeight()/2f,0);activity.dispatchTouchEvent(event);event.recycle();
    }
    private double selectedX(){return activity.overlay.selectedPosition().x*geometry.sourceWidth;}
    private double selectedY(){return activity.overlay.selectedPosition().y*geometry.sourceHeight;}
    private void assertCoordinateReadout(){
        assertEquals(activity.getString(R.string.tv_corner_coordinates,selectedX(),selectedY()),((TextView)activity.findViewById(R.id.tv_corner_coordinates)).getText().toString());
    }
    @Test public void outsideReleaseClampsAllEdgesAndKeepsTheNewPosition(){
        create();open();fourCorners();Rect frame=previewBounds();double[] u=geometry.usableViewRect();
        double[][] outside={{u[0]-10,u[1]+(u[3]-u[1])*.25},{u[0]+(u[2]-u[0])*.8,u[1]-10},{u[2]+10,u[1]+(u[3]-u[1])*.75},{u[0]+(u[2]-u[0])*.2,u[3]+10}};
        for(int i=0;i<4;i++){
            activity.overlay.selectCorner(i);Point2 before=activity.overlay.corners().get(i);double[] start=geometry.normalizedToView(before.x,before.y);
            event(MotionEvent.ACTION_DOWN,(float)start[0],(float)start[1]);event(MotionEvent.ACTION_MOVE,(float)outside[i][0],(float)outside[i][1]);
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(30));assertTrue(activity.overlay.boundaryLimited());assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
            assertEquals(activity.getString(R.string.tv_corner_limit),((TextView)activity.findViewById(R.id.tv_corner_limit_status)).getText().toString());assertCoordinateReadout();
            event(MotionEvent.ACTION_UP,(float)outside[i][0],(float)outside[i][1]);settle();
            Point2 now=activity.overlay.corners().get(i);double[] v=geometry.normalizedToView(now.x,now.y);
            assertTrue(com.example.alpr_v1.setup.TvCornerEditGeometry.contains(geometry,now));assertTrue(activity.overlay.selectedOnBoundary());assertFalse(activity.overlay.boundaryLimited());
            if(i==0)assertEquals(u[0],v[0],.0002);if(i==1)assertEquals(u[1],v[1],.0002);if(i==2)assertEquals(u[2],v[0],.0002);if(i==3)assertEquals(u[3],v[1],.0002);
            assertTrue(Math.hypot(now.x-before.x,now.y-before.y)>.01);assertEquals(frame,previewBounds());assertCoordinateReadout();
            assertEquals(activity.getString(R.string.tv_corner_on_edge),((TextView)activity.findViewById(R.id.tv_corner_limit_status)).getText().toString());
        }
    }
    @Test public void letterboxHalfOfHandleCanBeGrabbedWithoutJumpingAndSlidesAlongEdge(){
        portraitSource=true;create();open();dragCornerTo(0,0,.2);double[] u=geometry.usableViewRect();
        double x=selectedX(),y=selectedY();double[] start=geometry.normalizedToView(activity.overlay.selectedPosition().x,activity.overlay.selectedPosition().y);
        assertTrue(u[0]>24);event(MotionEvent.ACTION_DOWN,(float)start[0]-15,(float)start[1]);
        assertTrue(activity.overlay.isDragging());assertEquals(x,selectedX(),1e-8);assertEquals(y,selectedY(),1e-8);
        event(MotionEvent.ACTION_MOVE,(float)start[0]-30,(float)start[1]+20);assertTrue(activity.overlay.boundaryLimited());
        event(MotionEvent.ACTION_UP,(float)start[0]-30,(float)start[1]+20);settle();
        assertEquals(0,selectedX(),1e-8);double[] after=geometry.normalizedToView(activity.overlay.selectedPosition().x,activity.overlay.selectedPosition().y);
        assertEquals(start[1]+20,after[1],.0002);
        List<Point2> points=activity.overlay.corners();event(MotionEvent.ACTION_DOWN,2,activity.preview.getHeight()/2f);event(MotionEvent.ACTION_UP,2,activity.preview.getHeight()/2f);
        for(int i=0;i<4;i++){assertEquals(points.get(i).x,activity.overlay.corners().get(i).x,0);assertEquals(points.get(i).y,activity.overlay.corners().get(i).y,0);}
    }
    @Test public void arrowsUseOnePixelAndHeldRepeatStopsExactlyOnRelease(){
        create();open();fourCorners();activity.overlay.selectCorner(0);double x=selectedX(),y=selectedY();
        activity.findViewById(R.id.tv_corner_right).performClick();assertEquals(x+1,selectedX(),1e-7);assertEquals(y,selectedY(),1e-7);assertCoordinateReadout();
        x=selectedX();touchButton(R.id.tv_corner_right,MotionEvent.ACTION_DOWN);assertTrue(activity.overlay.isManipulating());assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        assertEquals(x+1,selectedX(),1e-7);ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(399));assertEquals(x+1,selectedX(),1e-7);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(351));assertEquals(x+5,selectedX(),1e-7);
        touchButton(R.id.tv_corner_right,MotionEvent.ACTION_UP);assertFalse(activity.overlay.isManipulating());double end=selectedX();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));assertEquals(end,selectedX(),0);assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        assertFalse(activity.findViewById(R.id.tv_corner_coordinates) instanceof EditText);
    }
    @Test public void heldArrowAndDragCancelOnGeometryLossWithoutLateUpdates(){
        create();open();fourCorners();activity.overlay.selectCorner(0);double original=selectedX();
        touchButton(R.id.tv_corner_right,MotionEvent.ACTION_DOWN);ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));assertTrue(selectedX()>original);
        activity.setup.onGeometry(null,null,"SYNTHETIC_TRANSFORM_LOSS");assertFalse(activity.overlay.isManipulating());
        double canceled=activity.overlay.selectedPosition().x;assertEquals(original/geometry.sourceWidth,canceled,1e-10);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(1000));assertEquals(canceled,activity.overlay.selectedPosition().x,0);
        touchButton(R.id.tv_corner_right,MotionEvent.ACTION_CANCEL);
    }
    @Test public void borderCalibrationConfirmsAndRestoresThroughTheExistingSnapshot() throws Exception {
        create();open();double[][] corners={{0,0},{1,0},{1,1},{0,1}};
        for(int i=0;i<4;i++)dragCornerTo(i,corners[i][0],corners[i][1]);
        assertTrue(activity.findViewById(R.id.tv_setup_confirm).isEnabled());activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        JSONObject saved=activity.setup.snapshot(1);assertEquals("READY",saved.getString("status"));assertEquals(1,saved.getJSONArray("corners_norm").getJSONObject(2).getDouble("x"),0);
        assertEquals(1,saved.getJSONArray("corners_norm").getJSONObject(2).getDouble("y"),0);
        com.example.alpr_v1.setup.TvSetupCalibration restored=com.example.alpr_v1.setup.TvSetupCalibration.restore(saved.toString());
        assertEquals(4,restored.corners.size());assertEquals("STALE",restored.status(geometry));
    }
    @Test public void draggingFeedbackAndReadinessTicksCannotResizeOrCancelTheGesture(){
        create();open();fourCorners();Rect frame=previewBounds();activity.overlay.selectCorner(0);Point2 point=activity.overlay.corners().get(0);double[] from=geometry.normalizedToView(point.x,point.y),u=geometry.usableViewRect();
        event(MotionEvent.ACTION_DOWN,(float)from[0],(float)from[1]);
        for(int i=0;i<20;i++){
            event(MotionEvent.ACTION_MOVE,(float)(u[0]-5+i),(float)(from[1]+i));activity.setup.maintainPreparationUi();settle();
            assertTrue(activity.overlay.isDragging());assertEquals(frame,previewBounds());assertCoordinateReadout();
        }
        event(MotionEvent.ACTION_UP,(float)u[0]+14,(float)from[1]+19);settle();assertEquals(frame,previewBounds());
    }
    private void assertNativeCoordinatesReadable(){
        for(int id:new int[]{R.id.tv_corner_active,R.id.tv_corner_coordinates}){
            TextView text=activity.findViewById(id);assertNotNull(text.getLayout());assertTrue(text.isShown());
            for(int i=0;i<text.getLayout().getLineCount();i++)assertEquals("Clipped coordinate width",0,text.getLayout().getEllipsisCount(i));
            assertTrue("Clipped coordinate height "+text.getText()+" layout="+text.getLayout().getHeight()+" view="+text.getHeight(),text.getLayout().getHeight()<=text.getHeight());
        }
    }
    @Test public void closingAndReopeningResumesExactDraftAndSelectedCornerWithoutConfirmation() throws Exception {
        create();open();fourCorners();activity.overlay.selectCorner(2);
        List<com.example.alpr_v1.setup.TvCornerEditGeometry.Position> points=activity.overlay.draftPositions();
        activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());assertEquals("UNCALIBRATED",activity.setup.snapshot(1).getString("status"));
        assertTrue(activity.getSharedPreferences("alpr_ui",0).contains(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE));
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(2,activity.overlay.selectedCorner());
        for(int i=0;i<4;i++){assertEquals(points.get(i).x,activity.overlay.draftPositions().get(i).x,0);assertEquals(points.get(i).y,activity.overlay.draftPositions().get(i).y,0);}
        assertTrue(((TextView)activity.findViewById(R.id.tv_setup_status)).getText().toString().contains("DRAFT"));
        assertEquals("Zamknij",((TextView)activity.findViewById(R.id.tv_setup_cancel)).getText().toString());
    }
    @Test public void backThroughCameraStepRetainsDraftRatherThanSeedingDefault(){
        create();open();fourCorners();List<Point2> before=activity.overlay.corners();
        activity.getOnBackPressedDispatcher().onBackPressed();settle();assertEquals(TvSetupController.Phase.CAMERA_SETUP,activity.setup.phase());
        activity.getOnBackPressedDispatcher().onBackPressed();settle();assertEquals(TvSetupController.Phase.NORMAL,activity.setup.phase());
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());
        for(int i=0;i<4;i++){assertEquals(before.get(i).x,activity.overlay.corners().get(i).x,0);assertEquals(before.get(i).y,activity.overlay.corners().get(i).y,0);}
    }
    @Test public void draftSurvivesActivityRecreationAndCalculatorRoundTrip() throws Exception {
        create();open();fourCorners();activity.overlay.selectCorner(3);List<Point2> before=activity.overlay.corners();
        activity.setup.onBackground();activityController.recreate().visible();activity=activityController.get();settle();
        activity.setup.open(true);settle();activity.findViewById(R.id.tv_setup_cancel).performClick();settle();
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(3,activity.overlay.selectedCorner());
        assertEquals(before.get(3).x,activity.overlay.corners().get(3).x,0);assertEquals(before.get(3).y,activity.overlay.corners().get(3).y,0);
        assertEquals("UNCALIBRATED",activity.setup.snapshot(1).getString("status"));
    }
    @Test public void closingDuringGestureRestoresLastReleasedPoint(){
        create();open();fourCorners();Point2 before=activity.overlay.corners().get(0);double[] p=geometry.normalizedToView(before.x,before.y);
        event(MotionEvent.ACTION_DOWN,(float)p[0],(float)p[1]);event(MotionEvent.ACTION_MOVE,(float)p[0]+25,(float)p[1]+15);
        activity.setup.close();settle();activity.setup.open(false);settle();
        assertEquals(before.x,activity.overlay.corners().get(0).x,0);assertEquals(before.y,activity.overlay.corners().get(0).y,0);
    }
    @Test public void confirmRemovesWorkingDraftAndPreservesExistingContract() throws Exception {
        create();open();fourCorners();assertTrue(activity.getSharedPreferences("alpr_ui",0).contains(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE));
        activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        assertFalse(activity.getSharedPreferences("alpr_ui",0).contains(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE));
        JSONObject saved=activity.setup.snapshot(1);assertEquals("READY",saved.getString("status"));assertEquals("alpr.tv_setup_calibration.v1",saved.getString("schema"));
        assertFalse(saved.has("selected_corner"));assertTrue(com.example.alpr_v1.setup.SetupJson.verified(saved));
    }
    @Test public void compatibleStaleCalibrationKeepsOutlineAndCanBeEditedAgain() throws Exception {
        create();open();fourCorners();activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        String corners=activity.setup.snapshot(1).getJSONArray("corners_norm").toString();JSONObject changed=geometry.toJson().put("binding_generation",9);
        activity.setup.onGeometry(CameraPreviewGeometry.fromJson(changed),null,"");layout();
        assertEquals("STALE",activity.setup.snapshot(1).getString("status"));assertTrue(activity.overlay.isShown());assertEquals(4,activity.overlay.placedCount());
        activity.setup.toggleOutline();assertFalse(activity.overlay.isShown());activity.setup.toggleOutline();assertTrue(activity.overlay.isShown());
        activity.setup.open(false);settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(4,activity.overlay.placedCount());
        assertEquals(corners,activity.setup.snapshot(1).getJSONArray("corners_norm").toString());
    }
    @Test public void incompatibleFramePreservesDraftButCannotShowItAsCurrent(){
        create();open();fourCorners();activity.setup.close();settle();
        String stored=activity.getSharedPreferences("alpr_ui",0).getString(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE,"");zoom=2;settle();activity.setup.open(false);settle();
        assertEquals(TvSetupController.Phase.REVIEW,activity.setup.phase());assertFalse(activity.overlay.editing());assertFalse(activity.findViewById(R.id.tv_setup_confirm).isEnabled());
        assertEquals(stored,activity.getSharedPreferences("alpr_ui",0).getString(com.example.alpr_v1.setup.TvEditDraft.PREFERENCE,""));
    }
    @Test public void reopeningDraftRestoresItsCameraBeforeExposingCorners(){
        create();installCameraControls();open();activity.getOnBackPressedDispatcher().onBackPressed();settle();chooseLens(1);fakeCamera.complete();settle();
        activity.findViewById(R.id.tv_setup_next).performClick();settle();fourCorners();List<Point2> before=activity.overlay.corners();
        activity.setup.close();settle();fakeCamera.complete();settle();assertEquals(fakeCamera.normal,fakeCamera.hardware);
        activity.setup.open(false);settle();assertEquals(fakeCamera.wide,fakeCamera.requested);assertFalse(activity.overlay.editing());
        fakeCamera.complete();settle();assertEquals(TvSetupController.Phase.EDITING,activity.setup.phase());assertEquals(fakeCamera.wide,fakeCamera.hardware);
        assertEquals(before.get(0).x,activity.overlay.corners().get(0).x,0);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderDraftResumeAndPreservedStaleOutline() throws Exception {
        portraitSource=true;create();open();fourCorners();activity.overlay.selectCorner(2);activity.setup.close();settle();syntheticFrame();capture("90-draft-main-restored");
        activity.setup.open(false);settle();syntheticFrame();capture("91-draft-resumed-selected-corner");activity.findViewById(R.id.tv_setup_confirm).performClick();settle();
        activity.setup.onGeometry(CameraPreviewGeometry.fromJson(geometry.toJson().put("binding_generation",9)),null,"");layout();syntheticFrame();capture("92-saved-outline-stale-preserved");
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderCornerPrecisionAtEdgesAndLargeFonts() throws Exception {
        portraitSource=true;create();systemInsets(true,48,0);open();fourCorners();referenceFrame();assertNativeCoordinatesReadable();capture("80-precision-coordinates");
        activity.overlay.selectCorner(0);Point2 point=activity.overlay.corners().get(0);double[] from=geometry.normalizedToView(point.x,point.y),u=geometry.usableViewRect();
        event(MotionEvent.ACTION_DOWN,(float)from[0],(float)from[1]);event(MotionEvent.ACTION_MOVE,(float)u[0]-10,(float)from[1]);ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(30));referenceFrame();capture("81-precision-outside-warning");
        event(MotionEvent.ACTION_UP,(float)u[0]-10,(float)from[1]);settle();referenceFrame();capture("82-precision-released-at-edge");activity.findViewById(R.id.tv_corner_right).performClick();referenceFrame();capture("83-precision-one-pixel");
        activityController.pause().stop().destroy();activityController=null;
        width=320;height=568;create(2f,false);systemInsets(true,48,0);open();dragCornerTo(2,1,1);referenceFrame();assertNativeCoordinatesReadable();fullyVisible(R.id.tv_corner_left,true);fullyVisible(R.id.tv_corner_limit_status,false);capture("84-precision-font-200");
        activityController.pause().stop().destroy();activityController=null;
        width=800;height=360;create();systemInsets(true,48,0);open();dragCornerTo(2,1,1);referenceFrame();assertNativeCoordinatesReadable();capture("85-precision-landscape");
    }
    private void referenceFrame(){
        Bitmap source=Bitmap.createBitmap(geometry.sourceWidth,geometry.sourceHeight,Bitmap.Config.ARGB_8888);Canvas scene=new Canvas(source);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        scene.drawColor(0xff806d4b);p.setColor(0xff382d1d);scene.drawRect(0,source.getHeight()*.76f,source.getWidth(),source.getHeight(),p);
        android.graphics.Path tv=new android.graphics.Path();double[][] corners={{.12,.18},{.83,.24},{.76,.70},{.19,.65}};
        for(int i=0;i<4;i++){float x=(float)(corners[i][0]*source.getWidth()),y=(float)(corners[i][1]*source.getHeight());if(i==0)tv.moveTo(x,y);else tv.lineTo(x,y);}tv.close();p.setColor(0xff111615);scene.drawPath(tv,p);
        int save=scene.save();scene.clipPath(tv);p.setColor(0xff54676b);scene.drawRect(0,source.getHeight()*.22f,source.getWidth(),source.getHeight()*.7f,p);
        p.setColor(0xffa3ada2);for(int i=0;i<5;i++)scene.drawRect(source.getWidth()*(.2f+i*.12f),source.getHeight()*.4f,source.getWidth()*(.28f+i*.12f),source.getHeight()*.54f,p);scene.restoreToCount(save);
        p.setColor(0xff0d1214);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(12);scene.drawPath(tv,p);p.setStyle(Paint.Style.FILL);
        Bitmap view=Bitmap.createBitmap(activity.preview.getWidth(),activity.preview.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(view);canvas.drawColor(0xff17160d);
        double[] raw=geometry.sourceToViewMatrix();float[] matrix=new float[9];for(int i=0;i<9;i++)matrix[i]=(float)raw[i];android.graphics.Matrix transform=new android.graphics.Matrix();transform.setValues(matrix);canvas.drawBitmap(source,transform,new Paint(Paint.FILTER_BITMAP_FLAG));
        p.setTextSize(10);p.setColor(Color.WHITE);canvas.drawText("SYNTHETIC CAMERA "+source.getWidth()+"×"+source.getHeight(),8,view.getHeight()-8,p);
        activity.preview.setBackground(new BitmapDrawable(activity.getResources(),view));source.recycle();
    }
    private void syntheticFrame(){
        Bitmap frame=Bitmap.createBitmap(activity.preview.getWidth(),activity.preview.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(frame);canvas.drawColor(0xff101927);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        double[] u=geometry.usableViewRect();p.setColor(0xff243346);canvas.drawRect((float)u[0],(float)u[1],(float)u[2],(float)u[3],p);
        p.setColor(0xff35465c);for(int i=1;i<8;i++){float x=(float)(u[0]+(u[2]-u[0])*i/8);canvas.drawLine(x,(float)u[1],x,(float)u[3],p);}
        p.setColor(Color.LTGRAY);p.setTextSize(12);canvas.drawText("SYNTHETIC CAMERA FRAME",12,(float)u[3]-10,p);
        activity.preview.setBackground(new BitmapDrawable(activity.getResources(),frame));
    }
    private void capture(String name) throws Exception {
        layout();captureView(name,activity.findViewById(R.id.main));
    }
    private void captureView(String name,View root) throws Exception {
        if(root.getWidth()==0||root.getHeight()==0){
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));
            root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
        }
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        File dir=new File("build/reports/tv-setup-ui");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
}
