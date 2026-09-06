package com.example.alpr_v1.ui;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.inference.ExecutionProfile;
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
        Presentation value = presentation(registry, autoTuneManager);
        return value.summary
                + "\nMP: " + value.vehicle
                + "\nMT: " + value.plate
                + "\nMZ: " + value.character;
    }

    public static Presentation presentation(
            ModelRegistry registry,
            AutoTuneManager autoTuneManager
    ) {
        InstalledAlprPackage activePackage = registry.getActivePackage();
        InstalledAlprPackage basePackage = registry.getBasePackage();
        boolean complete = registry.hasCompleteAlprComposition();
        String header;
        if (complete && basePackage != null && registry.isCompositionModified()) {
            header = "Stan: gotowa\nŹródło: kompozycja zmodyfikowana · baza: "
                    + displayName(basePackage.manifest().name())
                    + " v" + basePackage.manifest().version();
        } else if (complete && activePackage != null) {
            header = "Stan: gotowa\nŹródło: kompletny pakiet ALPR · "
                    + displayName(activePackage.manifest().name())
                    + " v" + activePackage.manifest().version();
            String createdAt = shortDate(activePackage.manifest().createdAt());
            if (!createdAt.isEmpty()) header += " • " + createdAt;
        } else if (complete) {
            header = "Stan: gotowa\nŹródło: kompozycja z modeli mobilnych · MT i MZ aktywne";
        } else {
            header = "Stan: niekompletna\nBrakuje: " + missingRequiredRoles(registry);
            if (basePackage != null && registry.isCompositionModified()) {
                header += "\nBaza: " + displayName(basePackage.manifest().name())
                        + " v" + basePackage.manifest().version();
            }
        }
        InstalledModel activeVehicle = registry.getActive(ModelRole.VEHICLE);
        String vehicleDescription = activeVehicle == null
                && basePackage != null
                && basePackage.vehicleModel() != null
                ? describeUnavailableBase(basePackage.vehicleModel())
                : describe(activeVehicle, autoTuneManager, false);
        return new Presentation(
                header,
                vehicleDescription,
                describe(registry.getActive(ModelRole.PLATE), autoTuneManager, true),
                describe(registry.getActive(ModelRole.CHARACTER), autoTuneManager, true)
        );
    }

    private static String missingRequiredRoles(ModelRegistry registry) {
        boolean missingPlate = registry.getActive(ModelRole.PLATE) == null;
        boolean missingCharacter = registry.getActive(ModelRole.CHARACTER) == null;
        if (missingPlate && missingCharacter) return "MT i MZ";
        return missingPlate ? "MT" : "MZ";
    }

    private static String describe(
            InstalledModel model,
            AutoTuneManager autoTuneManager,
            boolean required
    ) {
        if (model == null) {
            return required
                    ? "Brak aktywnego modelu\nModel wymagany do uruchomienia pipeline’u"
                    : "Brak aktywnego modelu\nEtap opcjonalny — pipeline może działać bez MP";
        }
        ModelManifest manifest = model.manifest();
        ModelVariant selected = autoTuneManager.chosenVariant(model);
        ExecutionProfile profile = autoTuneManager.chosenProfile(model);
        ModelInputSpec input = selected.input(manifest.input());
        ModelOutputSpec output = selected.output(manifest.output());

        StringBuilder first = new StringBuilder(displayName(manifest.name()));
        if (!manifest.yoloFamily().isEmpty()) first.append(" • ").append(manifest.yoloFamily());
        if (manifest.parameterCount() > 0L) {
            first.append(String.format(Locale.ROOT, " %.2fM", manifest.parameterCount() / 1_000_000.0));
        }

        String hardware = profile.gpu ? "GPU" : "CPU ×" + profile.cpuThreads;
        String second = selected.runtime().wireName().toUpperCase(Locale.ROOT)
                + " · " + selected.precision().toUpperCase(Locale.ROOT)
                + " · " + hardware
                + " • " + input.width() + '×' + input.height()
                + (autoTuneManager.isVariantPinned(model) ? " • ręczny" : " • AutoTune");

        StringBuilder third = new StringBuilder(String.format(
                Locale.ROOT,
                "conf %.2f • IoU %.2f",
                output.confidenceThreshold(),
                output.iouThreshold()
        ));
        double map = manifest.metric("best_map50_95");
        if (!Double.isNaN(map)) third.append(String.format(Locale.ROOT, " • mAP50–95 %.3f", map));
        return first.append('\n').append(second).append('\n').append(third).toString();
    }

    private static String describeUnavailableBase(InstalledModel model) {
        ModelManifest manifest = model.manifest();
        StringBuilder value = new StringBuilder(displayName(manifest.name()));
        value.append("\nNieaktywny • brak obsługi backendu dla wariantów z pakietu");
        return value.toString();
    }

    private static String displayName(String value) {
        if (value == null) return "";
        int technicalSuffix = value.indexOf('|');
        String display = technicalSuffix >= 0 ? value.substring(0, technicalSuffix) : value;
        return display.trim();
    }

    private static String shortDate(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
    }

    public static final class Presentation {
        public final String summary;
        public final String vehicle;
        public final String plate;
        public final String character;

        private Presentation(String summary, String vehicle, String plate, String character) {
            this.summary = summary;
            this.vehicle = vehicle;
            this.plate = plate;
            this.character = character;
        }
    }
}
