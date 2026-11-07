package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticPlateBackgroundWakeTest {
    private static final int SIZE = 200;
    // The plate stays identical and featureless while the surrounding photograph changes.
    private StaticSceneWatcher armed() {
        StaticSceneWatcher w = new StaticSceneWatcher();
        w.arm(new StaticSceneWatchRegions(null, Collections.singletonList(
                new NormalizedBounds(.02f, .02f, .10f, .10f)), .12f));
        w.observe(StaticSceneFeatureWatcherTest.sparseCorners(0, 0), SIZE, SIZE, 0, false);
        return w;
    }
    private byte[] blank() {
        byte[] image = new byte[SIZE * SIZE]; Arrays.fill(image, (byte)100); return image;
    }
    @Test public void backgroundReplacementWakesDespiteUnchangedPlateAndLowLumaFraction() {
        StaticSceneWatcher w = armed();
        assertFalse(w.observe(blank(), SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(w.observe(blank(), SIZE, SIZE, 200_000_000L, false).changed);
        StaticSceneWatcher.Result r = w.observe(blank(), SIZE, SIZE, 300_000_000L, false);
        assertTrue(r.changed);
        assertTrue(r.localFraction < StaticSceneWatcher.DEFAULT.localFraction);
        assertTrue(r.globalFraction < StaticSceneWatcher.DEFAULT.globalFraction);
    }
    @Test public void anchoredEvidenceSurvivesSlowIrregularCallbacks() {
        StaticSceneWatcher w = armed();
        assertFalse(w.observe(blank(), SIZE, SIZE, 700_000_000L, false).changed);
        assertFalse(w.observe(blank(), SIZE, SIZE, 1_500_000_000L, false).changed);
        assertTrue(w.observe(blank(), SIZE, SIZE, 2_600_000_000L, false).changed);
    }
    @Test public void originalGlobalChannelAlsoConfirmsWithGapsAbove500ms() {
        StaticSceneWatcher w = new StaticSceneWatcher();
        w.arm(new StaticSceneWatchRegions(null, null, 0f));
        w.observe(StaticSceneFeatureWatcherTest.sparseCorners(0, 0), SIZE, SIZE, 0, false);
        assertFalse(w.observe(blank(), SIZE, SIZE, 700_000_000L, false).changed);
        assertFalse(w.observe(blank(), SIZE, SIZE, 1_500_000_000L, false).changed);
        assertTrue(w.observe(blank(), SIZE, SIZE, 2_600_000_000L, false).changed);
    }
    @Test public void stationaryBackgroundExposureNoiseAndSmallJitterStayIdle() {
        for (int kind = 0; kind < 4; kind++) {
            StaticSceneWatcher w = armed();
            byte[] image = StaticSceneFeatureWatcherTest.sparseCorners(kind == 3 ? 1 : 0, 0);
            for (int i = 0; i < image.length; i++) {
                if (kind == 1) image[i] += 30;
                if (kind == 2) image[i] += i % 3 - 1;
            }
            for (int i = 1; i <= 120; i++)
                assertFalse("kind=" + kind, w.observe(image, SIZE, SIZE, i * 650_000_000L, false).changed);
        }
    }
    @Test public void transientBackgroundLossDoesNotAccumulateAcrossGoodObservation() {
        StaticSceneWatcher w = armed();
        assertFalse(w.observe(blank(), SIZE, SIZE, 700_000_000L, false).changed);
        assertFalse(w.observe(StaticSceneFeatureWatcherTest.sparseCorners(0, 0), SIZE, SIZE,
                1_400_000_000L, false).changed);
        assertFalse(w.observe(blank(), SIZE, SIZE, 2_100_000_000L, false).changed);
        assertFalse(w.observe(blank(), SIZE, SIZE, 2_800_000_000L, false).changed);
    }
}
