package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class ScanAcquisitionReportInstrumentedTest {
    @Test
    public void reportPreservesFinalizedAndSuppressedAcquisitionRecords() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ScanAcquisitionFinalizer finalizer = new ScanAcquisitionFinalizer();
        finalizer.finalizeAcquisition(
                9L, 91L, 1L, 101L, "KR 12345",
                0.91, 3, 1_000L, 500L, 2_000L, "crop-best-1"
        );
        finalizer.finalizeAcquisition(
                9L, 92L, 2L, 102L, "kr-12345",
                0.88, 2, 3_000L, 2_500L, 4_000L, "crop-best-2"
        );
        ScanAcquisitionStats stats = new ScanAcquisitionStats(
                2, 2, 2, 0, 0, 2,
                2, 1, 1,
                0.5, 0.0015, 0.0015, 1.0,
                0.0, 0.0, 0.0015, 0.0015, 1.0, 1.0
        );
        ScanAcquisitionSnapshot snapshot = new ScanAcquisitionSnapshot(
                9L,
                ScanRunState.STOPPED,
                60_000_000_000L,
                60_000_000_000L,
                AcquisitionQueueSnapshot.empty(0L),
                0L,
                0L,
                null,
                0,
                0,
                0L,
                0L,
                AcquisitionDirective.none(0L, 9L),
                false,
                null,
                stats,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap(),
                finalizer.records()
        );

        MetricsCollector metrics = new MetricsCollector();
        metrics.startMeasurementSession();
        metrics.updateScanAcquisition(snapshot);
        metrics.finishMeasurementSession();
        JSONObject report = new JSONObject(metrics.createJsonReport(
                DeviceProfile.capture(context),
                new ModelRegistry(context),
                new AutoTuneManager(context)
        ));
        JSONObject scan = report.getJSONObject("scan_acquisition");
        JSONArray records = scan.getJSONArray("acquisition_records");

        assertEquals(2, scan.getInt("acquisitions_finalized"));
        assertEquals(1, scan.getInt("unique_plates_saved"));
        assertEquals(1, scan.getInt("duplicate_acquisitions_suppressed"));
        assertEquals(0.5, scan.getDouble("duplicate_capture_rate"), 0.000001);
        assertEquals(1.0, scan.getDouble("unique_plates_per_wall_minute"), 0.000001);
        assertEquals(2, records.length());
        assertTrue(records.getJSONObject(0).getBoolean("unique_saved"));
        assertFalse(records.getJSONObject(1).getBoolean("unique_saved"));
        assertEquals(
                records.getJSONObject(0).getString("record_id"),
                records.getJSONObject(1).getString("duplicate_of_record_id")
        );
        assertEquals("crop-best-1", records.getJSONObject(0).getString("best_crop_id"));
    }
}
