package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;

import java.util.Arrays;

public final class ModelTensorContractValidator {
    private ModelTensorContractValidator() {
    }

    public static void validateInput(ModelInputSpec spec, TensorInfo tensor) {
        if (spec == null || tensor == null) {
            throw new IllegalArgumentException("Brak kontraktu wejścia modelu");
        }
        int[] expectedShape;
        if ("NCHW".equals(spec.layout())) {
            expectedShape = new int[]{1, spec.channels(), spec.height(), spec.width()};
        } else if ("NHWC".equals(spec.layout())) {
            expectedShape = new int[]{1, spec.height(), spec.width(), spec.channels()};
        } else {
            throw new IllegalArgumentException("Nieobsługiwany layout wejścia: " + spec.layout());
        }
        if (!Arrays.equals(expectedShape, tensor.shape)) {
            throw new IllegalArgumentException(
                    "Manifest wejścia nie odpowiada tensorowi: manifest="
                            + Arrays.toString(expectedShape)
                            + ", tensor="
                            + Arrays.toString(tensor.shape)
            );
        }
        if (!spec.dataType().equals(tensor.dataType)) {
            throw new IllegalArgumentException(
                    "Manifest wejścia wymaga " + spec.dataType()
                            + ", a artefakt udostępnia " + tensor.dataType
            );
        }
        if (spec.quantizationScale() != null &&
                (Math.abs(spec.quantizationScale() - tensor.quantizationScale)
                        > Math.max(1e-8f, Math.abs(spec.quantizationScale()) * 1e-5f)
                || spec.quantizationZeroPoint() != tensor.quantizationZeroPoint)) {
            throw new IllegalArgumentException("Kwantyzacja tensora nie odpowiada manifestowi");
        }
        if (("INT8".equals(tensor.dataType) || "UINT8".equals(tensor.dataType))
                && (!Float.isFinite(tensor.quantizationScale) || tensor.quantizationScale <= 0)) {
            throw new IllegalArgumentException("Brak poprawnej skali kwantyzacji tensora");
        }
    }

    public static void validateOutput(ModelOutputSpec spec, TensorInfo tensor) {
        if (spec == null || tensor == null) {
            throw new IllegalArgumentException("Brak kontraktu wyjścia modelu");
        }
        if (!"FLOAT32".equals(tensor.dataType)
                && !"UINT8".equals(tensor.dataType)
                && !"INT8".equals(tensor.dataType)) {
            throw new IllegalArgumentException(
                    "Nieobsługiwany typ wyjścia modelu: " + tensor.dataType
            );
        }
        int[] shape = tensor.shape;
        if (shape.length < 2) {
            throw new IllegalArgumentException(
                    "Wyjście YOLO musi mieć co najmniej dwa wymiary: "
                            + Arrays.toString(shape)
            );
        }
        for (int index = 0; index < shape.length; index++) {
            if (shape[index] <= 0) {
                throw new IllegalArgumentException(
                        "Wyjście YOLO ma nieprawidłowy kształt: " + Arrays.toString(shape)
                );
            }
            if (index < shape.length - 2 && shape[index] != 1) {
                throw new IllegalArgumentException(
                        "Obsługiwany jest wyłącznie batch size 1: " + Arrays.toString(shape)
                );
            }
        }

        int attributes = spec.channelsFirst()
                ? shape[shape.length - 2]
                : shape[shape.length - 1];
        int expected = spec.endToEnd()
                ? expectedEndToEndAttributes(
                spec.keypointCount(), spec.keypointDimensions()
        )
                : expectedRawAttributes(
                spec.classCount(),
                spec.hasObjectness(),
                spec.keypointCount(),
                spec.keypointDimensions()
        );
        if (attributes == expected) return;

        int alternate = spec.endToEnd()
                ? expectedRawAttributes(
                spec.classCount(),
                spec.hasObjectness(),
                spec.keypointCount(),
                spec.keypointDimensions()
        )
                : expectedEndToEndAttributes(
                spec.keypointCount(), spec.keypointDimensions()
        );
        if (attributes == alternate) {
            String actualFormat = spec.endToEnd() ? "RAW YOLO" : "END-TO-END";
            String requiredFormat = spec.endToEnd() ? "END-TO-END" : "RAW YOLO";
            throw new IllegalArgumentException(
                    "Model zwraca " + actualFormat + " (" + attributes
                            + " atrybutów), a konfiguracja wymaga " + requiredFormat
                            + " (" + expected + ")."
            );
        }
        throw new IllegalArgumentException(
                "Tensor wyjściowy ma " + attributes + " atrybutów, a konfiguracja "
                        + (spec.endToEnd() ? "END-TO-END" : "RAW YOLO")
                        + " wymaga " + expected + ". Kształt=" + Arrays.toString(shape)
        );
    }

    public static int expectedRawAttributes(
            int classCount,
            boolean hasObjectness,
            int keypointCount,
            int keypointDimensions
    ) {
        return Math.addExact(
                Math.addExact(4 + (hasObjectness ? 1 : 0), classCount),
                Math.multiplyExact(keypointCount, keypointDimensions)
        );
    }

    public static int expectedEndToEndAttributes(
            int keypointCount,
            int keypointDimensions
    ) {
        return Math.addExact(6, Math.multiplyExact(keypointCount, keypointDimensions));
    }
}
