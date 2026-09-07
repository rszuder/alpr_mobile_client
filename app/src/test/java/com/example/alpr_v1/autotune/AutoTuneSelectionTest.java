package com.example.alpr_v1.autotune;

import com.example.alpr_v1.model.ModelRuntime;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class AutoTuneSelectionTest {
    @Test public void fasterInt8WinsAgainstFp32AcrossRuntimesAndProfiles() {
        AutoTuneResult.Candidate int8 = candidate("tflite-int8", ModelRuntime.TFLITE, 8, "");
        assertSame(int8, AutoTuneManager.fastestSuccessful(Arrays.asList(
                candidate("tflite-fp32", ModelRuntime.TFLITE, 21, ""),
                candidate("onnx-fp32", ModelRuntime.ONNX, 15, ""),
                int8,
                candidate("onnx-int8", ModelRuntime.ONNX, 11, ""),
                candidate("ncnn-fp32", ModelRuntime.NCNN, 14, ""))));
    }

    @Test public void failedAndNonFiniteMeasurementsCannotWin() {
        AutoTuneResult.Candidate fp32 = candidate("onnx-fp32", ModelRuntime.ONNX, 10, "");
        assertSame(fp32, AutoTuneManager.fastestSuccessful(Arrays.asList(
                candidate("tflite-int8", ModelRuntime.TFLITE, 0, "contract failure"),
                candidate("onnx-int8", ModelRuntime.ONNX, Double.NaN, ""),
                candidate("ncnn-fp32", ModelRuntime.NCNN, -1, ""), fp32)));
        assertNull(AutoTuneManager.fastestSuccessful(Collections.emptyList()));
    }

    @Test public void equalTimesPreserveDeterministicBenchmarkOrder() {
        AutoTuneResult.Candidate first = candidate("tflite-int8", ModelRuntime.TFLITE, 10, "");
        assertSame(first, AutoTuneManager.fastestSuccessful(Arrays.asList(first,
                candidate("tflite-fp32", ModelRuntime.TFLITE, 10, ""))));
    }

    private static AutoTuneResult.Candidate candidate(String id, ModelRuntime runtime,
                                                     double median, String error) {
        return new AutoTuneResult.Candidate(id, runtime, 2, false, median, median, 1, 1, error);
    }
}
