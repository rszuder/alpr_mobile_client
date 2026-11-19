package com.example.alpr_v1.setup;

import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.vision.Point2;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static com.example.alpr_v1.setup.TvSetupGeometryTest.*;
import static org.junit.Assert.*;

/** Compatibility of approved source pixels, independent of touch-size and presentation constraints. */
public class F5a25CalibrationReuseTest {
    static final long NOW=10_000_000_000L;
    static final String MANUAL="2026-11-05T12:00:00Z",UTC="2026-11-08T20:00:00Z";
    final CameraPreviewGeometry original=geometry(0,960,540,1,"0",7);
    TvSetupCalibration manual(){return TvSetupCalibration.confirm(TRAPEZOID,original,1,MANUAL);}
    JSONObject snapshot(TvSetupCalibration c,CameraPreviewGeometry g){return c.snapshot(g,null,null,TvDistanceCalculator.unavailable("SYNTHETIC_HOST"),UTC);}
    TvSetupCalibration stored(){return TvSetupCalibration.restore(snapshot(manual(),original).toString());}
    static CameraSetupReadiness camera(CameraPreviewGeometry g){
        CameraSelection selection=new CameraSelection(g.cameraId,g.physicalCameraId);
        return new CameraSetupReadiness(CameraSetupReadiness.Reason.READY,new CameraSetupSnapshot(selection,(float)g.zoomRatio),selection,(float)g.zoomRatio,
                selection,(float)g.zoomRatio,g.bindingGeneration,3,NOW,NOW-100_000_000L,123456,2,1,true,true);
    }
    public static Set<String> keys(JSONObject json){Set<String> keys=new TreeSet<>();java.util.Iterator<String> i=json.keys();while(i.hasNext())keys.add(i.next());return keys;}
    TvSetupCalibration validate(TvSetupCalibration c,CameraPreviewGeometry g){return c.revalidated(g,camera(g),NOW,UTC);}
    @Test public void manualConfirmationIsStillRequiredForFirstCalibration(){
        assertEquals("UNCALIBRATED",validate(TvSetupCalibration.empty(),original).status(original));
        assertEquals("READY",manual().status(original));assertEquals(MANUAL,manual().confirmedAt);
    }
    @Test public void restoredApprovalWaitsForFreshCameraThenReusesOriginalConfirmation()throws Exception{
        TvSetupCalibration c=stored();assertEquals("STALE",c.status(original));c=validate(c,original);
        assertEquals("READY",c.status(original));assertEquals(MANUAL,c.confirmedAt);assertTrue(c.reused());
        assertEquals(snapshot(manual(),original).getString("digest_sha256"),c.approval().getString("digest_sha256"));
    }
    @Test public void rebindRequiresNewGenerationConfirmationButDoesNotChangeOriginalApproval(){
        CameraPreviewGeometry bound=geometry(0,960,540,1,"0",8);
        assertFalse(original.sameSourceGeometry(bound));assertFalse(original.sameGeometry(bound));
        TvSetupCalibration c=stored().revalidated(bound,camera(original),NOW,UTC);assertEquals("STALE",c.status(bound));
        assertEquals("CAMERA_SETUP_GENERATION_CHANGED",c.reason(bound));
        c=validate(c,bound);assertEquals("READY",c.status(bound));assertEquals(7,c.reference.bindingGeneration);assertEquals(MANUAL,c.confirmedAt);
    }
    @Test public void presentationResizeDoesNotReapplyEditorTouchThresholds()throws Exception{
        CameraPreviewGeometry tiny=geometry(0,60,34,1,"0",7);
        assertNotNull(TvSetupCalibration.validation(TRAPEZOID,tiny,1));
        TvSetupCalibration c=validate(stored(),tiny);assertEquals("READY",c.status(tiny));
        assertEquals(960,c.reference.previewWidth);assertEquals(60,snapshot(c,tiny).getJSONObject("source_geometry").getInt("preview_width"));
        assertEquals(SetupJson.canonical(snapshot(manual(),original).getJSONArray("corners_norm")),SetupJson.canonical(snapshot(c,tiny).getJSONArray("corners_norm")));
    }
    @Test public void missingCameraAndExpiredPositiveSnapshotCannotRestoreReady(){
        assertEquals("STALE",stored().revalidated(original,null,NOW,UTC).status(original));
        CameraSetupReadiness positive=camera(original);assertFalse(positive.frameFreshAt(NOW+400_000_000L));
        TvSetupCalibration c=stored().revalidated(original,positive,NOW+400_000_000L,UTC);
        assertEquals("STALE",c.status(original));assertEquals("NO_FRESH_CAMERA_FRAME",c.reason(original));
    }
    @Test public void unacknowledgedZoomCannotRestoreReady(){
        CameraSetupReadiness ready=camera(original);
        CameraSetupReadiness waiting=new CameraSetupReadiness(CameraSetupReadiness.Reason.ZOOM_NOT_ACKNOWLEDGED,null,ready.requested,1,
                ready.actual,1,7,3,NOW,NOW-100,123456,2,2,false,false);
        TvSetupCalibration c=stored().revalidated(original,waiting,NOW,UTC);assertEquals("STALE",c.status(original));
        assertEquals("ZOOM_NOT_ACKNOWLEDGED",c.reason(original));
    }
    @Test public void changedLensZoomAndSourceDimensionsRemainStale(){
        for(CameraPreviewGeometry g:Arrays.asList(geometry(0,960,540,2,"0",7),geometry(0,960,540,1,"1",7),original.withPhysicalCameraId("2"))){
            TvSetupCalibration c=validate(stored(),g);assertEquals("STALE",c.status(g));assertEquals("TV_CALIBRATION_SOURCE_CHANGED",c.reason(g));
        }
    }
    @Test public void cropRectAndSourcePixelChangesAreNotTransformedSilently()throws Exception{
        JSONObject crop=original.toJson();crop.getJSONArray("crop_rect_px").put(0,1);crop.put("source_image_width",1919);
        CameraPreviewGeometry changed=CameraPreviewGeometry.fromJson(crop);assertEquals("STALE",validate(stored(),changed).status(changed));
        JSONObject source=original.toJson();source.put("buffer_width",3840).put("buffer_height",2160).put("source_image_width",3840).put("source_image_height",2160);
        source.getJSONArray("crop_rect_px").put(2,3840).put(3,2160);source.getJSONArray("source_to_view_matrix").put(0,.25).put(4,.25);
        changed=CameraPreviewGeometry.fromJson(source);assertEquals("STALE",validate(stored(),changed).status(changed));
    }
    @Test public void sourceRotationAndDisplayRotationRemainStale()throws Exception{
        for(int r:new int[]{90,180,270}){CameraPreviewGeometry g=geometry(r,960,540,1,"0",7);assertEquals("STALE",validate(stored(),g).status(g));}
        JSONObject rotated=original.toJson();rotated.put("display_rotation_degrees",180);CameraPreviewGeometry g=CameraPreviewGeometry.fromJson(rotated);
        assertEquals("STALE",validate(stored(),g).status(g));
    }
    @Test public void currentMapperMustAgreeWithActualCameraSetup(){
        CameraPreviewGeometry other=geometry(0,960,540,2,"0",7);
        TvSetupCalibration c=stored().revalidated(original,camera(other),NOW,UTC);assertEquals("STALE",c.status(original));
        assertEquals("CAMERA_SETUP_GEOMETRY_MISMATCH",c.reason(original));
    }
    @Test public void corruptedAndNonApprovedRecordsCannotBecomeReady()throws Exception{
        JSONObject bad=snapshot(manual(),original);bad.getJSONArray("corners_norm").getJSONObject(0).put("x",.4);
        TvSetupCalibration c=validate(TvSetupCalibration.restore(bad.toString()),original);assertEquals("UNCALIBRATED",c.status(original));assertEquals("INVALID_STORED_CALIBRATION",c.reason(original));
        bad=snapshot(manual(),original);bad.put("status","STALE");SetupJson.seal(bad);
        assertEquals("STALE",validate(TvSetupCalibration.restore(bad.toString()),original).status(original));
        assertEquals("UNCALIBRATED",validate(TvSetupCalibration.restore("broken"),original).status(original));
    }
    @Test public void explicitHardInvalidationCannotBeUndoneByCameraPolling(){
        TvSetupCalibration c=manual().invalidated("EXPLICIT_INVALIDATION");assertEquals("STALE",validate(c,original).status(original));
        assertEquals("EXPLICIT_INVALIDATION",validate(c,original).reason(original));
    }
    @Test public void technicalSuspensionAndReturningToOriginalSourceDoNotRequireAnotherManualApproval(){
        TvSetupCalibration c=validate(stored(),original).awaitingVerification("CAMERA_GEOMETRY_CHANGED");
        CameraPreviewGeometry zoomed=geometry(0,960,540,2,"0",7);c=validate(c,zoomed);assertEquals("STALE",c.status(zoomed));
        c=validate(c,original);assertEquals("READY",c.status(original));assertEquals(MANUAL,c.confirmedAt);
    }
    @Test public void futureManualTimestampCannotBeRevalidated(){
        TvSetupCalibration c=TvSetupCalibration.confirm(TRAPEZOID,original,1,"2026-11-09T12:00:00Z");
        c=validate(c,original);assertEquals("STALE",c.status(original));assertEquals("CONFIRMATION_TIME_IN_FUTURE",c.reason(original));
    }
    @Test public void snapshotKeepsExistingSchemaAndManualFieldsWhileUsingCurrentMapper()throws Exception{
        JSONObject before=snapshot(manual(),original);String immutable=SetupJson.canonical(before);
        CameraPreviewGeometry view=geometry(0,360,700,1,"0",8);TvSetupCalibration c=validate(stored(),view);JSONObject after=snapshot(c,view);
        assertEquals(keys(before),keys(after));assertTrue(SetupJson.verified(after));
        assertEquals(MANUAL,after.getString("confirmed_at_utc"));assertEquals(immutable,SetupJson.canonical(c.approval()));
        before.remove("source_geometry");before.remove("digest_sha256");after.remove("source_geometry");after.remove("digest_sha256");
        assertEquals(SetupJson.canonical(before),SetupJson.canonical(after));
        JSONObject leaked=c.approval();leaked.put("confirmed_at_utc","bad");assertEquals(MANUAL,c.approval().getString("confirmed_at_utc"));
    }
    @Test public void manualEditCreatesNewVersionWithoutMutatingEarlierEvidence()throws Exception{
        JSONObject old=snapshot(manual(),original);String immutable=SetupJson.canonical(old);
        List<Point2> edited=new ArrayList<>(TRAPEZOID);edited.set(0,new Point2(.13f,.19f));
        TvSetupCalibration next=TvSetupCalibration.confirm(edited,original,1,UTC);assertEquals(UTC,next.confirmedAt);
        assertNotEquals(old.getString("digest_sha256"),snapshot(next,original).getString("digest_sha256"));assertEquals(immutable,SetupJson.canonical(old));
    }
}
