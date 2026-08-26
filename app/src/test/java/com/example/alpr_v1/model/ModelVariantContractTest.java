package com.example.alpr_v1.model;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public final class ModelVariantContractTest {
    @Test
    public void acceptsOnnxInt8QdqWithFloatInterface() {
        ModelVariantContract.validate(
                ModelRuntime.ONNX,
                "int8",
                input("NCHW", "FLOAT32")
        );
    }

    @Test
    public void rejectsIntegerPublicInputForOnnxInt8Qdq() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelVariantContract.validate(
                        ModelRuntime.ONNX,
                        "int8",
                        input("NCHW", "INT8")
                )
        );
    }

    @Test
    public void rejectsUnsupportedOnnxPrecision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelVariantContract.validate(
                        ModelRuntime.ONNX,
                        "uint8",
                        input("NCHW", "FLOAT32")
                )
        );
    }

    @Test
    public void keepsTfliteInt8ContractSupported() {
        ModelVariantContract.validate(
                ModelRuntime.TFLITE,
                "int8",
                input("NHWC", "INT8")
        );
    }

    @Test
    public void rejectsUnknownPrecisionForEveryRuntime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelVariantContract.validate(
                        ModelRuntime.TFLITE,
                        "int4",
                        input("NHWC", "INT8")
                )
        );
    }

    private static ModelInputSpec input(String layout, String dataType) {
        return new ModelInputSpec(
                640,
                640,
                3,
                layout,
                "RGB",
                dataType,
                1f / 255f,
                0f
        );
    }
}
