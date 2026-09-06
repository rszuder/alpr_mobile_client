package com.example.alpr_v1.inference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class NcnnBackendInstrumentedTest {
    @Test
    public void executesNchwInputAndReturnsTwoDimensionalOutput() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "ncnn-jni-test");
        deleteRecursively(root);
        assertTrue(root.mkdirs() || root.isDirectory());
        File param = new File(root, "model.param");
        File weights = new File(root, "model.bin");
        Files.write(
                param.toPath(),
                ("7767517\n"
                        + "2 2\n"
                        + "Input in0 0 1 in0\n"
                        + "Reshape reshape 1 1 in0 out0 0=12 1=5\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        Files.write(weights.toPath(), new byte[0]);

        ModelManifest manifest = ModelManifest.parse(manifest().toString());
        InstalledModel model = new InstalledModel(manifest, root, "ncnn-jni-test");
        ModelVariant variant = manifest.variants().get(0);

        assertTrue(NcnnBackend.isAvailable());
        try (NcnnBackend backend = new NcnnBackend(
                model,
                variant,
                new ExecutionProfile(ModelRuntime.NCNN, 1, false)
        )) {
            assertEquals(60 * Float.BYTES, backend.inputByteSize());
            ByteBuffer input = ByteBuffer.allocateDirect(backend.inputByteSize())
                    .order(ByteOrder.nativeOrder());
            float[] expected = new float[60];
            for (int index = 0; index < expected.length; index++) {
                expected[index] = index - 3.5f;
                input.putFloat(expected[index]);
            }
            input.rewind();

            InferenceRunResult result = backend.run(input);
            TensorInfo info = result.tensorInfo().get(0);
            assertArrayEquals(new int[]{1, 5, 12}, info.shape);
            assertArrayEquals(
                    expected,
                    TensorDataReader.toFloatArray(result.outputs().get(0), info),
                    0f
            );
            assertEquals("NCNN/CPU", backend.runtimeName());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void concurrentSessionsDoNotRaceOpenMpInitialization() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "ncnn-concurrency-test");
        deleteRecursively(root);
        assertTrue(root.mkdirs() || root.isDirectory());
        File param = new File(root, "model.param");
        File weights = new File(root, "model.bin");
        Files.write(
                param.toPath(),
                ("7767517\n"
                        + "2 2\n"
                        + "Input in0 0 1 in0\n"
                        + "Reshape reshape 1 1 in0 out0 0=12 1=5\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        Files.write(weights.toPath(), new byte[0]);

        ModelManifest manifest = ModelManifest.parse(manifest().toString());
        InstalledModel model = new InstalledModel(manifest, root, "ncnn-concurrency-test");
        ModelVariant variant = manifest.variants().get(0);
        int workers = 6;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<int[]>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try (NcnnBackend backend = new NcnnBackend(
                            model,
                            variant,
                            new ExecutionProfile(ModelRuntime.NCNN, 2, false)
                    )) {
                        ByteBuffer input = ByteBuffer.allocateDirect(backend.inputByteSize())
                                .order(ByteOrder.nativeOrder());
                        InferenceRunResult result = backend.run(input);
                        return result.tensorInfo().get(0).shape;
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<int[]> future : futures) {
                assertArrayEquals(
                        new int[]{1, 5, 12},
                        future.get(30, TimeUnit.SECONDS)
                );
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            deleteRecursively(root);
        }
    }

    private static JSONObject manifest() throws Exception {
        String param = "model.param";
        String bin = "model.bin";
        JSONObject input = new JSONObject()
                .put("width", 4)
                .put("height", 5)
                .put("channels", 3)
                .put("layout", "NCHW")
                .put("color", "RGB")
                .put("data_type", "FLOAT32")
                .put("scale", 1.0)
                .put("offset", 0.0);
        JSONObject output = new JSONObject()
                .put("decoder", "ultralytics_detect_raw_v1")
                .put("output_format", "raw_yolo")
                .put("box_format", "xywh")
                .put("nms_required", true)
                .put("class_count", 1)
                .put("keypoint_count", 0)
                .put("tensor_layout", "channels_first")
                .put("nms_in_graph", false);
        JSONObject variant = new JSONObject()
                .put("id", "ncnn-fp32")
                .put("runtime", "ncnn")
                .put("precision", "fp32")
                .put("files", new JSONArray().put(param).put(bin))
                .put("sha256", new JSONObject()
                        .put(param, repeat("a", 64))
                        .put(bin, repeat("b", 64)));
        return new JSONObject()
                .put("schema", ModelManifest.SCHEMA)
                .put("model_id", "ncnn-jni-test")
                .put("name", "NCNN JNI test")
                .put("version", "1")
                .put("role", "character")
                .put("task", "detect")
                .put("input", input)
                .put("output", output)
                .put("labels", new JSONArray().put("x"))
                .put("variants", new JSONArray().put(variant));
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete() && file.exists()) {
            throw new AssertionError("Nie można usunąć fixture NCNN: " + file);
        }
    }
}
