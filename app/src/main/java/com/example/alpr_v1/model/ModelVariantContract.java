package com.example.alpr_v1.model;

import java.util.Locale;

/** Waliduje kombinację runtime'u, precyzji i publicznego tensora wejściowego. */
final class ModelVariantContract {
    private ModelVariantContract() {
    }

    static void validate(
            ModelRuntime runtime,
            String precision,
            ModelInputSpec input
    ) {
        if (runtime == null) {
            throw new IllegalArgumentException("Wariant nie określa runtime'u");
        }
        if (input == null) {
            throw new IllegalArgumentException("Wariant nie określa wejścia modelu");
        }

        String normalizedPrecision = normalize(precision);
        if (!normalizedPrecision.equals("fp32")
                && !normalizedPrecision.equals("fp16")
                && !normalizedPrecision.equals("int8")
                && !normalizedPrecision.equals("uint8")) {
            throw new IllegalArgumentException(
                    "Nieobsługiwana precyzja wariantu: " + precision
            );
        }

        if (runtime != ModelRuntime.ONNX) return;

        if (!normalizedPrecision.equals("fp32")
                && !normalizedPrecision.equals("int8")) {
            throw new IllegalArgumentException(
                    "Backend ONNX obsługuje warianty fp32 oraz int8 QDQ"
            );
        }

        /*
         * Eksporter wykonuje statyczną kwantyzację S8S8 w formacie QDQ.
         * INT8 dotyczy wnętrza grafu, natomiast publiczne wejście pozostaje
         * FLOAT32 i dopiero QuantizeLinear zamienia aktywacje na int8.
         */
        if (!"FLOAT32".equals(input.dataType())) {
            throw new IllegalArgumentException(
                    normalizedPrecision.equals("int8")
                            ? "ONNX INT8 QDQ musi zachować publiczne wejście FLOAT32"
                            : "Backend ONNX wymaga publicznego wejścia FLOAT32"
            );
        }
    }

    static boolean isOnnxInt8(ModelVariant variant) {
        return variant != null
                && variant.runtime() == ModelRuntime.ONNX
                && "int8".equals(normalize(variant.precision()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
