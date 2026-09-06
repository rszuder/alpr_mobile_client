package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelVariant;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;

/** CPU backend for official NCNN Android static runtime. */
public final class NcnnBackend implements InferenceBackend {
    private static final ReentrantLock RUNTIME_LOCK = new ReentrantLock(true);
    private static final boolean LIBRARY_AVAILABLE;
    private static final String LIBRARY_ERROR;

    static {
        boolean available = false;
        String error = "";
        try {
            System.loadLibrary("alpr_ncnn");
            available = true;
        } catch (Throwable failure) {
            error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
        }
        LIBRARY_AVAILABLE = available;
        LIBRARY_ERROR = error;
    }

    private final TensorInfo inputInfo;
    private final InstalledModel contractModel;
    private final ModelVariant contractVariant;
    private final ModelOutputSpec outputSpec;
    private long nativeHandle;

    public NcnnBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile) {
        if (!LIBRARY_AVAILABLE) {
            throw new IllegalStateException(unavailableReason());
        }
        contractModel = model;
        contractVariant = variant;
        outputSpec = variant.output(model.manifest().output());
        ModelInputSpec input = variant.input(model.manifest().input());
        if (!"NCHW".equals(input.layout())) {
            throw new IllegalArgumentException("Backend NCNN v1 wymaga wejścia NCHW");
        }
        if (!"FLOAT32".equals(input.dataType())) {
            throw new IllegalArgumentException("Backend NCNN v1 wymaga wejścia FLOAT32");
        }
        if (input.channels() != 3) {
            throw new IllegalArgumentException("Backend NCNN v1 wymaga trzech kanałów wejścia");
        }
        File param = null;
        File weights = null;
        for (String relative : variant.files()) {
            String lower = relative.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".param")) param = model.resolve(relative);
            else if (lower.endsWith(".bin")) weights = model.resolve(relative);
        }
        if (param == null || weights == null || !param.isFile() || !weights.isFile()) {
            throw new IllegalArgumentException("Wariant NCNN wymaga istniejących plików .param i .bin");
        }
        int elements = Math.multiplyExact(
                Math.multiplyExact(input.width(), input.height()),
                input.channels()
        );
        inputInfo = new TensorInfo(
                0,
                new int[]{1, input.channels(), input.height(), input.width()},
                "FLOAT32",
                Math.multiplyExact(elements, Float.BYTES),
                0f,
                0
        );
        lockRuntimeInterruptibly();
        try {
            nativeHandle = nativeCreate(
                    param.getAbsolutePath(),
                    weights.getAbsolutePath(),
                    profile.cpuThreads,
                    input.width(),
                    input.height(),
                    input.channels()
            );
        } finally {
            RUNTIME_LOCK.unlock();
        }
        if (nativeHandle == 0L) {
            throw new IllegalStateException("Nie można utworzyć sesji NCNN");
        }
    }

    public static boolean isAvailable() {
        return LIBRARY_AVAILABLE;
    }

    public static String unavailableReason() {
        return LIBRARY_AVAILABLE
                ? "Runtime NCNN jest dostępny"
                : "Nie można załadować biblioteki NCNN/JNI"
                + (LIBRARY_ERROR.isEmpty() ? "" : ": " + LIBRARY_ERROR);
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
        ensureOpen();
        if (input == null || !input.isDirect() || input.capacity() != inputInfo.byteSize) {
            throw new IllegalArgumentException("NCNN wymaga bezpośredniego bufora o rozmiarze wejścia");
        }
        input.rewind();
        float[] values;
        int[] shape;
        lockRuntimeInterruptibly();
        try {
            values = nativeRun(nativeHandle, input);
            shape = nativeOutputShape(nativeHandle);
        } finally {
            RUNTIME_LOCK.unlock();
        }
        int elements = 1;
        for (int dimension : shape) elements = Math.multiplyExact(elements, dimension);
        if (values == null || values.length != elements) {
            throw new IllegalStateException(
                    "Rozmiar wyjścia NCNN nie odpowiada jego kształtowi: values="
                            + (values == null ? "null" : values.length)
                            + ", shape=" + java.util.Arrays.toString(shape)
            );
        }
        ByteBuffer output = ByteBuffer.allocateDirect(
                Math.multiplyExact(values.length, Float.BYTES)
        ).order(ByteOrder.nativeOrder());
        output.asFloatBuffer().put(values);
        output.rewind();
        TensorInfo info = new TensorInfo(
                0,
                shape,
                "FLOAT32",
                output.capacity(),
                0f,
                0
        );
        try {
            ModelTensorContractValidator.validateOutput(outputSpec, info);
        } catch (IllegalArgumentException error) {
            throw new RuntimeModelContractException(
                    contractModel,
                    contractVariant,
                    error.getMessage(),
                    error
            );
        }
        return new InferenceRunResult(
                Collections.singletonMap(0, output),
                Collections.singletonMap(0, info)
        );
    }

    @Override
    public String runtimeName() {
        return "NCNN/CPU";
    }

    @Override
    public synchronized void close() {
        if (nativeHandle == 0L) return;
        RUNTIME_LOCK.lock();
        try {
            nativeClose(nativeHandle);
            nativeHandle = 0L;
        } finally {
            RUNTIME_LOCK.unlock();
        }
    }

    private void ensureOpen() {
        if (nativeHandle == 0L) throw new IllegalStateException("Backend NCNN został zamknięty");
    }

    private static void lockRuntimeInterruptibly() {
        try {
            RUNTIME_LOCK.lockInterruptibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Operacja NCNN została przerwana");
        }
    }

    private static native long nativeCreate(
            String paramPath,
            String modelPath,
            int threads,
            int width,
            int height,
            int channels
    );

    private static native float[] nativeRun(long handle, ByteBuffer input);
    private static native int[] nativeOutputShape(long handle);
    private static native void nativeClose(long handle);
}
