package com.example.alpr_v1.model;

import org.junit.Test;
import static org.junit.Assert.*;

/** The normal RESEARCH_ONLY install ban remains; only explicitly consented debug SHA is eligible. */
public final class PinnedResearchMzDebugImportGateTest {
    private static final String PINNED =
            "084d37e12727577e4e1c123d4ee0e800a2a00c8642d41818e851869da52870cd";

    @Test public void pinnedDebugAndConsentPassesGate() throws Exception {
        ModelPackageImporter.requirePinnedResearchOptIn(true, true, PINNED);
    }

    @Test public void releaseBuildStillRejected() {
        rejected(false, true, PINNED, "DEBUG_ONLY");
    }

    @Test public void noExplicitConsentStillRejected() {
        rejected(true, false, PINNED, "CONSENT_REQUIRED");
    }

    @Test public void differentArchiveShaStillRejected() {
        rejected(true, true, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ARCHIVE_SHA_MISMATCH");
    }

    @Test public void missingArchiveShaStillRejected() {
        rejected(true, true, null, "ARCHIVE_SHA_MISMATCH");
    }

    private static void rejected(boolean debug, boolean consent, String sha, String code) {
        try {
            ModelPackageImporter.requirePinnedResearchOptIn(debug, consent, sha);
            fail("Should reject " + code);
        } catch (ModelPackageException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(code));
        }
    }
}
