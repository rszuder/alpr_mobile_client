package com.example.alpr_v1.camera;

import java.util.Locale;
import java.util.Objects;

/** Immutable live confirmation metadata. Never serialized into research/session contracts. */
public final class CameraSetupReadiness {
    public enum Reason {
        READY(""),
        CAMERA_NOT_BOUND("Uruchamianie wybranego obiektywu"),
        CAMERA_ROUTE_UNCONFIRMED("Czekam na potwierdzenie wybranego obiektywu"),
        ZOOM_NOT_ACKNOWLEDGED("Czekam na potwierdzenie zastosowanego zoomu"),
        NO_FRESH_CAMERA_FRAME("Czekam na świeżą klatkę potwierdzonej kamery"),
        CAMERA_SETUP_SNAPSHOT_MISSING("Brak potwierdzonego snapshotu kamery"),
        CAMERA_SETUP_SNAPSHOT_STALE("Potwierdzenie konfiguracji kamery jest nieaktualne"),
        CAMERA_SETUP_GEOMETRY_MISMATCH("Konfiguracja kamery nie odpowiada geometrii TV"),
        CAMERA_SETUP_GENERATION_CHANGED("Zmieniło się wiązanie kamery; przygotuj START ponownie");
        private final String message;
        Reason(String message){this.message=message;}
        public String description(){return this==READY?"":name()+" · "+message;}
    }
    public final Reason reason;
    public final CameraSetupSnapshot applied;
    public final CameraSelection requested,actual;
    public final float requestedZoom,actualZoom;
    public final int bindingGeneration;
    public final long revision,observedElapsedNanos,lastFrameElapsedNanos,frameTimestampNanos,frameSequence,ackFrameSequence;
    public final boolean zoomAcknowledged,publishedSnapshotPresent;
    public final String captureThread;

    public CameraSetupReadiness(Reason reason,CameraSetupSnapshot applied,CameraSelection requested,float requestedZoom,
            CameraSelection actual,float actualZoom,int bindingGeneration,long revision,long observedElapsedNanos,
            long lastFrameElapsedNanos,long frameTimestampNanos,long frameSequence,long ackFrameSequence,
            boolean zoomAcknowledged,boolean publishedSnapshotPresent){
        this.reason=Objects.requireNonNull(reason);this.applied=applied;this.requested=requested;this.requestedZoom=requestedZoom;
        this.actual=actual;this.actualZoom=actualZoom;this.bindingGeneration=bindingGeneration;this.revision=revision;
        this.observedElapsedNanos=observedElapsedNanos;this.lastFrameElapsedNanos=lastFrameElapsedNanos;
        this.frameTimestampNanos=frameTimestampNanos;this.frameSequence=frameSequence;this.ackFrameSequence=ackFrameSequence;
        this.zoomAcknowledged=zoomAcknowledged;this.publishedSnapshotPresent=publishedSnapshotPresent;
        captureThread=Thread.currentThread().getName();
        if(reason==Reason.READY&&applied==null)throw new IllegalArgumentException("READY requires confirmed camera setup");
    }
    public boolean ready(){return reason==Reason.READY&&applied!=null;}
    public boolean frameFreshAt(long elapsedNanos){
        long age=elapsedNanos-lastFrameElapsedNanos;
        return lastFrameElapsedNanos>0&&age>=0&&age<500_000_000L&&frameSequence>ackFrameSequence;
    }
    /** Identity only: READY here does not authorize START without a fresh current confirmation. */
    public Reason identityFailure(CameraSetupReadiness current){
        if(current==null)return Reason.CAMERA_SETUP_SNAPSHOT_MISSING;
        if(bindingGeneration!=current.bindingGeneration)return Reason.CAMERA_SETUP_GENERATION_CHANGED;
        if(revision!=current.revision||!Objects.equals(requested,current.requested)
                ||Math.abs(requestedZoom-current.requestedZoom)>=1e-4
                ||!Objects.equals(actual,current.actual)
                ||Float.isFinite(actualZoom)&&Float.isFinite(current.actualZoom)&&Math.abs(actualZoom-current.actualZoom)>=1e-4)
            return Reason.CAMERA_SETUP_SNAPSHOT_STALE;
        return Reason.READY;
    }
    public String diagnostics(){
        long age=lastFrameElapsedNanos<=0?-1:observedElapsedNanos-lastFrameElapsedNanos;
        return String.format(Locale.ROOT,
                "reason=%s requested_route=%s actual_route=%s requested_zoom=%.5f actual_zoom=%s binding=%d revision=%d zoom_ack=%s frame_sequence=%d ack_sequence=%d frame_sensor_ns=%s frame_received_elapsed_ns=%s frame_age_ms=%s published_applied=%s capture_thread=%s reader_thread=%s",
                reason,requested,actual==null?"—":actual,requestedZoom,Float.isFinite(actualZoom)?String.format(Locale.ROOT,"%.5f",actualZoom):"—",
                bindingGeneration,revision,zoomAcknowledged,frameSequence,ackFrameSequence,
                frameTimestampNanos>0?Long.toString(frameTimestampNanos):"—",lastFrameElapsedNanos>0?Long.toString(lastFrameElapsedNanos):"—",
                age<0?"—":String.format(Locale.ROOT,"%.1f",age/1_000_000.0),publishedSnapshotPresent,captureThread,Thread.currentThread().getName());
    }
}
