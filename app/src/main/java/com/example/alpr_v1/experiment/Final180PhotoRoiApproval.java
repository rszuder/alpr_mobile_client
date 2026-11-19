package com.example.alpr_v1.experiment;

import com.example.alpr_v1.setup.*;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Preference-only provenance of operator approval. Never serialized into a research archive.
 * A session gets a new, immutable v1 ROI bound to its actual TV snapshot, without moving a pixel. */
public final class Final180PhotoRoiApproval {
    public static final String PREFERENCE="f6_photo_roi_approval_context_v1";
    private static final String SCHEMA="alpr.f6_photo_roi_approval.v1";
    private final String snapshot;
    private final TvBboxCrop tv;
    private final JSONObject approval;
    private final Final180PhotoRoi photo;
    private Final180PhotoRoiApproval(JSONObject context,JSONObject saved)throws Exception {
        if(!SCHEMA.equals(context.getString("schema"))||!SetupJson.verified(context))throw new IllegalArgumentException("INVALID_APPROVAL_CONTEXT");
        tv=TvBboxCrop.fromJson(context.getJSONObject("tv_bbox_crop"));
        approval=context.getJSONObject("tv_calibration_approval");requireApprovedTv(tv,approval);
        photo=Final180PhotoRoi.fromJson(saved,tv);
        if(!saved.getString("digest_sha256").equals(context.getString("photo_roi_digest_sha256")))throw new IllegalArgumentException("PHOTO_APPROVAL_CONFLICT");
        snapshot=context.toString();
    }
    private static void requireApprovedTv(TvBboxCrop tv,JSONObject approved)throws Exception {
        JSONObject c=tv.calibrationJson();
        if(!SetupJson.verified(approved)||!"READY".equals(approved.getString("status"))
                ||!TvSetupCalibration.SCHEMA.equals(approved.getString("schema"))
                ||!approved.getString("confirmed_at_utc").equals(c.getString("confirmed_at_utc"))
                ||!SetupJson.canonical(approved.getJSONArray("corners_norm")).equals(SetupJson.canonical(c.getJSONArray("corners_norm")))
                ||!TvEditDraft.sameCoordinates(CameraPreviewGeometry.fromJson(approved.getJSONObject("source_geometry")),tv.geometry))
            throw new IllegalArgumentException("TV_APPROVAL_SOURCE_CHANGED");
    }
    public static Final180PhotoRoiApproval capture(Final180PhotoRoi photo,TvBboxCrop tv,JSONObject approval,String provenance){
        try{return new Final180PhotoRoiApproval(SetupJson.seal(new JSONObject().put("schema",SCHEMA)
                .put("photo_roi_digest_sha256",photo.toJson().getString("digest_sha256"))
                .put("tv_bbox_crop",tv.toJson()).put("tv_calibration_approval",new JSONObject(approval.toString()))
                .put("provenance",provenance)),photo.toJson());}
        catch(Exception e){throw stale(e);}
    }
    public JSONObject toJson(){try{return new JSONObject(snapshot);}catch(Exception e){throw new IllegalStateException(e);}}
    /** Read-only approval review. It grants no camera admission and creates no session snapshot. */
    public static Final180PhotoRoiApproval review(String saved,String context){
        try{return new Final180PhotoRoiApproval(new JSONObject(context),new JSONObject(saved));}catch(Exception e){throw stale(e);}
    }
    public JSONObject reviewAgainst(CameraPreviewGeometry knownSource,JSONObject currentApproval){
        try{
            if(!SetupJson.verified(currentApproval)||!approval.getString("digest_sha256").equals(currentApproval.getString("digest_sha256")))throw new IllegalArgumentException("TV_APPROVAL_CHANGED");
            if(knownSource!=null&&!TvEditDraft.sameCoordinates(tv.geometry,knownSource))throw new IllegalArgumentException("PHOTO_SOURCE_CHANGED");
            return photo.toJson();
        }catch(Exception e){throw stale(e);}
    }
    public Final180PhotoRoi forSession(TvBboxCrop current,JSONObject currentApproval){
        try{
            if(!SetupJson.verified(currentApproval)||!approval.getString("digest_sha256").equals(currentApproval.getString("digest_sha256")))
                throw new IllegalArgumentException("TV_APPROVAL_CHANGED");
            requireApprovedTv(current,currentApproval);
            if(!TvEditDraft.sameCoordinates(tv.geometry,current.geometry)||!tv.cameraSetup.same(current.cameraSetup))
                throw new IllegalArgumentException("PHOTO_SOURCE_CHANGED");
            return Final180PhotoRoi.prepare(photo.toJson().getJSONArray("corners_norm"),current);
        }catch(Exception e){throw stale(e);}
    }
    /** Reads only authentic persisted snapshots; never repairs old sessions or writes preferences.
     * Legacy lookup is off the UI thread in production and reads one bounded metadata file at a time. */
    public static Final180PhotoRoiApproval resolve(String saved,String context,TvBboxCrop current,JSONObject approved,File sessions){
        try{
            JSONObject p=new JSONObject(saved);
            if(context!=null&&!context.isEmpty()){
                Final180PhotoRoiApproval result=new Final180PhotoRoiApproval(new JSONObject(context),p);
                result.forSession(current,approved);return result;
            }
            String digest=p.getString("tv_bbox_crop_digest_sha256");
            if(current.digest.equals(digest))return capture(Final180PhotoRoi.fromJson(p,current),current,approved,"EXACT_CURRENT_TV_SNAPSHOT");
            File[] dirs=sessions==null?null:sessions.listFiles(File::isDirectory);
            if(dirs!=null){
                Arrays.sort(dirs,(a,b)->b.getName().compareTo(a.getName()));
                for(File dir:dirs){
                    File f=new File(dir,"session.json");if(!f.isFile()||f.length()>2L*1024*1024)continue;
                    try{
                        JSONObject s=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
                        JSONObject t=s.optJSONObject("tv_bbox_crop");
                        if(t==null||!digest.equals(t.optString("digest_sha256")))continue;
                        TvBboxCrop anchor=TvBboxCrop.fromJson(t);
                        Final180PhotoRoiApproval result=capture(Final180PhotoRoi.fromJson(p,anchor),anchor,approved,"SESSION_SNAPSHOT:"+dir.getName());
                        result.forSession(current,approved);return result;
                    }catch(Exception unrelatedOrInvalid){/* No guessed identity from names, order or a similar timestamp. */}
                }
            }
            throw new IllegalArgumentException("PHOTO_APPROVAL_EVIDENCE_MISSING");
        }catch(Exception e){throw stale(e);}
    }
    /** Integrity-only read for an explicitly unverified editor draft. Does not grant START admission. */
    public static JSONArray reviewCorners(String saved,int width,int height){
        try{
            JSONObject p=new JSONObject(saved);
            if(!SetupJson.verified(p)||!"alpr.f6_photo_roi.v1".equals(p.getString("schema"))
                    ||!TvBboxCrop.CAMERA_SPACE.equals(p.getString("coordinate_space"))
                    ||p.getInt("source_width_px")!=width||p.getInt("source_height_px")!=height)throw new IllegalArgumentException("PHOTO_DRAFT_SOURCE_MISMATCH");
            JSONArray corners=p.getJSONArray("corners_norm");int[] b=TvBboxCrop.bounds(corners,width,height);JSONObject box=p.getJSONObject("photo_roi_camera_xy");
            if(b[0]!=box.getInt("left")||b[1]!=box.getInt("top")||b[2]!=box.getInt("right")||b[3]!=box.getInt("bottom"))throw new IllegalArgumentException("PHOTO_DRAFT_BOUNDS_CONFLICT");
            return corners;
        }catch(Exception e){throw stale(e);}
    }
    private static IllegalArgumentException stale(Exception cause){return new IllegalArgumentException("F6_PHOTO_ROI_REQUIRED_OR_STALE",cause);}
}
