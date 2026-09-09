package com.example.alpr_v1.pipeline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.experiment.ResearchExecutionConfig;
import com.example.alpr_v1.experiment.ResearchStageExecutionConfig;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in replay using installed MP/MT/MZ and the original overlapping-car photograph. */
@RunWith(AndroidJUnit4.class)
public class DynamicMtMzInstrumentedTest {
    @Test public void overlappingCarsReachMzAndPublishPlateBoxes() throws Exception {
        org.junit.Assume.assumeTrue("Requires phone models and dynamic-mz-source.jpg",
                "true".equals(InstrumentationRegistry.getArguments().getString("liveDynamicMz")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ModelRegistry registry = new ModelRegistry(context);
        AutoTuneManager auto = new AutoTuneManager(context);
        ResearchExecutionConfig config = new ResearchExecutionConfig("quality", "R2", RoiBudgetPolicy.TWO_ROI,
                RecognitionProfile.BALANCED, "auto", false, false, true, true, true, false,
                stage(registry, ModelRole.VEHICLE, "onnx-fp32"),
                stage(registry, ModelRole.PLATE, "tflite-int8"),
                stage(registry, ModelRole.CHARACTER, "onnx-int8"));
        Bitmap screen = BitmapFactory.decodeFile(new File(context.getExternalFilesDir(null), "dynamic-mz-source.jpg").getPath());
        assertNotNull(screen);
        Bitmap frame = Bitmap.createScaledBitmap(screen, 960, Math.round(960f * screen.getHeight() / screen.getWidth()), true);
        if (screen != frame) screen.recycle();
        try (MobileAlprEngine engine = new MobileAlprEngine(registry, auto, RoiBudgetPolicy.TWO_ROI,
                MtExecutionPolicy.LEGACY_BURST, MtFallbackPolicy.SAME_CYCLE, VehicleTrackingPolicy.TRACKED_MP,
                config, new VehicleTrackingCoordinator())) {
            engine.setStaticSceneMode(false);
            long mz = 0;
            int[] visible = {0};
            for (int index = 1; index <= 3 && mz == 0; index++) {
                InferenceTrace trace = new InferenceTrace(index);
                PipelineResult result = engine.run(frame, trace, new ContinuityStamp(1, 0, 0, index),
                        (items, width, height, stamp) -> {
                            for (com.example.alpr_v1.ui.OverlayItem item : items)
                                if (item.kind == com.example.alpr_v1.ui.OverlayItem.Kind.PLATE) visible[0]++;
                        }, () -> false);
                try {
                    mz += trace.counters().getOrDefault("mz_runs", 0L);
                    android.util.Log.i("ALPR_DYNAMIC_MZ_TEST", trace.toJson().toString());
                    if (mz > 0) assertTrue(result.plateObservations.stream()
                            .anyMatch(observation -> observation.freshMzAttempted));
                } finally { result.close(); }
            }
            assertTrue("Valid owned MT detection must actually invoke MZ", mz > 0);
            assertTrue("MT callback must publish plate geometry", visible[0] > 0);
        } finally { frame.recycle(); }
    }

    private static ResearchStageExecutionConfig stage(ModelRegistry registry, ModelRole role, String variantId) {
        InstalledModel model = registry.getActive(role);
        assertNotNull("Missing " + role, model);
        ModelVariant variant = model.manifest().variants().stream()
                .filter(item -> item.id().equals(variantId)).findFirst().orElseThrow(AssertionError::new);
        return ResearchStageExecutionConfig.enabled(role, model, variant,
                new ExecutionProfile(variant.runtime(), 2, false), registry);
    }
}
