package com.example.alpr_v1.model;

import com.example.alpr_v1.inference.ModelTensorContractValidator;
import com.example.alpr_v1.inference.TensorInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public final class ModelOutputTensorContractTest {
    @Test
    public void computesPoseRawAndEndToEndAttributeCounts() {
        assertEquals(
                13,
                ModelTensorContractValidator.expectedRawAttributes(1, false, 4, 2)
        );
        assertEquals(
                14,
                ModelTensorContractValidator.expectedEndToEndAttributes(4, 2)
        );
    }

    @Test
    public void acceptsRawPoseInBothTensorLayouts() {
        ModelTensorContractValidator.validateOutput(
                output(false, true),
                tensor(1, 13, 8400)
        );
        ModelTensorContractValidator.validateOutput(
                output(false, false),
                tensor(1, 8400, 13)
        );
    }

    @Test
    public void rejectsRawTensorDeclaredAsEndToEnd() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ModelTensorContractValidator.validateOutput(
                        output(true, false),
                        tensor(1, 8400, 13)
                )
        );

        assertTrue(error.getMessage().contains("RAW YOLO (13 atrybutów)"));
        assertTrue(error.getMessage().contains("END-TO-END (14)"));
    }

    @Test
    public void acceptsEndToEndPoseTensor() {
        ModelTensorContractValidator.validateOutput(
                output(true, false),
                tensor(1, 300, 14)
        );
    }

    private static TensorInfo tensor(int... shape) {
        int elements = 1;
        for (int dimension : shape) elements = Math.multiplyExact(elements, dimension);
        return new TensorInfo(
                0,
                shape,
                "FLOAT32",
                Math.multiplyExact(elements, Float.BYTES),
                0f,
                0
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
