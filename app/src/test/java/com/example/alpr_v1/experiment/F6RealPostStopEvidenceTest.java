package com.example.alpr_v1.experiment;

import android.app.Application;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.metrics.ResearchArchive;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Read-only gate for the explicitly secured real session. No recovery or reserialization. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class F6RealPostStopEvidenceTest {
    @Test public void securedRealArchiveRemainsValidPartialWithAllOriginalCancellations()throws Exception{
        String path=System.getenv("F6_POST_STOP_REAL_ARCHIVE");Assume.assumeTrue("Explicit secured copy required",path!=null);
        File archive=new File(path);String before=RuntimeExecutionEvidence.sha256(archive);
        assertEquals("2c27e2dbaa1ebc23b52b39fee7d5b6ab158903c1cca788fd5944c94642e0a7f3",before);
        ResearchArchive.verifyEntryHashes(archive);assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));
        try(ZipFile zip=new ZipFile(archive)){
            JSONObject session=Final180ArchiveValidator.json(zip,"session.json");
            assertEquals("exp-1794585411433-6305e1b2",session.getString("session_id"));
            assertFalse(session.getBoolean("mt_detection_roles_complete"));assertFalse(session.getJSONObject("f6_evidence").getBoolean("complete"));
            assertEquals(0,session.getLong("dropped_sample_count"));assertEquals(0,session.getLong("dropped_telemetry_count"));
            assertEquals(0,session.getLong("scene_evidence_missing"));assertTrue(session.getBoolean("scene_evidence_complete"));
            assertEquals(472,session.getJSONObject("tv_bbox_input_evidence").getInt("images_written"));
            int roles=0,stale=0,incomplete=0,completeStale=0;
            try(BufferedReader in=new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry("samples/mt_detection_roles.jsonl")),StandardCharsets.UTF_8))){
                String line;while((line=in.readLine())!=null){
                    JSONObject r=new JSONObject(line);roles++;
                    if(r.getBoolean("stale_or_cancelled")){stale++;assertEquals("scene_superseded",r.getString("cancel_reason"));if("COMPLETE".equals(r.getString("role_proof_status")))completeStale++;}
                    if("INCOMPLETE".equals(r.getString("role_proof_status")))incomplete++;
                }
            }
            assertEquals(184,roles);assertEquals(51,stale);assertEquals(49,incomplete);assertEquals(2,completeStale);
        }
        assertEquals(before,RuntimeExecutionEvidence.sha256(archive));
    }
}
