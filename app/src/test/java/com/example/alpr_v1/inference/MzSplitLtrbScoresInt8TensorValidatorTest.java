package com.example.alpr_v1.inference;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** T2-I-2 JVM-only negative tests for strict signed INT8 I/O descriptors. */
public class MzSplitLtrbScoresInt8TensorValidatorTest {
    private static final float IN_SCALE = 0.003921568859368563f;
    private static final float BOX_SCALE = 0.006233580876141787f;
    private static final float SCORE_SCALE = 0.00390625f;
    private static TensorInfo input() {
        return new TensorInfo(0, new int[]{1,320,320,3}, "INT8", 307200, IN_SCALE, -128);
    }
    private static TensorInfo boxes() {
        return new TensorInfo(0, new int[]{1,4,2100}, "INT8", 8400, BOX_SCALE, -126);
    }
    private static TensorInfo scores() {
        return new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75600, SCORE_SCALE, -128);
    }
    private static Map<Integer, TensorInfo> outputs() {
        Map<Integer, TensorInfo> result = new LinkedHashMap<>();
        result.put(0, boxes());
        result.put(1, scores());
        return result;
    }
    private static void reject(String messagePart, Runnable check) {
        try { check.run(); fail("Expected fail-closed: " + messagePart); }
        catch (IllegalArgumentException expected) {
            assertTrue("Unexpected rejection: " + expected.getMessage(),
                    expected.getMessage().contains(messagePart));
        }
    }

    @Test public void acceptsPinnedContractRegardlessOfMapIterationOrder() {
        Map<Integer, TensorInfo> reversed = new LinkedHashMap<>();
        reversed.put(1, scores()); reversed.put(0, boxes());
        MzSplitLtrbScoresInt8TensorValidator.validate(input(), reversed);
        assertEquals(2, reversed.size());
    }
    @Test public void refusesMissingOutputPort() {
        Map<Integer, TensorInfo> out = outputs(); out.remove(1);
        reject("ports", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesAdditionalOutputPort() {
        Map<Integer, TensorInfo> out = outputs(); out.put(2, scores());
        reject("ports", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesSwappedOutputs() {
        Map<Integer, TensorInfo> out = new LinkedHashMap<>(); out.put(0, scores()); out.put(1, boxes());
        reject("port mismatch", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesMislabelledTensorIndexEvenWhenShapeMatches() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(0, new TensorInfo(1, new int[]{1,4,2100}, "INT8", 8400, BOX_SCALE, -126));
        reject("port mismatch", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesInputFloat32AndUint8() {
        reject("INT8", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,320,320,3},
                "FLOAT32", 1228800, 0f, 0), outputs()));
        reject("INT8", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,320,320,3},
                "UINT8", 307200, IN_SCALE, -128), outputs()));
    }
    @Test public void refusesInputShapeTranspose() {
        reject("shape", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,3,320,320},
                "INT8", 307200, IN_SCALE, -128), outputs()));
    }
    @Test public void refusesInputByteCount() {
        reject("byte count", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,320,320,3},
                "INT8", 307199, IN_SCALE, -128), outputs()));
    }
    @Test public void refusesInputQuantizerScaleAndZeroPoint() {
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,320,320,3},
                "INT8", 307200, 0.01f, -128), outputs()));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(new TensorInfo(0, new int[]{1,320,320,3},
                "INT8", 307200, IN_SCALE, 0), outputs()));
    }
    @Test public void refusesGeometryWrongLayout() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(0, new TensorInfo(0, new int[]{1,2100,4}, "INT8", 8400, BOX_SCALE, -126));
        reject("shape", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesScoresWrongLayoutOrClassCount() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(1, new TensorInfo(1, new int[]{1,2100,36}, "INT8", 75600, SCORE_SCALE, -128));
        reject("shape", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
        out.put(1, new TensorInfo(1, new int[]{1,35,2100}, "INT8", 73500, SCORE_SCALE, -128));
        reject("shape", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesGeometryWrongDataType() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(0, new TensorInfo(0, new int[]{1,4,2100}, "UINT8", 8400, BOX_SCALE, -126));
        reject("INT8", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesScoresWrongDataType() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "FLOAT32", 302400, SCORE_SCALE, -128));
        reject("INT8", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesGeometryWrongScaleAndZeroPoint() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(0, new TensorInfo(0, new int[]{1,4,2100}, "INT8", 8400, 0.008f, -126));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
        out.put(0, new TensorInfo(0, new int[]{1,4,2100}, "INT8", 8400, BOX_SCALE, -128));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesScoresWrongScaleAndZeroPoint() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75600, 0.01f, -128));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75600, SCORE_SCALE, 0));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
    @Test public void refusesWrongOutputBufferLengths() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(0, new TensorInfo(0, new int[]{1,4,2100}, "INT8", 8401, BOX_SCALE, -126));
        Map<Integer, TensorInfo> first = out;
        reject("byte count", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), first));
        out = outputs();
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75601, SCORE_SCALE, -128));
        Map<Integer, TensorInfo> caseTwo = out;
        reject("byte count", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), caseTwo));
    }
    @Test public void rejectsNullMapAndNullTensor() {
        reject("ports", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), null));
        Map<Integer, TensorInfo> out = outputs(); out.put(1, null);
        reject("missing", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
        reject("missing", () -> MzSplitLtrbScoresInt8TensorValidator.validate(null, outputs()));
    }
    @Test public void rejectsNonFiniteQuantizer() {
        Map<Integer, TensorInfo> out = outputs();
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75600, Float.NaN, -128));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
        out.put(1, new TensorInfo(1, new int[]{1,36,2100}, "INT8", 75600, Float.POSITIVE_INFINITY, -128));
        reject("quantizer", () -> MzSplitLtrbScoresInt8TensorValidator.validate(input(), out));
    }
}
