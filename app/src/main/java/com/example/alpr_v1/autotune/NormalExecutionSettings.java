package com.example.alpr_v1.autotune;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

/** Ordinary scanning only. Missing keys preserve legacy AutoTune without a migration write. */
public final class NormalExecutionSettings {
    public static final String PREFERENCES = "normal_execution";
    private final SharedPreferences preferences;
    public NormalExecutionSettings(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
    public static int maximumCpuThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }
    public static String threadLabel(int count) {
        return count + (count == 1 ? " wątek" : count % 10 >= 2 && count % 10 <= 4
                && (count % 100 < 12 || count % 100 > 14) ? " wątki" : " wątków");
    }
    private static String key(InstalledModel model) {
        return "cpu_threads." + model.manifest().role().wireName() + "." + model.fingerprint();
    }
    /** Zero is Auto, positive values are explicit CPU requests, never observed worker counts. */
    public int requestedThreads(InstalledModel model) {
        return model == null ? 0 : preferences.getInt(key(model), 0);
    }
    public int revision() { return preferences.getInt("revision", 0); }
    public boolean save(InstalledModel model, int threads) {
        if (model == null) throw new IllegalArgumentException("Najpierw wybierz model");
        if (threads < 0 || threads > maximumCpuThreads())
            throw new IllegalArgumentException("Liczba wątków CPU poza dostępnym zakresem: " + threads);
        if (requestedThreads(model) == threads) return false;
        SharedPreferences.Editor editor = preferences.edit();
        if (threads == 0) editor.remove(key(model)); else editor.putInt(key(model), threads);
        editor.putInt("revision", revision() + 1).apply();
        return true;
    }
    public ExecutionProfile resolve(InstalledModel model, ModelVariant variant, ExecutionProfile automatic) {
        int threads = requestedThreads(model);
        if (threads == 0) {
            if (automatic == null || automatic.runtime != variant.runtime()
                    || automatic.gpu && variant.runtime() != ModelRuntime.TFLITE)
                throw new IllegalArgumentException("Auto nie wskazuje wykonania zgodnego z wariantem");
            return automatic;
        }
        if (threads < 1 || threads > maximumCpuThreads())
            throw new IllegalArgumentException("Zapisano CPU: " + threads + " wątków. Wybierz dostępną liczbę w Model i wykonanie.");
        if (variant.runtime() != ModelRuntime.TFLITE && variant.runtime() != ModelRuntime.ONNX && variant.runtime() != ModelRuntime.NCNN)
            throw new IllegalArgumentException("Ten backend nie obsługuje ręcznych wątków CPU");
        return new ExecutionProfile(variant.runtime(), threads, false);
    }
}
