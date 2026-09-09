package com.example.alpr_v1.acquisition;

/** Immutable admission and spatial policy, frozen with each research run. */
public final class DynamicMtConfig {
    public static final String POLICY = "adaptive_entity_top1_v1";
    public static final float LOCAL_MARGIN_X = .5f, LOCAL_MARGIN_Y = .75f;
    public static final float PRIMARY_MARGIN_X = .04f, PRIMARY_MARGIN_BOTTOM = .08f;
    public static final long LOCAL_MAX_AGE_NANOS = 5_000_000_000L;
    public static final DynamicMtConfig INITIAL = new DynamicMtConfig(DynamicVehicleSizeGate.Config.INITIAL, .35f);
    public final DynamicVehicleSizeGate.Config size;
    public final float primaryTopFraction;

    public DynamicMtConfig(DynamicVehicleSizeGate.Config size, float primaryTopFraction) {
        if (size == null || !Float.isFinite(primaryTopFraction)
                || primaryTopFraction < 0f || primaryTopFraction > .8f)
            throw new IllegalArgumentException("Invalid dynamic MT configuration");
        this.size = size;
        this.primaryTopFraction = primaryTopFraction;
    }
}
