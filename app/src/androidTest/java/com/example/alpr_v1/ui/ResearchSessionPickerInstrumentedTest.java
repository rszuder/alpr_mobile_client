package com.example.alpr_v1.ui;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.ResearchSessionSummary;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ResearchSessionPickerInstrumentedTest {
    @Test public void durationUsesMonotonicClockAndConfigurationUsesFrozenExecution() throws Exception {
        JSONObject json=metadata("2026-09-08T15:17:18Z",69_005);
        json.put("finished_at","2026-09-09T15:17:18Z"); // Wall-clock change must not turn 69s into a day.
        json.put("variant","r2_two_roi");
        ResearchSessionSummary item=new ResearchSessionSummary(new File("example.alprsession"),json);
        assertEquals("08.09.2026 · 17:17:18",item.dateText(ZoneId.of("Europe/Warsaw")));
        assertEquals("1 min 09 s",item.durationText());assertFalse(item.approximateDuration);
        assertEquals("r0_full_frame",item.value("variant"));assertTrue(item.complete);
    }
    @Test public void recoveredInterruptedSessionDoesNotUseRecoveryDateAsStopTime() throws Exception {
        JSONObject json=metadata("2026-09-08T15:17:18Z",15_000);
        json.put("collection_complete",false).put("completion_reason","process_interrupted");
        json.remove("finished_elapsed_nanos");json.put("finished_at","2026-09-09T15:17:18Z");
        ResearchSessionSummary item=new ResearchSessionSummary(new File("partial.alprsession"),json);
        assertEquals("Czas nieustalony",item.durationText());assertFalse(item.complete);
    }
    @Test public void legacyWallDurationIsExplicitlyApproximateAndMissingMetadataIsHonest() throws Exception {
        JSONObject json=metadata("2026-09-08T15:17:18Z",15_000);
        json.remove("finished_elapsed_nanos");json.put("finished_at","2026-09-08T15:18:28Z");
        assertEquals("≈ 1 min 10 s",new ResearchSessionSummary(new File("old.alprsession"),json).durationText());
        ResearchSessionSummary missing=new ResearchSessionSummary(new File("broken.alprsession"),null);
        assertEquals("Data nieznana",missing.dateText(ZoneId.of("UTC")));
        assertEquals("Czas nieustalony",missing.durationText());assertFalse(missing.metadataAvailable);
    }
    @Test public void readsArchiveFallbackAndSortsBySessionStartWithoutWritingFiles() throws Exception {
        android.content.Context context=ApplicationProvider.getApplicationContext();
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"session-picker-").toFile();
        File finalDir=new File(root,"final");assertTrue(finalDir.mkdir());
        File archive=new File(finalDir,"long_technical_archive_name.alprsession");
        File older=new File(finalDir,"a_older_session.alprsession");
        File sidecar=new File(root,"session.json");Files.write(sidecar.toPath(),"{".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
                zip.putNextEntry(new ZipEntry("session.json"));
                zip.write(metadata("2026-09-08T15:17:18Z",15_000).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            byte[] before=Files.readAllBytes(archive.toPath());
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(older.toPath()))) {
                zip.putNextEntry(new ZipEntry("session.json"));
                zip.write(metadata("2026-09-08T14:17:18Z",20_000).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            // The older session is first in the input and has a newer filesystem timestamp.
            assertTrue(archive.setLastModified(1_000));assertTrue(older.setLastModified(2_000));
            java.util.List<ResearchSessionSummary> items=ResearchSessionSummary.load(Arrays.asList(older,archive));
            ResearchSessionSummary item=items.get(0);
            assertEquals("15 s",item.durationText());assertEquals(archive,item.archive);
            assertEquals(older,items.get(1).archive);
            assertArrayEquals(before,Files.readAllBytes(archive.toPath()));
            assertEquals("{",new String(Files.readAllBytes(sidecar.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        } finally {Files.deleteIfExists(archive.toPath());Files.deleteIfExists(older.toPath());Files.deleteIfExists(sidecar.toPath());Files.deleteIfExists(finalDir.toPath());Files.deleteIfExists(root.toPath());}
    }
    @Test public void rowsWrapMetadataAndSelectionRetainsExactArchive() throws Exception {
        ResearchSessionSummary first=new ResearchSessionSummary(new File("first.alprsession"),metadata("2026-09-08T15:17:18Z",15_000));
        ResearchSessionSummary second=new ResearchSessionSummary(new File("second.alprsession"),metadata("2026-09-08T14:17:18Z",69_000));
        AtomicReference<File> selected=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                ResearchSessionExportAdapter adapter=new ResearchSessionExportAdapter(Arrays.asList(first,second));
                View row=adapter.getView(0,null,new FrameLayout(activity));
                row.measure(View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                row.layout(0,0,560,row.getMeasuredHeight());
                assertEquals("Budżet ROI",((TextView)row.findViewById(R.id.session_export_title)).getText().toString());
                assertTrue(((TextView)row.findViewById(R.id.session_export_configuration)).getText().toString().contains("R0 · pełna klatka"));
                assertTrue(((TextView)row.findViewById(R.id.session_export_execution)).getText().toString().contains("LiteRT FP32"));
                for(int id:new int[]{R.id.session_export_date,R.id.session_export_configuration,R.id.session_export_execution,R.id.session_export_series}) {
                    TextView text=row.findViewById(id);
                    for(int line=0;line<text.getLineCount();line++)assertEquals(0,text.getLayout().getEllipsisCount(line));
                }
                AlertDialog dialog=ResearchSessionPicker.show(activity,Arrays.asList(first,second),selected::set);
                dialog.getListView().performItemClick(adapter.getView(1,null,dialog.getListView()),1,1);
                assertEquals(second.archive,selected.get());dialog.dismiss();
            });
        }
    }
    private static JSONObject metadata(String date,long duration) throws Exception {
        JSONObject stage=new JSONObject().put("enabled",true).put("runtime","tflite").put("precision","fp32")
                .put("cpu_threads",2).put("input",new JSONObject().put("width",512).put("height",512));
        return new JSONObject().put("started_at",date).put("started_elapsed_nanos",1_000_000L)
                .put("finished_elapsed_nanos",1_000_000L+duration*1_000_000L).put("collection_complete",true)
                .put("series_id","TEST-SERIA-O-DŁUGIEJ-NAZWIE").put("scenario_id","single_static").put("replicate_index",3)
                .put("execution",new JSONObject().put("experiment_type","roi_budget").put("variant","r0_full_frame")
                        .put("roi_budget_policy","r0_full_frame").put("analysis_mode","static")
                        .put("camera_requested_resolution","auto").put("recognition_profile","balanced")
                        .put("stages",new JSONObject().put("mt",stage)));
    }
}
