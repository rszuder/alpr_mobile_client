package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RuntimeEvidenceFixture {
    private RuntimeEvidenceFixture() { }
    public static InstalledModel model(File directory, String declared, String dtype, String graph) throws Exception {
        directory.mkdirs();
        File artifact = new File(directory, "synthetic.tflite");
        Files.write(artifact.toPath(), "synthetic model bytes; not an executable neural network".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(directory,"source.alprmodel").toPath(), "synthetic archive identity".getBytes(StandardCharsets.UTF_8));
        String sha = RuntimeExecutionEvidence.sha256(artifact);
        JSONObject input = new JSONObject().put("width",2).put("height",2).put("channels",3)
                .put("layout","NHWC").put("color","RGB").put("data_type",dtype);
        if (dtype.equals("INT8")) input.put("quantization",new JSONObject().put("scale",.05).put("zero_point",-128));
        JSONObject variant = new JSONObject().put("id","selected").put("runtime","tflite").put("precision",declared)
                .put("file",artifact.getName()).put("sha256",new JSONObject().put(artifact.getName(),sha));
        if (graph != null) {
            JSONObject report = new JSONObject().put("classification",graph).put("artifact_sha256",sha)
                    .put("execution_precision","UNVERIFIED").put("fixture",true);
            File proof = new File(directory,"graph.json"); Files.write(proof.toPath(),report.toString().getBytes(StandardCharsets.UTF_8));
            variant.put("graph_precision",new JSONObject().put("classification",graph).put("artifact_sha256",sha)
                    .put("report_file",proof.getName()).put("report_sha256",RuntimeExecutionEvidence.sha256(proof)));
        }
        JSONObject manifest = new JSONObject().put("schema","alpr.model.v1").put("model_id","synthetic").put("role","character")
                .put("task","detect").put("input",input).put("labels",new JSONArray().put("A"))
                .put("output",new JSONObject().put("decoder","yolo_char_v1").put("class_count",1).put("keypoint_count",0))
                .put("variants",new JSONArray().put(variant));
        return new InstalledModel(ModelManifest.parse(manifest.toString()),directory,"synthetic-manifest-fingerprint");
    }
}
