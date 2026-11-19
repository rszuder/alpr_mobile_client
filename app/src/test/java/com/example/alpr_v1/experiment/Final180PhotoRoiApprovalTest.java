package com.example.alpr_v1.experiment;

import android.app.Application;
import com.example.alpr_v1.setup.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class Final180PhotoRoiApprovalTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    TvBboxCrop original()throws Exception{return TvBboxCropTest.selection(640,400);}
    Final180PhotoRoi photo(TvBboxCrop tv)throws Exception{return Final180PhotoRoi.prepare(new JSONArray("[{x:.25,y:.3},{x:.7,y:.3},{x:.7,y:.7},{x:.25,y:.7}]"),tv);}
    TvBboxCrop changed(TvBboxCrop tv,String field,Object value)throws Exception{
        JSONObject g=tv.geometry.toJson().put(field,value);CameraPreviewGeometry geometry=CameraPreviewGeometry.fromJson(g);
        JSONObject c=tv.calibrationJson().put("source_geometry",g);SetupJson.seal(c);
        return TvBboxCrop.prepare(c,geometry,tv.cameraSetup);
    }
    @Test public void generationAndLayoutChangeSessionDigestButNotApprovedCoordinates()throws Exception{
        TvBboxCrop tv=original();Final180PhotoRoi p=photo(tv);JSONObject approved=tv.calibrationJson();
        Final180PhotoRoiApproval a=Final180PhotoRoiApproval.capture(p,tv,approved,"SYNTHETIC");
        TvBboxCrop current=changed(changed(tv,"binding_generation",99),"viewport_revision",501);
        assertNotEquals(tv.digest,current.digest);
        Final180PhotoRoi rebound=a.forSession(current,approved);
        assertEquals(p.toJson().getJSONArray("corners_norm").toString(),rebound.toJson().getJSONArray("corners_norm").toString());
        assertEquals(p.left,rebound.left);assertEquals(p.top,rebound.top);assertEquals(p.right,rebound.right);assertEquals(p.bottom,rebound.bottom);
        assertEquals(tv.digest,p.toJson().getString("tv_bbox_crop_digest_sha256"));
        assertEquals(current.digest,rebound.toJson().getString("tv_bbox_crop_digest_sha256"));
        assertFalse(rebound.toJson().has("tv_calibration_approval"));
    }
    @Test public void realSourceChangeAndNewTvApprovalRemainBlocked()throws Exception{
        TvBboxCrop tv=original();JSONObject approved=tv.calibrationJson();Final180PhotoRoiApproval a=Final180PhotoRoiApproval.capture(photo(tv),tv,approved,"SYNTHETIC");
        for(String field:Arrays.asList("rotation_degrees","display_rotation_degrees","camera_id","lens_facing","source_image_width")){
            JSONObject g=tv.geometry.toJson();Object replacement=field.equals("camera_id")?"other":field.equals("lens_facing")?"FRONT":field.contains("width")?641:90;
            g.put(field,replacement);JSONObject c=tv.calibrationJson().put("source_geometry",g);SetupJson.seal(c);
            assertThrows(field,IllegalArgumentException.class,()->a.forSession(TvBboxCrop.prepare(c,CameraPreviewGeometry.fromJson(g),tv.cameraSetup),approved));
        }
        JSONObject newApproval=new JSONObject(approved.toString()).put("confirmed_at_utc","2026-11-07T20:00:00Z");SetupJson.seal(newApproval);
        assertThrows(IllegalArgumentException.class,()->a.forSession(tv,newApproval));
    }
    @Test public void legacyLookupRequiresExactAuthenticDigestAndNeverWrites()throws Exception{
        TvBboxCrop tv=original(),current=changed(tv,"binding_generation",88);Final180PhotoRoi p=photo(tv);JSONObject approved=tv.calibrationJson();
        File root=temp.newFolder(),dir=new File(root,"authentic-session");assertTrue(dir.mkdir());File file=new File(dir,"session.json");
        String record=new JSONObject().put("tv_bbox_crop",tv.toJson()).toString();Files.write(file.toPath(),record.getBytes(StandardCharsets.UTF_8));
        Final180PhotoRoiApproval a=Final180PhotoRoiApproval.resolve(p.toJson().toString(),"",current,approved,root);
        assertEquals(p.left,a.forSession(current,approved).left);assertEquals(record,new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoiApproval.resolve(p.toJson().toString(),"",current,approved,temp.newFolder()));
        JSONObject tampered=tv.toJson().put("digest_sha256",current.digest);Files.write(file.toPath(),new JSONObject().put("tv_bbox_crop",tampered).toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoiApproval.resolve(p.toJson().toString(),"",current,approved,root));
    }
    @Test public void preferenceContextRoundTripCannotGrantAnotherRoiOrTv()throws Exception{
        TvBboxCrop tv=original();Final180PhotoRoi p=photo(tv);JSONObject approved=tv.calibrationJson();String saved=p.toJson().toString();
        Final180PhotoRoiApproval a=Final180PhotoRoiApproval.capture(p,tv,approved,"SYNTHETIC");String context=a.toJson().toString();
        assertEquals(p.left,Final180PhotoRoiApproval.resolve(saved,context,tv,approved,null).forSession(tv,approved).left);
        JSONObject bad=new JSONObject(saved);bad.getJSONArray("corners_norm").getJSONObject(0).put("x",.26);SetupJson.seal(bad);
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoiApproval.resolve(bad.toString(),context,tv,approved,null));
        JSONObject corrupt=new JSONObject(context).put("provenance","tampered");
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoiApproval.resolve(saved,corrupt.toString(),tv,approved,null));
        assertEquals(saved,p.toJson().toString());assertEquals(context,a.toJson().toString());
    }
    @Test public void actualPolicyCannotBeSpoofedByOperatorLabel()throws Exception{
        Final180EvidenceTest fixture=new Final180EvidenceTest();JSONObject e=fixture.config().toJson();e.put("variant","R0_FINAL");Final180PhotoRoi.requireExecution(e);
        for(String policy:Arrays.asList("r1_one_roi","r2_two_roi","unknown","")){
            JSONObject invalid=new JSONObject(e.toString()).put("variant","R0").put("roi_budget_policy",policy);
            assertThrows(IllegalArgumentException.class,()->Final180PhotoRoi.requireExecution(invalid));
        }
        for(Object enabled:Arrays.asList(true,"false",JSONObject.NULL)){
            JSONObject invalid=new JSONObject(e.toString());invalid.getJSONObject("stages").getJSONObject("mp").put("enabled",enabled);
            assertThrows(IllegalArgumentException.class,()->Final180PhotoRoi.requireExecution(invalid));
        }
    }
}
