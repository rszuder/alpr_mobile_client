package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.AppearanceDescriptor;

import org.junit.Test;

import java.util.Arrays;

public class VehicleAppearanceDescriptorTest {
    @Test
    public void uniformAndTexturedCropsAlwaysHaveFixedFiniteDimension() {
        float[] uniform = new float[72];
        Arrays.fill(uniform, 0.45f);
        float[] reflected = uniform.clone();
        for (int index = 0; index < reflected.length; index += 11) {
            reflected[index] = Math.min(1f, reflected[index] + 0.15f);
        }

        AppearanceDescriptor first = VehicleAppearanceDescriptor.fromSampledRgb(uniform);
        AppearanceDescriptor second = VehicleAppearanceDescriptor.fromSampledRgb(reflected);

        assertEquals(VehicleAppearanceDescriptor.DESCRIPTOR_SIZE, first.values().length);
        assertEquals(VehicleAppearanceDescriptor.DESCRIPTOR_SIZE, second.values().length);
        for (float value : first.values()) assertTrue(Float.isFinite(value));
        for (float value : second.values()) assertTrue(Float.isFinite(value));
        float similarity = first.cosineSimilarity(second);
        assertTrue(Float.isFinite(similarity));
        assertTrue(similarity > 0.70f);
    }

    @Test
    public void invalidSamplesProduceExplicitUnavailableDescriptor() {
        assertEquals(0,
                VehicleAppearanceDescriptor.fromSampledRgb(new float[3]).values().length);
    }
}
