package com.example.alpr_v1.camera;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Size;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.vision.CameraImageConverter;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class CameraGeometryProviderTest {
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void final176SharedAspectEditorAndSourcePixelsAndStaleApproval()throws Exception{
        start();
        List<Size> available=java.util.Arrays.asList(new Size(960,720),new Size(1920,1080),new Size(1440,1080),new Size(1280,960),new Size(1280,720));
        assertEquals(java.util.Arrays.asList(new Size(1440,1080),new Size(960,720),new Size(1280,960)),CameraController.compatibleResolutions(available,new Size(1440,1080)));
        assertEquals(java.util.Arrays.asList(new Size(1920,1080),new Size(1280,720)),CameraController.compatibleResolutions(available,new Size(1920,1080)));
        com.example.alpr_v1.setup.TvSetupCalibration previous=null;String originalApproval=null;org.json.JSONObject previousPhoto=null;
        com.example.alpr_v1.ui.TvSetupController editor=new com.example.alpr_v1.ui.TvSetupController(controller.get(),preview,
                controller.get().findViewById(com.example.alpr_v1.R.id.detection_overlay),()->true,()->{},(n,d)->{});
        int nonFullCrops=0;
        for(int height:new int[]{120,90})for(int degrees:new int[]{0,90,180,270}){
            preview.layout(0,0,360,520); // both actual buffers are letterboxed in the editor
            // Include unlike stream aspects too: CameraX supplies a nonzero common viewport crop.
            Size previewBuffer=degrees==180?new Size(960,height==120?540:720):new Size(960,height*6);
            hardware.configureSdk(new Rect(0,0,4000,3000),new Size(160,height),previewBuffer,degrees);
            if(!hardware.crop.equals(new Rect(0,0,160,height)))nonFullCrops++;
            hardware.bitmap=Bitmap.createBitmap(160,height,Bitmap.Config.ARGB_8888);
            for(int y=0;y<height;y++)for(int x=0;x<160;x++)hardware.bitmap.setPixel(x,y,Color.rgb(x,y,17));
            CameraGeometryProvider.GeometryState state=publish();assertEquals(state.detail,CameraGeometryProvider.Availability.READY,state.availability);
            CameraPreviewGeometry g=state.geometry;editor.onGeometryState(state);assertSame(g,editor.observationGeometry());
            Bitmap actual=CameraImageConverter.convert(hardware.image()).bitmap;
            assertEquals(actual.getWidth(),g.sourceWidth);assertEquals(actual.getHeight(),g.sourceHeight);
            java.util.List<com.example.alpr_v1.vision.Point2> points=java.util.Arrays.asList(new com.example.alpr_v1.vision.Point2(.1f,.1f),new com.example.alpr_v1.vision.Point2(.9f,.1f),new com.example.alpr_v1.vision.Point2(.9f,.9f),new com.example.alpr_v1.vision.Point2(.1f,.9f));
            com.example.alpr_v1.setup.TvSetupCalibration tv=com.example.alpr_v1.setup.TvSetupCalibration.confirm(points,g,1,"2026-11-14T00:00:00Z");
            com.example.alpr_v1.camera.CameraSetupSnapshot camera=new CameraSetupSnapshot(new CameraSelection(g.cameraId,null),1);
            com.example.alpr_v1.setup.TvBboxCrop crop=com.example.alpr_v1.setup.TvBboxCrop.prepare(tv.approval(),g,camera);
            com.example.alpr_v1.experiment.Final180PhotoRoi photo=com.example.alpr_v1.experiment.Final180PhotoRoi.prepare(
                    new org.json.JSONArray("[{x:.25,y:.3},{x:.75,y:.3},{x:.75,y:.7},{x:.25,y:.7}]"),crop);
            for(double[] corner:new double[][]{{.1,.1},{.9,.9},{.25,.3},{.75,.7}}){
                int x=(int)(corner[0]*actual.getWidth()),y=(int)(corner[1]*actual.getHeight());int pixel=actual.getPixel(x,y);
                float[] independent={Color.red(pixel)+.5f,Color.green(pixel)+.5f};
                hardware.sdkAnalysisToSensor.mapPoints(independent);preview.getSensorToViewTransform().mapPoints(independent);
                double[] mapped=g.normalizedToView((x+.5)/g.sourceWidth,(y+.5)/g.sourceHeight);
                assertEquals(independent[0],mapped[0],2);assertEquals(independent[1],mapped[1],2);
            }
            try(com.example.alpr_v1.setup.TvBboxCrop.Frame f=crop.open(actual,g,camera)){
                assertEquals(actual.getPixel(photo.left,photo.top),f.input.getPixel(photo.left-crop.left,photo.top-crop.top));
                assertEquals(actual.getPixel(photo.right-1,photo.bottom-1),f.input.getPixel(photo.right-1-crop.left,photo.bottom-1-crop.top));
            }
            if(previous!=null){assertNotEquals("READY",previous.status(g));assertEquals(originalApproval,previous.approval().toString());
                org.json.JSONObject stale=previousPhoto;
                assertThrows(IllegalArgumentException.class,()->com.example.alpr_v1.experiment.Final180PhotoRoi.fromJson(stale,crop));}
            previous=tv;previousPhoto=photo.toJson();originalApproval=tv.approval().toString();actual.recycle();hardware.bitmap.recycle();
        }
        assertTrue("The mapping must exercise ImageProxy crop offsets, not only full buffers",nonFullCrops>0);
        // A presentation change keeps source coordinates; old frame callbacks cannot revive a binding.
        CameraPreviewGeometry saved=editor.observationGeometry();
        CameraPreviewGeometry layout=CameraPreviewGeometry.fromJson(saved.toJson().put("preview_width",600).put("viewport_revision",99));
        assertTrue(com.example.alpr_v1.setup.TvEditDraft.sameCoordinates(saved,layout));
        ImageProxy old=hardware.image();hardware.binding++;hardware.bind();hardware.provider.observeFrame(old,hardware.binding-1);idle();
        assertEquals(CameraGeometryProvider.Availability.WAITING_FRAME,hardware.provider.latestState().availability);
    }
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle bundle){setTheme(R.style.Theme_ALPR_v1);super.onCreate(bundle);setContentView(R.layout.activity_main);}
    }
    private ActivityController<Host> controller;
    private GeometryProviderFixture hardware;
    private PreviewView preview;
    private final List<CameraGeometryProvider.GeometryState> received=new ArrayList<>();
    private void start(){controller=Robolectric.buildActivity(Host.class).setup().visible();Host host=controller.get();
        View decor=host.getWindow().getDecorView();int w=host.getResources().getDisplayMetrics().widthPixels,h=host.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));decor.layout(0,0,w,h);
        preview=host.findViewById(R.id.camera_preview);hardware=new GeometryProviderFixture(preview,host);hardware.provider.setListener(received::add);hardware.bind();idle();}
    private void idle(){ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(20));}
    private CameraGeometryProvider.GeometryState publish(){hardware.frame();idle();return hardware.provider.latestState();}
    @After public void stop(){if(hardware!=null)hardware.provider.onStopped();if(controller!=null)controller.pause().stop().destroy();}

    @Test public void realPublisherDistinguishesFrameTransformAndReadyWithoutFov(){
        start();assertEquals(CameraGeometryProvider.Availability.WAITING_FRAME,hardware.provider.latestState().availability);
        hardware.transformAvailable=false;assertEquals(CameraGeometryProvider.Availability.WAITING_TRANSFORM,publish().availability);
        hardware.transformAvailable=true;CameraGeometryProvider.GeometryState state=publish();
        assertEquals(CameraGeometryProvider.Availability.READY,state.availability);assertNotNull(state.geometry);assertNull(state.distanceMetadata);
        assertTrue(state.distanceReason.contains("Camera2"));assertTrue(state.frameTimestampNanos>0);
    }
    @Test public void explicitRefreshRepublishesUnchangedMapping(){
        start();publish();int before=received.size();hardware.provider.refresh();idle();
        assertEquals(before+1,received.size());assertEquals(CameraGeometryProvider.Availability.READY,received.get(received.size()-1).availability);
    }
    @Test public void uiListenerExceptionDoesNotBecomeAMatrixFailure(){
        start();publish();IllegalStateException uiFailure=new IllegalStateException("UI listener failure");
        hardware.provider.setListener(state->{if(state.availability==CameraGeometryProvider.Availability.READY)throw uiFailure;});
        assertSame(uiFailure,assertThrows(IllegalStateException.class,this::idle));
        assertEquals(CameraGeometryProvider.Availability.READY,hardware.provider.latestState().availability);
    }
    @Test public void optionalCameraMetadataExceptionCannotRevokeGeometry(){
        start();hardware.failDistanceMetadata=true;CameraGeometryProvider.GeometryState state=publish();
        assertEquals(CameraGeometryProvider.Availability.READY,state.availability);assertNotNull(state.geometry);
        assertNull(state.distanceMetadata);assertTrue(state.distanceReason.contains("IllegalStateException"));
    }
    @Test public void invalidMatrixIsExplicitAndNeverFabricatesMapping(){
        start();hardware.viewOffsetX=20;CameraGeometryProvider.GeometryState state=publish();
        assertEquals(CameraGeometryProvider.Availability.INVALID_TRANSFORM,state.availability);
        assertEquals("STOP_GEOMETRY",state.reasonCode);assertNull(state.geometry);
        assertTrue(state.detail.contains("ViewPort"));
    }
    @Test public void missingCameraIdentityIsExplicit(){
        start();hardware.failIdentity=true;hardware.bind();assertEquals("CAMERA_INFO_UNAVAILABLE",publish().reasonCode);
    }
    @Test public void metadataObservationFailureDoesNotEscapeIntoTheAnalyzer(){
        start();hardware.failFrameMetadata=true;
        CameraGeometryProvider.GeometryState state=publish();
        assertEquals("FRAME_METADATA_UNAVAILABLE",state.reasonCode);assertNull(state.geometry);
        assertEquals("UnsupportedOperationException",state.detail);
        hardware.failFrameMetadata=false;assertEquals(CameraGeometryProvider.Availability.READY,publish().availability);
    }
    @Test public void oldBindingCannotSupplyAFrameToNewCamera(){
        start();ImageProxy previous=hardware.image();publish();hardware.binding=2;hardware.bind();
        hardware.provider.observeFrame(previous,1);idle();assertEquals(CameraGeometryProvider.Availability.WAITING_FRAME,hardware.provider.latestState().availability);
        assertEquals(2,publish().geometry.bindingGeneration);
    }
    @Test public void roiFreeCroppedRotatedImageUsesCameraMatricesInPixelSpace(){
        start();hardware.crop=new Rect(160,90,1760,990);
        float[][] expected={{1,0,-160,0,1,-90,0,0,1},{0,-1,990,1,0,-160,0,0,1},
                {-1,0,1760,0,-1,990,0,0,1},{0,1,-90,-1,0,1760,0,0,1}};
        for(int i=0;i<4;i++){hardware.rotation=i*90;hardware.expectedSensorToSource.setValues(expected[i]);verifyExpectedMapping(publish().geometry);}
    }
    @Test public void alreadyRotatedImageInfoZeroDoesNotRotateTwice(){
        start();hardware.width=1080;hardware.height=1920;hardware.crop=new Rect(0,0,1080,1920);hardware.rotation=0;
        hardware.sensorToBuffer.setValues(new float[]{0,-1,1080,1,0,0,0,0,1});hardware.expectedSensorToSource.set(hardware.sensorToBuffer);
        verifyExpectedMapping(publish().geometry);
    }
    @Test @Config(qualifiers="w720dp-h1600dp-mdpi") public void realCameraXViewportRoundingIsNotAnInvalidTransform(){
        start();hardware.configureSdk(new Rect(0,0,4000,3000),new Size(160,122),new Size(1920,1080),90);
        CameraGeometryProvider.GeometryState state=publish();
        assertEquals("CameraX crop/preview metadata was rejected: "+state.detail,CameraGeometryProvider.Availability.READY,state.availability);
    }
    @Test public void actualSdkCpuRotationMetadataMustMapTheRotatedBitmap() throws Exception {
        start();hardware.configureSdk(new Rect(0,0,4000,3000),new Size(1280,960),new Size(1920,1080),90);
        hardware.rawAnalysis=new AnalysisTransformResolver.RawGeometry(1280,960,hardware.crop,90,true);
        Class<?> type=Class.forName("androidx.camera.core.ImageAnalysisBlockingAnalyzer");
        Object analyzer=org.robolectric.util.ReflectionHelpers.callConstructor(type);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(analyzer,"setRelativeRotation",org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,90));
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(analyzer,"setSensorToBufferTransformMatrix",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Matrix.class,hardware.sensorToBuffer));
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(analyzer,"setViewPortCropRect",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Rect.class,hardware.crop));
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(analyzer,"recalculateTransformMatrixAndCropRect",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,1280),org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,960),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,960),org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,1280));
        hardware.sensorToBuffer=org.robolectric.util.ReflectionHelpers.getField(analyzer,"mUpdatedSensorToBufferTransformMatrix");
        hardware.crop=new Rect((Rect)org.robolectric.util.ReflectionHelpers.getField(analyzer,"mUpdatedViewPortCropRect"));
        hardware.width=960;hardware.height=1280;hardware.rotation=0;
        CameraGeometryProvider.GeometryState state=publish();
        assertEquals(state.detail,CameraGeometryProvider.Availability.READY,state.availability);
        assertTrue(new org.json.JSONObject(state.diagnostics).getBoolean("sdk_140_cpu_rotation_corrected"));
        double[] before=state.geometry.sourceToViewMatrix();
        hardware.sensorToBuffer.setValues(new float[]{0,-.32f,960,.32f,0,0,0,0,1});
        CameraGeometryProvider.GeometryState alreadyCorrect=publish();
        assertEquals(CameraGeometryProvider.Availability.READY,alreadyCorrect.availability);
        assertFalse(new org.json.JSONObject(alreadyCorrect.diagnostics).getBoolean("sdk_140_cpu_rotation_corrected"));
        assertArrayEquals(before,alreadyCorrect.geometry.sourceToViewMatrix(),.002);
    }
    @Test public void cpuRotationCannotFabricateGeometryFromInconsistentImageMetadata(){
        start();hardware.configureSdk(new Rect(0,0,4000,3000),new Size(1280,960),new Size(1920,1080),90);
        hardware.rawAnalysis=new AnalysisTransformResolver.RawGeometry(1280,960,hardware.crop,90,true);hardware.rotation=0;
        CameraGeometryProvider.GeometryState state=publish();assertNull(state.geometry);
        assertEquals(CameraGeometryProvider.Availability.INVALID_TRANSFORM,state.availability);assertTrue(state.detail.contains("PIXELS_METADATA_MISMATCH"));
    }
    @Test public void pinnedPhysicalOutputIsIdentifiedByItsResultRatherThanLogicalActiveSensor(){
        assertEquals("2",CameraGeometryProvider.physicalStreamIdentity("2","0",java.util.Collections.singleton("2")));
        assertEquals("0",CameraGeometryProvider.physicalStreamIdentity("2","0",java.util.Collections.emptySet()));
        assertEquals("0",CameraGeometryProvider.physicalStreamIdentity(null,"0",java.util.Collections.singleton("2")));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void sdkViewportAndActualConverterPixelsAgreeForAllRotationsAndCpuRotatedFrames() throws Exception {
        start();
        for(Size previewSize:new Size[]{new Size(1920,1080),new Size(320,240)})
        for(boolean front:new boolean[]{false,true})for(int degrees:new int[]{0,90,180,270})for(boolean alreadyRotated:new boolean[]{false,true}){
            hardware.front=front;
            preview.layout(0,0,previewSize.getWidth()==320?800:360,previewSize.getWidth()==320?320:640);
            hardware.configureSdk(new Rect(0,0,4000,3000),new Size(160,122),previewSize,degrees);
            hardware.bitmap=Bitmap.createBitmap(160,122,Bitmap.Config.ARGB_8888);
            for(int y=0;y<122;y++)for(int x=0;x<160;x++)hardware.bitmap.setPixel(x,y,Color.rgb(x,y,1));
            if(alreadyRotated&&degrees!=0){
                // Bitmap pixels and actual SDK metadata are generated independently.
                Matrix bitmapRotation=new Matrix();bitmapRotation.setRotate(degrees);
                Bitmap rotated=Bitmap.createBitmap(hardware.bitmap,0,0,hardware.width,hardware.height,bitmapRotation,false);
                hardware.bitmap.recycle();hardware.bitmap=rotated;hardware.applySdkCpuRotationMetadata();
            }
            CameraGeometryProvider.GeometryState state=publish();
            assertEquals("rotation="+degrees+" CPU="+alreadyRotated+" "+state.detail,CameraGeometryProvider.Availability.READY,state.availability);
            Bitmap actual=CameraImageConverter.convert(hardware.image()).bitmap;
            assertEquals(actual.getWidth(),state.geometry.sourceWidth);assertEquals(actual.getHeight(),state.geometry.sourceHeight);
            Matrix sensorToView=preview.getSensorToViewTransform();
            for(double[] corner:new double[][]{{.12,.18},{.83,.24},{.76,.70},{.19,.65}}){
                int x=(int)(corner[0]*actual.getWidth()),y=(int)(corner[1]*actual.getHeight());int pixel=actual.getPixel(x,y);
                float[] independent={Color.red(pixel)+.5f,Color.green(pixel)+.5f};
                hardware.sdkAnalysisToSensor.mapPoints(independent);sensorToView.mapPoints(independent);
                double[] mapped=state.geometry.normalizedToView((x+.5)/actual.getWidth(),(y+.5)/actual.getHeight());
                assertEquals(independent[0],mapped[0],2);assertEquals(independent[1],mapped[1],2);
                double[] back=state.geometry.viewToNormalized(mapped[0],mapped[1]);
                assertEquals(x+.5,back[0]*actual.getWidth(),2);assertEquals(y+.5,back[1]*actual.getHeight(),2);
            }
            actual.recycle();hardware.bitmap.recycle();
        }
    }
    @Test public void sdkViewportRejectsWrongTransformAndDetailsCarryActualMatrices() throws Exception {
        start();hardware.configureSdk(new Rect(0,0,4000,3000),new Size(1920,1080),new Size(1280,720),90);
        hardware.viewOffsetX=20;CameraGeometryProvider.GeometryState state=publish();
        assertEquals(CameraGeometryProvider.Availability.INVALID_TRANSFORM,state.availability);
        assertNull(state.geometry);assertTrue(state.detail.contains("ViewPort"));
        org.json.JSONObject detail=new org.json.JSONObject(state.diagnostics);
        assertEquals(9,detail.getJSONArray("sensor_to_buffer_matrix").length());
        assertEquals(9,detail.getJSONArray("source_to_view_matrix").length());
        assertEquals(4,detail.getJSONArray("viewport_edges_in_source_px").length());
        assertEquals(2,detail.getJSONArray("crop_rounding_tolerance_source_px").length());
    }
    private void verifyExpectedMapping(CameraPreviewGeometry geometry){
        assertNotNull(geometry);Matrix expected=hardware.expectedSourceToView(preview);
        for(float[] point:new float[][]{{.12f,.18f},{.83f,.24f},{.76f,.70f},{.19f,.65f}}){
            float[] screen={point[0]*geometry.sourceWidth,point[1]*geometry.sourceHeight};expected.mapPoints(screen);
            double[] actual=geometry.normalizedToView(point[0],point[1]);assertEquals(screen[0],actual[0],.002);assertEquals(screen[1],actual[1],.002);
        }
    }
}
