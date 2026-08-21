package com.example.alpr_v1.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.model.ModelRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class ResearchArchiveInstrumentedTest {
    @Test
    public void thesisBundleContainsManifestTexTablesAndBibtex() throws Exception {
        String report = "{"
                + "\"schema\":\"alpr.mobile_benchmark_report.v1\","
                + "\"report_id\":\"test-report\","
                + "\"package_id\":\"mt-mz-test\","
                + "\"variant_id\":\"cpu-fp32\","
                + "\"measured_at\":\"2026-08-20T12:00:00Z\","
                + "\"device\":{},\"capture\":{},\"execution\":{},\"latency\":{},"
                + "\"quality\":{\"available\":false}"
                + "}";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ResearchArchive.writeThesisBundle(
                output,
                report,
                "frame_id,total_ms\n1,10.0\n",
                java.util.Collections.emptyList()
        );

        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("summary.tex"));
        assertTrue(entries.containsKey("references.bib"));
        assertTrue(entries.containsKey("tables/configuration_mt_mz.tex"));
        assertTrue(entries.containsKey("tables/mobile_quality.tex"));
        assertTrue(entries.containsKey("tables/mobile_latency.tex"));
        JSONObject manifest = new JSONObject(new String(
                entries.get("manifest.json"), StandardCharsets.UTF_8
        ));
        assertEquals(ResearchArchive.THESIS_SCHEMA, manifest.getString("schema"));
        assertEquals(0, manifest.getInt("crop_count"));
        assertTrue(manifest.getJSONObject("entry_sha256").has("summary.tex"));
    }

    @Test
    public void reportComputesExactMatchAndCerPerUniqueTrack() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MetricsCollector metrics = new MetricsCollector();
        metrics.startCropSession("quality-test", 10);
        CapturedPlateItem exact = crop("c1", 1L, "KR12345");
        exact.verificationStatus = CapturedPlateItem.VerificationStatus.ACCEPTED;
        exact.groundTruthText = "KR12345";
        exact.verifiedAtMillis = 1L;
        metrics.recordCapturedCrop(exact);
        CapturedPlateItem corrected = crop("c2", 2L, "KR1234S");
        corrected.verificationStatus = CapturedPlateItem.VerificationStatus.CORRECTED;
        corrected.groundTruthText = "KR12345";
        corrected.verifiedAtMillis = 2L;
        metrics.recordCapturedCrop(corrected);
        CapturedPlateItem duplicateTrack = crop("c3", 2L, "BAD");
        metrics.recordCapturedCrop(duplicateTrack);

        String report = metrics.createJsonReport(
                DeviceProfile.capture(context),
                new ModelRegistry(context),
                new AutoTuneManager(context)
        );
        JSONObject quality = new JSONObject(report).getJSONObject("quality");

        assertTrue(quality.getBoolean("available"));
        assertEquals(2, quality.getInt("ground_truth_samples"));
        assertEquals(2, quality.getInt("unit_count"));
        assertEquals(0.5, quality.getDouble("exact_match_rate"), 0.000001);
        assertEquals(1.0 / 14.0, quality.getDouble("cer"), 0.000001);
        exact.recycle();
        corrected.recycle();
        duplicateTrack.recycle();
    }

    private static CapturedPlateItem crop(String captureId, long trackId, String text) {
        return new CapturedPlateItem(
                captureId,
                "quality-test",
                trackId,
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                text,
                0.9,
                0.8,
                true,
                Collections.emptyList(),
                System.currentTimeMillis(),
                android.os.SystemClock.elapsedRealtimeNanos(),
                0.7f,
                null
        );
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream value = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read > 0) value.write(buffer, 0, read);
                }
                entries.put(entry.getName(), value.toByteArray());
            }
        }
        return entries;
    }
}
