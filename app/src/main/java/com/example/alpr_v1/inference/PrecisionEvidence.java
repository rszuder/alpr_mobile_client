package com.example.alpr_v1.inference;

import java.util.Collection;

/** Status is meaningful only together with its evidence scope. Java I/O is not kernel proof. */
public final class PrecisionEvidence {
    public enum Status { FP32, INT8_PROVEN, MIXED, INT8_NOT_PROVEN, UNVERIFIED }
    private PrecisionEvidence() { }

    public static Status publicIo(Collection<TensorInfo> tensors) {
        if (tensors == null || tensors.isEmpty()) return Status.UNVERIFIED;
        boolean fp32 = false, integer = false;
        for (TensorInfo tensor : tensors) {
            if (tensor == null) return Status.UNVERIFIED;
            if ("FLOAT32".equals(tensor.dataType)) fp32 = true;
            else if ("INT8".equals(tensor.dataType) || "UINT8".equals(tensor.dataType)) integer = true;
            else return Status.UNVERIFIED;
        }
        if (fp32 && integer) return Status.MIXED;
        return fp32 ? Status.FP32 : Status.INT8_NOT_PROVEN;
    }

    /** No public Java API supplies executed-node coverage or arithmetic kernel identity. */
    public static Status javaKernelStatus(String declared, String graph, Status io) {
        if ("int8".equalsIgnoreCase(declared) || "uint8".equalsIgnoreCase(declared)
                || "INT8".equals(graph) || "MIXED".equals(graph)
                || io == Status.INT8_NOT_PROVEN || io == Status.MIXED) return Status.INT8_NOT_PROVEN;
        return Status.UNVERIFIED;
    }

    public static void validateStorage(TensorInfo tensor) {
        int width;
        switch (tensor.dataType) {
            case "FLOAT32": case "INT32": width = 4; break;
            case "FLOAT16": case "INT16": width = 2; break;
            case "INT64": case "FLOAT64": width = 8; break;
            case "INT8": case "UINT8": case "BOOL": width = 1; break;
            default: throw new IllegalArgumentException("Nieobsługiwany dtype bufora: " + tensor.dataType);
        }
        long bytes = width;
        for (int dim : tensor.shape) {
            if (dim <= 0) throw new IllegalArgumentException("Niestatyczny kształt tensora");
            bytes = Math.multiplyExact(bytes, dim);
        }
        if (bytes != tensor.byteSize || bytes > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Rozmiar bufora nie odpowiada dtype/shape");
        if ("INT8".equals(tensor.dataType) || "UINT8".equals(tensor.dataType)) {
            int min = "INT8".equals(tensor.dataType) ? -128 : 0;
            int max = "INT8".equals(tensor.dataType) ? 127 : 255;
            if (!Float.isFinite(tensor.quantizationScale) || tensor.quantizationScale <= 0
                    || tensor.quantizationZeroPoint < min || tensor.quantizationZeroPoint > max)
                throw new IllegalArgumentException("Nieprawidłowy quantizer tensora runtime");
        }
    }
}
