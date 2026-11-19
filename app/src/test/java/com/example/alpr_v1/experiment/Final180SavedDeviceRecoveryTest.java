package com.example.alpr_v1.experiment;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import com.example.alpr_v1.setup.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static org.junit.Assert.*;

/** Read-only original A12 evidence; production recovery executes exclusively on private copies. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class Final180SavedDeviceRecoveryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    File original(){String path=System.getenv("F6_DEVICE_COPY");Assume.assumeTrue("Explicit secured A12 copy required",path!=null);return new File(path);}
    static String read(File f)throws Exception{return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
    static String pref(File root,String key)throws Exception{
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        NodeList entries=factory.newDocumentBuilder().parse(new File(root,"shared_prefs/alpr_ui.xml")).getElementsByTagName("string");
        for(int i=0;i<entries.getLength();i++){Element e=(Element)entries.item(i);if(key.equals(e.getAttribute("name")))return e.getTextContent();}
        return "";
    }
    @Test public void preservedA12PhotoLoadsAgainstFreshCompatibleBindingWithoutRecalibration()throws Exception{
        File root=original(),sessions=new File(root,"files/research/sessions");
        JSONObject session=new JSONObject(read(new File(sessions,"exp-1794537174642-a9ffe9cc/session.json")));
        TvBboxCrop anchor=TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop"));
        JSONObject source=anchor.geometry.toJson().put("binding_generation",901).put("viewport_revision",9001);
        CameraPreviewGeometry fresh=CameraPreviewGeometry.fromJson(source);JSONObject c=anchor.calibrationJson().put("source_geometry",source);SetupJson.seal(c);
        TvBboxCrop current=TvBboxCrop.prepare(c,fresh,anchor.cameraSetup);String saved=pref(root,Final180PhotoRoi.PREFERENCE),approved=pref(root,"tv_setup_calibration_v1");
        String hash=com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(new File(root,"shared_prefs/alpr_ui.xml"));
        Final180PhotoRoi result=Final180PhotoRoiApproval.resolve(saved,"",current,new JSONObject(approved),sessions).forSession(current,new JSONObject(approved));
        assertEquals(new JSONObject(saved).getJSONArray("corners_norm").toString(),result.toJson().getJSONArray("corners_norm").toString());
        assertEquals(141,result.left);assertEquals(519,result.top);assertEquals(771,result.right);assertEquals(789,result.bottom);
        assertEquals(hash,com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(new File(root,"shared_prefs/alpr_ui.xml")));
    }
    @Test public void productionRecoveryOfCopiedA12SessionsPreservesAuthenticFrozenRoi()throws Exception{
        File source=original(),work=temp.newFolder();
        try(java.util.stream.Stream<Path> paths=Files.walk(new File(source,"files/research").toPath())){
            for(Path p:(Iterable<Path>)paths::iterator){Path target=new File(work,"research").toPath().resolve(new File(source,"files/research").toPath().relativize(p));
                if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);}
        }
        Context context=new ContextWrapper(RuntimeEnvironment.getApplication()){@Override public File getFilesDir(){return work;}};
        List<File> recovered=ResearchSessionStore.recoverInterrupted(context);assertEquals(2,recovered.size());
        for(File dir:recovered){
            JSONObject after=new JSONObject(read(new File(dir,"session.json")));
            String id=after.getString("session_id");
            JSONObject before=new JSONObject(read(new File(source,"files/research/sessions/"+id+"/session.json")));
            assertEquals("Raw failed session must remain unchanged",read(new File(source,"files/research/sessions/"+id+"/session.json")),read(new File(work,"research/sessions/"+id+"/session.json")));
            assertEquals(SetupJson.canonical(before.getJSONObject("execution")),SetupJson.canonical(after.getJSONObject("execution")));
            assertEquals("R0_FINAL",after.getJSONObject("execution").getString("variant"));assertEquals("PARTIAL",after.getString("state"));
            assertEquals(before.getString("completion_reason"),after.getString("completion_reason"));
            File archive=new File(dir,"final/"+id+".alprsession");assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));
            assertTrue(new File(dir,"recovery_provenance.json").isFile());
            String out=System.getenv("F6_RECOVERED_FIXTURES");if(out!=null){Files.createDirectories(Paths.get(out));Files.copy(archive.toPath(),Paths.get(out,id+"-HOST-RECOVERED-PARTIAL.alprsession"),StandardCopyOption.REPLACE_EXISTING);Files.copy(new File(dir,"recovery_provenance.json").toPath(),Paths.get(out,id+"-recovery-provenance.json"),StandardCopyOption.REPLACE_EXISTING);}
            assertTrue(before.getString("last_write_error").contains("F6 requires actual R0 without MP"));
        }
        assertTrue(ResearchSessionStore.recoverInterrupted(context).isEmpty());
    }
    @Test public void acceptedMpV1ArchivesRemainCompatibleWithoutRegeneration()throws Exception{
        String path=System.getenv("F6_ACCEPTED_MP_FIXTURES");Assume.assumeTrue("Explicit accepted MP v1 fixtures required",path!=null);
        File[] archives=new File(path).listFiles((d,n)->n.endsWith(".alprsession"));assertNotNull(archives);assertTrue(archives.length>=20);
        boolean longSeen=false;
        for(File archive:archives){
            String before=com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(archive);
            String status=F6ProducerContractVerifier.validate(archive);assertTrue(status,status.equals("PASS")||status.equals("PARTIAL"));
            if(archive.getName().equals("long-session-4100-frames.alprsession")){
                assertEquals("f4c9c9976e460ad85cb8e655311f45dbb090c9f8368dd0644fc70ec8ebff50d9",before);assertEquals("PASS",status);longSeen=true;
            }
            assertEquals(before,com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(archive));
        }
        assertTrue(longSeen);System.out.println("ACCEPTED_MP_V1_ARCHIVES_VALIDATED="+archives.length);
    }
}
