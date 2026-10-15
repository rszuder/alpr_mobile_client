package com.example.alpr_v1.vision;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Research-only T2-I-1 CPU unit tests; not Android end-to-end/runtime parity. */
public class Yolo26SplitLtrbScoresInt8DecoderTest {
    private static final int ANCHORS = 2100;
    private static final int[] GEOMETRY_SHAPE = {1, 4, ANCHORS};
    private static final int[] SCORES_SHAPE = {1, 36, ANCHORS};

    private static ByteBuffer filled(int count, byte value) {
        ByteBuffer result = ByteBuffer.allocate(count);
        for (int i = 0; i < count; i++) result.put(i, value);
        return result;
    }

    private static ByteBuffer geometry() { return filled(4 * ANCHORS, (byte) -126); }
    private static ByteBuffer scores() { return filled(36 * ANCHORS, (byte) -128); }
    private static void score(ByteBuffer scores, int anchor, int cls, int signed) {
        scores.put(cls * ANCHORS + anchor, (byte) signed);
    }
    private static void distances(ByteBuffer box, int anchor, int signed) {
        for (int channel = 0; channel < 4; channel++) box.put(channel * ANCHORS + anchor, (byte) signed);
    }
    private static List<Detection> decode(ByteBuffer box, ByteBuffer score, float threshold,
                                            boolean clip, boolean nms) {
        return Yolo26SplitLtrbScoresInt8Decoder.decode(
                box, GEOMETRY_SHAPE, Yolo26SplitLtrbScoresInt8Decoder.GEOMETRY_SCALE, -126,
                score, SCORES_SHAPE, 0.00390625f, -128, threshold, clip, nms, 0.45f
        );
    }

    @Test public void reconstructsP3P4P5AnchorCentersAt320() {
        ByteBuffer b = geometry(), s = scores();
        score(s, 0, 0, 100); score(s, 1600, 1, 99); score(s, 2000, 2, 98);
        List<Detection> list = decode(b, s, 0.25f, false, false);
        assertEquals(3, list.size());
        assertEquals(4f, list.get(0).centerX(), 0.0001f);
        assertEquals(4f, list.get(0).centerY(), 0.0001f);
        assertEquals(8f, list.get(1).centerX(), 0.0001f);
        assertEquals(8f, list.get(1).centerY(), 0.0001f);
        assertEquals(16f, list.get(2).centerX(), 0.0001f);
        assertEquals(16f, list.get(2).centerY(), 0.0001f);
    }

    @Test public void topkUsesAnchorThenAnchorClassPairsAndStableTies() {
        ByteBuffer b = geometry(), s = scores();
        score(s, 820, 10, 120); score(s, 820, 11, 120);
        score(s, 821, 12, 120);
        List<Detection> list = decode(b, s, 0.25f, false, false);
        assertEquals(3, list.size());
        assertEquals(10, list.get(0).classId);
        assertEquals(11, list.get(1).classId);
        assertEquals(12, list.get(2).classId);
        assertEquals(120f / 256f + 0.5f, list.get(0).confidence, 1e-6f);
    }

    @Test public void nmsSuppressesSameClassDuplicateButKeepsDifferentClass() {
        ByteBuffer b = geometry(), s = scores();
        distances(b, 820, -110);
        distances(b, 821, -110);
        score(s, 820, 10, 105); score(s, 821, 10, 75); score(s, 821, 11, 115);
        List<Detection> reference = decode(b, s, 0.25f, true, false);
        List<Detection> app = decode(b, s, 0.25f, true, true);
        assertEquals(3, reference.size());
        assertEquals(2, app.size());
        assertEquals(11, app.get(0).classId);
        assertEquals(10, app.get(1).classId);
    }

    @Test public void preservesSeparatedRepeatedCharacterBoxes() {
        ByteBuffer b = geometry(), s = scores();
        distances(b, 820, -120);
        distances(b, 850, -120);
        score(s, 820, 1, 100); score(s, 850, 1, 90);
        List<Detection> selected = decode(b, s, 0.25f, true, true);
        assertEquals(2, selected.size());
        assertEquals(1, selected.get(0).classId);
        assertEquals(1, selected.get(1).classId);
    }

    @Test public void supportsLegacyPointTenCandidateFloorWithoutGlobalThresholdOverride() {
        ByteBuffer b = geometry(), s = scores();
        score(s, 820, 12, -102); // score = 26/256 = 0.1015625
        assertEquals(0, decode(b, s, 0.25f, true, true).size());
        assertEquals(1, decode(b, s, 0.10f, true, true).size());
    }

    @Test public void clipsOnlyWhenRequestedAndPreservesClassQIndex() {
        ByteBuffer b = geometry(), s = scores();
        distances(b, 0, -110); // anchor center x=4,y=4; LTRB reaches out of frame
        score(s, 0, 26, 127); // Q is still class 26 of the 36-output model
        List<Detection> raw = decode(b, s, 0.25f, false, false);
        List<Detection> clipped = decode(b, s, 0.25f, true, true);
        assertEquals(1, raw.size());
        assertEquals(26, raw.get(0).classId);
        assertTrue(raw.get(0).left < 0f);
        assertTrue(raw.get(0).top < 0f);
        assertEquals(0f, clipped.get(0).left, 0f);
        assertEquals(0f, clipped.get(0).top, 0f);
    }

    @Test public void refusesWrongShapesByteCountsAndQuantizer() {
        ByteBuffer b = geometry(), s = scores();
        try {
            Yolo26SplitLtrbScoresInt8Decoder.decode(b, new int[]{1, 2100, 4},
                    Yolo26SplitLtrbScoresInt8Decoder.GEOMETRY_SCALE, -126,
                    s, SCORES_SHAPE, 0.00390625f, -128, 0.25f, true, true, 0.45f);
            fail("layout mismatch must fail closed");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("shape")); }
        try {
            decode(filled(30, (byte) 0), s, 0.25f, true, true);
            fail("short tensor must fail closed");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("bytes")); }
        try {
            Yolo26SplitLtrbScoresInt8Decoder.decode(b, GEOMETRY_SHAPE,
                    Yolo26SplitLtrbScoresInt8Decoder.GEOMETRY_SCALE, -126,
                    s, SCORES_SHAPE, 0.004f, -128, 0.25f, true, true, 0.45f);
            fail("unknown quantizer must fail closed");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("quantizer")); }
    }

    @Test public void rejectsNonFiniteThresholds() {
        ByteBuffer b = geometry(), s = scores();
        try { decode(b, s, Float.NaN, true, true); fail("NaN threshold"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("threshold")); }
    }
}
