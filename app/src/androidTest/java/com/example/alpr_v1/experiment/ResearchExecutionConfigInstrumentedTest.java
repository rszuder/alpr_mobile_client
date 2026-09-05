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
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ResearchExecutionConfigInstrumentedTest {
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
                stage(ModelRole.CHARACTER, "mz-int8", ModelRuntime.TFLITE, 4, false)
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
}
