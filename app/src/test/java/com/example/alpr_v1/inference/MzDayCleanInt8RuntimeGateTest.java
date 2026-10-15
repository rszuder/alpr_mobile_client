package com.example.alpr_v1.inference;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertThrows;

/** Research admission policy only; not an Android runtime or F4 test. */
public final class MzDayCleanInt8RuntimeGateTest {
    private static void allow(boolean debug, boolean gpu, String sha, int size) {
        MzDayCleanInt8RuntimeGate.requirePinnedArtifact(sha, size, debug, gpu);
    }

    @Test public void allowsOnlyKnownShaInDebugCpu() {
        allow(true, false, MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                MzDayCleanInt8RuntimeGate.MODEL_BYTES);
    }

    @Test public void refusesReleaseEvenForCorrectModel() {
        assertThrows(IllegalArgumentException.class, () -> allow(false, false,
                MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                MzDayCleanInt8RuntimeGate.MODEL_BYTES));
    }

    @Test public void refusesGpuEvenForCorrectModel() {
        assertThrows(IllegalArgumentException.class, () -> allow(true, true,
                MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                MzDayCleanInt8RuntimeGate.MODEL_BYTES));
    }

    @Test public void refusesWrongArtifactShaAndSize() {
        assertThrows(IllegalArgumentException.class, () -> allow(true, false,
                "b2f715399bdcb574da899e202388b5324d66f574b8519626cb611bf0e263e792",
                MzDayCleanInt8RuntimeGate.MODEL_BYTES));
        assertThrows(IllegalArgumentException.class, () -> allow(true, false,
                MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                MzDayCleanInt8RuntimeGate.MODEL_BYTES - 1));
    }

    private static List<String> alphabet() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < 10; i++) result.add(Integer.toString(i));
        for (char c = 'A'; c <= 'Z'; c++) result.add(Character.toString(c));
        return result;
    }

    @Test public void acceptsAll36ClassesIncludingQIndex26() {
        List<String> labels = alphabet();
        MzDayCleanInt8RuntimeGate.requireClassOrder(labels);
        org.junit.Assert.assertEquals("Q", labels.get(26));
    }

    @Test public void refusesOmittedOrSwappedQ() {
        List<String> labels = alphabet();
        labels.remove(26);
        final List<String> missing = labels;
        assertThrows(IllegalArgumentException.class,
                () -> MzDayCleanInt8RuntimeGate.requireClassOrder(missing));
        labels = alphabet();
        java.util.Collections.swap(labels, 26, 27);
        final List<String> wrong = labels;
        assertThrows(IllegalArgumentException.class,
                () -> MzDayCleanInt8RuntimeGate.requireClassOrder(wrong));
    }
}
