package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import java.util.Map;
import java.util.TreeMap;

/** Validated local motion only; interpolates a neural source between adjacent camera samples. */
final class LocalMotionHistory {
    private static final long MAX_GAP = 500_000_000L, MAX_AGE = 10_000_000_000L;
    private final TreeMap<Long, RobustAffineTransform.Result> samples = new TreeMap<>();
    private SourceFrameStamp context;

    static boolean sameContext(SourceFrameStamp a, SourceFrameStamp b) {
        return a != null && b != null && a.domain == b.domain
                && a.sceneGeneration == b.sceneGeneration && a.visualEpoch == b.visualEpoch
                && a.cameraTransformGeneration == b.cameraTransformGeneration;
    }

    void record(SourceFrameStamp source, RobustAffineTransform.Result motion) {
        if (source == null || !source.domain.cameraDerived() || !motion.valid) { clear(); return; }
        if (!sameContext(context, source) || !samples.isEmpty()
                && source.sourceTimestampNanos - samples.lastKey() > MAX_GAP) clear();
        context = source;
        samples.put(source.sourceTimestampNanos, motion);
        while (samples.size() > 128 || !samples.isEmpty()
                && source.sourceTimestampNanos - samples.firstKey() > MAX_AGE) samples.pollFirstEntry();
    }

    RobustAffineTransform.Result at(SourceFrameStamp source) {
        if (!sameContext(context, source)) return RobustAffineTransform.Result.invalid();
        Map.Entry<Long, RobustAffineTransform.Result> lo = samples.floorEntry(source.sourceTimestampNanos);
        Map.Entry<Long, RobustAffineTransform.Result> hi = samples.ceilingEntry(source.sourceTimestampNanos);
        if (lo == null || hi == null || hi.getKey() - lo.getKey() > MAX_GAP)
            return RobustAffineTransform.Result.invalid();
        if (lo.getKey().equals(hi.getKey())) return lo.getValue();
        float t = (source.sourceTimestampNanos - lo.getKey()) / (float)(hi.getKey() - lo.getKey());
        RobustAffineTransform.Result a = lo.getValue(), b = hi.getValue();
        return new RobustAffineTransform.Result(true, mix(a.a,b.a,t), mix(a.b,b.b,t), mix(a.tx,b.tx,t),
                mix(a.c,b.c,t), mix(a.d,b.d,t), mix(a.ty,b.ty,t),
                Math.min(a.inlierCount,b.inlierCount), Math.max(a.meanError,b.meanError));
    }

    private static float mix(float a,float b,float t) { return a+(b-a)*t; }
    void clear() { samples.clear(); context = null; }
}
