package com.example.alpr_v1.ui;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;

import java.util.Locale;

public final class ModelStatusFormatter {
    private ModelStatusFormatter() {}

    public static String format(ModelRegistry registry, AutoTuneManager autoTuneManager) {
        InstalledAlprPackage activePackage = registry.getActivePackage();
        InstalledAlprPackage basePackage = registry.getBasePackage();
        String header;
        if (basePackage != null && registry.isCompositionModified()) {
            header = "Kompozycja zmodyfikowana · baza: " + basePackage.manifest().name()
                    + " v" + basePackage.manifest().version();
        } else if (activePackage != null) {
            header = "Komplet gotowy: " + activePackage.manifest().name()
                    + " v" + activePackage.manifest().version();
            String createdAt = shortDate(activePackage.manifest().createdAt());
            if (!createdAt.isEmpty()) header += " • " + createdAt;
        } else if (registry.hasRequiredPipeline()) {
            header = "Pipeline z pojedynczych pakietów — MT+MZ aktywne";
        } else {
            header = "Tryb częściowy — zaimportuj brakujący model MT lub MZ";
        }
        InstalledModel activeVehicle = registry.getActive(ModelRole.VEHICLE);
        String vehicleDescription = activeVehicle == null
                && basePackage != null
                && basePackage.vehicleModel() != null
                ? describeUnavailableBase("MP", basePackage.vehicleModel())
                : describe("MP", activeVehicle, autoTuneManager);
        return header
                + "\n" + vehicleDescription
                + "\n" + describe("MT", registry.getActive(ModelRole.PLATE), autoTuneManager)
                + "\n" + describe("MZ", registry.getActive(ModelRole.CHARACTER), autoTuneManager);
    }

    private static String describe(String label, InstalledModel model, AutoTuneManager autoTuneManager) {
        if (model == null) return label + ": brak";
        ModelManifest manifest = model.manifest();
        ModelVariant selected = autoTuneManager.chosenVariant(model);
        ExecutionProfile profile = autoTuneManager.chosenProfile(model);
        ModelInputSpec input = selected.input(manifest.input());
        ModelOutputSpec output = selected.output(manifest.output());

        StringBuilder first = new StringBuilder(label).append(": ").append(manifest.modelId());
        if (!manifest.yoloFamily().isEmpty()) first.append(" • ").append(manifest.yoloFamily());
        if (manifest.parameterCount() > 0L) {
            first.append(String.format(Locale.ROOT, " %.2fM", manifest.parameterCount() / 1_000_000.0));
        }
        first.append(" • aktywny ").append(selected.id())
                .append(autoTuneManager.isVariantPinned(model) ? " (ręczny)" : " (auto)")
                .append('/').append(profile.gpu ? "GPU" : "CPU")
                .append(" • ").append(input.width()).append('×').append(input.height())
                .append(String.format(
                        Locale.ROOT,
                        " • conf %.2f / IoU %.2f",
                        output.confidenceThreshold(), output.iouThreshold()
                ));

        StringBuilder second = new StringBuilder("   warianty: ");
        boolean firstVariant = true;
        for (ModelVariant variant : manifest.variants()) {
            if (!firstVariant) second.append(", ");
            second.append(variant.id());
            if (!RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                second.append(" [niedostępny]");
            }
            firstVariant = false;
        }
        double map = manifest.metric("best_map50_95");
        if (!Double.isNaN(map)) second.append(String.format(Locale.ROOT, " • mAP50-95 %.3f", map));
        String exportedAt = shortDate(manifest.exportedAt());
        if (!exportedAt.isEmpty()) second.append(" • eksport ").append(exportedAt);
        return first.append('\n').append(second).toString();
    }

    private static String describeUnavailableBase(String label, InstalledModel model) {
        StringBuilder value = new StringBuilder(label)
                .append(": ")
                .append(model.manifest().modelId())
                .append(" · nieaktywny (brak backendu)")
                .append("\n   warianty: ");
        boolean first = true;
        for (ModelVariant variant : model.manifest().variants()) {
            if (!first) value.append(", ");
            value.append(variant.id());
            if (!RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                value.append(" [niedostępny]");
            }
            first = false;
        }
        return value.toString();
    }

    private static String shortDate(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
    }
}
