package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import com.example.alpr_v1.tracking.RegionFeatureChangeDetector;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticSceneFeatureAdditionTest {
    private static final int SIZE = 200;
    private static final NormalizedBounds PLATE = new NormalizedBounds(.30f, .30f, .70f, .70f);

    private StaticSceneWatcher armed(byte[] baseline) {
        StaticSceneWatcher watcher = new StaticSceneWatcher();
        watcher.arm(new StaticSceneWatchRegions(null, Collections.singletonList(PLATE), 0f));
        watcher.observe(baseline, SIZE, SIZE, 0L, false);
        return watcher;
    }

    @Test public void lowContrastFeaturesAddedOutsideUnchangedPlateWakeAfterThreeSamples() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
        StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE, 300_000_000L, false);
        assertEquals(0f, result.localFraction, 0f);
        assertEquals(0f, result.globalFraction, 0f);
        assertTrue("New background structure must wake the fixed plate scene", result.changed);
        assertEquals("static_scene_features_added", result.reason);
        assertEquals(16, result.addedFeaturePoints);
        assertEquals(.75f, result.addedFeatureUnmatchedFraction, 0f);
        assertEquals(1f, result.addedFeaturePersistence, 0f);
        assertEquals(3, result.addedFeatureConfirmations);
    }

    @Test public void featurelessOriginalSceneStillDetectsStableNewStructure() {
        byte[] baseline = new byte[SIZE * SIZE];
        Arrays.fill(baseline, (byte) 100);
        StaticSceneWatcher watcher = armed(baseline);
        byte[] changed = addBackground(baseline, 90, 110);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
        StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE, 300_000_000L, false);
        assertTrue(result.changed);
        assertEquals(0, result.featurePoints);
        assertEquals(0, result.sceneFeaturePoints);
        assertEquals(1f, result.addedFeatureUnmatchedFraction, 0f);
        assertEquals("static_scene_features_added", result.reason);
    }

    @Test public void additionChannelAlsoRunsWithGlobalAndVehicleWatchRegions() {
        for (boolean vehicles : new boolean[]{false, true}) {
            byte[] baseline = originalScene();
            byte[] changed = addBackground(baseline, 90, 110);
            StaticSceneWatcher watcher = new StaticSceneWatcher();
            watcher.arm(new StaticSceneWatchRegions(vehicles ? Collections.singletonList(PLATE) : null, null, 0f));
            watcher.observe(baseline, SIZE, SIZE, 0L, false);
            assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
            assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
            StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE, 300_000_000L, false);
            assertTrue(result.changed);
            assertEquals("static_scene_features_added", result.reason);
        }
    }

    @Test public void irregularSchedulingGapsDoNotEraseStableCandidateEvidence() {
        StaticSceneWatcher watcher = armed(originalScene());
        byte[] changed = addBackground(originalScene(), 90, 110);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 700_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 1_500_000_000L, false).changed);
        StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE, 2_600_000_000L, false);
        assertTrue(result.changed);
        assertEquals(3, result.addedFeatureConfirmations);
    }

    @Test public void returnToOriginalSceneClearsTransientAddition() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
        StaticSceneWatcher.Result returned = watcher.observe(baseline, SIZE, SIZE, 300_000_000L, false);
        assertFalse(returned.changed);
        assertEquals(0, returned.addedFeatureConfirmations);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 400_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 500_000_000L, false).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 600_000_000L, false).changed);
    }

    @Test public void alternatingNovelTexturesMustNotConfirmOneScene() {
        byte[] first = addBackground(originalScene(), 90, 110);
        byte[] second = alternateBackground();
        StaticSceneWatcher watcher = armed(originalScene());
        for (int i = 1; i <= 12; i++) {
            StaticSceneWatcher.Result result = watcher.observe(i % 2 == 1 ? first : second,
                    SIZE, SIZE, i * 100_000_000L, false);
            assertFalse("sample=" + i, result.changed);
            assertTrue(result.addedFeatureUnmatchedFraction >= .65f);
            assertEquals("Inconsistent candidate must start fresh", 1, result.addedFeatureConfirmations);
        }
    }

    @Test public void candidateOwnsCopyWhenCameraReusesItsBuffer() {
        byte[] buffer = addBackground(originalScene(), 90, 110);
        StaticSceneWatcher watcher = armed(originalScene());
        assertFalse(watcher.observe(buffer, SIZE, SIZE, 100_000_000L, false).changed);
        byte[] second = alternateBackground();
        System.arraycopy(second, 0, buffer, 0, buffer.length);
        StaticSceneWatcher.Result replaced = watcher.observe(buffer, SIZE, SIZE, 200_000_000L, false);
        assertFalse(replaced.changed);
        assertEquals(1, replaced.addedFeatureConfirmations);
        assertFalse(watcher.observe(buffer, SIZE, SIZE, 300_000_000L, false).changed);
        assertTrue(watcher.observe(buffer, SIZE, SIZE, 400_000_000L, false).changed);
    }

    @Test public void delayedRegionDiscoveryDoesNotReplaceOriginalOrCandidateScene() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(PLATE), null, 0f));
        watcher.prepareForZoom();
        assertTrue(watcher.observe(changed, SIZE, SIZE, 300_000_000L, false).changed);
    }

    @Test public void backwardsTimeReseedsEvenIfItStaysAfterFirstCandidateTime() {
        StaticSceneWatcher watcher = armed(originalScene());
        byte[] changed = addBackground(originalScene(), 90, 110);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 300_000_000L, false).changed);
        StaticSceneWatcher.Result backwards = watcher.observe(changed, SIZE, SIZE, 200_000_000L, false);
        assertFalse(backwards.changed);
        assertEquals(1, backwards.addedFeatureConfirmations);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 400_000_000L, false).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 600_000_000L, false).changed);
    }

    @Test public void repeatedFastCallbacksCannotActAsIndependentConfirmations() {
        StaticSceneWatcher watcher = armed(originalScene());
        byte[] changed = addBackground(originalScene(), 90, 110);
        for (int i = 0; i < 100; i++) {
            StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE,
                    100_000_000L + i * 1_000_000L, false);
            assertFalse(result.changed);
            assertEquals(1, result.addedFeatureConfirmations);
        }
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 250_000_000L, false).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 300_000_000L, false).changed);
    }

    @Test public void resetDiscardsAnUnfinishedCandidate() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        watcher.observe(changed, SIZE, SIZE, 100_000_000L, false);
        watcher.observe(changed, SIZE, SIZE, 200_000_000L, false);
        watcher.reset();
        watcher.arm(new StaticSceneWatchRegions(null, Collections.singletonList(PLATE), 0f));
        assertFalse(watcher.observe(baseline, SIZE, SIZE, 300_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 400_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 500_000_000L, false).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 600_000_000L, false).changed);
    }

    @Test public void discoveringNewRegionsCannotAccelerateAdditionConfirmations() {
        StaticSceneWatcher watcher = armed(originalScene());
        byte[] changed = addBackground(originalScene(), 90, 110);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 100_000_000L, false).changed);
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(
                new NormalizedBounds(.31f, .30f, .69f, .70f)), null, 0f));
        StaticSceneWatcher.Result early = watcher.observe(changed, SIZE, SIZE, 160_000_000L, false);
        assertFalse(early.changed);
        assertEquals(1, early.addedFeatureConfirmations);
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(
                new NormalizedBounds(.32f, .30f, .68f, .70f)), null, 0f));
        StaticSceneWatcher.Result second = watcher.observe(changed, SIZE, SIZE, 220_000_000L, false);
        assertFalse(second.changed);
        assertEquals(2, second.addedFeatureConfirmations);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 320_000_000L, false).changed);
    }

    @Test public void zoomCandidateCannotLeakIntoBaseScene() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        assertFalse(watcher.observe(baseline, SIZE, SIZE, 100_000_000L, true, 1.8f).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 200_000_000L, true, 1.8f).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 300_000_000L, true, 1.8f).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 400_000_000L, false).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 500_000_000L, false).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 600_000_000L, false).changed);
    }

    @Test public void stableZoomStillDetectsAdditionAndChangedZoomGetsNewAnchor() {
        byte[] baseline = originalScene();
        byte[] changed = addBackground(baseline, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        watcher.observe(baseline, SIZE, SIZE, 100_000_000L, true, 1.8f);
        watcher.observe(changed, SIZE, SIZE, 200_000_000L, true, 1.8f);
        watcher.observe(changed, SIZE, SIZE, 300_000_000L, true, 1.8f);
        for (int i = 4; i <= 8; i++)
            assertFalse(watcher.observe(changed, SIZE, SIZE, i * 100_000_000L, true, 2f).changed);
        watcher.observe(baseline, SIZE, SIZE, 900_000_000L, true, 1.8f);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 1_000_000_000L, true, 1.8f).changed);
        assertFalse(watcher.observe(changed, SIZE, SIZE, 1_100_000_000L, true, 1.8f).changed);
        assertTrue(watcher.observe(changed, SIZE, SIZE, 1_200_000_000L, true, 1.8f).changed);
    }

    @Test public void stationaryExposureContrastAndSmallJitterStayIdle() {
        byte[] baseline = addBackground(originalScene(), 95, 105);
        for (int kind = 0; kind < 5; kind++) {
            StaticSceneWatcher watcher = armed(baseline);
            byte[] image = kind == 2 ? addBackground(originalScene(), 90, 110) : baseline.clone();
            if (kind == 1) for (int i = 0; i < image.length; i++) image[i] += 30;
            // Stay below the existing 2.5 px motion threshold: sqrt(2) px or 2 px.
            if (kind >= 3) image = shifted(image, kind - 2, kind == 3 ? 1 : 0);
            for (int i = 1; i <= 12; i++) {
                StaticSceneWatcher.Result result = watcher.observe(image, SIZE, SIZE, i * 650_000_000L, false);
                assertFalse("kind=" + kind + " reason=" + result.reason, result.changed);
                assertEquals(0, result.addedFeatureConfirmations);
            }
        }
    }

    @Test public void backgroundBlurAndSharpeningDoNotInventStructure() {
        byte[] sharp = addBackground(originalScene(), 90, 110);
        for (int passes = 1; passes <= 3; passes++) for (boolean sharpen : new boolean[]{false, true}) {
            byte[] blurred = blurBackground(sharp, passes);
            StaticSceneWatcher watcher = armed(sharpen ? blurred : sharp);
            byte[] image = sharpen ? sharp : blurred;
            for (int i = 1; i <= 12; i++) {
                StaticSceneWatcher.Result result = watcher.observe(image, SIZE, SIZE, i * 100_000_000L, false);
                assertFalse("sharpen=" + sharpen + " reason=" + result.reason, result.changed);
                assertEquals(0, result.addedFeatureConfirmations);
            }
        }
    }

    @Test public void existingMotionAndStrongBlurCuesKeepTheirBoundaryReasons() {
        // FIX06 leaves forward-flow thresholds unchanged. These already caused resets.
        for (boolean blur : new boolean[]{false, true}) {
            byte[] baseline = addBackground(originalScene(), blur ? 90 : 95, blur ? 110 : 105);
            byte[] image = blur ? blurBackground(baseline, 4) : shifted(baseline, 2, 2);
            StaticSceneWatcher watcher = armed(baseline);
            assertFalse(watcher.observe(image, SIZE, SIZE, 100_000_000L, false).changed);
            assertFalse(watcher.observe(image, SIZE, SIZE, 200_000_000L, false).changed);
            StaticSceneWatcher.Result result = watcher.observe(image, SIZE, SIZE, 300_000_000L, false);
            assertTrue(result.changed);
            assertEquals(blur ? "static_scene_features_changed" : "static_alpr_features_changed", result.reason);
            assertEquals(0, result.addedFeatureConfirmations);
        }
    }

    @Test public void independentlyChangingNoiseDoesNotPersistAsANewScene() {
        byte[] baseline = originalScene();
        for (int amplitude : new int[]{3, 9}) {
            StaticSceneWatcher watcher = armed(baseline);
            Random random = new Random(42);
            for (int sample = 1; sample <= 40; sample++) {
                byte[] image = baseline.clone();
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
                    if (x < 50 || x > 140 || y < 50 || y > 140)
                        image[y * SIZE + x] += random.nextInt(amplitude * 2 + 1) - amplitude;
                assertFalse("noise amplitude=" + amplitude + " sample=" + sample,
                        watcher.observe(image, SIZE, SIZE, sample * 100_000_000L, false).changed);
            }
        }
    }

    @Test public void smallLocalBackgroundAdditionIsInsufficient() {
        byte[] baseline = originalScene();
        byte[] changed = baseline.clone();
        corner(changed, 22, 22, 90, 110);
        StaticSceneWatcher watcher = armed(baseline);
        for (int i = 1; i <= 12; i++) {
            StaticSceneWatcher.Result result = watcher.observe(changed, SIZE, SIZE, i * 100_000_000L, false);
            assertFalse(result.changed);
            assertEquals(0, result.addedFeatureConfirmations);
        }
    }

    @Test public void appearanceMatchingUsesNormalizedContrastAndTwoPixelSearch() {
        byte[] image = addBackground(originalScene(), 90, 110);
        RegionFeatureChangeDetector detector = currentFeatures(image);
        RegionFeatureChangeDetector.AppearanceResult base = detector.compareAppearance(originalScene());
        assertTrue(base.novel);
        assertEquals(16, base.points);
        assertEquals(4, base.supportedPoints);
        byte[] weaker = addBackground(originalScene(), 95, 105);
        for (byte[] supported : new byte[][]{image, weaker, shifted(image, 2, -2), blurBackground(image, 4)}) {
            RegionFeatureChangeDetector.AppearanceResult result = detector.compareAppearance(supported);
            assertFalse(result.novel);
            assertTrue(result.persistent);
            assertEquals(16, result.supportedPoints);
        }
    }

    @Test public void nearlyFlatPatchesAreNotAppearanceEvidence() {
        RegionFeatureChangeDetector detector = currentFeatures(addBackground(originalScene(), 90, 110));
        RegionFeatureChangeDetector.AppearanceResult result = detector.compareAppearance(
                addBackground(originalScene(), 99, 101));
        assertTrue(result.novel);
        assertEquals(4, result.supportedPoints);
    }

    @Test public void fewerThanFourReliablePointsAndInvalidInputCannotInventEvidence() {
        byte[] image = new byte[SIZE * SIZE];
        Arrays.fill(image, (byte) 100);
        for (int x : new int[]{22, 72, 122}) corner(image, x, 22, 90, 110);
        RegionFeatureChangeDetector detector = currentFeatures(image);
        assertEquals(0, detector.compareAppearance(originalScene()).points);
        detector = currentFeatures(addBackground(originalScene(), 90, 110));
        assertFalse(detector.compareAppearance(null).novel);
        assertFalse(detector.compareAppearance(new byte[1]).persistent);
        detector.reset();
        assertEquals(0, detector.compareAppearance(originalScene()).points);
    }

    private static RegionFeatureChangeDetector currentFeatures(byte[] image) {
        RegionFeatureChangeDetector detector = new RegionFeatureChangeDetector();
        detector.anchor(image, SIZE, SIZE, Collections.singletonList(new NormalizedBounds(0f, 0f, 1f, 1f)));
        return detector;
    }

    private static byte[] alternateBackground() {
        byte[] image = originalScene();
        for (int top = 22; top < 180; top += 31) for (int left = 22; left < 180; left += 31) {
            if (left >= 50 && left <= 140 && top >= 50 && top <= 140) continue;
            for (int y = top; y < top + 9; y++) for (int x = left; x < left + 9; x++)
                image[y * SIZE + x] = (byte) (y < top + 5 ? 90 : 110);
        }
        return image;
    }

    private static byte[] shifted(byte[] source, int dx, int dy) {
        byte[] image = new byte[source.length];
        Arrays.fill(image, (byte) 100);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (x + dx >= 0 && x + dx < SIZE && y + dy >= 0 && y + dy < SIZE)
                image[(y + dy) * SIZE + x + dx] = source[y * SIZE + x];
        return image;
    }

    private static byte[] blurBackground(byte[] source, int passes) {
        byte[] image = source.clone();
        int[] kernel = {1, 4, 6, 4, 1};
        for (int pass = 0; pass < passes; pass++) {
            byte[] blurred = image.clone();
            for (int y = 2; y < SIZE - 2; y++) for (int x = 2; x < SIZE - 2; x++) {
                if (x >= 50 && x <= 140 && y >= 50 && y <= 140) continue; // Keep the plate fixed.
                int sum = 0;
                for (int sy = -2; sy <= 2; sy++) for (int sx = -2; sx <= 2; sx++)
                    sum += (image[(y + sy) * SIZE + x + sx] & 255) * kernel[sy + 2] * kernel[sx + 2];
                blurred[y * SIZE + x] = (byte) ((sum + 128) / 256);
            }
            image = blurred;
        }
        return image;
    }

    private static byte[] originalScene() {
        byte[] image = new byte[SIZE * SIZE];
        Arrays.fill(image, (byte) 100);
        for (int y : new int[]{72, 112}) for (int x : new int[]{72, 112}) corner(image, x, y, 50, 170);
        return image;
    }

    private static byte[] addBackground(byte[] baseline, int low, int high) {
        byte[] image = baseline.clone();
        for (int y = 22; y < 180; y += 31) for (int x = 22; x < 180; x += 31) {
            if (x >= 50 && x <= 140 && y >= 50 && y <= 140) continue;
            corner(image, x, y, low, high);
        }
        return image;
    }

    private static void corner(byte[] image, int left, int top, int low, int high) {
        for (int y = top; y < top + 9; y++) for (int x = left; x < left + 9; x++)
            image[y * SIZE + x] = (byte) (x < left + 5 ? low : high);
    }
}
