package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelVariant;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Immutable JSON snapshots for one actual backend instance, including failed initialization. */
public final class RuntimeExecutionEvidence {
    public static final String SCHEMA = "alpr.runtime_execution_evidence.v1";
    private final JSONObject data = new JSONObject();
    private final InstalledModel model;
    private final ModelVariant variant;
    private final RuntimeEvidenceSink sink;
    private final List<TensorInfo> tensors = new ArrayList<>();
    private String graph = "UNVERIFIED";
    private long sequence;

    public RuntimeExecutionEvidence(InstalledModel model, ModelVariant variant, ExecutionProfile profile,
                                    RuntimeEvidenceSink sink) {
        this.model = model; this.variant = variant;
        this.sink = sink == null ? RuntimeEvidenceSink.NONE : sink;
        put("schema", SCHEMA); put("runtime_instance_id", UUID.randomUUID().toString());
        put("role", model.manifest().role().wireName()); put("model_id", model.manifest().modelId());
        put("model_manifest_fingerprint", model.fingerprint()); put("variant_id", variant.id());
        put("declared_precision", variant.precision()); put("runtime", variant.runtime().wireName());
        put("backend_requested", profile.gpu ? "LiteRT/GPU" : variant.runtime().wireName() + "/CPU");
        put("backend_effective", JSONObject.NULL); put("backend_effective_reason", "BACKEND_NOT_OPENED");
        put("cpu_threads_requested", profile.cpuThreads); put("cpu_threads_option_accepted", JSONObject.NULL);
        put("cpu_threads_effective", JSONObject.NULL);
        put("cpu_threads_effective_reason", "PUBLIC_API_HAS_NO_OBSERVED_WORKER_COUNT");
        put("xnnpack_requested", profile.runtime == com.example.alpr_v1.model.ModelRuntime.TFLITE && !profile.gpu);
        put("xnnpack_effective", "UNVERIFIED"); put("xnnpack_effective_reason", "NO_PUBLIC_OPERATOR_ASSIGNMENT_API");
        put("delegate_requested", profile.gpu ? "GPU" : profile.runtime == com.example.alpr_v1.model.ModelRuntime.TFLITE ? "XNNPACK_CPU" : "NONE");
        put("delegate_effective", "UNVERIFIED"); put("delegate_effective_reason", "NO_PUBLIC_OPERATOR_ASSIGNMENT_API");
        put("operator_assignment", new JSONArray()); put("operator_assignment_status", "UNVERIFIED");
        put("operator_assignment_reason", "PUBLIC_JAVA_API_DOES_NOT_EXPOSE_EXECUTION_PLAN");
        put("kernel_precision_provider", "NONE"); put("kernel_precision_reason", "NO_EXECUTED_KERNEL_ARITHMETIC_PROOF");
        put("inputs", new JSONArray()); put("outputs", new JSONArray());
        put("contract_validation", "NOT_CHECKED"); put("invocation_count_at_observation", 0); put("invoke_observed", false);
        put("runtime_version", JSONObject.NULL); put("runtime_version_reason", "RUNTIME_NOT_OPENED");
        put("static_graph_classification", graph); put("static_graph_evidence_status", "NOT_AVAILABLE");
    }

    public void put(String key, Object value) {
        try { data.put(key, value == null ? JSONObject.NULL : value); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    public void captureArtifactFiles() throws IOException {
        JSONObject actual = new JSONObject();
        for (String relative : variant.files()) {
            String sha = sha256(safeFile(model, relative));
            try { actual.put(relative, sha); } catch (JSONException e) { throw new IllegalStateException(e); }
            put("artifact_sha256_actual", actual);
            if (!sha.equals(variant.sha256().get(relative))) throw new IOException("SHA wariantu nie odpowiada manifestowi: " + relative);
        }
        put("model_sha256", actual.optString(variant.primaryFile()));
        put("model_file", variant.primaryFile());
        put("model_sha_matches_manifest", true);
        put("model_package_sha256", model.sourceArchive().isFile() ? sha256(model.sourceArchive()) : JSONObject.NULL);
        put("package_sha_status", model.sourceArchive().isFile() ? "OBSERVED_FILE_BYTES" : "SOURCE_ARCHIVE_UNAVAILABLE");
    }

    /** The interpreter uses this private immutable copy, whose SHA is recorded, not a reopened file. */
    public ByteBuffer loadModel() throws IOException {
        File file = safeFile(model, variant.primaryFile());
        long length = file.length();
        if (length <= 0 || length > Integer.MAX_VALUE) throw new IOException("Nieprawidłowy rozmiar modelu");
        ByteBuffer buffer = ByteBuffer.allocateDirect((int) length).order(ByteOrder.nativeOrder());
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new IOException("Model zmienił długość podczas odczytu");
            if (channel.size() != length) throw new IOException("Model zmienił długość podczas odczytu");
        }
        buffer.flip();
        String actual = sha256(buffer);
        put("model_file", variant.primaryFile()); put("model_sha256", actual);
        put("model_byte_size", length); put("model_buffer_mode", "private_direct_copy");
        String expected = variant.sha256().get(variant.primaryFile());
        put("manifest_model_sha256", expected);
        put("model_sha_matches_manifest", actual.equals(expected));
        if (!actual.equals(expected)) throw new IOException("SHA modelu nie odpowiada zatwierdzonemu wariantowi");
        File archive = model.sourceArchive();
        if (archive.isFile()) { put("model_package_sha256", sha256(archive)); put("package_sha_status", "OBSERVED_FILE_BYTES"); }
        else { put("model_package_sha256", JSONObject.NULL); put("package_sha_status", "SOURCE_ARCHIVE_UNAVAILABLE"); }
        readGraphEvidence(actual);
        return buffer;
    }

    private void readGraphEvidence(String actual) {
        try {
            JSONArray variants = new JSONObject(model.manifest().rawJson()).getJSONArray("variants");
            for (int i = 0; i < variants.length(); i++) {
                JSONObject row = variants.getJSONObject(i);
                if (!variant.id().equals(row.optString("id"))) continue;
                JSONObject proof = row.optJSONObject("graph_precision");
                if (proof == null) return;
                File reportFile = safeFile(model, proof.getString("report_file"));
                byte[] bytes = Files.readAllBytes(reportFile.toPath());
                if (!actual.equals(proof.optString("artifact_sha256"))
                        || !sha256(ByteBuffer.wrap(bytes)).equals(proof.optString("report_sha256")))
                    throw new IllegalArgumentException("B1_GRAPH_EVIDENCE_SHA_MISMATCH");
                JSONObject report = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                String classification = report.getString("classification");
                if (!actual.equals(report.optString("artifact_sha256"))
                        || !classification.equals(proof.optString("classification"))
                        || !java.util.Arrays.asList("FP32", "INT8", "MIXED", "UNVERIFIED").contains(classification))
                    throw new IllegalArgumentException("B1_GRAPH_EVIDENCE_CONTRADICTION");
                graph = classification;
                put("static_graph_classification", graph); put("static_graph_evidence_status", "ATTACHED_REPORT_HASH_BOUND");
                put("static_graph_evidence", proof);
                put("static_graph_scope", "DESKTOP_REPORT_NOT_ANDROID_KERNEL_PROOF");
            }
        } catch (Exception error) {
            graph = "UNVERIFIED";
            put("static_graph_classification", graph); put("static_graph_evidence_status", "INVALID");
            put("static_graph_evidence_error", error.toString());
            throw new IllegalArgumentException("Nieprawidłowy dowód grafu B1", error);
        }
    }

    public void tensorInfo(Collection<TensorInfo> inputs, Collection<TensorInfo> outputs,
                           Collection<Integer> outputCapacities) {
        tensors.clear(); tensors.addAll(inputs); tensors.addAll(outputs);
        put("io_tensor_set_complete", !inputs.isEmpty() && !outputs.isEmpty());
        put("inputs", tensorArray(inputs)); put("outputs", tensorArray(outputs));
        put("output_buffer_capacities", new JSONArray(outputCapacities));
        put("quantization_scope", "PUBLIC_IO_PER_TENSOR; internal_per_axis_quantizers_not_exposed");
    }

    public void emit(String phase) {
        put("phase", phase); put("sequence", ++sequence);
        put("observed_at_epoch_ms", System.currentTimeMillis()); put("observed_monotonic_ns", System.nanoTime());
        PrecisionEvidence.Status io = data.optBoolean("io_tensor_set_complete") ? PrecisionEvidence.publicIo(tensors) : PrecisionEvidence.Status.UNVERIFIED;
        put("public_io_precision_status", io.name()); put("public_io_precision_scope", "PUBLIC_IO_TENSORS_ONLY");
        put("kernel_execution_status", PrecisionEvidence.javaKernelStatus(variant.precision(), graph, io).name());
        put("kernel_execution_scope", "EXECUTED_KERNEL_ARITHMETIC");
        put("int8_execution_proven", false);
        try { sink.record(new JSONObject(data.toString())); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    public void failure(String phase, Throwable error) {
        put("error_phase", phase); put("error_type", error.getClass().getName());
        put("error_reason", error.getMessage() == null ? error.toString() : error.getMessage());
        put("fallback_performed", false); emit("ERROR");
    }

    public JSONObject snapshot() {
        try { return new JSONObject(data.toString()); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    public static JSONArray tensorArray(Collection<TensorInfo> values) {
        JSONArray array = new JSONArray();
        try {
            for (TensorInfo t : values) {
                JSONArray shape = new JSONArray(); for (int n : t.shape) shape.put(n);
                JSONObject item = new JSONObject().put("index", t.index).put("dtype", t.dataType)
                        .put("shape", shape).put("byte_size", t.byteSize)
                        .put("scale", Float.isFinite(t.quantizationScale) ? t.quantizationScale : JSONObject.NULL)
                        .put("zero_point", t.quantizationZeroPoint);
                array.put(item);
            }
            return array;
        } catch (JSONException error) { throw new IllegalStateException(error); }
    }

    public static File safeFile(InstalledModel model, String relative) throws IOException {
        File root = model.directory().getCanonicalFile(), file = model.resolve(relative).getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath()) || file.equals(root)) throw new IOException("Ścieżka poza modelem");
        return file;
    }

    public static String sha256(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public static String sha256(ByteBuffer bytes) {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(bytes.asReadOnlyBuffer()); return hex(digest.digest()); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
}
