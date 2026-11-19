package com.example.alpr_v1.camera;

/** Explicit ownership of transient bitmap allocations, including work waiting for the UI. */
public final class BitmapMemoryBudget {
    private final long maximum;
    private long used, peak;
    public BitmapMemoryBudget(long maximum) { this.maximum = Math.max(0, maximum); }
    public synchronized Lease acquire(long bytes) {
        if (bytes <= 0 || bytes > maximum - used) return null;
        used += bytes; peak = Math.max(peak, used);
        return new Lease(bytes);
    }
    public synchronized long usedBytes() { return used; }
    public synchronized long peakBytes() { return peak; }
    public final class Lease implements AutoCloseable {
        private long bytes;
        private Lease(long bytes) { this.bytes = bytes; }
        @Override public void close() {
            synchronized (BitmapMemoryBudget.this) { used -= bytes; bytes = 0; }
        }
    }
}
