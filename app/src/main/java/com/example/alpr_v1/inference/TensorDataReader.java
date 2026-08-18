package com.example.alpr_v1.inference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TensorDataReader {
    private TensorDataReader() {}

    public static float[] toFloatArray(ByteBuffer source, TensorInfo info) {
        ByteBuffer buffer = source.duplicate().order(ByteOrder.nativeOrder());
        buffer.rewind();
        int elements = elementCount(info.shape);
        float[] values = new float[elements];
        switch (info.dataType) {
            case "FLOAT32":
                for (int i = 0; i < elements; i++) values[i] = buffer.getFloat();
                break;
            case "UINT8":
                for (int i = 0; i < elements; i++) {
                    int raw = buffer.get() & 0xff;
                    values[i] = (raw - info.quantizationZeroPoint) * info.quantizationScale;
                }
                break;
            case "INT8":
                for (int i = 0; i < elements; i++) {
                    int raw = buffer.get();
                    values[i] = (raw - info.quantizationZeroPoint) * info.quantizationScale;
                }
                break;
            default:
                throw new IllegalArgumentException("Nieobsługiwany typ wyjścia: " + info.dataType);
        }
        return values;
    }

    private static int elementCount(int[] shape) {
        int count = 1;
        for (int dimension : shape) count = Math.multiplyExact(count, dimension);
        return count;
    }
}
