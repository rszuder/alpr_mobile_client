package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;

public final class RuntimeModelContractException extends IllegalStateException {
    public RuntimeModelContractException(
            InstalledModel model,
            ModelVariant variant,
            String detail,
            Throwable cause
    ) {
        super(message(model, variant, detail), cause);
    }

    private static String message(
            InstalledModel model,
            ModelVariant variant,
            String detail
    ) {
        ModelRole role = model.manifest().role();
        return stage(role) + " / " + variant.id() + " nie odpowiada manifestowi. "
                + detail
                + " Zaimportuj ponownie model wyeksportowany z aktualnej wersji Desktop.";
    }

    private static String stage(ModelRole role) {
        if (role == ModelRole.VEHICLE) return "MP";
        if (role == ModelRole.PLATE) return "MT";
        return "MZ";
    }
}
