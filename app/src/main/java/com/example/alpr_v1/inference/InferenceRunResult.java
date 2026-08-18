package com.example.alpr_v1.inference;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InferenceRunResult {
    private final Map<Integer, ByteBuffer> outputs;
    private final Map<Integer, TensorInfo> tensorInfo;

    public InferenceRunResult(Map<Integer, ByteBuffer> outputs, Map<Integer, TensorInfo> tensorInfo) {
        this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.tensorInfo = Collections.unmodifiableMap(new LinkedHashMap<>(tensorInfo));
    }

    public Map<Integer, ByteBuffer> outputs() { return outputs; }
    public Map<Integer, TensorInfo> tensorInfo() { return tensorInfo; }
}
