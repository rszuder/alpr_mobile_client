package com.example.alpr_v1.camera;

import java.util.Arrays;

/** Display-only meter at camera callback ingress, independent of inference admission. */
public final class LiveFrameRateMeter {
    private final long[] seconds = {-1L,-1L,-1L};
    private final long[] counts = new long[3];
    private long started = -1L, last = -1L;
    public synchronized void reset() {
        Arrays.fill(seconds,-1L); Arrays.fill(counts,0L); started = last = -1L;
    }
    public synchronized void onFrame(long now) {
        if (now < 0L) return;
        if (last > now) reset();
        if (started < 0L) started = now;
        last = now;
        long second = now / 1_000_000_000L;
        int index = (int)(second % 3);
        if (seconds[index] != second) { seconds[index] = second; counts[index] = 0L; }
        counts[index]++;
    }
    public synchronized double rate(long now) {
        if (started < 0L || now < last) return Double.NaN;
        long end = now / 1_000_000_000L * 1_000_000_000L;
        long begin = Math.max(started,end-2_000_000_000L);
        if (end-begin < 500_000_000L) return Double.NaN;
        long frames = 0L;
        for (int i=0;i<3;i++) if(seconds[i]>=begin/1_000_000_000L && seconds[i]<end/1_000_000_000L)
            frames += counts[i];
        return frames*1_000_000_000.0/(end-begin);
    }
}
