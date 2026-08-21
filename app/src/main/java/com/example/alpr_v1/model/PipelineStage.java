package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class PipelineStage {
    private final String stage;
    private final String model;
    private final String role;
    private final String task;
    private final String implementation;

    private PipelineStage(String stage, String model, String role, String task, String implementation) {
        this.stage = stage;
        this.model = model;
        this.role = role;
        this.task = task;
        this.implementation = implementation;
    }

    public static PipelineStage fromJson(JSONObject json) throws JSONException {
        String stage = json.getString("stage").trim();
        if (stage.isEmpty()) throw new JSONException("Etap pipeline'u nie ma nazwy");
        return new PipelineStage(
                stage,
                json.optString("model", "").trim(),
                json.optString("role", "").trim(),
                json.optString("task", "").trim(),
                json.optString("implementation", "").trim()
        );
    }

    public String stage() { return stage; }
    public String model() { return model; }
    public String role() { return role; }
    public String task() { return task; }
    public String implementation() { return implementation; }
}
