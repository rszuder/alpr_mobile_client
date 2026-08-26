package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.ModelInputSpec;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/** Sprawdza kontrakt i obsługę operatorów modelu ONNX bez wykonywania inferencji. */
public final class OnnxModelCompatibilityValidator {
    private OnnxModelCompatibilityValidator() {
    }

    public static void validateQuantizedModel(
            File modelFile,
            ModelInputSpec inputSpec
    ) {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IllegalArgumentException("Brak pliku ONNX INT8");
        }
        if (inputSpec == null || !"FLOAT32".equals(inputSpec.dataType())) {
            throw new IllegalArgumentException(
                    "ONNX INT8 QDQ musi mieć publiczne wejście FLOAT32"
            );
        }

        validateQdqGraphMarkers(modelFile);

        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1);
            options.setOptimizationLevel(
                    OrtSession.SessionOptions.OptLevel.ALL_OPT
            );
            try (OrtSession session = environment.createSession(
                    modelFile.getAbsolutePath(),
                    options
            )) {
                validateSessionContract(session, inputSpec);
            }
        } catch (OrtException error) {
            throw new IllegalArgumentException(
                    "ONNX Runtime 1.26 nie może otworzyć modelu INT8 QDQ: "
                            + error.getMessage(),
                    error
            );
        }
    }

    static void validateQdqGraphMarkers(File modelFile) {
        try {
            if (!containsQdqMarkers(modelFile)) {
                throw new IllegalArgumentException(
                        "Plik oznaczony jako ONNX INT8 nie zawiera grafu QDQ"
                );
            }
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "Nie można sprawdzić grafu ONNX INT8: "
                            + error.getMessage(),
                    error
            );
        }
    }

    private static boolean containsQdqMarkers(File modelFile) throws IOException {
        byte[] quantize = "QuantizeLinear".getBytes(StandardCharsets.US_ASCII);
        byte[] dequantize = "DequantizeLinear".getBytes(StandardCharsets.US_ASCII);
        int quantizeMatched = 0;
        int dequantizeMatched = 0;
        boolean hasQuantize = false;
        boolean hasDequantize = false;
        byte[] buffer = new byte[64 * 1024];
        try (BufferedInputStream input = new BufferedInputStream(
                new FileInputStream(modelFile),
                buffer.length
        )) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                for (int index = 0; index < read; index++) {
                    byte value = buffer[index];
                    if (!hasQuantize) {
                        quantizeMatched = advanceToken(
                                value,
                                quantize,
                                quantizeMatched
                        );
                        hasQuantize = quantizeMatched == quantize.length;
                    }
                    if (!hasDequantize) {
                        dequantizeMatched = advanceToken(
                                value,
                                dequantize,
                                dequantizeMatched
                        );
                        hasDequantize = dequantizeMatched == dequantize.length;
                    }
                    if (hasQuantize && hasDequantize) return true;
                }
            }
        }
        return false;
    }

    private static int advanceToken(byte value, byte[] token, int matched) {
        if (value == token[matched]) return matched + 1;
        return value == token[0] ? 1 : 0;
    }

    static void validateSessionContract(
            OrtSession session,
            ModelInputSpec inputSpec
    ) throws OrtException {
        if (session.getInputNames().size() != 1) {
            throw new IllegalArgumentException(
                    "Backend ONNX v1 obsługuje modele z jednym wejściem"
            );
        }

        String inputName = session.getInputNames().iterator().next();
        NodeInfo inputNode = session.getInputInfo().get(inputName);
        if (inputNode == null
                || !(inputNode.getInfo() instanceof ai.onnxruntime.TensorInfo)) {
            throw new IllegalArgumentException("Wejście ONNX nie jest tensorem");
        }
        ai.onnxruntime.TensorInfo input =
                (ai.onnxruntime.TensorInfo) inputNode.getInfo();
        if (input.type != OnnxJavaType.FLOAT) {
            throw new IllegalArgumentException(
                    "ONNX INT8 QDQ musi mieć publiczne wejście FLOAT32"
            );
        }

        long[] expectedShape = expectedInputShape(inputSpec);
        if (!Arrays.equals(expectedShape, input.getShape())) {
            throw new IllegalArgumentException(
                    "Manifest wejścia ONNX nie odpowiada tensorowi: manifest="
                            + Arrays.toString(expectedShape)
                            + ", tensor="
                            + Arrays.toString(input.getShape())
            );
        }

        Map<String, NodeInfo> outputs = session.getOutputInfo();
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Model ONNX nie ma wyjścia");
        }
        for (Map.Entry<String, NodeInfo> entry : outputs.entrySet()) {
            if (!(entry.getValue().getInfo()
                    instanceof ai.onnxruntime.TensorInfo)) {
                throw new IllegalArgumentException(
                        "Wyjście ONNX " + entry.getKey() + " nie jest tensorem"
                );
            }
            ai.onnxruntime.TensorInfo output =
                    (ai.onnxruntime.TensorInfo) entry.getValue().getInfo();
            if (output.type != OnnxJavaType.FLOAT) {
                throw new IllegalArgumentException(
                        "ONNX INT8 QDQ musi udostępniać wyjście FLOAT32: "
                                + entry.getKey()
                );
            }
            for (long dimension : output.getShape()) {
                if (dimension <= 0L || dimension > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Model ONNX ma dynamiczny lub zbyt duży wymiar wyjścia"
                    );
                }
            }
        }
    }

    static long[] expectedInputShape(ModelInputSpec inputSpec) {
        if ("NCHW".equals(inputSpec.layout())) {
            return new long[]{
                    1L,
                    inputSpec.channels(),
                    inputSpec.height(),
                    inputSpec.width()
            };
        }
        if ("NHWC".equals(inputSpec.layout())) {
            return new long[]{
                    1L,
                    inputSpec.height(),
                    inputSpec.width(),
                    inputSpec.channels()
            };
        }
        throw new IllegalArgumentException(
                "ONNX wymaga layoutu NCHW albo NHWC"
        );
    }
}
