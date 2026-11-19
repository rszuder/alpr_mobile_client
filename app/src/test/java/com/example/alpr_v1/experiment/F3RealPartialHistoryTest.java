package com.example.alpr_v1.experiment;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.example.alpr_v1.R;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.ui.ResearchSessionPicker;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** One focused control: real secured producer bytes, native validation and actual history dialogs.
 * Deliberately incomplete/altered diagnostic-only ZIPs below are negative tests, never device exports. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32) @LooperMode(LooperMode.Mode.PAUSED)
public class F3RealPartialHistoryTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle saved){setTheme(R.style.Theme_ALPR_v1);super.onCreate(saved);setContentView(new android.widget.FrameLayout(this));}
    }
    private File negative(File original,String mode)throws Exception{
        File target=new File(temporary.getRoot(),"SYNTHETIC_NEGATIVE_"+mode+".alprsession");
        try(ZipFile source=new ZipFile(original);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(target.toPath()))){
            JSONObject manifest=Final180ArchiveValidator.json(source,"manifest.json");
            for(String name:new String[]{"session.json",Final180Evidence.FILE,F5aEvidence.ROLES,TvBboxEvidence.INDEX}){
                if("missing_journal".equals(mode)&&F5aEvidence.ROLES.equals(name))continue;
                byte[] bytes=source.getInputStream(source.getEntry(name)).readAllBytes();
                if(Final180Evidence.FILE.equals(name)&&mode.startsWith("broken")){
                    bytes="{broken JSON\n".getBytes(StandardCharsets.UTF_8);
                    if("broken_json".equals(mode)){
                        StringBuilder digest=new StringBuilder();
                        for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(Locale.ROOT,"%02x",b&255));
                        manifest.getJSONObject("entry_sha256").put(name,digest.toString());
                    }
                }
                out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("manifest.json"));out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        return target;
    }
    private static String text(AlertDialog dialog){return ((TextView)dialog.findViewById(android.R.id.message)).getText().toString();}
    @Test public void authenticPartialCountsAndHistoryCallbacksNeverInventLossesOrSessions()throws Exception{
        String path=System.getenv("F3_REAL_ARCHIVE");
        org.junit.Assume.assumeTrue("Explicit private secured archive required; unavailable on generic CI",path!=null&&!path.isEmpty());
        File archive=new File(path);String sha=RuntimeExecutionEvidence.sha256(archive);
        assertEquals("fe34f86d72eb49ee6e4ec303ad2eae25921e106003d7f29a0c411ea2f8da9a11",sha);
        assertEquals("PARTIAL",Final180ArchiveValidator.validate(archive));
        F6ProducerContractVerifier.checkScenes(archive); // Includes the existing MP OFF schema; no synthetic receipt.
        try(ZipFile zip=new ZipFile(archive)){
            int[] withoutScene={0};
            Final180ArchiveValidator.rows(zip,Final180Evidence.FILE,event->{
                if("SCENE_OBSERVATION".equals(event.optString("event"))&&event.isNull("scene_event_id")){
                    withoutScene[0]++;JSONObject mp=event.getJSONObject("details").getJSONObject("mp");
                    F6ProducerContractVerifier.mp(mp);assertEquals("NOT_RUN",mp.getString("execution_status"));
                    JSONObject missingField=new JSONObject(mp.toString());missingField.remove("trace_frame_id");
                    assertThrows(java.io.IOException.class,()->F6ProducerContractVerifier.mp(missingField));
                    JSONObject fakeRun=new JSONObject(mp.toString()).put("execution_status","EXECUTED");
                    assertThrows(java.io.IOException.class,()->F6ProducerContractVerifier.mp(fakeRun));
                    JSONObject conflict=new JSONObject(event.toString());
                    conflict.put("source_stamp",new JSONObject(mp.getJSONObject("source_stamp").toString()).put("source_sequence",mp.getJSONObject("source_stamp").getLong("source_sequence")+1));
                    try(ExternalJsonSort joins=new ExternalJsonSort(temporary.getRoot())){
                        assertThrows(java.io.IOException.class,()->F6ProducerContractVerifier.lifecycleObservation(joins,conflict));
                    }
                }
            });assertEquals(1,withoutScene[0]);
        }
        ResearchSessionSummary summary=ResearchSessionSummary.load(Collections.singletonList(archive)).get(0);
        assertEquals("exp-1794625079647-242826bb",summary.session.getString("session_id"));
        String before=summary.session.toString(),breakdown=summary.evidenceBreakdown();
        assertTrue(breakdown,breakdown.contains("390 / 390 / 390"));assertTrue(breakdown.contains("185 / 185"));
        assertTrue(breakdown.contains("201 / 189"));assertTrue(breakdown.contains("scene_superseded): 213"));
        assertTrue(breakdown.contains("629 / 629"));assertTrue(breakdown.contains("brak lub niezapisane: 0"));
        assertTrue(breakdown.contains("Straty kolejki próbek / telemetrii: 0 / 0"));
        assertTrue(breakdown.contains("Straty dziennika F6 / błędy projekcji: 0 / 0"));
        assertTrue(breakdown.contains("Niepełny FINAL pozostaje niepełny"));
        assertEquals(before,summary.session.toString());assertFalse(summary.complete);
        for(String mode:new String[]{"missing_journal","broken_checksum","broken_json"}){
            ResearchSessionSummary damaged=new ResearchSessionSummary(negative(archive,mode),summary.session);
            String result=damaged.evidenceBreakdown();assertTrue(result,result.startsWith("Rozliczenie F6: nieustalone"));
            assertFalse(result.contains("390 / 390 / 390"));assertEquals(before,damaged.session.toString());
        }
        String missingImages=new ResearchSessionSummary(negative(archive,"missing_images"),summary.session).evidenceBreakdown();
        assertTrue(missingImages,missingImages.contains("brak lub niezapisane: 1258"));
        assertTrue(new ResearchSessionSummary(new File(temporary.getRoot(),"absent.alprsession"),summary.session).evidenceBreakdown().contains("nieustalone"));
        JSONObject legacy=new JSONObject(before);legacy.getJSONObject("execution").remove("capture_profile");legacy.put("variant","R0_FINAL");
        assertFalse(new ResearchSessionSummary(archive,legacy).hasF6Evidence());assertEquals("",new ResearchSessionSummary(archive,legacy).evidenceBreakdown());

        org.robolectric.android.controller.ActivityController<Host> host=Robolectric.buildActivity(Host.class).setup().visible();
        try{
            List<Runnable> work=new ArrayList<>();List<ResearchSessionSummary> rows=Collections.singletonList(summary);
            AlertDialog list=ResearchSessionPicker.showHistory(host.get(),rows,()->"SYNTHETIC history executor",f->fail("History read must not export or recover"),work::add);
            list.getListView().performItemClick(list.getListView().getChildAt(0),0,0);
            AlertDialog old=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(text(old).contains("Odczytywanie"));old.dismiss();
            list=ResearchSessionPicker.showHistory(host.get(),rows,()->"SYNTHETIC history executor",f->fail("Unexpected export"),work::add);
            list.getListView().performItemClick(list.getListView().getChildAt(0),0,0);
            AlertDialog current=(AlertDialog)ShadowDialog.getLatestDialog();assertEquals(2,work.size());
            work.get(0).run();ShadowLooper.shadowMainLooper().idle();assertTrue(text(current).contains("Odczytywanie"));
            work.get(1).run();ShadowLooper.shadowMainLooper().idle();assertTrue(text(current).contains("390 / 390 / 390"));
            current.dismiss();
            ResearchSessionPicker.showHistory(host.get(),rows,()->"SYNTHETIC",f->fail("Unexpected export"),r->{throw new java.util.concurrent.RejectedExecutionException();})
                    .getListView().performItemClick(null,0,0);
            AlertDialog rejected=(AlertDialog)ShadowDialog.getLatestDialog();ShadowLooper.shadowMainLooper().idle();
            assertTrue(text(rejected).contains("odczyt historii jest niedostępny"));rejected.dismiss();
        }finally{host.pause().stop().destroy();}
        assertEquals(before,summary.session.toString());assertEquals(sha,RuntimeExecutionEvidence.sha256(archive));
        String target=System.getenv("F3_TEST_EVIDENCE");if(target!=null)Files.write(new File(target,"real-history-text.txt").toPath(),breakdown.getBytes(StandardCharsets.UTF_8));
    }
}
