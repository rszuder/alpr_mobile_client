package com.example.alpr_v1.model;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.inference.InferenceRunResult;
import com.example.alpr_v1.inference.MzSplitLtrbScoresInt8TensorValidator;
import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.inference.TfliteBackend;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Yolo26SplitLtrbScoresInt8Decoder;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * T2-I-12: prove that the exact T2-I-10 research ZIP, as parsed by the Android
 * importer, can run through the real TfliteBackend and existing split decoder.
 *
 * NEVER calls importPackage(), changes ModelRegistry, publishes a mobile
 * package, samples final_test, or certifies mobile INT8 arithmetic/GT quality.
 * The model and precision report are extracted ONLY into a fresh private cache
 * directory and are always deleted. Release/reusable import stays BLOCKED.
 */
@RunWith(AndroidJUnit4.class)
public final class MzCleanResearchPackageRuntimeInstrumentedTest {
    private static final String PACKAGE_SHA =
            "084d37e12727577e4e1c123d4ee0e800a2a00c8642d41818e851869da52870cd";
    private static final String MODEL_MEMBER = MzCleanResearchPackageValidator.MODEL_MEMBER;
    private static final String REPORT_MEMBER = MzCleanResearchPackageValidator.REPORT_MEMBER;

    @Test public void exactT2I10PackageRunsThroughBackendAndDecoderWithoutInstallation() throws Exception {
        assertTrue("Research backend is enabled only for debug", BuildConfig.DEBUG);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File archive = new File(context.getFilesDir(),
                "f4_mz_day_int8_first_invoke/MZ_DAY_CLEAN_INT8_RESEARCH_ONLY.alprmodel");
        assertTrue("Stage T2-I-10 ZIP using the existing ADB transfer first", archive.isFile());
        assertEquals("T2-I-10 package SHA", PACKAGE_SHA, Hashing.sha256(archive));

        // Exercise the real Android read-only importer validator, not a hand-
        // written substitute manifest. No model is registered/installed.
        File prohibitedRegistry = new File(context.getCacheDir(),
                "t2i12-no-registry-" + UUID.randomUUID());
        assertFalse(prohibitedRegistry.exists());
        ModelPackageImporter importer = new ModelPackageImporter(context, prohibitedRegistry);
        ModelManifest manifest = importer.validatePackage(archive);
        assertEquals("mz-day-clean-int8-research-only", manifest.modelId());
        assertEquals("raw_split_ltrb_scores_v1", manifest.output().outputFormat());
        assertEquals(36, manifest.labels().size());
        assertEquals("Q", manifest.labels().get(26));
        assertFalse("Validation must not create a model registry", prohibitedRegistry.exists());

        File sandbox = new File(context.getCacheDir(), "t2i12-package-backend-" + UUID.randomUUID());
        try {
            assertTrue("Must start in an empty isolated cache path", sandbox.mkdir());
            // Unpack ONLY the pinned model and its SHA-bound graph report.
            // Both names are literals already verified by validatePackage().
            try (ZipFile zip = new ZipFile(archive)) {
                extractExactMember(zip, sandbox, MODEL_MEMBER,
                        MzCleanResearchPackageValidator.MODEL_BYTES);
                extractExactMember(zip, sandbox, REPORT_MEMBER, -1L);
            }
            File extractedModel = new File(sandbox, MODEL_MEMBER);
            File extractedReport = new File(sandbox, REPORT_MEMBER);
            assertEquals(MzCleanResearchPackageValidator.MODEL_SHA, Hashing.sha256(extractedModel));
            JSONObject raw = new JSONObject(manifest.rawJson());
            JSONObject graphProof = raw.getJSONArray("variants").getJSONObject(0)
                    .getJSONObject("graph_precision");
            assertEquals(REPORT_MEMBER, graphProof.getString("report_file"));
            assertEquals(graphProof.getString("report_sha256"), Hashing.sha256(extractedReport));

            InstalledModel isolated = new InstalledModel(manifest, sandbox,
                    "t2i12-isolated-debug-cpu-never-installed");
            ModelVariant variant = manifest.variants().get(0);
            try (TfliteBackend backend = new TfliteBackend(isolated, variant,
                    ExecutionProfile.tfliteCpu(1))) {
                TensorInfo inputInfo = backend.inputInfo();
                Map<Integer, TensorInfo> outputInfo = backend.outputInfo();
                MzSplitLtrbScoresInt8TensorValidator.validate(inputInfo, outputInfo);
                int pixel = Math.round((114f / 255f) / inputInfo.quantizationScale)
                        + inputInfo.quantizationZeroPoint;
                assertTrue("Padding value must be representable as signed INT8",
                        pixel >= -128 && pixel <= 127);
                ByteBuffer sample = ByteBuffer.allocateDirect(inputInfo.byteSize)
                        .order(ByteOrder.nativeOrder());
                for (int i = 0; i < inputInfo.byteSize; i++) sample.put((byte) pixel);
                sample.rewind();
                InferenceRunResult result = backend.run(sample);
                assertEquals(2, result.outputs().size());
                assertTrue(result.outputs().containsKey(0));
                assertTrue(result.outputs().containsKey(1));
                TensorInfo geometry = result.tensorInfo().get(0);
                TensorInfo scores = result.tensorInfo().get(1);
                List<Detection> direct = Yolo26SplitLtrbScoresInt8Decoder.decode(
                        result.outputs().get(0), geometry.shape,
                        geometry.quantizationScale, geometry.quantizationZeroPoint,
                        result.outputs().get(1), scores.shape,
                        scores.quantizationScale, scores.quantizationZeroPoint,
                        .10f, true, true, .45f);
                assertNotNull(direct);
                assertTrue(direct.size() <= 300);
                for (Detection hit : direct) {
                    assertTrue(hit.classId >= 0 && hit.classId < 36);
                    assertTrue(Float.isFinite(hit.confidence));
                }
                // Pipeline is package-private; use reflection rather than
                // changing its production visibility or reimplementing routing.
                Class<?> pipeline = Class.forName("com.example.alpr_v1.pipeline.MobileAlprEngine");
                Method dispatch = pipeline.getDeclaredMethod("decodeSplitMZ",
                        InferenceRunResult.class, ModelInputSpec.class, ModelOutputSpec.class, float.class);
                dispatch.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Detection> routed = (List<Detection>) dispatch.invoke(null, result,
                        variant.input(manifest.input()), variant.output(manifest.output()), .10f);
                assertEquals(direct.size(), routed.size());
                for (int i = 0; i < direct.size(); i++) {
                    Detection a = direct.get(i), b = routed.get(i);
                    assertEquals(a.classId, b.classId);
                    assertEquals(a.confidence, b.confidence, 1e-6f);
                    assertEquals(a.left, b.left, 1e-5f);
                    assertEquals(a.top, b.top, 1e-5f);
                    assertEquals(a.right, b.right, 1e-5f);
                    assertEquals(a.bottom, b.bottom, 1e-5f);
                }
                JSONObject execution = backend.executionEvidence();
                assertEquals("MATCH", execution.optString("contract_validation"));
                assertEquals("RESEARCH_DEBUG_CPU_PINNED_SHA_ONLY",
                        execution.optString("mz_split_int8_scope"));
                assertEquals(MzCleanResearchPackageValidator.MODEL_SHA,
                        execution.optString("model_sha256"));
                assertEquals(MODEL_MEMBER, execution.optString("model_file"));
                assertEquals("INT8", execution.optString("static_graph_classification"));
                assertEquals("ATTACHED_REPORT_HASH_BOUND",
                        execution.optString("static_graph_evidence_status"));
                assertTrue(execution.optBoolean("invoke_observed", false));
                assertFalse(execution.optBoolean("mz_split_int8_android_import_approved", true));
                assertFalse(execution.optBoolean("int8_execution_proven", true));
                assertEquals("NOT_RUN", execution.optString("mz_split_int8_final_test"));
            }
            assertFalse("The importer cannot write to an isolated registry", prohibitedRegistry.exists());
        } finally {
            // Delete even on assertion/invoke failures. Never delete source ZIP.
            ModelPackageImporter.deleteRecursively(sandbox.toPath());
        }
        assertFalse("Extracted research model must be removed after the test", sandbox.exists());
        assertEquals("Source archive must remain unchanged", PACKAGE_SHA, Hashing.sha256(archive));
    }

    private static void extractExactMember(ZipFile zip, File sandbox, String member,
                                            long requiredSize) throws Exception {
        ZipEntry entry = zip.getEntry(member);
        assertNotNull("Required verified package member missing: " + member, entry);
        assertFalse("Expected a regular ZIP member: " + member, entry.isDirectory());
        if (requiredSize > 0L) {
            assertEquals("Model bytes must be frozen", requiredSize, entry.getSize());
        } else {
            assertTrue("Precision report has an invalid byte length",
                    entry.getSize() > 0L && entry.getSize() <= 4 * 1024 * 1024L);
        }
        Path destination = new File(sandbox, member).toPath();
        Files.createDirectories(destination.getParent());
        try (InputStream input = zip.getInputStream(entry)) {
            Files.copy(input, destination); // fails if already exists: no overwrite
        }
        assertEquals(entry.getSize(), Files.size(destination));
    }
}
