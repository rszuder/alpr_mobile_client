package com.example.alpr_v1.experiment;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;
import java.util.function.Predicate;

/** A research selection is a requirement, not a preference eligible for AUTO fallback. */
public final class ResearchVariantSelection {
    private ResearchVariantSelection() { }
    public static ModelVariant require(InstalledModel model, String pinnedId, Predicate<ModelRuntime> available) {
        if (model == null || pinnedId == null || pinnedId.trim().isEmpty())
            throw new IllegalStateException("RESEARCH_VARIANT_PIN_REQUIRED: wybierz jawny wariant MT/MZ; AUTO jest niedozwolone");
        for (ModelVariant variant : model.manifest().variants()) if (pinnedId.equals(variant.id())) {
            if (!available.test(variant.runtime())) throw new IllegalStateException("RESEARCH_RUNTIME_UNAVAILABLE: " + variant.runtime().wireName());
            return variant;
        }
        throw new IllegalStateException("RESEARCH_PINNED_VARIANT_MISSING: " + pinnedId);
    }
}
