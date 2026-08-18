package com.example.alpr_v1.autotune;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.ModelRuntime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AutoTuneResult {
    public static final class Candidate {
        public final String variantId;
        public final ModelRuntime runtime;
        public final int threads;
        public final boolean gpu;
        public final double medianMs;
        public final double p95Ms;
        public final double modelLoadMs;
        public final double coldInferenceMs;
        public final String error;

        public Candidate(
                String variantId,
                ModelRuntime runtime,
                int threads,
                boolean gpu,
                double medianMs,
                double p95Ms,
                double modelLoadMs,
                double coldInferenceMs,
                String error
        ) {
            this.variantId = variantId;
            this.runtime = runtime;
            this.threads = threads;
            this.gpu = gpu;
            this.medianMs = medianMs;
            this.p95Ms = p95Ms;
            this.modelLoadMs = modelLoadMs;
            this.coldInferenceMs = coldInferenceMs;
            this.error = error == null ? "" : error;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("variant_id", variantId);
            json.put("runtime", runtime.wireName());
            json.put("threads", threads);
            json.put("gpu", gpu);
            json.put("median_ms", medianMs);
            json.put("p95_ms", p95Ms);
            json.put("model_load_ms", modelLoadMs);
            json.put("cold_inference_ms", coldInferenceMs);
            if (!error.isEmpty()) json.put("error", error);
            return json;
        }
    }

    public final String modelId;
    public final String fingerprint;
    public final String chosenVariantId;
    public final ExecutionProfile chosenProfile;
    public final List<Candidate> candidates;

    public AutoTuneResult(
            String modelId,
            String fingerprint,
            String chosenVariantId,
            ExecutionProfile chosenProfile,
            List<Candidate> candidates
    ) {
        this.modelId = modelId;
        this.fingerprint = fingerprint;
        this.chosenVariantId = chosenVariantId;
        this.chosenProfile = chosenProfile;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schema", "alpr.autotune.v1");
        json.put("model_id", modelId);
        json.put("fingerprint", fingerprint);
        json.put("chosen_variant_id", chosenVariantId);
        json.put("runtime", chosenProfile.runtime.wireName());
        json.put("cpu_threads", chosenProfile.cpuThreads);
        json.put("gpu", chosenProfile.gpu);
        JSONArray array = new JSONArray();
        for (Candidate candidate : candidates) array.put(candidate.toJson());
        json.put("candidates", array);
        return json;
    }
}
