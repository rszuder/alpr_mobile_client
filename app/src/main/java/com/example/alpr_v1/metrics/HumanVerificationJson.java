package com.example.alpr_v1.metrics;

import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.capture.VerificationIssue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class HumanVerificationJson {
    private HumanVerificationJson() {}

    public static JSONObject from(CapturedPlateItem item) throws JSONException {
        JSONObject verification = new JSONObject();
        verification.put("status", item.verificationStatus.wireName());
        verification.put("ground_truth_text", item.groundTruthText);
        verification.put("eligible_for_text_metrics", item.eligibleForTextMetrics());
        JSONArray issues = new JSONArray();
        for (VerificationIssue issue : item.verificationIssues) {
            issues.put(issue.wireName());
        }
        verification.put("issue_codes", issues);
        verification.put("needs_desktop_review", item.needsDesktopReview);
        verification.put("note", item.verificationNote);
        verification.put("verified_at_millis", item.verifiedAtMillis);
        verification.put("verified_at_ms", item.verifiedAtMillis);
        verification.put("revision", item.verificationRevision);
        verification.put("prediction_text", item.text);
        verification.put("original_prediction", item.text);
        return verification;
    }

    public static boolean eligibleForTextMetrics(JSONObject verification) {
        if (verification == null) return false;
        if (verification.has("eligible_for_text_metrics")) {
            return verification.optBoolean("eligible_for_text_metrics", false);
        }
        String status = verification.optString("status", "not_reviewed");
        String groundTruth = verification.optString("ground_truth_text", "").trim();
        return ("accepted".equals(status) || "corrected".equals(status))
                && !groundTruth.isEmpty();
    }
}
