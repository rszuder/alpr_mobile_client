package com.example.alpr_v1.ui;

/**
 * Lokalna generacja prezentacji używana zanim ciężki koordynator zdąży
 * podnieść domenową visualEpoch.
 */
public final class PreviewPresentationBarrier {
    private long generation;
    private boolean active;

    public synchronized long capture() {
        return generation;
    }

    public synchronized long activate() {
        active = true;
        return ++generation;
    }

    public synchronized long release() {
        active = false;
        return ++generation;
    }

    public synchronized boolean permits(long capturedGeneration) {
        return !active && capturedGeneration == generation;
    }

    public synchronized boolean active() {
        return active;
    }

    public synchronized boolean isActiveGeneration(long expectedGeneration) {
        return active && generation == expectedGeneration;
    }

    public synchronized void reset() {
        active = false;
        generation++;
    }
}
