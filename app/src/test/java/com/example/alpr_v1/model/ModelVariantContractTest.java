package com.example.alpr_v1.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void canonicalizesExplicitNcnnEndToEndOverrideToRaw() {
        ModelOutputSpec endToEnd = output(true, true);
        ModelVariant variant = variant(endToEnd);

        ModelOutputSpec resolved = variant.output(endToEnd);

        assertEquals("ultralytics_pose_raw_v1", resolved.decoder());
        assertEquals("raw_yolo", resolved.outputFormat());
        assertEquals("xywh", resolved.boxFormat());
        assertTrue(resolved.nmsRequired());
        assertFalse(resolved.endToEnd());
    }

    @Test
    public void preservesExplicitNcnnRawOverride() {
        ModelOutputSpec raw = output(false, true);

        assertSame(raw, variant(raw).output(output(true, true)));
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

    private static ModelVariant variant(ModelOutputSpec outputOverride) {
        return new ModelVariant(
                "ncnn-fp32",
                ModelRuntime.NCNN,
                "fp32",
                Collections.singletonList("variants/model.param"),
                Collections.emptyMap(),
                null,
                outputOverride
        );
    }

    private static ModelOutputSpec output(boolean endToEnd, boolean channelsFirst) {
        return new ModelOutputSpec(
                endToEnd
                        ? "ultralytics_pose_end2end_v1"
                        : "ultralytics_pose_raw_v1",
                endToEnd ? "end2end_detections" : "raw_yolo",
                endToEnd ? "xyxy" : "xywh",
                1,
                4,
                2,
                false,
                channelsFirst,
                false,
                false,
                !endToEnd,
                0.25f,
                0.45f,
                4,
                5
        );
    }
}
