package com.example.alpr_v1.autotune;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Build;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.inference.InferenceBackend;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.metrics.Statistics;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutoTuneManager {
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 8;
    private final Context context;
    private final SharedPreferences preferences;
    private final String environmentId;

    public AutoTuneManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences("autotune", Context.MODE_PRIVATE);
        String version;
        try {
            version = String.valueOf(this.context.getPackageManager()
                    .getPackageInfo(this.context.getPackageName(), 0).getLongVersionCode());
        } catch (Exception e) {
            version = "unknown";
        }
        String identity = Build.FINGERPRINT + '|' + Build.SUPPORTED_ABIS[0] + '|' + version;
        this.environmentId = Integer.toHexString(identity.hashCode());
    }

    public AutoTuneResult tune(InstalledModel model) {
        if (isThermallyConstrained()) {
            throw new IllegalStateException("Autotuning odłożony: urządzenie jest zbyt rozgrzane");
        }
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        Set<Integer> threadCounts = new LinkedHashSet<>();
        threadCounts.add(1);
        threadCounts.add(Math.min(2, cores));
        threadCounts.add(Math.min(4, cores));

        List<AutoTuneResult.Candidate> results = new ArrayList<>();
        AutoTuneResult.Candidate best = null;
        for (ModelVariant variant : model.manifest().variants()) {
            if (!RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) continue;
            for (int threads : threadCounts) {
                ExecutionProfile profile = new ExecutionProfile(variant.runtime(), threads, false);
                AutoTuneResult.Candidate candidate = benchmark(model, variant, profile);
                results.add(candidate);
                if (isBetter(candidate, best)) best = candidate;
            }
            if (variant.runtime() == ModelRuntime.TFLITE && !isThermallyConstrained()) {
                AutoTuneResult.Candidate candidate = benchmark(model, variant, ExecutionProfile.tfliteGpu());
                results.add(candidate);
                if (isBetter(candidate, best)) best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Żaden dostępny runtime nie przeszedł autotuningu");
        }
        ExecutionProfile chosen = new ExecutionProfile(best.runtime, best.threads, best.gpu);
        AutoTuneResult result = new AutoTuneResult(
                model.manifest().modelId(),
                model.fingerprint(),
                best.variantId,
                chosen,
                results
        );
        try {
            preferences.edit().putString(key(model), result.toJson().toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Nie można zapisać profilu autotuningu", e);
        }
        return result;
    }

    private AutoTuneResult.Candidate benchmark(
            InstalledModel model,
            ModelVariant variant,
            ExecutionProfile profile
    ) {
        List<Double> measured = new ArrayList<>();
        InferenceBackend backend = null;
        double modelLoadMs = 0.0;
        double coldInferenceMs = 0.0;
        try {
            long loadStarted = SystemClock.elapsedRealtimeNanos();
            backend = RuntimeBackendFactory.create(model, variant, profile);
            modelLoadMs = (SystemClock.elapsedRealtimeNanos() - loadStarted) / 1_000_000.0;
            ByteBuffer input = ByteBuffer.allocateDirect(backend.inputByteSize()).order(ByteOrder.nativeOrder());
            long coldStarted = SystemClock.elapsedRealtimeNanos();
            backend.run(input);
            coldInferenceMs = (SystemClock.elapsedRealtimeNanos() - coldStarted) / 1_000_000.0;
            for (int i = 0; i < WARMUP_RUNS; i++) backend.run(input);
            for (int i = 0; i < MEASURED_RUNS; i++) {
                long started = SystemClock.elapsedRealtimeNanos();
                backend.run(input);
                measured.add((SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0);
            }
            Statistics.Summary stats = Statistics.summarize(measured);
            return new AutoTuneResult.Candidate(
                    variant.id(), variant.runtime(), profile.cpuThreads, profile.gpu,
                    stats.median, stats.p95, modelLoadMs, coldInferenceMs, ""
            );
        } catch (Exception e) {
            return new AutoTuneResult.Candidate(
                    variant.id(), variant.runtime(), profile.cpuThreads, profile.gpu,
                    0, 0, modelLoadMs, coldInferenceMs, e.getMessage()
            );
        } finally {
            if (backend != null) backend.close();
        }
    }

    private static boolean isBetter(AutoTuneResult.Candidate candidate, AutoTuneResult.Candidate current) {
        return candidate.error.isEmpty() && (current == null || candidate.medianMs < current.medianMs);
    }

    public boolean hasProfile(InstalledModel model) {
        return preferences.contains(key(model));
    }

    public String getProfileJson(InstalledModel model) {
        return preferences.getString(key(model), "");
    }

    public ExecutionProfile chosenProfile(InstalledModel model) {
        JSONObject parsed = profileObject(model);
        if (parsed == null) {
            ModelRuntime runtime = fallbackVariant(model).runtime();
            return new ExecutionProfile(runtime, Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors())), false);
        }
        try {
            ModelRuntime runtime = ModelRuntime.fromWire(parsed.getString("runtime"));
            return new ExecutionProfile(
                    runtime,
                    Math.max(1, parsed.optInt("cpu_threads", 2)),
                    parsed.optBoolean("gpu", false)
            );
        } catch (Exception e) {
            return new ExecutionProfile(fallbackVariant(model).runtime(), 2, false);
        }
    }

    public ModelVariant chosenVariant(InstalledModel model) {
        JSONObject parsed = profileObject(model);
        String selectedId = parsed == null ? "" : parsed.optString("chosen_variant_id", "");
        for (ModelVariant variant : model.manifest().variants()) {
            if (variant.id().equals(selectedId) && RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                return variant;
            }
        }
        return fallbackVariant(model);
    }

    public JSONObject exportProfiles() throws JSONException {
        JSONObject profiles = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            String value = (String) entry.getValue();
            try {
                profiles.put(entry.getKey(), new JSONObject(value));
            } catch (JSONException ignored) {
                profiles.put(entry.getKey(), value);
            }
        }
        return profiles;
    }

    private JSONObject profileObject(InstalledModel model) {
        String json = getProfileJson(model);
        if (json == null || json.isEmpty()) return null;
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    private static ModelVariant fallbackVariant(InstalledModel model) {
        for (ModelVariant variant : model.manifest().variants()) {
            if (variant.runtime() == ModelRuntime.TFLITE) return variant;
        }
        for (ModelVariant variant : model.manifest().variants()) {
            if (RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) return variant;
        }
        throw new IllegalStateException("Pakiet nie zawiera wariantu wykonywalnego w tej wersji aplikacji");
    }

    private boolean isThermallyConstrained() {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
    }

    private String key(InstalledModel model) {
        return "profile." + environmentId + "." + model.manifest().role().wireName() + "." + model.fingerprint();
    }
}
