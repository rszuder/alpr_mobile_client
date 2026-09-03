package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.ModelRuntime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ResearchExecutionChoiceTest {
    @Test
    public void explicitCpuProfilesResolveWithoutAutotuneOverride() throws Exception {
        ExecutionProfile one = ResearchExecutionChoice.CPU_1.resolve(
                ModelRuntime.ONNX,
                "onnx-fp32",
                new ExecutionProfile(ModelRuntime.ONNX, 4, false)
        );
        ExecutionProfile two = ResearchExecutionChoice.CPU_2.resolve(
                ModelRuntime.ONNX,
                "onnx-fp32",
                new ExecutionProfile(ModelRuntime.ONNX, 4, false)
        );
        ExecutionProfile four = ResearchExecutionChoice.CPU_4.resolve(
                ModelRuntime.ONNX,
                "onnx-fp32",
                new ExecutionProfile(ModelRuntime.ONNX, 1, false)
        );

        assertEquals(1, one.cpuThreads);
        assertEquals(2, two.cpuThreads);
        assertEquals(4, four.cpuThreads);
        assertFalse(one.gpu);
        assertEquals(ModelRuntime.ONNX, four.runtime);
    }

    @Test
    public void autoCopiesTheProfileResolvedAtSessionStart() throws Exception {
        ExecutionProfile frozen = ResearchExecutionChoice.AUTO.resolve(
                ModelRuntime.TFLITE,
                "tflite-fp32",
                ExecutionProfile.tfliteGpu()
        );

        assertTrue(frozen.gpu);
        assertEquals(1, frozen.cpuThreads);
    }

    @Test
    public void unsupportedGpuDoesNotSilentlyFallBackToCpu() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchExecutionChoice.GPU.resolve(
                        ModelRuntime.NCNN,
                        "ncnn-fp32",
                        new ExecutionProfile(ModelRuntime.NCNN, 2, false)
                )
        );
    }
}
