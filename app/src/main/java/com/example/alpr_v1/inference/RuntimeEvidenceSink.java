package com.example.alpr_v1.inference;

import org.json.JSONObject;

public interface RuntimeEvidenceSink {
    RuntimeEvidenceSink NONE = evidence -> { };
    void record(JSONObject evidence);
}
