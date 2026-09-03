package com.example.alpr_v1.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureDirectoryStoreTest {
    @Test
    public void usesOneStablePublicCropDirectory() {
        assertEquals(
                "Download/Mobilny ALPR - cropy/",
                CaptureDirectoryStore.RELATIVE_PATH
        );
    }
}
