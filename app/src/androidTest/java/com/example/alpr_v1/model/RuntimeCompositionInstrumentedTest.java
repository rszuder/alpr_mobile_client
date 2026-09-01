package com.example.alpr_v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.autotune.AutoTuneManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public final class RuntimeCompositionInstrumentedTest {
    private Context context;
    private Context targetContext;
    private File isolatedFiles;
    private File isolatedExternalFiles;
    private String suffix;

    @Before
    public void setUp() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        isolatedFiles = new File(targetContext.getCacheDir(), "composition-fixture-" + suffix);
        File androidProvidedRoot = targetContext.getExternalFilesDir(null);
        assertNotNull(androidProvidedRoot);
        isolatedExternalFiles = new File(
                androidProvidedRoot,
                "composition-external-fixture-" + suffix
        );
        assertTrue(isolatedFiles.mkdirs() || isolatedFiles.isDirectory());
        assertTrue(isolatedExternalFiles.mkdirs() || isolatedExternalFiles.isDirectory());
        context = new ContextWrapper(targetContext) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public File getFilesDir() {
                return isolatedFiles;
            }

            @Override
            public File getExternalFilesDir(String type) {
                return isolatedExternalFiles;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return targetContext.getSharedPreferences("test-" + suffix + "-" + name, mode);
            }
        };
        context.getSharedPreferences("model_registry", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("autotune", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        context.getSharedPreferences("model_registry", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("autotune", Context.MODE_PRIVATE).edit().clear().commit();
        deleteRecursively(isolatedFiles);
        deleteRecursively(isolatedExternalFiles);
    }

    @Test
    public void replacementPreservesBasePackageAndCanBeRestored() throws Exception {
        String basePlateStorage = "plate-base-" + suffix;
        String replacementPlateStorage = "plate-replacement-" + suffix;
        String characterStorage = "character-base-" + suffix;
        writeModel(ModelRole.PLATE, basePlateStorage, "plate-base-" + suffix);
        writeModel(ModelRole.PLATE, replacementPlateStorage, "plate-replacement-" + suffix);
        writeModel(ModelRole.CHARACTER, characterStorage, "character-base-" + suffix);
        String packageStorage = writePackage(basePlateStorage, characterStorage);

        ModelRegistry registry = new ModelRegistry(context);
        assertEquals(
                new File(isolatedExternalFiles, "models").getCanonicalPath(),
                registry.modelsRoot().getCanonicalPath()
        );
        assertEquals(2, registry.getInstalled(ModelRole.PLATE).size());
        assertEquals(1, registry.getInstalled(ModelRole.CHARACTER).size());
        assertNotNull(findStorage(registry.getInstalled(ModelRole.PLATE), basePlateStorage));
        assertNotNull(findStorage(registry.getInstalled(ModelRole.CHARACTER), characterStorage));
        assertEquals(1, registry.getInstalledPackages().size());
        InstalledAlprPackage base = registry.findPackage(packageStorage);
        assertNotNull(base);
        registry.activate(base);
        assertNotNull(registry.getActivePackage());
        assertFalse(registry.isCompositionModified());

        InstalledModel replacement = findStorage(
                registry.getInstalled(ModelRole.PLATE), replacementPlateStorage
        );
        registry.activate(replacement);

        assertNull(registry.getActivePackage());
        assertNotNull(registry.getBasePackage());
        assertTrue(registry.isCompositionModified());
        assertTrue(registry.canRestoreBaseModels());
        assertFalse(registry.isModelFromBase(ModelRole.PLATE));
        assertTrue(registry.isModelFromBase(ModelRole.CHARACTER));

        ModelRegistry recreated = new ModelRegistry(context);
        assertNotNull(recreated.getBasePackage());
        assertTrue(recreated.isCompositionModified());
        recreated.restoreBasePackage();
        assertNotNull(recreated.getActivePackage());
        assertFalse(recreated.isCompositionModified());
        assertFalse(recreated.canRestoreBaseModels());
    }

    @Test
    public void legacyPrivateModelIsCopiedToAndroidProvidedDirectory() throws Exception {
        String storage = "legacy-plate-" + suffix;
        File legacy = new File(isolatedFiles, "models/plate/" + storage);
        assertTrue(legacy.mkdirs() || legacy.isDirectory());
        Files.write(
                new File(legacy, "manifest.json").toPath(),
                modelManifest(ModelRole.PLATE, storage)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        ModelRegistry registry = new ModelRegistry(context);

        File migrated = new File(isolatedExternalFiles, "models/plate/" + storage);
        assertTrue(new File(migrated, "manifest.json").isFile());
        assertTrue(new File(legacy, "manifest.json").isFile());
        assertNotNull(findStorage(registry.getInstalled(ModelRole.PLATE), storage));
    }

    @Test
    public void modelImportPublishesFromPrivateCacheToAndroidProvidedDirectory() throws Exception {
        byte[] modelBytes = new byte[]{1, 3, 3, 7};
        String modelId = "external-import-" + suffix;
        JSONObject manifest = modelManifest(ModelRole.CHARACTER, modelId);
        manifest.put("variants", new JSONArray().put(
                new JSONObject()
                        .put("id", "tflite-fp32")
                        .put("runtime", "tflite")
                        .put("precision", "fp32")
                        .put("file", "variants/model.tflite")
                        .put("sha256", new JSONObject().put(
                                "variants/model.tflite",
                                Hashing.sha256(modelBytes)
                        ))
        ));
        ModelManifest.parse(manifest.toString());
        File archive = new File(isolatedFiles, modelId + ".alprmodel");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("variants/model.tflite"));
            zip.write(modelBytes);
            zip.closeEntry();
        }

        File modelsRoot = new File(isolatedExternalFiles, "models");
        InstalledModel installed = new ModelPackageImporter(context, modelsRoot)
                .importPackage(archive);

        assertTrue(installed.directory().getCanonicalPath().startsWith(
                modelsRoot.getCanonicalPath() + File.separator
        ));
        assertTrue(new File(installed.directory(), "manifest.json").isFile());
        assertTrue(new File(installed.directory(), "variants/model.tflite").isFile());
        assertTrue(installed.sourceArchive().isFile());
    }

    @Test
    public void manualVariantOverridesAutoAndSupportsNcnn() throws Exception {
        String storage = "plate-variants-" + suffix;
        writeModel(ModelRole.PLATE, storage, "plate-variants-" + suffix);
        File directory = new File(isolatedExternalFiles, "models/plate/" + storage);
        InstalledModel model = new InstalledModel(
                ModelManifest.parse(modelManifest(
                        ModelRole.PLATE, "plate-variants-" + suffix
                ).toString()),
                directory,
                suffix
        );
        AutoTuneManager manager = new AutoTuneManager(context);

        assertEquals("tflite-fp32", manager.chosenVariant(model).id());
        manager.pinVariant(model, "onnx-fp32");
        assertTrue(manager.isVariantPinned(model));
        assertEquals("onnx-fp32", manager.chosenVariant(model).id());

        manager.pinVariant(model, "ncnn-fp32");
        assertEquals("ncnn-fp32", manager.chosenVariant(model).id());

        manager.clearPinnedVariant(model);
        assertFalse(manager.isVariantPinned(model));
        assertEquals("tflite-fp32", manager.chosenVariant(model).id());
    }

    @Test
    public void ncnnOnlyVehicleActivatesCompletePipeline() throws Exception {
        String vehicleStorage = "vehicle-ncnn-" + suffix;
        String plateStorage = "plate-base-" + suffix;
        String characterStorage = "character-base-" + suffix;
        writeNcnnOnlyVehicle(vehicleStorage, "vehicle-ncnn-" + suffix);
        writeModel(ModelRole.PLATE, plateStorage, "plate-base-" + suffix);
        writeModel(ModelRole.CHARACTER, characterStorage, "character-base-" + suffix);
        String packageStorage = writePackage(plateStorage, characterStorage);
        addVehicleToPackage(packageStorage, vehicleStorage);

        ModelRegistry registry = new ModelRegistry(context);
        InstalledAlprPackage completePackage = registry.findPackage(packageStorage);
        assertNotNull(completePackage);
        assertNotNull(completePackage.vehicleModel());
        assertTrue(ModelRegistry.isExecutable(completePackage.vehicleModel()));
        ModelVariant ncnn = completePackage.vehicleModel().manifest().variants().get(0);
        ModelOutputSpec ncnnOutput = ncnn.output(completePackage.vehicleModel().manifest().output());
        assertEquals("raw_yolo", ncnnOutput.outputFormat());
        assertEquals("ultralytics_detect_raw_v1", ncnnOutput.decoder());
        assertFalse(ncnnOutput.channelsFirst());
        assertTrue(ncnnOutput.nmsRequired());

        registry.activate(completePackage);

        assertNotNull(registry.getBasePackage());
        assertNotNull(registry.getActivePackage());
        assertNotNull(registry.getActive(ModelRole.VEHICLE));
        assertNotNull(registry.getActive(ModelRole.PLATE));
        assertNotNull(registry.getActive(ModelRole.CHARACTER));
        assertFalse(registry.isCompositionModified());
        assertFalse(registry.canRestoreBaseModels());
    }

    private void writeModel(ModelRole role, String storageId, String modelId) throws Exception {
        File directory = new File(new File(isolatedExternalFiles, "models"),
                role.wireName() + "/" + storageId);
        assertTrue(directory.mkdirs() || directory.isDirectory());
        JSONObject manifest = modelManifest(role, modelId);
        ModelManifest.parse(manifest.toString());
        Files.write(
                new File(directory, "manifest.json").toPath(),
                manifest.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeNcnnOnlyVehicle(String storageId, String modelId) throws Exception {
        File directory = new File(new File(isolatedExternalFiles, "models"),
                "vehicle/" + storageId);
        assertTrue(directory.mkdirs() || directory.isDirectory());
        JSONObject manifest = modelManifest(ModelRole.VEHICLE, modelId);
        manifest.getJSONObject("input").put("layout", "NCHW");
        manifest.put("output", new JSONObject()
                .put("decoder", "ultralytics_detect_end2end_v1")
                .put("output_format", "end2end_detections")
                .put("box_format", "xyxy")
                .put("nms_required", false)
                .put("class_count", 1)
                .put("keypoint_count", 0)
                .put("tensor_layout", "detections_first")
                .put("nms_in_graph", true));
        manifest.put("variants", new JSONArray().put(
                variant("ncnn-fp32", "ncnn", "fp32", "variants/model.param")
        ));
        ModelManifest.parse(manifest.toString());
        Files.write(
                new File(directory, "manifest.json").toPath(),
                manifest.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String writePackage(String plateStorage, String characterStorage) throws Exception {
        String packageId = "composition-" + suffix;
        String storageId = packageId + "-fingerprint" + suffix;
        File directory = new File(isolatedExternalFiles, "alpr-packages/" + storageId);
        assertTrue(directory.mkdirs() || directory.isDirectory());

        JSONObject models = new JSONObject()
                .put("plate", packageEntry(
                        "plate", "pose", "plate-base-" + suffix, "models/plate"
                ))
                .put("character", packageEntry(
                        "character", "detect", "character-base-" + suffix, "models/character"
                ));
        JSONArray pipeline = new JSONArray()
                .put(modelStage("plate_detection", "plate", "plate", "pose"))
                .put(new JSONObject()
                        .put("stage", "plate_rectification")
                        .put("implementation", "android_alpr_rectifier"))
                .put(modelStage("character_detection", "character", "character", "detect"))
                .put(new JSONObject()
                        .put("stage", "sequence_assembly")
                        .put("implementation", "android_alpr_sequence_decoder"));
        JSONObject manifest = new JSONObject()
                .put("schema", AlprPackageManifest.SCHEMA)
                .put("kind", AlprPackageManifest.KIND)
                .put("package_id", packageId)
                .put("name", "Test composition")
                .put("version", "1")
                .put("created_at", "2026-08-21T00:00:00Z")
                .put("models", models)
                .put("pipeline", pipeline);
        JSONObject installation = new JSONObject()
                .put("plate_storage_id", "plate/" + plateStorage)
                .put("character_storage_id", "character/" + characterStorage)
                .put("source_size_bytes", 1234L)
                .put("source_sha256", repeat("a", 64));
        AlprPackageManifest.parse(manifest.toString());
        Files.write(
                new File(directory, "manifest.json").toPath(),
                manifest.toString().getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
                new File(directory, "installation.json").toPath(),
                installation.toString().getBytes(StandardCharsets.UTF_8)
        );
        return storageId;
    }

    private void addVehicleToPackage(String packageStorage, String vehicleStorage) throws Exception {
        File directory = new File(isolatedExternalFiles, "alpr-packages/" + packageStorage);
        File manifestFile = new File(directory, "manifest.json");
        JSONObject manifest = new JSONObject(
                new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8)
        );
        manifest.getJSONObject("models").put("vehicle", packageEntry(
                "vehicle", "detect", "vehicle-ncnn-" + suffix, "models/vehicle"
        ));
        JSONArray previous = manifest.getJSONArray("pipeline");
        JSONArray pipeline = new JSONArray().put(
                modelStage("vehicle_detection", "vehicle", "vehicle", "detect")
        );
        for (int index = 0; index < previous.length(); index++) {
            pipeline.put(previous.getJSONObject(index));
        }
        manifest.put("pipeline", pipeline);
        AlprPackageManifest.parse(manifest.toString());
        Files.write(
                manifestFile.toPath(),
                manifest.toString().getBytes(StandardCharsets.UTF_8)
        );

        File installationFile = new File(directory, "installation.json");
        JSONObject installation = new JSONObject(
                new String(Files.readAllBytes(installationFile.toPath()), StandardCharsets.UTF_8)
        ).put("vehicle_storage_id", "vehicle/" + vehicleStorage);
        Files.write(
                installationFile.toPath(),
                installation.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static JSONObject modelManifest(ModelRole role, String modelId) throws Exception {
        boolean plate = role == ModelRole.PLATE;
        JSONObject input = new JSONObject()
                .put("width", 320)
                .put("height", 320)
                .put("channels", 3)
                .put("layout", "NHWC")
                .put("color", "RGB")
                .put("data_type", "FLOAT32")
                .put("scale", 1.0 / 255.0)
                .put("offset", 0.0);
        JSONObject output = new JSONObject()
                .put("decoder", plate ? "ultralytics_pose_raw_v1" : "ultralytics_detect_raw_v1")
                .put("output_format", "raw_yolo")
                .put("box_format", "xywh")
                .put("nms_required", true)
                .put("class_count", 1)
                .put("keypoint_count", plate ? 4 : 0)
                .put("keypoint_dimensions", plate ? 2 : 0)
                .put("tensor_layout", "channels_first")
                .put("nms_in_graph", false);
        JSONArray variants = new JSONArray()
                .put(variant("tflite-fp32", "tflite", "fp32", "variants/model.tflite"))
                .put(variant("onnx-fp32", "onnx", "fp32", "variants/model.onnx"))
                .put(variant("ncnn-fp32", "ncnn", "fp32", "variants/model.param"));
        return new JSONObject()
                .put("schema", ModelManifest.SCHEMA)
                .put("model_id", modelId)
                .put("name", modelId)
                .put("version", "1")
                .put("role", role.wireName())
                .put("task", plate ? "pose" : "detect")
                .put("input", input)
                .put("output", output)
                .put("labels", new JSONArray().put(plate ? "plate" : "character"))
                .put("variants", variants);
    }

    private static JSONObject variant(String id, String runtime, String precision, String file)
            throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("runtime", runtime)
                .put("precision", precision)
                .put("file", file)
                .put("sha256", new JSONObject().put(file, repeat("b", 64)));
    }

    private static JSONObject packageEntry(
            String role,
            String task,
            String modelId,
            String prefix
    ) throws Exception {
        String packageFile = prefix + "/model.alprmodel";
        String manifestFile = prefix + "/manifest.json";
        return new JSONObject()
                .put("role", role)
                .put("task", task)
                .put("model_id", modelId)
                .put("schema", ModelManifest.SCHEMA)
                .put("package_file", packageFile)
                .put("manifest_file", manifestFile)
                .put("sha256", new JSONObject()
                        .put(packageFile, repeat("c", 64))
                        .put(manifestFile, repeat("d", 64)));
    }

    private static JSONObject modelStage(String stage, String model, String role, String task)
            throws Exception {
        return new JSONObject()
                .put("stage", stage)
                .put("model", model)
                .put("role", role)
                .put("task", task);
    }

    private static InstalledModel findStorage(
            java.util.List<InstalledModel> models,
            String storageId
    ) {
        for (InstalledModel model : models) {
            if (storageId.equals(model.storageId())
                    || model.storageId().endsWith("/" + storageId)) return model;
        }
        StringBuilder available = new StringBuilder();
        for (InstalledModel model : models) {
            if (available.length() > 0) available.append(", ");
            available.append(model.storageId());
        }
        throw new AssertionError(
                "Nie znaleziono modelu " + storageId + "; dostępne: " + available
        );
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete() && file.exists()) {
            throw new AssertionError("Nie można usunąć fixture: " + file);
        }
    }
}
