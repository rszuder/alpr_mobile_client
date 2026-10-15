package com.example.alpr_v1.model;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/**
 * T2-I-11: read-only validation of the exact existing Desktop research ZIP on
 * A12. The negative installation attempt targets isolated cache, not registry.
 * This is NOT model activation, F4, final_test, or deployment approval.
 */
@RunWith(AndroidJUnit4.class)
public final class MzCleanResearchPackageValidationInstrumentedTest {
    private static final String PACKAGE_SHA =
            "084d37e12727577e4e1c123d4ee0e800a2a00c8642d41818e851869da52870cd";

    @Test public void exactT2I10ArchiveValidatesButCannotBeInstalled() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File archive = new File(context.getFilesDir(),
                "f4_mz_day_int8_first_invoke/MZ_DAY_CLEAN_INT8_RESEARCH_ONLY.alprmodel");
        assertTrue("Stage exact T2-I-10 ZIP into private app files first", archive.isFile());
        assertEquals("T2-I-10 ZIP SHA mismatch", PACKAGE_SHA, Hashing.sha256(archive));
        File scratch = new File(context.getCacheDir(), "t2i11-research-validation-never-install");
        ModelPackageImporter importer = new ModelPackageImporter(context, scratch);
        ModelManifest manifest = importer.validatePackage(archive); // no model installation
        assertEquals("mz-day-clean-int8-research-only", manifest.modelId());
        assertEquals("raw_split_ltrb_scores_v1", manifest.output().outputFormat());
        assertEquals(36, manifest.labels().size());
        assertEquals("Q", manifest.labels().get(26));
        assertEquals(MzCleanResearchPackageValidator.MODEL_SHA,
                manifest.variants().get(0).sha256().get(MzCleanResearchPackageValidator.MODEL_MEMBER));
        try {
            importer.importPackage(archive); // must abort before registration/staging destination
            fail("Research-only MZ cannot be installed in any build");
        } catch (ModelPackageException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("IMPORT_NOT_APPROVED"));
        }
        assertFalse("Import may not create a character registry directory",
                new File(scratch, "character").exists());
    }
}
