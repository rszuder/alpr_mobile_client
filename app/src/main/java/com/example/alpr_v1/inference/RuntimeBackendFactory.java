package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

public final class RuntimeBackendFactory {
    private RuntimeBackendFactory() {}

    public static boolean isRuntimeAvailable(ModelRuntime runtime) {
        return runtime == ModelRuntime.TFLITE || runtime == ModelRuntime.ONNX;
    }

    public static String unavailableReason(ModelRuntime runtime) {
        switch (runtime) {
            case NCNN:
                return "Backend NCNN/JNI nie został jeszcze dołączony do APK";
            default:
                return "Runtime jest dostępny";
        }
    }

    public static InferenceBackend create(
            InstalledModel model,
            ModelVariant variant,
            ExecutionProfile profile
    ) {
        if (variant.runtime() != profile.runtime) {
            throw new IllegalArgumentException("Profil i wariant używają różnych runtime'ów");
        }
        if (variant.runtime() == ModelRuntime.TFLITE) {
            return new TfliteBackend(model, variant, profile);
        }
        if (variant.runtime() == ModelRuntime.ONNX) {
            return new OnnxBackend(model, variant, profile);
        }
        throw new UnsupportedOperationException(unavailableReason(variant.runtime()));
    }
}
