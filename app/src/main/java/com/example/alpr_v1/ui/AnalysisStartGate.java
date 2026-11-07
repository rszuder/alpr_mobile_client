package com.example.alpr_v1.ui;

/** A cancelled/replaced preparation must never start a camera or a measurement later. */
public final class AnalysisStartGate {
    private long generation;
    private boolean preparing;
    public synchronized long begin() {
        if (preparing) return -1L;
        preparing = true;
        return ++generation;
    }
    public synchronized boolean isCurrent(long ticket) { return preparing && ticket == generation; }
    public synchronized boolean preparing() { return preparing; }
    public synchronized boolean complete(long ticket) {
        if (!isCurrent(ticket)) return false;
        preparing = false;
        return true;
    }
    public synchronized void cancel() { preparing = false; generation++; }
}
