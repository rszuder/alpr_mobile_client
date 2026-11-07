package com.example.alpr_v1.setup;

import com.example.alpr_v1.camera.CameraSelection;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.setup.TvCornerEditGeometry.Position;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvEditDraftTest {
    private CameraPreviewGeometry geometry(double zoom,int binding,int viewW){
        double s=viewW/1920.;return new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"0","BACK",zoom,binding,binding,viewW,640,
                new double[]{s,0,0,0,s,100,0,0,1},new double[]{0,0,viewW,640},0);
    }
    private List<Position> points(){return Arrays.asList(new Position(.123456789012345,.18),new Position(.83,.24),new Position(.76,.7),new Position(.19,.65));}
    private TvEditDraft draft(){return new TvEditDraft(points(),2,geometry(1,1,360),new CameraSetupSnapshot(new CameraSelection("0",null),1));}
    @Test public void roundTripRetainsDoublePointsAndSelection(){
        TvEditDraft d=TvEditDraft.restore(draft().toJson().toString());assertNotNull(d);assertEquals(2,d.selected);
        for(int i=0;i<4;i++){assertEquals(points().get(i).x,d.points.get(i).x,0);assertEquals(points().get(i).y,d.points.get(i).y,0);}
        assertTrue(d.camera.same(draft().camera));
    }
    @Test public void newBindingAndPresentationCanReuseCoordinatesWithoutBecomingReady(){
        assertTrue(TvEditDraft.sameCoordinates(geometry(1,1,360),geometry(1,9,480)));
        TvSetupCalibration confirmed=TvSetupCalibration.confirm(Arrays.asList(points().get(0).toPoint(),points().get(1).toPoint(),points().get(2).toPoint(),points().get(3).toPoint()),geometry(1,1,360),1,"2026-11-06T12:00:00Z");
        assertEquals("STALE",confirmed.status(geometry(1,9,480)));assertNotNull(TvEditDraft.fromCalibration(confirmed));
    }
    @Test public void changedZoomIsNotCoordinateCompatible(){assertFalse(TvEditDraft.sameCoordinates(geometry(1,1,360),geometry(2,1,360)));}
    @Test public void differentCameraAndMissingGeometryAreNotCoordinateCompatible(){
        assertFalse(TvEditDraft.sameCoordinates(geometry(1,1,360),geometry(1,1,360).withPhysicalCameraId("wide")));
        assertFalse(TvEditDraft.sameCoordinates(geometry(1,1,360),null));
    }
    @Test public void corruptOrMissingDraftIsSafelyUnavailable()throws Exception{
        assertNull(TvEditDraft.restore(""));assertNull(TvEditDraft.restore("{}"));JSONObject bad=draft().toJson();bad.put("selected_corner",3);assertNull(TvEditDraft.restore(bad.toString()));
    }
    @Test public void unknownSpaceAndSchemaAreRejectedEvenWithValidDigest()throws Exception{
        JSONObject j=draft().toJson();j.put("schema","unknown");SetupJson.seal(j);assertNull(TvEditDraft.restore(j.toString()));
        j=draft().toJson();j.put("coordinate_space","preview_px");SetupJson.seal(j);assertNull(TvEditDraft.restore(j.toString()));
    }
    @Test public void invalidEditingPolygonCanBeRecoveredButIsNeverConfirmed(){
        List<Position> p=Arrays.asList(new Position(.2,.2),new Position(.8,.8),new Position(.8,.2),new Position(.2,.8));
        TvEditDraft recovered=TvEditDraft.restore(new TvEditDraft(p,1,geometry(1,1,360),null).toJson().toString());assertNotNull(recovered);
        assertNotNull(TvSetupCalibration.validation(Arrays.asList(p.get(0).toPoint(),p.get(1).toPoint(),p.get(2).toPoint(),p.get(3).toPoint()),recovered.reference,1));
    }
    @Test(expected=IllegalArgumentException.class) public void nonFiniteOrOutsidePointsAreRejected(){
        new TvEditDraft(Arrays.asList(new Position(-.1,.2),new Position(.8,.2),new Position(.8,.8),new Position(.2,.8)),0,geometry(1,1,360),null);
    }
    @Test(expected=IllegalArgumentException.class) public void mismatchedCameraCannotBeRestoredAsThisDraft(){
        new TvEditDraft(points(),0,geometry(1,1,360),new CameraSetupSnapshot(new CameraSelection("wide",null),1));
    }
}
