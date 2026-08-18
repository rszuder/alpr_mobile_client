package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelVariant;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TfliteBackend implements InferenceBackend {
    private final Interpreter interpreter;
    private final TensorInfo inputInfo;
    private final GpuDelegate gpuDelegate;
    private final boolean gpu;
    private final Map<Integer, ByteBuffer> outputBuffers = new LinkedHashMap<>();
    private final Map<Integer, TensorInfo> outputInfo = new LinkedHashMap<>();

    public TfliteBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile) {
        File modelFile = model.resolve(variant.primaryFile());
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(profile.cpuThreads);
        GpuDelegate createdDelegate = null;
        if (profile.gpu) {
            CompatibilityList compatibility = new CompatibilityList();
            if (!compatibility.isDelegateSupportedOnThisDevice()) {
                throw new IllegalStateException("Delegat GPU nie jest obsługiwany na tym urządzeniu");
            }
            createdDelegate = new GpuDelegate(compatibility.getBestOptionsForThisDevice());
            options.addDelegate(createdDelegate);
        }
        gpuDelegate = createdDelegate;
        gpu = profile.gpu;
        Interpreter createdInterpreter;
        try {
            createdInterpreter = new Interpreter(modelFile, options);
        } catch (RuntimeException error) {
            if (createdDelegate != null) createdDelegate.close();
            throw error;
        }
        interpreter = createdInterpreter;

        Tensor input = interpreter.getInputTensor(0);
        inputInfo = toInfo(0, input);
        for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
            Tensor tensor = interpreter.getOutputTensor(i);
            TensorInfo info = toInfo(i, tensor);
            outputInfo.put(i, info);
            outputBuffers.put(i, directBuffer(info.byteSize));
        }
    }

    @Override
    public synchronized int inputByteSize() {
        return inputInfo.byteSize;
    }

    @Override
    public TensorInfo inputInfo() {
        return inputInfo;
    }

    @Override
    public synchronized InferenceRunResult run(ByteBuffer input) {
        input.rewind();
        Map<Integer, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<Integer, ByteBuffer> entry : outputBuffers.entrySet()) {
            entry.getValue().rewind();
            outputs.put(entry.getKey(), entry.getValue());
        }
        interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);
        for (ByteBuffer output : outputBuffers.values()) output.rewind();
        return new InferenceRunResult(outputBuffers, outputInfo);
    }

    @Override
    public String runtimeName() {
        return gpu ? "LiteRT/GPU" : "LiteRT/CPU";
    }

    @Override
    public synchronized void close() {
        interpreter.close();
        if (gpuDelegate != null) gpuDelegate.close();
    }

    private static TensorInfo toInfo(int index, Tensor tensor) {
        Tensor.QuantizationParams quantization = tensor.quantizationParams();
        return new TensorInfo(
                index,
                tensor.shape(),
                tensor.dataType().toString(),
                tensor.numBytes(),
                quantization.getScale(),
                quantization.getZeroPoint()
        );
    }

    private static ByteBuffer directBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
}
