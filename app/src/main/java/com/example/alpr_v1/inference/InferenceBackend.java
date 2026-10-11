package com.example.alpr_v1.inference;

import java.nio.ByteBuffer;

public interface InferenceBackend extends AutoCloseable {
    int inputByteSize();
    TensorInfo inputInfo();
    InferenceRunResult run(ByteBuffer input);
    String runtimeName();
    default java.util.Map<Integer, TensorInfo> outputInfo() { return java.util.Collections.emptyMap(); }
    default org.json.JSONObject executionEvidence() { return new org.json.JSONObject(); }
    @Override void close();
}
