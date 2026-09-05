package com.example.alpr_v1.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.AlprPackageManifest;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class ResearchExecutionConfigInstrumentedTest {
    private static final String HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void sessionFreezesExactConfigurationAndReportRoundTripsIt() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ResearchExecutionConfig config = configuration();
        ExperimentSession session = new ExperimentSession();
        assertTrue(session.start(
                config.experimentType,
                config.variant,
                TimerConfig.disabled(),
                ThermalConfig.disabled(),
                new ExperimentIdentity("SERIES_A", "STATIC_3", 7, "pilot", false, 4.0),
                config
        ));

        ResearchExecutionConfig unrelatedLaterSelection = new ResearchExecutionConfig(
                "runtime",
                "later-selection",
                RoiBudgetPolicy.TWO_ROI,
                RecognitionProfile.FAST,
                "auto",
                true,
                false,
                true,
                true,
                true,
                true,
                stage(ModelRole.VEHICLE, "mp-later", ModelRuntime.ONNX, 4, false),
                stage(ModelRole.PLATE, "mt-later", ModelRuntime.ONNX, 4, false),
                stage(ModelRole.CHARACTER, "mz-later", ModelRuntime.ONNX, 4, false)
        );
        assertEquals("later-selection", unrelatedLaterSelection.variant);
        assertSame(config, session.snapshot().frozenExecutionConfig);

        session.finish(ExperimentSession.CompletionReason.MANUAL);
        MetricsCollector metrics = new MetricsCollector();
        metrics.startMeasurementSession();
        metrics.setCrashMeasurement(true, 2);
        metrics.finishMeasurementSession();
        String reportText = metrics.createJsonReport(
                DeviceProfile.capture(context),
                new ModelRegistry(context),
                new AutoTuneManager(context),
                session.snapshot()
        );
        JSONObject report = new JSONObject(reportText);
        assertEquals(
                "unbundled-sha256-mt-fp32-sha256-mz-int8",
                report.getString("package_id")
        );
        assertFalse(report.has("package_version"));
        assertFalse(report.has("package_created_at"));
        assertFalse(report.has("package_source_sha256"));
        assertFalse(report.has("base_package_fingerprint"));
        assertEquals(12.0, report.getJSONObject("memory").getDouble("package_size_mb"), 0.0);
        JSONObject appBuild = report.getJSONObject("app_build");
        assertTrue(appBuild.has("git_dirty"));
        assertTrue(appBuild.has("git_dirty_available"));
        assertTrue(appBuild.has("source_state"));
        JSONObject experiment = report.getJSONObject("experiment");
        JSONObject effective = experiment.getJSONObject("effective_execution_config");

        assertEquals("runtime", experiment.getString("type"));
        assertEquals("controlled-cpu", experiment.getString("variant"));
        assertEquals("r1_one_roi", experiment.getString("effective_roi_budget_policy"));
        assertEquals("r1_one_roi", effective.getString("roi_budget_policy"));
        assertEquals("1920x1080", effective.getString("camera_requested_resolution"));
        assertFalse(effective.getJSONObject("feature_flags").getBoolean("lock"));
        assertFalse(effective.getJSONObject("feature_flags").getBoolean("autozoom"));
        assertStage(effective.getJSONObject("stages").getJSONObject("mp"), "mp-fp32", 1);
        assertStage(effective.getJSONObject("stages").getJSONObject("mt"), "mt-fp32", 2);
        assertStage(effective.getJSONObject("stages").getJSONObject("mz"), "mz-int8", 4);
        assertEquals(
                "mt-fp32",
                report.getJSONObject("model_refs").getJSONObject("plate").getString("model_id")
        );
        assertTrue(report.getJSONObject("model_refs").getJSONObject("plate")
                .getJSONObject("training").isNull("lineage_total_epochs"));
        assertEquals(2, report.getJSONObject("errors").getInt("crash_count"));
        assertTrue(report.getJSONObject("errors").getBoolean("crash_measurement_available"));
    }

    @Test
    public void basePackageMetadataAndSizeComeFromFrozenConfiguration() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long frozenBytes = 8L * 1024L * 1024L;
        InstalledAlprPackage frozenPackage = new InstalledAlprPackage(
                AlprPackageManifest.parse(packageManifest().toString()),
                new File("unused-package"),
                "frozen-package-fingerprint",
                frozenBytes,
                HASH,
                null,
                null,
                null
        );
        ResearchExecutionConfig config = new ResearchExecutionConfig(
                "runtime", "base-package", RoiBudgetPolicy.ONE_ROI,
                RecognitionProfile.ACCURATE, "1920x1080", false, false,
                true, true, true, true,
                stage(ModelRole.VEHICLE, "mp-fp32", ModelRuntime.TFLITE, 1, false),
                stage(ModelRole.PLATE, "mt-fp32", ModelRuntime.TFLITE, 2, false),
                stage(ModelRole.CHARACTER, "mz-int8", ModelRuntime.TFLITE, 4, false),
                frozenPackage,
                false,
                frozenBytes
        );
        ExperimentSession session = new ExperimentSession();
        assertTrue(session.start(
                config.experimentType, config.variant, TimerConfig.disabled(),
                ThermalConfig.disabled(),
                new ExperimentIdentity("SERIES_P1", "STATIC_1", 1, "", false, 0.0),
                config
        ));
        session.finish(ExperimentSession.CompletionReason.MANUAL);

        MetricsCollector metrics = new MetricsCollector();
        metrics.startMeasurementSession();
        metrics.finishMeasurementSession();
        JSONObject report = new JSONObject(metrics.createJsonReport(
                DeviceProfile.capture(context),
                new ModelRegistry(context),
                new AutoTuneManager(context),
                session.snapshot()
        ));

        assertEquals("frozen-package", report.getString("package_id"));
        assertEquals("1", report.getString("package_version"));
        assertEquals("2026-09-05T10:00:00Z", report.getString("package_created_at"));
        assertEquals(HASH, report.getString("package_source_sha256"));
        assertEquals(
                "frozen-package-fingerprint",
                report.getString("base_package_fingerprint")
        );
        assertEquals(8.0, report.getJSONObject("memory").getDouble("package_size_mb"), 0.0);
    }

    private static ResearchExecutionConfig configuration() {
        return new ResearchExecutionConfig(
                "runtime",
                "controlled-cpu",
                RoiBudgetPolicy.ONE_ROI,
                RecognitionProfile.ACCURATE,
                "1920x1080",
                false,
                false,
                true,
                true,
                true,
                true,
                stage(ModelRole.VEHICLE, "mp-fp32", ModelRuntime.TFLITE, 1, false),
                stage(ModelRole.PLATE, "mt-fp32", ModelRuntime.TFLITE, 2, false),
                stage(ModelRole.CHARACTER, "mz-int8", ModelRuntime.TFLITE, 4, false),
                12L * 1024L * 1024L
        );
    }

    private static ResearchStageExecutionConfig stage(
            ModelRole role,
            String modelId,
            ModelRuntime runtime,
            int threads,
            boolean gpu
    ) {
        return new ResearchStageExecutionConfig(
                role,
                true,
                modelId,
                "sha256-" + modelId,
                runtime.wireName() + "-variant",
                runtime,
                modelId.contains("int8") ? "int8" : "fp32",
                threads,
                gpu,
                gpu ? "gpu" : "cpu",
                640,
                640,
                3,
                "nhwc",
                "rgb",
                "float32"
        );
    }

    private static void assertStage(JSONObject stage, String modelId, int threads)
            throws Exception {
        assertTrue(stage.getBoolean("enabled"));
        assertEquals(modelId, stage.getString("model_id"));
        assertEquals("sha256-" + modelId, stage.getString("model_fingerprint"));
        assertEquals(threads, stage.getInt("cpu_threads"));
        assertFalse(stage.getBoolean("gpu"));
        assertEquals(640, stage.getJSONObject("input").getInt("width"));
    }

    private static JSONObject packageManifest() throws Exception {
        JSONObject models = new JSONObject()
                .put("plate", packageModel("plate", "pose"))
                .put("character", packageModel("character", "detect"));
        JSONArray pipeline = new JSONArray()
                .put(new JSONObject().put("stage", "plate_detection")
                        .put("model", "plate").put("role", "plate").put("task", "pose"))
                .put(new JSONObject().put("stage", "plate_rectification")
                        .put("implementation", "android_alpr_rectifier"))
                .put(new JSONObject().put("stage", "character_detection")
                        .put("model", "character").put("role", "character")
                        .put("task", "detect"))
                .put(new JSONObject().put("stage", "sequence_assembly")
                        .put("implementation", "android_alpr_sequence_decoder"));
        return new JSONObject()
                .put("schema", AlprPackageManifest.SCHEMA)
                .put("kind", AlprPackageManifest.KIND)
                .put("package_id", "frozen-package")
                .put("version", "1")
                .put("created_at", "2026-09-05T10:00:00Z")
                .put("models", models)
                .put("pipeline", pipeline);
    }

    private static JSONObject packageModel(String role, String task) throws Exception {
        String packageFile = "models/" + role + "/model.alprmodel";
        String manifestFile = "models/" + role + "/manifest.json";
        return new JSONObject()
                .put("role", role)
                .put("task", task)
                .put("model_id", role + "-frozen")
                .put("schema", "alpr.model.v1")
                .put("package_file", packageFile)
                .put("manifest_file", manifestFile)
                .put("sha256", new JSONObject()
                        .put(packageFile, HASH)
                        .put(manifestFile, HASH));
    }
}
