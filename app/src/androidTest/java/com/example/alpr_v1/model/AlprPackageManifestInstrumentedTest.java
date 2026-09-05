package com.example.alpr_v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AlprPackageManifestInstrumentedTest {
    private static final String HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void legacyFourStagePackageRemainsSupported() throws Exception {
        AlprPackageManifest manifest = AlprPackageManifest.parse(
                completeManifest(false, false).toString()
        );

        assertNull(manifest.vehicle());
        assertEquals(4, manifest.pipeline().size());
        assertEquals("plate_detection", manifest.pipeline().get(0).stage());
    }

    @Test
    public void vehiclePackageRequiresAndParsesFiveStageCascade() throws Exception {
        AlprPackageManifest manifest = AlprPackageManifest.parse(
                completeManifest(true, true).toString()
        );

        assertNotNull(manifest.vehicle());
        assertEquals(ModelRole.VEHICLE, manifest.vehicle().role());
        assertEquals(5, manifest.pipeline().size());
        assertEquals("vehicle_detection", manifest.pipeline().get(0).stage());
        assertEquals("plate_detection", manifest.pipeline().get(1).stage());
    }

    @Test
    public void vehicleEntryWithoutVehicleStageIsRejected() throws Exception {
        assertInvalidCompleteManifest(completeManifest(true, false));
    }

    @Test
    public void vehicleStageWithoutVehicleEntryIsRejected() throws Exception {
        assertInvalidCompleteManifest(completeManifest(false, true));
    }

    @Test
    public void modelRefsPreservePortableProvenanceAndNulls() throws Exception {
        JSONObject manifestJson = completeManifest(false, false);
        JSONObject training = new JSONObject()
                .put("run_epochs_completed", 10)
                .put("lineage_total_epochs", JSONObject.NULL)
                .put("lineage_total_epochs_known", false)
                .put("known_epochs_minimum", 10)
                .put("known_sample_presentations_minimum", 9000)
                .put("provenance_status", "partial");
        JSONObject plateRef = new JSONObject()
                .put("model_id", "plate-instrumented")
                .put("installed_model_fingerprint", "portable-plate")
                .put("checkpoint_sha256", HASH)
                .put("package_sha256", HASH)
                .put("training", training);
        manifestJson.put("model_refs", new JSONObject().put("plate", plateRef));

        AlprPackageManifest manifest = AlprPackageManifest.parse(manifestJson.toString());
        JSONObject parsed = manifest.modelRef(ModelRole.PLATE);

        assertTrue(manifest.hasModelRefs());
        assertEquals("portable-plate", parsed.getString("installed_model_fingerprint"));
        assertEquals(HASH, parsed.getString("checkpoint_sha256"));
        assertTrue(parsed.getJSONObject("training").isNull("lineage_total_epochs"));
        assertEquals("partial", parsed.getJSONObject("training").getString("provenance_status"));
    }

    @Test
    public void mismatchedModelRefIsRejected() throws Exception {
        JSONObject manifest = completeManifest(false, false);
        manifest.put("model_refs", new JSONObject().put(
                "plate", new JSONObject().put("model_id", "different-model")
        ));
        try {
            AlprPackageManifest.parse(manifest.toString());
            fail("Niezgodny model_id powinien zostać odrzucony");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("model_refs.plate"));
        }
    }

    private static void assertInvalidCompleteManifest(JSONObject json) throws Exception {
        try {
            AlprPackageManifest.parse(json.toString());
            fail("Nieprawidłowa relacja models.vehicle/pipeline powinna zostać odrzucona");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("dokładnie"));
        }
    }

    private static JSONObject completeManifest(boolean includeVehicle, boolean includeVehicleStage)
            throws Exception {
        JSONObject models = new JSONObject();
        if (includeVehicle) {
            models.put("vehicle", modelEntry("vehicle", "detect"));
        }
        models.put("plate", modelEntry("plate", "pose"));
        models.put("character", modelEntry("character", "detect"));

        JSONArray pipeline = new JSONArray();
        if (includeVehicleStage) {
            pipeline.put(modelStage("vehicle_detection", "vehicle", "vehicle", "detect"));
        }
        pipeline.put(modelStage("plate_detection", "plate", "plate", "pose"));
        pipeline.put(new JSONObject()
                .put("stage", "plate_rectification")
                .put("implementation", "android_alpr_rectifier"));
        pipeline.put(modelStage(
                "character_detection", "character", "character", "detect"
        ));
        pipeline.put(new JSONObject()
                .put("stage", "sequence_assembly")
                .put("implementation", "android_alpr_sequence_decoder"));

        return new JSONObject()
                .put("schema", AlprPackageManifest.SCHEMA)
                .put("kind", AlprPackageManifest.KIND)
                .put("package_id", "instrumented-complete-package")
                .put("name", "Pakiet testowy")
                .put("version", "1")
                .put("models", models)
                .put("pipeline", pipeline);
    }

    private static JSONObject modelEntry(String role, String task) throws Exception {
        String packagePath = "models/" + role + "/model.alprmodel";
        String manifestPath = "models/" + role + "/manifest.json";
        return new JSONObject()
                .put("role", role)
                .put("task", task)
                .put("model_id", role + "-instrumented")
                .put("schema", ModelManifest.SCHEMA)
                .put("package_file", packagePath)
                .put("manifest_file", manifestPath)
                .put("sha256", new JSONObject()
                        .put(packagePath, HASH)
                        .put(manifestPath, HASH));
    }

    private static JSONObject modelStage(String stage, String model, String role, String task)
            throws Exception {
        return new JSONObject()
                .put("stage", stage)
                .put("model", model)
                .put("role", role)
                .put("task", task);
    }
}
