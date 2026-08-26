package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.ModelInputSpec;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public final class OnnxModelCompatibilityValidatorTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void buildsNchwShapeFromManifest() {
        assertArrayEquals(
                new long[]{1L, 3L, 384L, 640L},
                OnnxModelCompatibilityValidator.expectedInputShape(
                        input(640, 384, "NCHW")
                )
        );
    }

    @Test
    public void buildsNhwcShapeFromManifest() {
        assertArrayEquals(
                new long[]{1L, 384L, 640L, 3L},
                OnnxModelCompatibilityValidator.expectedInputShape(
                        input(640, 384, "NHWC")
                )
        );
    }

    @Test
    public void rejectsUnknownLayout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OnnxModelCompatibilityValidator.expectedInputShape(
                        input(640, 384, "HWCN")
                )
        );
    }

    @Test
    public void acceptsQdqMarkersInOnnxGraph() throws Exception {
        File model = temporaryFolder.newFile("model.onnx");
        Files.write(
                model.toPath(),
                "Conv\u0000QuantizeLinear\u0000DequantizeLinear\u0000output"
                        .getBytes(StandardCharsets.US_ASCII)
        );

        OnnxModelCompatibilityValidator.validateQdqGraphMarkers(model);
    }

    @Test
    public void rejectsModelMarkedInt8WithoutQdqGraph() throws Exception {
        File model = temporaryFolder.newFile("model.onnx");
        Files.write(
                model.toPath(),
                "Conv\u0000Relu\u0000output".getBytes(StandardCharsets.US_ASCII)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OnnxModelCompatibilityValidator
                        .validateQdqGraphMarkers(model)
        );
    }

    private static ModelInputSpec input(int width, int height, String layout) {
        return new ModelInputSpec(
                width,
                height,
                3,
                layout,
                "RGB",
                "FLOAT32",
                1f / 255f,
                0f
        );
    }
}
