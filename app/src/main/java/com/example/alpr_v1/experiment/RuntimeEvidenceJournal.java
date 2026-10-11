package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** Recovered from durable session data; never synthesizes new runtime observations. */
public final class RuntimeEvidenceJournal {
    public static final String FILE = "telemetry/runtime_execution.jsonl";
    private RuntimeEvidenceJournal() { }
    public static JSONObject summarize(File directory) throws Exception {
        File file = new File(directory, FILE);
        JSONArray events = new JSONArray(); Set<String> roles = new HashSet<>(); int malformed = 0, failures = 0;
        JSONObject stages = new JSONObject(); boolean int8NotProven = false;
        if (file.isFile()) for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            try {
                JSONObject event = new JSONObject(line);
                if (!RuntimeExecutionEvidence.SCHEMA.equals(event.optString("schema"))) throw new IllegalArgumentException("schema");
                events.put(event);
                if (event.optString("phase").contains("ERROR")) failures++;
                if (event.optString("kernel_execution_status").equals("INT8_NOT_PROVEN")) int8NotProven = true;
                String role = event.optString("role");
                if (role.equals("plate") || role.equals("character") || role.equals("vehicle")) stages.put(role,event);
                if (event.optString("phase").equals("FIRST_INVOKE_COMPLETED")) roles.add(event.optString("role"));
            } catch (Exception error) { malformed++; }
        }
        return new JSONObject().put("schema", "alpr.runtime_execution_journal.v1")
                .put("scope", "RUNTIME_OBSERVATIONS_NOT_KERNEL_PROOF")
                .put("status", malformed > 0 ? "INCOMPLETE" : failures > 0 ? "ERROR" : events.length() == 0 ? "UNVERIFIED" : "OBSERVED")
                .put("missing_evidence_reason", events.length() == 0 ? "NO_RUNTIME_OBSERVATIONS" : "")
                .put("file", FILE).put("file_sha256", file.isFile() ? RuntimeExecutionEvidence.sha256(file) : JSONObject.NULL)
                .put("event_count", events.length()).put("malformed_records", malformed).put("error_event_count",failures).put("stages",stages)
                .put("mt_invoke_observed", roles.contains("plate")).put("mz_invoke_observed", roles.contains("character"))
                .put("kernel_execution_status", int8NotProven ? "INT8_NOT_PROVEN" : "UNVERIFIED").put("int8_execution_proven", false).put("events", events);
    }
}
