package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Jawny wybór profilu wykonania dostępny przed startem sesji badawczej. */
public enum ResearchExecutionChoice {
    AUTO("auto", "AUTO"),
    CPU_1("cpu_1", "CPU 1"),
    CPU_2("cpu_2", "CPU 2"),
    CPU_4("cpu_4", "CPU 4"),
    GPU("gpu", "GPU");

    private final String wireName;
    private final String label;

    ResearchExecutionChoice(String wireName, String label) {
        this.wireName = wireName;
        this.label = label;
    }

    public String wireName() { return wireName; }
    public String label() { return label; }

    public ExecutionProfile resolve(
            ModelVariant variant,
            ExecutionProfile automaticProfile
    ) {
        if (variant == null) throw new IllegalArgumentException("Brak wariantu modelu");
        return resolve(variant.runtime(), variant.id(), automaticProfile);
    }

    ExecutionProfile resolve(
            ModelRuntime runtime,
            String variantId,
            ExecutionProfile automaticProfile
    ) {
        if (runtime == null) throw new IllegalArgumentException("Brak runtime wariantu");
        if (this == AUTO) {
            if (automaticProfile == null
                    || automaticProfile.runtime != runtime) {
                throw new IllegalArgumentException(
                        "AUTO nie wskazuje profilu zgodnego z runtime wariantu "
                                + variantId
                );
            }
            return new ExecutionProfile(
                    automaticProfile.runtime,
                    automaticProfile.cpuThreads,
                    automaticProfile.gpu
            );
        }
        if (this == GPU) {
            if (runtime != ModelRuntime.TFLITE) {
                throw new IllegalArgumentException(
                        "GPU jest obsługiwane wyłącznie dla wariantów TFLite"
                );
            }
            return ExecutionProfile.tfliteGpu();
        }
        int threads = this == CPU_1 ? 1 : this == CPU_2 ? 2 : 4;
        return new ExecutionProfile(runtime, threads, false);
    }

    public static List<ResearchExecutionChoice> supportedFor(ModelRuntime runtime) {
        if (runtime == null) return Collections.singletonList(AUTO);
        List<ResearchExecutionChoice> choices = new ArrayList<>();
        choices.add(AUTO);
        choices.add(CPU_1);
        choices.add(CPU_2);
        choices.add(CPU_4);
        if (runtime == ModelRuntime.TFLITE) choices.add(GPU);
        return Collections.unmodifiableList(choices);
    }

    public static ResearchExecutionChoice fromWireName(String value) {
        String normalized = value == null
                ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ResearchExecutionChoice choice : values()) {
            if (choice.wireName.equals(normalized)) return choice;
        }
        throw new IllegalArgumentException("Nieznany profil badawczy: " + value);
    }
}
