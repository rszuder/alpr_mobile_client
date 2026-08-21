package com.example.alpr_v1.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.alpr_v1.inference.RuntimeBackendFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModelRegistry {
    private static final String TAG = "ModelRegistry";
    private static final String PREFS = "model_registry";
    private static final String ACTIVE_PACKAGE = "active.package";
    private static final String BASE_PACKAGE = "composition.base_package";

    private final File modelsRoot;
    private final File packagesRoot;
    private final SharedPreferences preferences;
    private final Map<ModelRole, List<InstalledModel>> installed = new EnumMap<>(ModelRole.class);
    private final Map<ModelRole, InstalledModel> active = new EnumMap<>(ModelRole.class);
    private final List<InstalledAlprPackage> installedPackages = new ArrayList<>();
    private InstalledAlprPackage activePackage;
    private InstalledAlprPackage basePackage;

    public ModelRegistry(Context context) {
        this.modelsRoot = new File(context.getFilesDir(), "models");
        this.packagesRoot = new File(context.getFilesDir(), "alpr-packages");
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    public synchronized void reload() {
        installed.clear();
        active.clear();
        installedPackages.clear();
        activePackage = null;
        basePackage = null;
        for (ModelRole role : ModelRole.values()) {
            List<InstalledModel> roleModels = scanRole(role);
            installed.put(role, roleModels);
            String activeStorageId = preferences.getString("active." + role.wireName(), "");
            InstalledModel selected = null;
            for (InstalledModel model : roleModels) {
                if (model.storageId().equals(activeStorageId)) {
                    selected = model;
                    break;
                }
            }
            if (selected != null && !isExecutable(selected)) selected = null;
            if (selected == null && !roleModels.isEmpty()) {
                for (int index = roleModels.size() - 1; index >= 0; index--) {
                    if (isExecutable(roleModels.get(index))) {
                        selected = roleModels.get(index);
                        break;
                    }
                }
            }
            if (selected != null) active.put(role, selected);
        }

        installedPackages.addAll(scanPackages());
        String activePackageId = preferences.getString(ACTIVE_PACKAGE, "");
        String basePackageId = preferences.getString(BASE_PACKAGE, activePackageId);
        for (InstalledAlprPackage completePackage : installedPackages) {
            if (completePackage.storageId().equals(basePackageId)) {
                basePackage = completePackage;
            }
            if (completePackage.storageId().equals(activePackageId)
                    && sameModel(active.get(ModelRole.PLATE), completePackage.plateModel())
                    && sameModel(active.get(ModelRole.CHARACTER), completePackage.characterModel())
                    && (completePackage.vehicleModel() == null
                    || sameModel(active.get(ModelRole.VEHICLE), completePackage.vehicleModel()))) {
                activePackage = completePackage;
                break;
            }
        }
    }

    public File modelsRoot() { return modelsRoot; }
    public File packagesRoot() { return packagesRoot; }

    public synchronized void activate(InstalledModel model) {
        if (!isExecutable(model)) {
            throw new IllegalArgumentException("Pakiet nie zawiera obsługiwanego runtime'u wykonawczego");
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString("active." + model.manifest().role().wireName(), model.storageId());
        if (basePackage != null) {
            editor.putString(BASE_PACKAGE, basePackage.storageId());
        }
        if (model.manifest().role() == ModelRole.PLATE
                || model.manifest().role() == ModelRole.CHARACTER
                || (model.manifest().role() == ModelRole.VEHICLE
                && activePackage != null && activePackage.vehicleModel() != null)) {
            editor.remove(ACTIVE_PACKAGE);
        }
        editor.apply();
        reload();
    }

    public synchronized void activate(InstalledAlprPackage completePackage) {
        if (!isExecutable(completePackage.plateModel()) || !isExecutable(completePackage.characterModel())) {
            throw new IllegalArgumentException("Kompletny pakiet nie zawiera wykonywalnych wariantów MT i MZ");
        }
        boolean executableVehicle = completePackage.vehicleModel() != null
                && isExecutable(completePackage.vehicleModel());
        SharedPreferences.Editor editor = preferences.edit()
                .putString("active." + ModelRole.PLATE.wireName(), completePackage.plateModel().storageId())
                .putString("active." + ModelRole.CHARACTER.wireName(), completePackage.characterModel().storageId())
                .putString(BASE_PACKAGE, completePackage.storageId());
        if (completePackage.vehicleModel() == null || executableVehicle) {
            editor.putString(ACTIVE_PACKAGE, completePackage.storageId());
        } else {
            editor.remove(ACTIVE_PACKAGE);
        }
        if (executableVehicle) {
            editor.putString(
                    "active." + ModelRole.VEHICLE.wireName(),
                    completePackage.vehicleModel().storageId()
            );
        } else {
            editor.remove("active." + ModelRole.VEHICLE.wireName());
        }
        editor.apply();
        reload();
    }

    public synchronized InstalledModel getActive(ModelRole role) { return active.get(role); }

    public synchronized List<InstalledModel> getInstalled(ModelRole role) {
        List<InstalledModel> models = installed.get(role);
        return models == null ? Collections.emptyList() : new ArrayList<>(models);
    }

    public synchronized InstalledAlprPackage getActivePackage() { return activePackage; }

    public synchronized List<InstalledAlprPackage> getInstalledPackages() {
        return new ArrayList<>(installedPackages);
    }

    /** Pakiet źródłowy kompozycji; pozostaje ustawiony po podmianie pojedynczego modelu. */
    public synchronized InstalledAlprPackage getBasePackage() { return basePackage; }

    public synchronized boolean isCompositionModified() {
        if (basePackage == null) return false;
        return !sameModel(active.get(ModelRole.VEHICLE), basePackage.vehicleModel())
                || !sameModel(active.get(ModelRole.PLATE), basePackage.plateModel())
                || !sameModel(active.get(ModelRole.CHARACTER), basePackage.characterModel());
    }

    public synchronized boolean isModelFromBase(ModelRole role) {
        if (basePackage == null) return false;
        InstalledModel expected = role == ModelRole.VEHICLE
                ? basePackage.vehicleModel()
                : role == ModelRole.PLATE
                ? basePackage.plateModel()
                : basePackage.characterModel();
        return sameModel(active.get(role), expected);
    }

    public synchronized boolean canRestoreBaseModels() {
        if (basePackage == null) return false;
        if (!sameModel(active.get(ModelRole.PLATE), basePackage.plateModel())
                || !sameModel(active.get(ModelRole.CHARACTER), basePackage.characterModel())) {
            return true;
        }
        InstalledModel baseVehicle = basePackage.vehicleModel();
        InstalledModel activeVehicle = active.get(ModelRole.VEHICLE);
        if (baseVehicle == null) return activeVehicle != null;
        if (!isExecutable(baseVehicle)) return activeVehicle != null;
        return !sameModel(activeVehicle, baseVehicle);
    }

    public synchronized void deactivateVehicle() {
        SharedPreferences.Editor editor = preferences.edit()
                .remove("active." + ModelRole.VEHICLE.wireName());
        if (basePackage != null) {
            editor.putString(BASE_PACKAGE, basePackage.storageId());
        }
        if (activePackage != null && activePackage.vehicleModel() != null) {
            editor.remove(ACTIVE_PACKAGE);
        }
        editor.apply();
        reload();
    }

    public synchronized void restoreBasePackage() {
        if (basePackage == null) {
            throw new IllegalStateException("Brak pakietu bazowego do przywrócenia");
        }
        activate(basePackage);
    }

    public synchronized InstalledAlprPackage findPackage(String storageId) {
        for (InstalledAlprPackage completePackage : installedPackages) {
            if (completePackage.storageId().equals(storageId)) return completePackage;
        }
        return null;
    }

    public synchronized boolean hasRequiredPipeline() {
        return active.containsKey(ModelRole.PLATE) && active.containsKey(ModelRole.CHARACTER);
    }

    public static ModelVariant preferredVariant(InstalledModel model) {
        for (ModelVariant variant : model.manifest().variants()) {
            if (variant.runtime() == ModelRuntime.TFLITE
                    && RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                return variant;
            }
        }
        for (ModelVariant variant : model.manifest().variants()) {
            if (RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) return variant;
        }
        return model.manifest().variants().get(0);
    }

    public static boolean isExecutable(InstalledModel model) {
        for (ModelVariant variant : model.manifest().variants()) {
            if (RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) return true;
        }
        return false;
    }

    private List<InstalledModel> scanRole(ModelRole role) {
        List<InstalledModel> result = new ArrayList<>();
        File roleDirectory = new File(modelsRoot, role.wireName());
        File[] directories = roleDirectory.listFiles(File::isDirectory);
        if (directories == null) return result;
        for (File directory : directories) {
            File manifestFile = new File(directory, "manifest.json");
            if (!manifestFile.isFile()) continue;
            try {
                String json = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                ModelManifest manifest = ModelManifest.parse(json);
                if (manifest.role() != role) continue;
                String suffix = directory.getName();
                int separator = suffix.lastIndexOf('-');
                String fingerprint = separator >= 0 ? suffix.substring(separator + 1) : "unknown";
                result.add(new InstalledModel(manifest, directory, fingerprint));
            } catch (Exception e) {
                Log.w(TAG, "Pomijam uszkodzony pakiet " + directory, e);
            }
        }
        result.sort((left, right) -> left.directory().getName().compareTo(right.directory().getName()));
        return result;
    }

    private List<InstalledAlprPackage> scanPackages() {
        List<InstalledAlprPackage> result = new ArrayList<>();
        File[] directories = packagesRoot.listFiles(File::isDirectory);
        if (directories == null) return result;
        for (File directory : directories) {
            File manifestFile = new File(directory, "manifest.json");
            File installationFile = new File(directory, "installation.json");
            if (!manifestFile.isFile() || !installationFile.isFile()) continue;
            try {
                AlprPackageManifest manifest = AlprPackageManifest.parse(
                        new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8)
                );
                org.json.JSONObject installation = new org.json.JSONObject(
                        new String(Files.readAllBytes(installationFile.toPath()), StandardCharsets.UTF_8)
                );
                String vehicleStorageId = installation.optString("vehicle_storage_id", "");
                InstalledModel vehicle = vehicleStorageId.isEmpty()
                        ? null
                        : findModelByStorageId(vehicleStorageId);
                InstalledModel plate = findModelByStorageId(installation.getString("plate_storage_id"));
                InstalledModel character = findModelByStorageId(installation.getString("character_storage_id"));
                long sourceSizeBytes = Math.max(0L, installation.optLong("source_size_bytes", 0L));
                String sourceSha256 = installation.optString("source_sha256", "");
                if (plate == null || character == null
                        || plate.manifest().role() != ModelRole.PLATE
                        || character.manifest().role() != ModelRole.CHARACTER) continue;
                if ((manifest.vehicle() != null && vehicle == null)
                        || (manifest.vehicle() == null && vehicle != null)
                        || (vehicle != null && vehicle.manifest().role() != ModelRole.VEHICLE)) continue;
                String suffix = directory.getName();
                int separator = suffix.lastIndexOf('-');
                String fingerprint = separator >= 0 ? suffix.substring(separator + 1) : "unknown";
                result.add(new InstalledAlprPackage(
                        manifest,
                        directory,
                        fingerprint,
                        sourceSizeBytes,
                        sourceSha256,
                        vehicle,
                        plate,
                        character
                ));
            } catch (Exception e) {
                Log.w(TAG, "Pomijam uszkodzony kompletny pakiet " + directory, e);
            }
        }
        result.sort((left, right) -> left.storageId().compareTo(right.storageId()));
        return result;
    }

    private InstalledModel findModelByStorageId(String storageId) {
        for (List<InstalledModel> roleModels : installed.values()) {
            for (InstalledModel model : roleModels) {
                if (model.storageId().equals(storageId)) return model;
            }
        }
        return null;
    }

    private static boolean sameModel(InstalledModel left, InstalledModel right) {
        if (left == null || right == null) return left == right;
        return left.storageId().equals(right.storageId());
    }

}
