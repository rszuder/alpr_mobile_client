package com.example.alpr_v1.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.capture.VerificationIssue;
import com.example.alpr_v1.pipeline.PlateGeometry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class HumanVerificationJsonInstrumentedTest {
    @Test
    public void acceptedExportsEligibleGroundTruthAndIssueMetadata() throws Exception {
        CapturedPlateItem item = item("WI1234A");
        item.verificationStatus = CapturedPlateItem.VerificationStatus.ACCEPTED;
        item.groundTruthText = item.text;
        item.verificationIssues.add(VerificationIssue.RECTIFICATION);
        item.verificationIssues.add(VerificationIssue.MZ_MISSING_CHARACTER);
        item.needsDesktopReview = true;
        item.verificationNote = "sprawdzić narożniki";
        item.verifiedAtMillis = 42L;
        item.verificationRevision = 3;

        JSONObject json = HumanVerificationJson.from(item);

        assertEquals("accepted", json.getString("status"));
        assertEquals("WI1234A", json.getString("ground_truth_text"));
        assertTrue(json.getBoolean("eligible_for_text_metrics"));
        assertEquals(2, json.getJSONArray("issue_codes").length());
        assertTrue(json.getBoolean("needs_desktop_review"));
        assertEquals(42L, json.getLong("verified_at_millis"));
        assertEquals(3, json.getInt("revision"));
    }

    @Test
    public void rejectedIsNotEligibleForTextMetrics() throws Exception {
        CapturedPlateItem item = item("WI1234A");
        item.verificationStatus = CapturedPlateItem.VerificationStatus.REJECTED;
        item.groundTruthText = "";

        assertFalse(HumanVerificationJson.from(item).getBoolean(
                "eligible_for_text_metrics"
        ));
    }

    @Test
    public void legacyVerificationDerivesEligibilityFromStatusAndGroundTruth() throws Exception {
        JSONObject acceptedLegacy = new JSONObject()
                .put("status", "accepted")
                .put("ground_truth_text", "WI1234A");
        JSONObject rejectedLegacy = new JSONObject()
                .put("status", "rejected")
                .put("ground_truth_text", "");

        assertTrue(HumanVerificationJson.eligibleForTextMetrics(acceptedLegacy));
        assertFalse(HumanVerificationJson.eligibleForTextMetrics(rejectedLegacy));
    }

    private static CapturedPlateItem item(String text) {
        return new CapturedPlateItem(
                "capture", "session", 1L, null, text, 0.9, 0.8, true,
                Collections.emptyList(), 1L, 1L, 0.7f, null, 1f, "normal",
                PlateGeometry.unavailable(), ImageDifficultyMetrics.unavailable(), true,
                true, true, 3, 1, "single_row", Collections.emptyList(), text
        );
    }
}
