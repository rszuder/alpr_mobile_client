package com.example.alpr_v1.experiment;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class ExperimentIdentityTest {
    @Test
    public void normalizesRequiredCampaignKeysAndReplicate() {
        ExperimentIdentity identity = new ExperimentIdentity("  ", " ", 0, null);

        assertEquals("unassigned_series", identity.seriesId);
        assertEquals("live_camera", identity.scenarioId);
        assertEquals(1, identity.replicateIndex);
        assertEquals("", identity.notes);
        assertFalse(identity.autoZoomEnabled);
    }
}
