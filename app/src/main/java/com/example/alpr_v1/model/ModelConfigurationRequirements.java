package com.example.alpr_v1.model;

/** Static engine contracts. Reading them never opens an inference backend. */
public final class ModelConfigurationRequirements {
    private ModelConfigurationRequirements() { }

    public static void requireOutput(ModelRole role, ModelOutputSpec spec) {
        if (role == ModelRole.CHARACTER) requireCharacterDecoder(spec);
        else requireDecoder(spec);
    }
    public static void requireRuntimeInput(ModelRuntime runtime,ModelInputSpec input){
        if(runtime!=ModelRuntime.NCNN)return;
        requireNcnnInput(input);
    }
    public static void requireNcnnInput(ModelInputSpec input){
        if(!"NCHW".equals(input.layout()))throw new IllegalArgumentException("Backend NCNN v1 wymaga wejścia NCHW");
        if(!"FLOAT32".equals(input.dataType()))throw new IllegalArgumentException("Backend NCNN v1 wymaga wejścia FLOAT32");
        if(input.channels()!=3)throw new IllegalArgumentException("Backend NCNN v1 wymaga trzech kanałów wejścia");
    }
    public static void requireCharacterDecoder(ModelOutputSpec spec) {
        if (!"raw_split_ltrb_scores_v1".equals(spec.outputFormat())) {
            requireDecoder(spec); return;
        }
        if (!"ultralytics_detect_split_ltrb_scores_int8_v1".equals(spec.decoder())
                || spec.classCount() != 36 || spec.keypointCount() != 0
                || spec.hasObjectness() || !spec.channelsFirst()
                || !spec.normalizedCoordinates() || spec.nmsInGraph()
                || spec.nmsRequired() || !"ltrb".equals(spec.boxFormat()))
            throw new IllegalArgumentException("MZ_SPLIT_INT8_INVALID_CHARACTER_CONTRACT");
    }
    public static void requireDecoder(ModelOutputSpec spec) {
        if (spec.nmsInGraph())
            throw new IllegalArgumentException("Dekoder v1 wymaga eksportu bez NMS w grafie");
        String decoder = spec.decoder();
        if (!decoder.equals("ultralytics_yolo_raw_v1")
                && !decoder.equals("ultralytics_pose_raw_v1")
                && !decoder.equals("ultralytics_detect_raw_v1")
                && !decoder.equals("ultralytics_pose_end2end_v1")
                && !decoder.equals("ultralytics_detect_end2end_v1")
                && !decoder.equals("ultralytics_yolo_end2end_v1"))
            throw new IllegalArgumentException("Nieobsługiwany dekoder: " + decoder);
    }
    public static void requireResearchArtifacts(InstalledModel model, ModelVariant variant) {
        try {
            for (String file : variant.files()) {
                String actual = com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(
                        com.example.alpr_v1.inference.RuntimeExecutionEvidence.safeFile(model,file));
                if (!actual.equals(variant.sha256().get(file)))
                    throw new IllegalStateException("RESEARCH_MODEL_SHA_CHANGED: " + file);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("RESEARCH_MODEL_FILE_UNAVAILABLE",error);
        }
    }
}
