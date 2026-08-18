package com.example.alpr_v1.inference;

import java.nio.ByteBuffer;

public interface InferenceBackend extends AutoCloseable {
    int inputByteSize();
    TensorInfo inputInfo();
    InferenceRunResult run(ByteBuffer input);
    String runtimeName();
    @Override void close();
}
