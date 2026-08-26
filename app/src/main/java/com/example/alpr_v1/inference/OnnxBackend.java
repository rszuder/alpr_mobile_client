package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelVariant;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public final class OnnxBackend implements InferenceBackend {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final long[] inputShape;
    private final TensorInfo inputInfo;

    public OnnxBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile) {
        try {
            environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(profile.cpuThreads);
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                session = environment.createSession(model.resolve(variant.primaryFile()).getAbsolutePath(), options);
            }
            if (session.getInputNames().size() != 1) {
                session.close();
                throw new IllegalArgumentException("Backend ONNX v1 obsługuje modele z jednym wejściem");
            }
            inputName = session.getInputNames().iterator().next();
            NodeInfo node = session.getInputInfo().get(inputName);
            if (!(node.getInfo() instanceof ai.onnxruntime.TensorInfo)) {
                session.close();
                throw new IllegalArgumentException("Wejście ONNX nie jest tensorem");
            }
            ai.onnxruntime.TensorInfo info = (ai.onnxruntime.TensorInfo) node.getInfo();
            inputShape = info.getShape();
            if (info.type != OnnxJavaType.FLOAT) {
                session.close();
                throw new IllegalArgumentException("Backend ONNX v1 wymaga wejścia FLOAT32");
            }
            try {
                OnnxModelCompatibilityValidator.validateSessionContract(
                        session,
                        variant.input(model.manifest().input())
                );
            } catch (IllegalArgumentException error) {
                session.close();
                throw error;
            }
            inputInfo = tensorInfo(0, info);
        } catch (OrtException e) {
            throw new IllegalStateException("Nie można otworzyć modelu ONNX: " + e.getMessage(), e);
        }
    }

    @Override
    public int inputByteSize() {
        return inputInfo.byteSize;
    }

    @Override
    public TensorInfo inputInfo() {
        return inputInfo;
    }

    @Override
    public synchronized InferenceRunResult run(ByteBuffer input) {
        input.rewind();
        Map<Integer, ByteBuffer> outputBuffers = new LinkedHashMap<>();
        Map<Integer, TensorInfo> outputInfo = new LinkedHashMap<>();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input.asFloatBuffer(), inputShape);
             OrtSession.Result result = session.run(java.util.Collections.singletonMap(inputName, tensor))) {
            for (int i = 0; i < result.size(); i++) {
                OnnxValue value = result.get(i);
                if (!(value instanceof OnnxTensor)) {
                    throw new IllegalArgumentException("Wyjście ONNX nr " + i + " nie jest tensorem");
                }
                OnnxTensor output = (OnnxTensor) value;
                ai.onnxruntime.TensorInfo info = output.getInfo();
                TensorInfo converted = tensorInfo(i, info);
                ByteBuffer source = output.getByteBuffer().order(ByteOrder.nativeOrder());
                ByteBuffer copy = ByteBuffer.allocateDirect(converted.byteSize).order(ByteOrder.nativeOrder());
                source.rewind();
                copy.put(source);
                copy.rewind();
                outputInfo.put(i, converted);
                outputBuffers.put(i, copy);
            }
            return new InferenceRunResult(outputBuffers, outputInfo);
        } catch (OrtException e) {
            throw new IllegalStateException("Błąd inferencji ONNX: " + e.getMessage(), e);
        }
    }

    @Override
    public String runtimeName() {
        return "ONNX Runtime/CPU";
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException ignored) {
            // Sesja jest zamykana podczas kończenia pipeline'u.
        }
    }

    private static TensorInfo tensorInfo(int index, ai.onnxruntime.TensorInfo info) {
        int[] shape = new int[info.getShape().length];
        int elements = 1;
        for (int i = 0; i < shape.length; i++) {
            long dimension = info.getShape()[i];
            if (dimension <= 0 || dimension > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Model ONNX ma dynamiczny lub zbyt duży wymiar tensora");
            }
            shape[i] = (int) dimension;
            elements = Math.multiplyExact(elements, shape[i]);
        }
        String dataType;
        int bytes;
        switch (info.type) {
            case FLOAT:
                dataType = "FLOAT32";
                bytes = 4;
                break;
            case UINT8:
                dataType = "UINT8";
                bytes = 1;
                break;
            case INT8:
                dataType = "INT8";
                bytes = 1;
                break;
            default:
                throw new IllegalArgumentException("Nieobsługiwany typ tensora ONNX: " + info.type);
        }
        return new TensorInfo(index, shape, dataType, Math.multiplyExact(elements, bytes), 0f, 0);
    }
}
