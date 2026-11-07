package com.example.alpr_v1.ui;

/** Operator intent is independent of background model preparation and permission lifecycle. */
public final class ExperimentStartRequest {
    private long generation;
    private boolean pending, permissionDialog;
    private long permissionGeneration = -1L;

    public synchronized long request() {
        if (!pending) { pending = true; generation++; }
        return generation;
    }
    public synchronized boolean pending() { return pending; }
    public synchronized boolean waitingForPermission() { return permissionDialog; }
    public synchronized void permissionStarted() {
        permissionDialog = true;
        permissionGeneration = pending ? generation : -1L;
    }
    public synchronized boolean permissionResult(boolean granted) {
        boolean current = pending && permissionGeneration == generation;
        permissionDialog = false;
        permissionGeneration = -1L;
        if (!granted && current) cancel();
        return granted && current;
    }
    public synchronized boolean consumeIfReady(boolean ready) {
        if (!pending || !ready || permissionDialog) return false;
        pending = false;
        generation++;
        return true;
    }
    public synchronized void backgrounded() {
        if (!permissionDialog) cancel();
    }
    public synchronized void cancel() {
        pending = false;
        generation++;
    }
}
