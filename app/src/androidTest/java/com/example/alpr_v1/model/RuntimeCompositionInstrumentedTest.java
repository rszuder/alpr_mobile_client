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
import com.example.alpr_v1.experiment.ResearchExecutionConfig;
import com.example.alpr_v1.experiment.ResearchStageExecutionConfig;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.ui.ModelStatusFormatter;

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
    public void plateOnlyIsIncomplete() throws Exception {
        InstalledModel plate = importSingleModel(ModelRole.PLATE, "plate-only-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(plate);

        assertNotNull(registry.getActive(ModelRole.PLATE));
        assertNull(registry.getActive(ModelRole.CHARACTER));
        assertNull(registry.getActivePackage());
        assertFalse(registry.hasCompleteAlprComposition());
        assertTrue(ModelStatusFormatter.presentation(
                registry,
                new AutoTuneManager(context)
        ).summary.contains("Brakuje: MZ"));
    }

    @Test
    public void characterOnlyIsIncomplete() throws Exception {
        InstalledModel character = importSingleModel(ModelRole.CHARACTER, "character-only-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(character);

        assertNull(registry.getActive(ModelRole.PLATE));
        assertNotNull(registry.getActive(ModelRole.CHARACTER));
        assertNull(registry.getActivePackage());
        assertFalse(registry.hasCompleteAlprComposition());
    }

    @Test
    public void separatePlateAndCharacterAreCompleteWithoutActivePackage() throws Exception {
        InstalledModel plate = importSingleModel(ModelRole.PLATE, "plate-separate-");
        InstalledModel character = importSingleModel(ModelRole.CHARACTER, "character-separate-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(plate);
        registry.activate(character);

        assertTrue(registry.hasCompleteAlprComposition());
        assertNull(registry.getActivePackage());
        assertNull(registry.getBasePackage());
        assertTrue(ModelStatusFormatter.presentation(
                registry,
                new AutoTuneManager(context)
        ).summary.contains("Źródło: kompozycja z modeli mobilnych"));
    }

    @Test
    public void vehicleOnlyIsIncomplete() throws Exception {
        InstalledModel vehicle = importSingleModel(ModelRole.VEHICLE, "vehicle-only-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(vehicle);

        assertNotNull(registry.getActive(ModelRole.VEHICLE));
        assertFalse(registry.hasCompleteAlprComposition());
    }

    @Test
    public void vehicleAndPlateAreIncomplete() throws Exception {
        InstalledModel vehicle = importSingleModel(ModelRole.VEHICLE, "vehicle-plate-");
        InstalledModel plate = importSingleModel(ModelRole.PLATE, "plate-vehicle-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(vehicle);
        registry.activate(plate);

        assertNull(registry.getActive(ModelRole.CHARACTER));
        assertFalse(registry.hasCompleteAlprComposition());
    }

    @Test
    public void vehicleAndCharacterAreIncomplete() throws Exception {
        InstalledModel vehicle = importSingleModel(ModelRole.VEHICLE, "vehicle-character-");
        InstalledModel character = importSingleModel(ModelRole.CHARACTER, "character-vehicle-");
        ModelRegistry registry = new ModelRegistry(context);

        registry.activate(vehicle);
        registry.activate(character);

        assertNull(registry.getActive(ModelRole.PLATE));
        assertFalse(registry.hasCompleteAlprComposition());
    }

    @Test
    public void packageWithPlateAndCharacterIsComplete() throws Exception {
        String plateStorage = "plate-base-" + suffix;
        String characterStorage = "character-base-" + suffix;
        writeModel(ModelRole.PLATE, plateStorage, "plate-base-" + suffix);
        writeModel(ModelRole.CHARACTER, characterStorage, "character-base-" + suffix);
        String packageStorage = writePackage(plateStorage, characterStorage);
        ModelRegistry registry = new ModelRegistry(context);
        InstalledAlprPackage completePackage = registry.findPackage(packageStorage);
        assertNotNull(completePackage);

        registry.activate(completePackage);

        assertTrue(registry.hasCompleteAlprComposition());
        assertNotNull(registry.getActivePackage());
        assertNotNull(registry.getBasePackage());
        assertNull(registry.getActive(ModelRole.VEHICLE));
        assertTrue(ModelStatusFormatter.presentation(
                registry,
                new AutoTuneManager(context)
        ).summary.contains("Źródło: kompletny pakiet ALPR"));
    }

    @Test
    public void replacementPreservesBasePackageAndCanBeRestored() throws Exception {
        String basePlateStorage = "plate-base-" + suffix;
        String replacementPlateStorage = "plate-replacement-" + suffix;
        String characterStorage = "character-base-" + suffix;
        String vehicleStorage = "vehicle-ncnn-" + suffix;
        writeModel(ModelRole.PLATE, basePlateStorage, "plate-base-" + suffix);
        writeModel(ModelRole.PLATE, replacementPlateStorage, "plate-replacement-" + suffix);
        writeModel(ModelRole.CHARACTER, characterStorage, "character-base-" + suffix);
        writeNcnnOnlyVehicle(vehicleStorage, "vehicle-ncnn-" + suffix);
        String packageStorage = writePackage(basePlateStorage, characterStorage);
        addVehicleToPackage(packageStorage, vehicleStorage);

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
        InstalledModel basePlate = base.plateModel();
        InstalledModel baseVehicle = base.vehicleModel();
        assertNotNull(basePlate);
        assertNotNull(baseVehicle);
        registry.activate(base);
        assertNotNull(registry.getActivePackage());
        assertTrue(registry.hasCompleteAlprComposition());
        assertFalse(registry.isCompositionModified());

        InstalledModel replacement = findStorage(
                registry.getInstalled(ModelRole.PLATE), replacementPlateStorage
        );
        registry.activate(replacement);

        assertEquals(
                replacement.fingerprint(),
                registry.getActive(ModelRole.PLATE).fingerprint()
        );
        assertEquals(
                basePlate.fingerprint(),
                registry.getBasePackage().plateModel().fingerprint()
        );
        assertEquals(
                baseVehicle.fingerprint(),
                registry.getActive(ModelRole.VEHICLE).fingerprint()
        );
        assertNull(registry.getActivePackage());
        assertNotNull(registry.getBasePackage());
        assertTrue(registry.hasCompleteAlprComposition());
        assertTrue(registry.isCompositionModified());
        assertTrue(registry.canRestoreBaseModels());
        assertFalse(registry.isModelFromBase(ModelRole.PLATE));
        assertTrue(registry.isModelFromBase(ModelRole.CHARACTER));
        assertTrue(ModelStatusFormatter.presentation(
                registry,
                new AutoTuneManager(context)
        ).summary.contains("Źródło: kompozycja zmodyfikowana"));

        AutoTuneManager autoTune = new AutoTuneManager(context);
        ResearchExecutionConfig frozen = new ResearchExecutionConfig(
                "contract",
                "replacement-mt",
                RoiBudgetPolicy.ONE_ROI,
                RecognitionProfile.BALANCED,
                "auto",
                false,
                false,
                true,
                true,
                true,
                true,
                frozenStage(registry, autoTune, ModelRole.VEHICLE),
                frozenStage(registry, autoTune, ModelRole.PLATE),
                frozenStage(registry, autoTune, ModelRole.CHARACTER),
                registry.getBasePackage(),
                registry.isCompositionModified(),
                registry.getBasePackage().sourceSizeBytes()
        );
        JSONObject modelRefs = frozen.modelRefsJson();
        assertEquals(
                replacement.manifest().modelId(),
                modelRefs.getJSONObject("plate").getString("model_id")
        );
        assertEquals(
                replacement.fingerprint(),
                modelRefs.getJSONObject("plate").getString("local_manifest_fingerprint")
        );
        assertEquals(
                baseVehicle.fingerprint(),
                modelRefs.getJSONObject("vehicle").getString("local_manifest_fingerprint")
        );
        assertEquals(
                base.characterModel().fingerprint(),
                modelRefs.getJSONObject("character").getString("local_manifest_fingerprint")
        );
        assertEquals(base.manifest().packageId(), frozen.basePackageId);
        assertEquals(base.fingerprint(), frozen.basePackageFingerprint);
        assertTrue(frozen.compositionModified);

        ModelRegistry recreated = new ModelRegistry(context);
        assertNotNull(recreated.getBasePackage());
        assertTrue(recreated.isCompositionModified());
        assertTrue(recreated.hasCompleteAlprComposition());
        recreated.restoreBasePackage();
        assertNotNull(recreated.getActivePackage());
        assertTrue(recreated.hasCompleteAlprComposition());
        assertEquals(
                basePlate.fingerprint(),
                recreated.getActive(ModelRole.PLATE).fingerprint()
        );
        assertEquals(
                baseVehicle.fingerprint(),
                recreated.getActive(ModelRole.VEHICLE).fingerprint()
        );
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
    public void explicitNcnnEndToEndOverrideIsCanonicalizedToRaw() throws Exception {
        JSONObject manifest = modelManifest(ModelRole.PLATE, "plate-yolo26n-" + suffix);
        JSONObject endToEnd = new JSONObject()
                .put("decoder", "ultralytics_pose_end2end_v1")
                .put("output_format", "end2end_detections")
                .put("box_format", "xyxy")
                .put("nms_required", false)
                .put("class_count", 1)
                .put("keypoint_count", 4)
                .put("keypoint_dimensions", 2)
                .put("tensor_layout", "channels_first")
                .put("nms_in_graph", false);
        manifest.put("output", endToEnd);
        manifest.getJSONArray("variants")
                .getJSONObject(2)
                .put("output", new JSONObject(endToEnd.toString()));

        ModelManifest parsed = ModelManifest.parse(manifest.toString());
        ModelVariant ncnn = parsed.variants().get(2);
        ModelOutputSpec resolved = ncnn.output(parsed.output());

        assertEquals("ultralytics_pose_raw_v1", resolved.decoder());
        assertEquals("raw_yolo", resolved.outputFormat());
        assertEquals("xywh", resolved.boxFormat());
        assertTrue(resolved.channelsFirst());
        assertTrue(resolved.nmsRequired());
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
        assertTrue(registry.hasCompleteAlprComposition());
        assertFalse(registry.isCompositionModified());
        assertFalse(registry.canRestoreBaseModels());
    }

    @Test public void autoAcceptsStoredInt8WinnerWhileFp32Exists() throws Exception {
        InstalledModel model = variantModel(ModelRole.PLATE, suffix);
        AutoTuneManager manager = new AutoTuneManager(context);
        storeProfile(manager, model, "tflite-int8", "tflite", 4);
        assertEquals("tflite-int8", manager.chosenVariant(model).id());
        assertEquals(4, manager.chosenProfile(model).cpuThreads);
        assertEquals("lowest_successful_median_all_executable_variants",
                new com.example.alpr_v1.autotune.AutoTuneResult("id", suffix, "tflite-int8",
                        manager.chosenProfile(model), java.util.Collections.emptyList())
                        .toJson().getString("selection_policy"));
    }

    @Test public void pinSurvivesRecreationAndRetuningUntilAutoIsSelected() throws Exception {
        InstalledModel model = variantModel(ModelRole.PLATE, suffix);
        AutoTuneManager manager = new AutoTuneManager(context);
        storeProfile(manager, model, "tflite-fp32", "tflite", 4);
        manager.pinVariant(model, "tflite-int8");
        AutoTuneManager recreated = new AutoTuneManager(context);
        assertEquals("tflite-int8", recreated.chosenVariant(model).id());
        assertEquals("tflite-fp32", recreated.automaticVariant(model).id());
        assertEquals(4, recreated.automaticProfile(model).cpuThreads);
        storeProfile(recreated, model, "onnx-fp32", "onnx", 1);
        assertEquals("tflite-int8", recreated.chosenVariant(model).id());
        assertEquals(ModelRuntime.TFLITE, recreated.chosenProfile(model).runtime);
        recreated.clearPinnedVariant(model);
        assertEquals("onnx-fp32", recreated.chosenVariant(model).id());
        assertEquals(1, recreated.chosenProfile(model).cpuThreads);
    }

    @Test public void pinsAreIndependentByRoleAndFingerprint() throws Exception {
        AutoTuneManager manager = new AutoTuneManager(context);
        InstalledModel mp = variantModel(ModelRole.VEHICLE, suffix);
        InstalledModel mt = variantModel(ModelRole.PLATE, suffix);
        InstalledModel mz = variantModel(ModelRole.CHARACTER, suffix);
        manager.pinVariant(mp, "ncnn-fp32");
        manager.pinVariant(mt, "tflite-int8");
        manager.pinVariant(mz, "onnx-int8");
        assertEquals("ncnn-fp32", manager.chosenVariant(mp).id());
        assertEquals("tflite-int8", manager.chosenVariant(mt).id());
        assertEquals("onnx-int8", manager.chosenVariant(mz).id());
        InstalledModel updated = variantModel(ModelRole.PLATE, suffix + "new");
        assertFalse(manager.isVariantPinned(updated));
        assertEquals("tflite-fp32", manager.chosenVariant(updated).id());
    }

    @Test public void frozenInt8IgnoresLaterGlobalPinAndPreservesFloatQdqInterface() throws Exception {
        AutoTuneManager manager = new AutoTuneManager(context);
        InstalledModel mt = variantModel(ModelRole.PLATE, suffix);
        manager.pinVariant(mt, "onnx-int8");
        ResearchStageExecutionConfig frozen = ResearchStageExecutionConfig.enabled(
                ModelRole.PLATE, mt, manager.chosenVariant(mt), manager.chosenProfile(mt),
                new ModelRegistry(context));
        manager.pinVariant(mt, "tflite-fp32");
        assertEquals("onnx-int8", frozen.requireVariant(mt).id());
        assertEquals(ModelRuntime.ONNX, frozen.runtime);
        assertEquals("int8", frozen.precision);
        assertEquals("FLOAT32", frozen.inputDataType);
        assertEquals("tflite-fp32", manager.chosenVariant(mt).id());
    }

    @Test public void staleAutoWinnerFallsBackWithoutBorrowingItsHardwareProfile() throws Exception {
        AutoTuneManager manager = new AutoTuneManager(context);
        InstalledModel mt = variantModel(ModelRole.PLATE, suffix);
        storeProfile(manager, mt, "deleted-variant", "tflite", 4);
        assertEquals("tflite-fp32", manager.chosenVariant(mt).id());
        assertEquals(Math.min(2, Runtime.getRuntime().availableProcessors()),
                manager.chosenProfile(mt).cpuThreads);
    }

    @Test public void directStageMenuListsFiveVariantsAndAppliesPinWithRevision() throws Exception {
        String storage = "variant-ui-" + suffix;
        writeModel(ModelRole.PLATE, storage, storage);
        ModelRegistry registry = new ModelRegistry(context);
        InstalledModel model = findStorage(registry.getInstalled(ModelRole.PLATE), storage);
        registry.activate(model);
        AutoTuneManager manager = new AutoTuneManager(context);
        SharedPreferences settings = context.getSharedPreferences("alpr_ui", Context.MODE_PRIVATE);
        try (androidx.test.core.app.ActivityScenario<com.example.alpr_v1.SettingsActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(com.example.alpr_v1.SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    for (String name : new String[]{"modelRegistry", "autoTuneManager", "preferences"}) {
                        java.lang.reflect.Field field = activity.getClass().getDeclaredField(name);
                        field.setAccessible(true);
                        field.set(activity, name.equals("modelRegistry") ? registry
                                : name.equals("autoTuneManager") ? manager : settings);
                    }
                    activity.findViewById(com.example.alpr_v1.R.id.settings_node_plate).performClick();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    "Wybierz wariant wykonawczy")).perform(androidx.test.espresso.action.ViewActions.click());
            // Inspect every adapter row, including those outside the screen, and availability.
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers
                    .isAssignableFrom(android.widget.ListView.class)).check((view, error) -> {
                if (error != null) throw error;
                android.widget.ListAdapter rows = ((android.widget.ListView) view).getAdapter();
                assertEquals(6, rows.getCount());
                assertTrue(rows.getItem(0).toString().startsWith("AUTO — aktualnie: TFLite FP32 · CPU ×"));
                for (int index = 0; index < model.manifest().variants().size(); index++) {
                    ModelVariant variant = model.manifest().variants().get(index);
                    assertTrue(rows.getItem(index + 1).toString().startsWith(
                            ModelStatusFormatter.variantLabel(variant)));
                    assertEquals(com.example.alpr_v1.inference.RuntimeBackendFactory
                            .isRuntimeAvailable(variant.runtime()), rows.isEnabled(index + 1));
                }
            });
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.anything())
                    .inAdapterView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(
                            android.widget.ListView.class)).atPosition(4)
                    .perform(androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    "Zastosuj")).perform(androidx.test.espresso.action.ViewActions.click());
            assertEquals("tflite-int8", manager.chosenVariant(model).id());
            assertEquals(1, settings.getInt(com.example.alpr_v1.SettingsActivity.KEY_REVISION, 0));
        } finally {
            settings.edit().clear().commit();
        }
    }

    /** Opt-in device acceptance: uses the installed package as read-only test data. */
    @Test public void installedFiveVariantPackageImportsBothWaysAndExecutesEveryMtBackend() throws Exception {
        org.junit.Assume.assumeTrue("Requires -e installedVariants true",
                "true".equals(InstrumentationRegistry.getArguments().getString("installedVariants")));
        ModelRegistry installedRegistry = new ModelRegistry(targetContext);
        InstalledAlprPackage source = installedRegistry.getBasePackage();
        assertNotNull("Install the five-variant acceptance package first", source);
        File archive = source.sourceArchive();
        assertTrue(archive.isFile());
        File singleArchive = new File(isolatedFiles, "acceptance-mt.alprmodel");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive)) {
            java.util.zip.ZipEntry entry = zip.getEntry(source.manifest().plate().packageFile());
            assertNotNull(entry);
            try (java.io.InputStream stream = zip.getInputStream(entry)) {
                Files.copy(stream, singleArchive.toPath());
            }
        }
        ModelRegistry registry = new ModelRegistry(context);
        AlprPackageImporter importer = new AlprPackageImporter(context, registry);
        InstalledModel single = importer.importPackage(android.net.Uri.fromFile(singleArchive)).singleModel();
        assertNotNull(single);
        assertEquals(5, single.manifest().variants().size());
        InstalledAlprPackage nested = importer.importPackage(android.net.Uri.fromFile(archive)).completePackage();
        assertNotNull(nested);
        assertEquals(5, nested.plateModel().manifest().variants().size());
        assertEquals(single.fingerprint(), nested.plateModel().fingerprint());
        AutoTuneManager manager = new AutoTuneManager(context);
        for (ModelVariant variant : nested.plateModel().manifest().variants()) {
            manager.pinVariant(nested.plateModel(), variant.id());
            ModelVariant selected = manager.chosenVariant(nested.plateModel());
            assertEquals(variant.id(), selected.id());
            try (com.example.alpr_v1.inference.InferenceBackend backend =
                         com.example.alpr_v1.inference.RuntimeBackendFactory.create(
                                 nested.plateModel(), selected, manager.chosenProfile(nested.plateModel()))) {
                java.nio.ByteBuffer input = java.nio.ByteBuffer.allocateDirect(backend.inputByteSize())
                        .order(java.nio.ByteOrder.nativeOrder());
                assertFalse(backend.run(input).outputs().isEmpty());
                android.util.Log.i("ALPR_VARIANT_ACCEPTANCE", "executed=" + selected.id()
                        + " files=" + selected.files() + " input=" + backend.inputInfo().dataType);
            }
        }
    }

    private InstalledModel variantModel(ModelRole role, String fingerprint) throws Exception {
        return new InstalledModel(ModelManifest.parse(modelManifest(role, "variant-test").toString()),
                isolatedFiles, fingerprint);
    }

    private void storeProfile(AutoTuneManager manager, InstalledModel model,
                              String id, String runtime, int threads) throws Exception {
        java.lang.reflect.Method key = AutoTuneManager.class.getDeclaredMethod("key", InstalledModel.class);
        key.setAccessible(true);
        JSONObject json = new JSONObject().put("chosen_variant_id", id)
                .put("runtime", runtime).put("cpu_threads", threads).put("gpu", false);
        context.getSharedPreferences("autotune", Context.MODE_PRIVATE).edit()
                .putString((String) key.invoke(manager, model), json.toString()).commit();
    }

    private InstalledModel importSingleModel(ModelRole role, String prefix) throws Exception {
        byte[] modelBytes = new byte[]{4, 2, 1, (byte) role.ordinal()};
        String modelId = prefix + suffix;
        JSONObject manifest = modelManifest(role, modelId);
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
        File archive = new File(isolatedFiles, modelId + ".alprmodel");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("variants/model.tflite"));
            zip.write(modelBytes);
            zip.closeEntry();
        }
        return new ModelPackageImporter(
                context,
                new File(isolatedExternalFiles, "models")
        ).importPackage(archive);
    }

    private static ResearchStageExecutionConfig frozenStage(
            ModelRegistry registry,
            AutoTuneManager autoTune,
            ModelRole role
    ) {
        InstalledModel model = registry.getActive(role);
        ModelVariant variant = autoTune.chosenVariant(model);
        return ResearchStageExecutionConfig.enabled(
                role,
                model,
                variant,
                autoTune.chosenProfile(model),
                registry
        );
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
                .put(variant("ncnn-fp32", "ncnn", "fp32", "variants/model.param"))
                .put(variant("tflite-int8", "tflite", "int8", "variants/model-int8.tflite"))
                .put(variant("onnx-int8", "onnx", "int8", "variants/model-int8.onnx")
                        .put("input", new JSONObject(input.toString()).put("layout", "NCHW")));
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
