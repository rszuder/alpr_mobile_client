package com.example.alpr_v1.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;

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

    private final File modelsRoot;
    private final SharedPreferences preferences;
    private final Map<ModelRole, List<InstalledModel>> installed = new EnumMap<>(ModelRole.class);
    private final Map<ModelRole, InstalledModel> active = new EnumMap<>(ModelRole.class);

    public ModelRegistry(Context context) {
        this.modelsRoot = new File(context.getFilesDir(), "models");
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    public synchronized void reload() {
        installed.clear();
        active.clear();
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
            if (selected != null) {
                active.put(role, selected);
            }
        }
    }

    public File modelsRoot() {
        return modelsRoot;
    }

    public synchronized void activate(InstalledModel model) {
        if (!isExecutable(model)) {
            throw new IllegalArgumentException("Pakiet nie zawiera jeszcze obsługiwanego runtime'u wykonawczego");
        }
        preferences.edit()
                .putString("active." + model.manifest().role().wireName(), model.storageId())
                .apply();
        reload();
    }

    public synchronized InstalledModel getActive(ModelRole role) {
        return active.get(role);
    }

    public synchronized List<InstalledModel> getInstalled(ModelRole role) {
        List<InstalledModel> models = installed.get(role);
        return models == null ? Collections.emptyList() : new ArrayList<>(models);
    }

    public synchronized boolean hasRequiredPipeline() {
        return active.containsKey(ModelRole.PLATE) && active.containsKey(ModelRole.CHARACTER);
    }

    public synchronized String summary() {
        StringBuilder text = new StringBuilder();
        for (ModelRole role : new ModelRole[]{ModelRole.VEHICLE, ModelRole.PLATE, ModelRole.CHARACTER}) {
            InstalledModel model = active.get(role);
            if (text.length() > 0) text.append(" • ");
            text.append(role.displayName()).append(": ");
            if (model == null) {
                text.append("brak");
            } else {
                text.append(model.manifest().name()).append(" v").append(model.manifest().version());
            }
        }
        return text.toString();
    }

    public static ModelVariant preferredVariant(InstalledModel model) {
        for (ModelVariant variant : model.manifest().variants()) {
            if (variant.runtime() == ModelRuntime.TFLITE) {
                return variant;
            }
        }
        return model.manifest().variants().get(0);
    }

    public static boolean isExecutable(InstalledModel model) {
        for (ModelVariant variant : model.manifest().variants()) {
            if (variant.runtime() == ModelRuntime.TFLITE || variant.runtime() == ModelRuntime.ONNX) return true;
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
}
