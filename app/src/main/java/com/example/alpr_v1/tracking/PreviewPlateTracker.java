package com.example.alpr_v1.tracking;

import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.ObservationMask;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;

import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.ui.OverlayViewportTransform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Lekki tracker tablic działający na klatkach PreviewView.
 *
 * MT okresowo dostarcza dokładne położenie tablic,
 * a pomiędzy kolejnymi wynikami MT tracker szacuje
 * ich przesunięcie na obrazie.
 *
 * Wersja v2:
 * - obsługuje wiele tablic,
 * - każda tablica ma własne cztery punkty,
 * - wspólny obraz Preview służy jako źródło ruchu,
 * - na razie śledzona jest translacja całego quada.
 */
public final class PreviewPlateTracker {
    private final boolean partialVehicle;

    public PreviewPlateTracker() { this(false); }

    /** Partial visibility is supported only by the ordinary dynamic vehicle tracker. */
    public PreviewPlateTracker(boolean partialVehicle) { this.partialVehicle=partialVehicle; }

    public static final class CameraMotionSample {
        public final com.example.alpr_v1.continuity.SourceFrameStamp from,to;
        public final FrameMotionTransform motion;
        public CameraMotionSample(com.example.alpr_v1.continuity.SourceFrameStamp from,
                com.example.alpr_v1.continuity.SourceFrameStamp to,FrameMotionTransform motion) {
            this.from=from;this.to=to;this.motion=motion;
        }
        boolean matches(com.example.alpr_v1.continuity.SourceFrameStamp previous,
                com.example.alpr_v1.continuity.SourceFrameStamp current) {
            return previous!=null&&current!=null&&LocalMotionHistory.sameContext(previous,current)
                    &&LocalMotionHistory.sameContext(from,previous)&&LocalMotionHistory.sameContext(to,current)
                    &&from.sourceSequence==previous.sourceSequence&&from.sourceTimestampNanos==previous.sourceTimestampNanos
                    &&to.sourceSequence==current.sourceSequence&&to.sourceTimestampNanos==current.sourceTimestampNanos
                    &&current.domain.cameraDerived()&&current.sourceTimestampNanos>previous.sourceTimestampNanos
                    &&current.sourceTimestampNanos-previous.sourceTimestampNanos<=500_000_000L
                    &&motion!=null&&motion.valid&&motion.significant()&&motion.quality.reliableCameraMotion();
        }
    }
    private FrameMotionTransform processingCameraMotion;

    private ObservationMask observationMask;

    private static final int MAX_TRACK_WIDTH =
            240;

    private static final int PATCH_RADIUS =
            3;

    private static final int SEARCH_RADIUS =
            14;

    private static final int ANCHOR_SEARCH_RADIUS =
            22;

    private static final float MAX_MEAN_ERROR =
            34f;

    private static final int DISPLACEMENT_CONSISTENCY =
            3;

    /*
     * Po kilku kolejnych nieudanych dopasowaniach
     * przestajemy pokazywać dany track.
     */
    private static final int MAX_CONSECUTIVE_FAILURES =
            3;

    private static final int APPEARANCE_GRID_X = 20;
    private static final int APPEARANCE_GRID_Y = 8;


    private static final class TrackState {

        OverlayItem basePlate;
        boolean foregroundOnly;
        boolean localPixelValidated;
        boolean localPositionAvailable;
        long lastReliableSourceNanos;
        DynamicVehicleIdentity.Stamp identityOwner;
        long anchorSourceTimestampNanos;
        final LocalMotionHistory motionHistory = new LocalMotionHistory();
        List<SparsePyramidalFlow.Point> displayAnchorPoints = Collections.emptyList();
        List<SparsePyramidalFlow.Point> referenceAnchorPoints = Collections.emptyList();

        List<PointF> trackedPoints =
                Collections.emptyList();

        List<PointF> anchorPoints =
                Collections.emptyList();

        List<SparsePyramidalFlow.Point> flowAnchorPoints =
                Collections.emptyList();

        List<SparsePyramidalFlow.Point> currentFlowPoints =
                Collections.emptyList();

        RobustAffineTransform.Result affineTransform =
                RobustAffineTransform.Result.identity();

        final PlateBoxKalman kalman = new PlateBoxKalman();

        OverlayItem lastTrackedOverlay;

        int trackerInliers;

        int consecutiveFailures;
        int ageFrames;
        int framesSinceMtAnchor;
        float trackingQuality = 1f;
        float supportRatio = 1f;
        float meanMatchError;
        float[] anchorAppearance;
        byte[] referenceGray;
        float localAppearanceSimilarity;
        boolean localAppearanceValidated;


        TrackState(
                OverlayItem basePlate
        ) {

            this.basePlate =
                    basePlate;
        }
    }


    private final Object controls = new Object();
    private static final class Control {
        final List<TrackState> tracks;
        final int width, height;
        final long revision;
        final GrayFrame sourceReference;
        final com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp;
        final boolean ownerScoped;
        Control(List<TrackState> tracks, int width, int height, long revision) {
            this(tracks,width,height,revision,null,null);
        }
        Control(List<TrackState> tracks,int width,int height,long revision,GrayFrame sourceReference,
                com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp) {
            this.tracks = tracks; this.width = width; this.height = height; this.revision = revision;
            this.sourceReference=sourceReference;
            this.sourceStamp=sourceStamp;
            boolean scoped=sourceStamp!=null&&sourceStamp.domain.cameraDerived()&&tracks!=null&&!tracks.isEmpty();
            // A localized camera search never evaluates every domain owner, even if a stamp is unavailable.
            ownerScoped=scoped;
        }
    }
    private Control pendingControl;
    private Control lastQueuedMeasurement;
    private com.example.alpr_v1.continuity.SourceFrameStamp processingSourceStamp, lastProcessedSource;
    private long controlRevision, appliedControlRevision;
    private boolean ownerScopedTracking;
    private volatile int publishedSourceWidth, publishedSourceHeight;
    private volatile java.util.Set<Long> retainedTrackIds;
    private GrayFrame pendingSourceReference;
    private com.example.alpr_v1.continuity.SourceFrameStamp pendingSourceStamp;
    private com.example.alpr_v1.continuity.SourceFrameStamp replayOrigin, replayCursor;
    private int replaySteps;
    private volatile int lastReplaySteps;
    public int lastReplaySteps() {return lastReplaySteps;}

    /** Filtering preserves the chosen anchor and its evidence instead of resetting the selected tracker. */
    public void retainOnly(java.util.Set<Long> ids) {
        retainedTrackIds=ids==null?null:new java.util.HashSet<>(ids);
    }

    /** Called only by the serialized tracking worker, never while holding the controls lock in UI. */
    private void applyPendingControl() {
        Control command;
        synchronized (controls) {
            command = pendingControl;
            if(replayOrigin!=null&&compatibleControl(command)) {retainSelectedTracks();return;}
            pendingControl = null;
        }
        if (command == null) {
            retainSelectedTracks();
            return;
        }
        ownerScopedTracking=command.ownerScoped;
        if (command.tracks == null) resetImageState();
        else if (applyOwnerIsolatedMeasurement(command)||applyContinuousMeasurement(command)) {
            appliedControlRevision = command.revision;
            retainSelectedTracks();
            return;
        }
        else {
            android.util.Log.d("ALPR_PREVIEW_TRACK","measurement=restart previous_tracks="+tracks.size()
                    +" appearance="+(tracks.isEmpty()?0:tracks.get(0).localAppearanceSimilarity)
                    +" failures="+(tracks.isEmpty()?0:tracks.get(0).consecutiveFailures));
            replayOrigin=null;replayCursor=null;replaySteps=0;
            tracks = command.tracks;
            sourceWidth = command.width; sourceHeight = command.height;
            previousGray = null;
        }
        pendingSourceReference=command.sourceReference;
        pendingSourceStamp=command.sourceStamp;
        appliedControlRevision = command.revision;
        retainSelectedTracks();
    }

    private static boolean sameOwner(DynamicVehicleIdentity.Stamp a, DynamicVehicleIdentity.Stamp b) {
        return a == b || a != null && b != null && a.entityId == b.entityId && a.epoch == b.epoch
                && a.vehicleTrackId == b.vehicleTrackId && a.analysisRunId == b.analysisRunId;
    }

    /** An asynchronous measurement of B must not restart A's current, source-proven pixel path. */
    private boolean applyOwnerIsolatedMeasurement(Control command) {
        if(command.sourceStamp==null||!command.sourceStamp.domain.cameraDerived()||previousGray==null
                ||lastProcessedSource==null||tracks.isEmpty()||command.sourceReference==null
                ||command.width!=sourceWidth||command.height!=sourceHeight
                ||command.sourceReference.width!=trackerWidth||command.sourceReference.height!=trackerHeight
                ||!LocalMotionHistory.sameContext(command.sourceStamp,lastProcessedSource))return false;
        long run=0;
        Map<Long,Integer> counts=new java.util.HashMap<>();
        for(TrackState incoming:command.tracks) {
            if(!incoming.foregroundOnly||incoming.identityOwner==null||incoming.identityOwner.analysisRunId<=0)return false;
            if(run!=0&&run!=incoming.identityOwner.analysisRunId)return false;
            run=incoming.identityOwner.analysisRunId;
            counts.put(incoming.identityOwner.entityId,counts.getOrDefault(incoming.identityOwner.entityId,0)+1);
        }
        for(TrackState live:tracks)if(!live.foregroundOnly||live.identityOwner==null||live.identityOwner.analysisRunId!=run)return false;
        if(command.sourceStamp.sourceTimestampNanos>lastProcessedSource.sourceTimestampNanos) {
            // A faster neural worker may deliver a source not yet reached by local tracking.
            synchronized(controls) {
                if(pendingControl==null||pendingControl.revision<command.revision)pendingControl=command;
            }
            return true;
        }
        List<TrackState> retained=new ArrayList<>(tracks);
        int rebased=0,deferred=0;
        for(TrackState incoming:command.tracks) {
            boolean revoked=false;
            for(TrackState old:retained)if(old.identityOwner.entityId==incoming.identityOwner.entityId
                    &&old.identityOwner.epoch>incoming.identityOwner.epoch){revoked=true;break;}
            if(revoked){deferred++;continue;}
            retained.removeIf(old->old.identityOwner.entityId==incoming.identityOwner.entityId
                    &&!sameOwner(old.identityOwner,incoming.identityOwner));
            if(counts.get(incoming.identityOwner.entityId)!=1){deferred++;continue;}
            TrackState live=continuousTrack(incoming);
            Control one=new Control(Collections.singletonList(incoming),command.width,command.height,command.revision,
                    command.sourceReference,command.sourceStamp);
            if(live!=null&&applyContinuousMeasurement(one))continue;
            TrackState fresh=rebaseOwnedMeasurement(incoming,command);
            if(fresh==null){deferred++;continue;}
            if(live!=null)retained.remove(live);
            if(retained.size()>=com.example.alpr_v1.domain.VehicleEntityRepository.MAX_ACTIVE_ENTITIES){deferred++;continue;}
            retained.add(fresh);rebased++;
        }
        tracks=retained;
        android.util.Log.d("ALPR_PREVIEW_TRACK","measurement=owner_isolated tracks="+tracks.size()
                +" rebased="+rebased+" deferred="+deferred);
        return true;
    }

    /** Join only after a direct, validated source-to-current local match; no camera-motion substitute. */
    private TrackState rebaseOwnedMeasurement(TrackState fresh,Control command) {
        long gap=lastProcessedSource.sourceTimestampNanos-command.sourceStamp.sourceTimestampNanos;
        if(gap<0||gap>5_000_000_000L)return null;
        List<PointF> points=initialTrackerPoints(fresh.basePlate,previousPreviewWidth,previousPreviewHeight,trackerWidth,trackerHeight);
        if(points.size()<4)return null;
        fresh.anchorPoints=points;fresh.trackedPoints=points;
        fresh.flowAnchorPoints=initialFlowPoints(points,true);
        fresh.currentFlowPoints=fresh.flowAnchorPoints;
        fresh.referenceAnchorPoints=new ArrayList<>(fresh.flowAnchorPoints.subList(0,4));
        fresh.referenceGray=command.sourceReference.gray;
        fresh.anchorAppearance=localAppearanceDescriptor(fresh.referenceGray,trackerWidth,trackerHeight,points,partialVehicle);
        if(fresh.anchorAppearance==null)return null;
        AffineTrackUpdate update=affineStep(fresh,fresh.referenceGray,previousGray,trackerWidth,trackerHeight,true);
        if(!update.valid)return null;
        fresh.affineTransform=update.transform;
        fresh.currentFlowPoints=transformedPoints(fresh.flowAnchorPoints,update.transform);
        fresh.trackedPoints=firstFourAndroidPoints(fresh.currentFlowPoints);
        fresh.supportRatio=update.supportRatio;fresh.meanMatchError=update.meanError;fresh.trackerInliers=update.inliers;
        fresh.trackingQuality=affineTrackingQuality(update,0);
        float[] appearance=localAppearanceDescriptor(previousGray,trackerWidth,trackerHeight,fresh.trackedPoints,partialVehicle);
        if(appearance==null)return null;
        fresh.localAppearanceValidated=true;
        fresh.localAppearanceSimilarity=localAppearanceSimilarity(fresh.anchorAppearance,appearance);
        OverlayItem overlay=buildAffineTrackedPlate(fresh,previousPreviewWidth,previousPreviewHeight);
        if(overlay==null)return null;
        TrackedPlate proof=new TrackedPlate(overlay,fresh.trackingQuality,fresh.supportRatio,fresh.meanMatchError,
                fresh.trackerInliers,0,0,0,fresh.localAppearanceSimilarity,true,appearance,
                SystemClock.elapsedRealtimeNanos(),fresh.identityOwner,fresh.anchorSourceTimestampNanos);
        if(!VehicleTrackingCoordinator.validLocal(proof))return null;
        fresh.localPixelValidated=true;fresh.localPositionAvailable=true;fresh.lastTrackedOverlay=overlay;
        fresh.lastReliableSourceNanos=lastProcessedSource.sourceTimestampNanos;
        fresh.motionHistory.record(command.sourceStamp,RobustAffineTransform.Result.identity());
        fresh.motionHistory.record(lastProcessedSource,update.transform);
        return fresh;
    }

    private TrackState continuousTrack(TrackState incoming) {
        TrackState owned = null;
        for (TrackState live : tracks) {
            if (live.basePlate.trackId == incoming.basePlate.trackId)
                return sameOwner(live.identityOwner,incoming.identityOwner) ? live : null;
            // A detector track may expire while this owner's pixel track is still healthy.
            // Never transfer between owners, or choose between two plates of one owner.
            if (incoming.identityOwner != null && sameOwner(live.identityOwner,incoming.identityOwner)) {
                if (owned != null) return null;
                owned = live;
            }
        }
        return owned;
    }

    private boolean overlapsMeasuredTrack(TrackState live,List<PointF> measured,
            RobustAffineTransform.Result motion) {
        List<PointF> predicted=firstFourAndroidPoints(transformedPoints(live.referenceAnchorPoints,motion));
        RectF a=pointBounds(predicted),b=pointBounds(measured);
        if(a==null||b==null)return false;
        float intersection=Math.max(0f,Math.min(a.right,b.right)-Math.max(a.left,b.left))
                *Math.max(0f,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        float union=a.width()*a.height()+b.width()*b.height()-intersection;
        return union>0f&&(intersection/union>=.5f
                ||intersection/(a.width()*a.height())>=.95f&&b.width()*b.height()<=4f*a.width()*a.height());
    }

    private static RectF pointBounds(List<PointF> points) {
        if(points==null||points.size()<4)return null;
        RectF bounds=new RectF(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY);
        for(PointF p:points){bounds.left=Math.min(bounds.left,p.x);bounds.top=Math.min(bounds.top,p.y);
            bounds.right=Math.max(bounds.right,p.x);bounds.bottom=Math.max(bounds.bottom,p.y);}
        return bounds.width()>0f&&bounds.height()>0f?bounds:null;
    }

    private boolean clippedVehicleMeasurement(TrackState live,List<PointF> measured,RobustAffineTransform.Result motion) {
        if(!partialVehicle||!live.foregroundOnly)return false;
        List<SparsePyramidalFlow.Point> reference=live.displayAnchorPoints.isEmpty()?live.flowAnchorPoints:live.displayAnchorPoints;
        RectF full=pointBounds(firstFourAndroidPoints(transformedPoints(reference,motion))),box=pointBounds(measured);
        if(full==null||box==null)return false;
        boolean edge=full.left< -1&&box.left<=1.5f||full.top< -1&&box.top<=1.5f
                ||full.right>trackerWidth+1&&box.right>=trackerWidth-1.5f
                ||full.bottom>trackerHeight+1&&box.bottom>=trackerHeight-1.5f;
        if(!edge)return false;
        RectF visible=new RectF(Math.max(0,full.left),Math.max(0,full.top),
                Math.min(trackerWidth,full.right),Math.min(trackerHeight,full.bottom));
        float intersection=Math.max(0,Math.min(visible.right,box.right)-Math.max(visible.left,box.left))
                *Math.max(0,Math.min(visible.bottom,box.bottom)-Math.max(visible.top,box.top));
        float union=visible.width()*visible.height()+box.width()*box.height()-intersection;
        return union>0&&intersection/union>=.75f;
    }

    /** A delayed neural correction must not throw away an already validated pixel path. */
    private boolean applyContinuousMeasurement(Control command) {
        if (command.sourceStamp == null || previousGray == null || command.width != sourceWidth
                || command.height != sourceHeight || !LocalMotionHistory.sameContext(command.sourceStamp,lastProcessedSource)
                || command.sourceReference == null
                || command.sourceReference.width != trackerWidth || command.sourceReference.height != trackerHeight) return false;
        java.util.Set<TrackState> matched=new java.util.HashSet<>();
        for (TrackState incoming : command.tracks) {
            TrackState live = continuousTrack(incoming);
            boolean retainWeak=live!=null&&live.localPositionAvailable&&live.lastReliableSourceNanos>0&&lastProcessedSource!=null
                    &&lastProcessedSource.sourceTimestampNanos-live.lastReliableSourceNanos<=2_000_000_000L;
            if (live == null || !sameOwner(live.identityOwner,incoming.identityOwner) || !live.localPixelValidated&&!retainWeak
                    || !matched.add(live)) return false;
        }
        for (TrackState incoming : command.tracks) {
            TrackState live=continuousTrack(incoming);
            if (command.sourceStamp.sourceTimestampNanos <= live.anchorSourceTimestampNanos) continue;
            RobustAffineTransform.Result measuredMotion = live.motionHistory.at(command.sourceStamp);
            RobustAffineTransform.Result inverse = measuredMotion.inverse();
            if (!inverse.valid) continue; // No proven path to this old source: retain current tracking.
            List<PointF> measured = initialTrackerPoints(incoming.basePlate,previousPreviewWidth,previousPreviewHeight,trackerWidth,trackerHeight);
            // Compare in the captured image, before applying camera/object motion. A small
            // partial MT box must not collapse a healthy full-plate pixel track.
            if(!overlapsMeasuredTrack(live,measured,measuredMotion))continue;
            if(clippedVehicleMeasurement(live,measured,measuredMotion)) {
                // The detector measured the visible fragment, not a smaller physical car.
                // Retain the pixel-proven full coordinate system and its attached plate.
                android.util.Log.d("ALPR_PREVIEW_TRACK","measurement=partial_vehicle_retained track="+live.basePlate.trackId);
                continue;
            }
            List<SparsePyramidalFlow.Point> corrected = new ArrayList<>();
            for (PointF point : measured) corrected.add(inverse.apply(new SparsePyramidalFlow.Point(point.x,point.y)));
            if (!RobustAffineTransform.reasonableQuad(live.affineTransform,corrected,trackerWidth,trackerHeight,
                    partialVehicle&&live.foregroundOnly)) continue;
            com.example.alpr_v1.domain.NormalizedQuad reference=normalizedReference(live.referenceAnchorPoints,trackerWidth,trackerHeight);
            com.example.alpr_v1.domain.NormalizedQuad proposed=normalizedReference(corrected,trackerWidth,trackerHeight);
            if(reference!=null&&proposed!=null&&DynamicPlateGeometryGate.coverage(reference,proposed)>=.9999f
                    &&DynamicPlateGeometryGate.area(proposed)>DynamicPlateGeometryGate.area(reference))
                live.referenceAnchorPoints=new ArrayList<>(corrected);
            live.displayAnchorPoints = corrected;
            live.basePlate = incoming.basePlate;
            live.anchorSourceTimestampNanos = command.sourceStamp.sourceTimestampNanos;
            live.framesSinceMtAnchor = 0;
            float[] appearance=localAppearanceDescriptor(command.sourceReference.gray,trackerWidth,trackerHeight,
                    firstFourAndroidPoints(transformedPoints(live.flowAnchorPoints,measuredMotion)),partialVehicle&&live.foregroundOnly);
            // A detector's source may be several seconds old, or captured between
            // processed KLT frames. Refresh only if it explains the latest actual
            // pixels at least as well as the existing validated reference.
            float[] currentAppearance=localAppearanceDescriptor(previousGray,trackerWidth,trackerHeight,
                    firstFourAndroidPoints(live.currentFlowPoints),partialVehicle&&live.foregroundOnly);
            float proposedAgreement=appearance!=null&&currentAppearance!=null
                    ?localAppearanceSimilarity(appearance,currentAppearance):-1f;
            float previousAgreement=live.anchorAppearance!=null&&currentAppearance!=null
                    ?localAppearanceSimilarity(live.anchorAppearance,currentAppearance):-1f;
            if(proposedAgreement>=.30f&&proposedAgreement>=previousAgreement)live.anchorAppearance=appearance;
            else android.util.Log.d("ALPR_PREVIEW_TRACK","measurement=appearance_reference_retained proposed="
                    +proposedAgreement+" previous="+previousAgreement);
        }
        android.util.Log.d("ALPR_PREVIEW_TRACK","measurement=continuous tracks="+tracks.size());
        return true;
    }

    private static boolean sameMeasurement(Control a, Control b) {
        if (a == null || a.sourceStamp == null || b.sourceStamp == null
                || !LocalMotionHistory.sameContext(a.sourceStamp,b.sourceStamp)
                || a.sourceStamp.sourceSequence != b.sourceStamp.sourceSequence
                || a.sourceStamp.sourceTimestampNanos != b.sourceStamp.sourceTimestampNanos
                || a.width != b.width || a.height != b.height || a.tracks.size() != b.tracks.size()) return false;
        for (TrackState x:a.tracks) {
            boolean found=false;
            for(TrackState y:b.tracks)if(x.basePlate.trackId==y.basePlate.trackId&&sameOwner(x.identityOwner,y.identityOwner))found=true;
            if(!found)return false;
        }
        return true;
    }
    private void retainSelectedTracks() {
        java.util.Set<Long> retained=retainedTrackIds;
        if(retained==null)return;
        List<TrackState> kept=new ArrayList<>();
        for(TrackState track:tracks)if(retained.contains(track.basePlate.trackId))kept.add(track);
        tracks=kept;
    }

    private boolean controlStillCurrent() {
        synchronized (controls) { return appliedControlRevision == controlRevision || compatibleControl(pendingControl); }
    }

    private boolean compatibleControl(Control command) {
        if(command==null||command.tracks==null||command.sourceStamp==null||command.width!=sourceWidth
                ||command.height!=sourceHeight||command.tracks.size()!=tracks.size()
                ||!LocalMotionHistory.sameContext(command.sourceStamp,lastProcessedSource))return false;
        java.util.Set<TrackState> matched=new java.util.HashSet<>();
        for(TrackState incoming:command.tracks) {
            TrackState live=continuousTrack(incoming);
            if(live==null||!matched.add(live))return false;
        }
        return true;
    }

    private List<TrackState> tracks =
            Collections.emptyList();


    private byte[] previousGray;
    private byte[] anchorGray;

    private int trackerWidth;
    private int trackerHeight;

    private int previousPreviewWidth;
    private int previousPreviewHeight;

    private int sourceWidth;
    private int sourceHeight;


    /**
     * Nowy wynik MT ponownie kotwiczy wszystkie
     * aktualnie wykryte tablice.
     */
    public boolean anchor(
            List<OverlayItem> items,
            int sourceWidth,
            int sourceHeight
    ) {
        return anchor(items,sourceWidth,sourceHeight,java.util.Collections.emptyMap());
    }

    public boolean anchor(List<OverlayItem> items,int sourceWidth,int sourceHeight,
            java.util.Map<Long,DynamicVehicleIdentity.Stamp> owners) {
        return anchor(items,sourceWidth,sourceHeight,owners,null,null);
    }

    /** Delayed measurements must describe their own source pixels, never the next live frame. */
    public boolean anchorLuma(List<OverlayItem> items,int sourceWidth,int sourceHeight,
            java.util.Map<Long,DynamicVehicleIdentity.Stamp> owners,byte[] gray,int width,int height) {
        return anchorLuma(items,sourceWidth,sourceHeight,owners,gray,width,height,null);
    }
    public boolean anchorLuma(List<OverlayItem> items,int sourceWidth,int sourceHeight,
            java.util.Map<Long,DynamicVehicleIdentity.Stamp> owners,byte[] gray,int width,int height,
            com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp) {
        if(gray==null||width<=0||height<=0||(long)width*height>262144L||gray.length<(long)width*height)return false;
        return anchor(items,sourceWidth,sourceHeight,owners,
                new GrayFrame(width,height,Arrays.copyOf(gray,width*height)),sourceStamp);
    }

    private boolean anchor(List<OverlayItem> items,int sourceWidth,int sourceHeight,
            java.util.Map<Long,DynamicVehicleIdentity.Stamp> owners,GrayFrame sourceReference,
            com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp) {

        if (items == null
                || sourceWidth <= 0
                || sourceHeight <= 0) {

            return false;
        }


        List<TrackState> newTracks =
                new ArrayList<>();


        for (OverlayItem item :
                items) {

            if (item.kind
                    != OverlayItem.Kind.PLATE) {

                continue;
            }
            if(retainedTrackIds!=null&&!retainedTrackIds.contains(item.trackId))continue;


            /*
             * Do ponownego kotwiczenia używamy
             * rzeczywistej obserwacji MT, a nie
             * krótkiej predykcji starego trackera.
             */
            if (item.carriedPrediction) {

                continue;
            }


            if (item.normalizedKeypoints.size()
                    < 4) {

                continue;
            }


            TrackState state=new TrackState(item);
            state.foregroundOnly=sourceStamp!=null;
            state.anchorSourceTimestampNanos=sourceStamp==null?0L:sourceStamp.sourceTimestampNanos;
            state.identityOwner=owners.get(item.trackId);
            newTracks.add(state);
        }


        /*
         * Pojedynczy miss MT nie kasuje od razu
         * istniejącego trackera.
         */
        if (newTracks.isEmpty()) {

            return false;
        }


        synchronized (controls) {
            Control next = new Control(newTracks, sourceWidth, sourceHeight, controlRevision+1,sourceReference,sourceStamp);
            if (sameMeasurement(lastQueuedMeasurement,next)) return false;
            if(lastQueuedMeasurement!=null&&sourceStamp!=null&&LocalMotionHistory.sameContext(lastQueuedMeasurement.sourceStamp,sourceStamp)
                    &&sourceStamp.sourceTimestampNanos<lastQueuedMeasurement.sourceStamp.sourceTimestampNanos)return false;
            pendingControl = next;
            lastQueuedMeasurement = next;
            controlRevision++;
            publishedSourceWidth = sourceWidth; publishedSourceHeight = sourceHeight;
        }

        return true;
    }


    /**
     * Aktualizacja wszystkich aktywnych tablic
     * na podstawie jednej klatki PreviewView.
     */
    public synchronized List<OverlayItem> update(
            Bitmap preview
    ) {
        PreviewTrackingFrame frame = updateTracking(preview);
        return frame == null ? null : frame.overlayItems;
    }


    /**
     * Techniczna aktualizacja trackera. Oprócz geometrii overlay zwraca jakość,
     * błędy i wiek kotwicy potrzebne schedulerowi MT.
     */
    public synchronized PreviewTrackingFrame updateTracking(Bitmap preview) {
        applyPendingControl();
        if(observationMask!=null){previousGray=null;anchorGray=null;observationMask=null;}
        PreviewTrackingFrame result = updateTrackingImage(preview);
        return controlStillCurrent() ? result : null;
    }

    private PreviewTrackingFrame updateTrackingImage(Bitmap preview) {

        if (preview == null
                || preview.isRecycled()
                || tracks.isEmpty()) {

            return null;
        }


        GrayFrame current =
                grayFrame(
                        preview
                );


        if (current == null) {

            return null;
        }

        return updateGrayFrame(
                current,
                preview.getWidth(),
                preview.getHeight()
        );
    }


    private PreviewTrackingFrame updateGrayFrame(
            GrayFrame current,
            int previewWidth,
            int previewHeight
    ) {
        GrayFrame measuredSource=pendingSourceReference;
        pendingSourceReference=null;
        pendingSourceStamp=null;
        if(measuredSource!=null) {
            if(measuredSource.width!=current.width||measuredSource.height!=current.height) {
                // A camera/observation-domain change cannot rebind old geometry to new pixels.
                resetImageState();return null;
            }
            updateGrayFrame(measuredSource,previewWidth,previewHeight);
        }

        /*
         * Pierwsza klatka po nowym wyniku MT
         * tworzy obraz odniesienia.
         */
        if (previousGray == null
                || trackerWidth != current.width
                || trackerHeight != current.height
                || previousPreviewWidth
                != previewWidth
                || previousPreviewHeight
                != previewHeight) {

            trackerWidth =
                    current.width;

            trackerHeight =
                    current.height;

            previousPreviewWidth =
                    previewWidth;

            previousPreviewHeight =
                    previewHeight;


            for (TrackState track :
                    tracks) {

                List<PointF> initialPoints =
                        initialTrackerPoints(
                                track.basePlate,
                                previewWidth,
                                previewHeight,
                                trackerWidth,
                                trackerHeight
                        );

                track.anchorPoints =
                        initialPoints;

                track.trackedPoints =
                        initialPoints;

                track.flowAnchorPoints =
                        initialFlowPoints(initialPoints,track.foregroundOnly);

                track.currentFlowPoints =
                        track.flowAnchorPoints;
                track.referenceAnchorPoints=new ArrayList<>(track.flowAnchorPoints.subList(0,4));

                track.affineTransform =
                        RobustAffineTransform.Result.identity();

                track.kalman.reset();

                track.lastTrackedOverlay = null;

                track.trackerInliers = 0;

                track.consecutiveFailures =
                        0;
                track.ageFrames = 0;
                track.framesSinceMtAnchor = 0;
                track.trackingQuality = 1f;
                track.supportRatio = 1f;
                track.meanMatchError = 0f;
                track.anchorAppearance = localAppearanceDescriptor(
                        current.gray,
                        current.width,
                        current.height,
                        initialPoints,partialVehicle&&track.foregroundOnly
                );
                track.localAppearanceSimilarity = 0f;
                track.localAppearanceValidated = false;
                track.motionHistory.record(processingSourceStamp,RobustAffineTransform.Result.identity());
            }


            previousGray =
                    current.gray;

            anchorGray =
                    current.gray;
            for(TrackState track:tracks)track.referenceGray=anchorGray;


            return null;
        }


        List<TrackState> survivingTracks =
                new ArrayList<>();

        List<OverlayItem> visible =
                new ArrayList<>();

        List<TrackedPlate> technicalResults =
                new ArrayList<>();

        long updatedAtNanos = SystemClock.elapsedRealtimeNanos();


        for (TrackState track :
                tracks) {

            if (track.trackedPoints.size()
                    < 4) {

                continue;
            }

            AffineTrackUpdate affineUpdate = updateAffineTrack(
                    track,
                    previousGray,
                    track.referenceGray==null?anchorGray:track.referenceGray,
                    current.gray,
                    trackerWidth,
                    trackerHeight
            );

            if (affineUpdate.valid) {
                track.consecutiveFailures = 0;
                track.ageFrames++;
                track.framesSinceMtAnchor++;
                track.affineTransform = affineUpdate.transform;
                track.currentFlowPoints = transformedPoints(
                        track.flowAnchorPoints,
                        affineUpdate.transform
                );
                track.trackedPoints = firstFourAndroidPoints(track.currentFlowPoints);
                track.supportRatio = affineUpdate.supportRatio;
                track.meanMatchError = affineUpdate.meanError;
                track.trackerInliers = affineUpdate.inliers;
                track.trackingQuality = affineTrackingQuality(
                        affineUpdate,
                        track.framesSinceMtAnchor
                );

                OverlayItem affinePlate = buildAffineTrackedPlate(
                        track,
                        previewWidth,
                        previewHeight
                );
                if (affinePlate != null) {
                    track.lastTrackedOverlay = affinePlate;
                    visible.add(affinePlate);
                    technicalResults.add(
                            technicalResult(
                                    track, affinePlate, current.gray,
                                    trackerWidth, trackerHeight, updatedAtNanos
                            )
                    );
                    survivingTracks.add(track);
                    android.util.Log.d(
                            "ALPR_PREVIEW_TRACK",
                            "track=" + track.basePlate.trackId
                                    + " mode=KLT_AFFINE"
                                    + " inliers=" + affineUpdate.inliers
                                    + " support=" + affineUpdate.supportRatio
                                    + " quality=" + track.trackingQuality
                    );
                    continue;
                }
            }


            TrackUpdate incrementalUpdate =
                    track.foregroundOnly?TrackUpdate.invalid():updateTrack(
                            track.trackedPoints,
                            previousGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight,
                            SEARCH_RADIUS
                    );

            TrackUpdate anchorUpdate =
                    track.foregroundOnly?TrackUpdate.invalid():updateTrack(
                            track.anchorPoints,
                            anchorGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight,
                            ANCHOR_SEARCH_RADIUS
                    );

            int currentAbsoluteDx = Math.round(
                    track.trackedPoints.get(0).x
                            - track.anchorPoints.get(0).x
            );
            int currentAbsoluteDy = Math.round(
                    track.trackedPoints.get(0).y
                            - track.anchorPoints.get(0).y
            );

            PreviewTrackerDriftGuard.Decision update =
                    PreviewTrackerDriftGuard.reconcile(
                            currentAbsoluteDx,
                            currentAbsoluteDy,
                            driftMotion(incrementalUpdate),
                            driftMotion(anchorUpdate)
                    );


            if (!update.valid) {

                track.consecutiveFailures++;
                track.ageFrames++;
                track.framesSinceMtAnchor++;
                track.trackingQuality = Math.max(
                        0f,
                        track.trackingQuality * 0.62f
                );
                track.supportRatio = Math.max(
                        incrementalUpdate.support,
                        anchorUpdate.support
                ) / 4f;
                track.meanMatchError = Math.min(
                        finiteError(incrementalUpdate.meanError),
                        finiteError(anchorUpdate.meanError)
                );


                /*
                 * Krótki pojedynczy miss nie powoduje
                 * migotania ramki.
                 */
                if (track.consecutiveFailures
                        < MAX_CONSECUTIVE_FAILURES) {

                    OverlayItem unchanged =
                            buildKalmanPrediction(
                                    track,
                                    previewWidth,
                                    previewHeight
                            );

                    if (unchanged == null) {
                        unchanged = buildTrackedPlate(
                                track,
                                previewWidth,
                                previewHeight
                        );
                    }


                    if (unchanged != null) {

                        visible.add(
                                unchanged
                        );

                        technicalResults.add(
                                technicalResult(
                                        track, unchanged, current.gray,
                                        trackerWidth, trackerHeight, updatedAtNanos
                                )
                        );

                        survivingTracks.add(
                                track
                        );
                    }
                }


                continue;
            }


            track.consecutiveFailures =
                    0;
            track.ageFrames++;
            track.framesSinceMtAnchor++;
            track.supportRatio = update.support / 4f;
            track.trackerInliers = update.support;
            track.meanMatchError = update.anchored
                    ? anchorUpdate.meanError
                    : incrementalUpdate.meanError;
            track.trackingQuality = trackingQuality(
                    track.supportRatio,
                    track.meanMatchError,
                    update.anchored,
                    track.framesSinceMtAnchor
            );


            List<PointF> moved =
                    new ArrayList<>(
                            track.trackedPoints.size()
                    );


            for (PointF point :
                    track.anchorPoints) {

                moved.add(
                        new PointF(
                                point.x + update.absoluteDx,
                                point.y + update.absoluteDy
                        )
                );
            }


            track.trackedPoints =
                    Collections.unmodifiableList(
                            moved
                    );


            OverlayItem trackedPlate =
                    buildTrackedPlate(
                            track,
                            previewWidth,
                            previewHeight
                    );


            if (trackedPlate != null) {

                track.lastTrackedOverlay = trackedPlate;

                visible.add(
                        trackedPlate
                );

                technicalResults.add(
                        technicalResult(
                                track, trackedPlate, current.gray,
                                trackerWidth, trackerHeight, updatedAtNanos
                        )
                );

                survivingTracks.add(
                        track
                );
            }


            android.util.Log.d(
                    "ALPR_PREVIEW_TRACK",
                    "track="
                            + track.basePlate.trackId
                            + " absoluteDx="
                            + update.absoluteDx
                            + " absoluteDy="
                            + update.absoluteDy
                            + " anchored="
                            + update.anchored
                            + " support="
                            + update.support
            );
        }


        tracks =
                survivingTracks;


        previousGray =
                current.gray;


        /*
         * Ważne rozróżnienie:
         *
         * null:
         * tracker nie ma jeszcze nowej informacji,
         * więc UI powinno pozostawić bieżący overlay.
         *
         * emptyList:
         * tracker miał aktywne tablice, ale właśnie
         * stracił wszystkie tracki. UI musi wtedy
         * natychmiast usunąć stare ramki.
         */
        if (visible.isEmpty()) {

            return new PreviewTrackingFrame(
                    Collections.emptyList(),
                    updatedAtNanos
            );
        }


        return new PreviewTrackingFrame(
                technicalResults,
                updatedAtNanos
        );
    }


    /**
     * Aktualizacja bez odczytu GPU/PreviewView. Wejściem jest mała płaszczyzna
     * luminancji skopiowana bezpośrednio z ImageAnalysis CameraX.
     */
    public synchronized PreviewTrackingFrame updateLuma(byte[] gray, int width, int height) {
        return updateLuma(gray,width,height,null);
    }
    public synchronized PreviewTrackingFrame updateLuma(byte[] gray,int width,int height,ObservationMask mask) {
        return updateLuma(gray,width,height,mask,null,null);
    }
    public synchronized PreviewTrackingFrame updateLuma(byte[] gray,int width,int height,ObservationMask mask,
            com.example.alpr_v1.continuity.SourceFrameStamp currentSource,PreviewLumaHistory history) {
        return updateLuma(gray,width,height,mask,currentSource,history,null);
    }
    public synchronized PreviewTrackingFrame updateLuma(byte[] gray,int width,int height,ObservationMask mask,
            com.example.alpr_v1.continuity.SourceFrameStamp currentSource,PreviewLumaHistory history,CameraMotionSample cameraMotion) {
        lastReplaySteps=0;
        if(mask!=null) mask.requireSize(width,height);
        applyPendingControl();
        if(!ObservationMask.same(observationMask,mask)){
            previousGray=null;anchorGray=null;
            if(replayOrigin!=null){resetImageState();return PreviewTrackingFrame.pending(PreviewTrackingFrame.State.UNAVAILABLE,currentSource);}
        }
        observationMask=mask;
        if(pendingSourceReference!=null&&pendingSourceStamp!=null&&history!=null&&currentSource!=null) {
            GrayFrame measured=pendingSourceReference;
            replayOrigin=pendingSourceStamp;replayCursor=pendingSourceStamp;replaySteps=0;
            processingSourceStamp=replayOrigin;
            pendingSourceReference=null;pendingSourceStamp=null;
            if(measured.width!=width||measured.height!=height
                    ||!LocalMotionHistory.sameContext(replayOrigin,currentSource)) {
                return unavailableAnchor(currentSource,"anchor_dimensions_or_context");
            }
            updateGrayFrame(measured,width,height);
            lastProcessedSource=replayCursor;
        }
        boolean finishingReplay=replayOrigin!=null;
        if(replayOrigin!=null) {
            if(history==null||!LocalMotionHistory.sameContext(replayOrigin,currentSource)) {
                return unavailableAnchor(currentSource,"replay_context");
            }
            if(!history.canFinish(replayOrigin,replayCursor,currentSource)) {
                if(replaySteps>=history.maximumReplaySteps()) {
                    return unavailableAnchor(currentSource,"replay_step_limit");
                }
                PreviewLumaHistory.Frame next=history.nextReplayFrame(replayOrigin,replayCursor,currentSource);
                if(next==null||next.width!=width||next.height!=height) {
                    return unavailableAnchor(currentSource,"replay_pixels_unavailable");
                }
                processingSourceStamp=next.stamp;
                updateGrayFrame(new GrayFrame(width,height,next.gray),width,height);
                replayCursor=next.stamp;lastProcessedSource=next.stamp;replaySteps++;lastReplaySteps=1;
                return PreviewTrackingFrame.pending(controlStillCurrent()?PreviewTrackingFrame.State.REPLAYING:
                        PreviewTrackingFrame.State.UNAVAILABLE,next.stamp);
            }
            replayOrigin=null;replayCursor=null;replaySteps=0;
        }
        // Unsupported local observation is N/A, never evidence of a LOST target.
        if(mask!=null) for(TrackState track:tracks) {
            List<PointF> points=previousGray==null?initialTrackerPoints(track.basePlate,width,height,width,height):track.trackedPoints;
            if(!supportedObservation(points,mask)) return null;
            if(previousGray!=null&&!supportedObservation(track.anchorPoints,mask)) return null;
        }
        processingSourceStamp=currentSource;
        boolean absenceEvaluated=!finishingReplay&&(currentSource==null||lastProcessedSource!=null
                &&LocalMotionHistory.sameContext(currentSource,lastProcessedSource)
                &&currentSource.sourceTimestampNanos>lastProcessedSource.sourceTimestampNanos
                &&currentSource.sourceTimestampNanos-lastProcessedSource.sourceTimestampNanos<=500_000_000L);
        processingCameraMotion=pendingSourceReference==null&&mask==null&&cameraMotion!=null
                &&cameraMotion.matches(lastProcessedSource,currentSource)
                ?cameraMotion.motion:null;
        PreviewTrackingFrame result;
        try {result=updateLumaImage(gray,width,height);}
        finally {processingCameraMotion=null;}
        lastProcessedSource=currentSource;
        if(!controlStillCurrent()||result==null)return currentSource==null?null:
                PreviewTrackingFrame.pending(PreviewTrackingFrame.State.UNAVAILABLE,currentSource);
        return result.withSourceStamp(currentSource,absenceEvaluated,ownerScopedTracking);
    }

    private PreviewTrackingFrame updateLumaImage(byte[] gray, int width, int height) {
        if (gray == null || width <= 0 || height <= 0
                || gray.length < width * height
                || tracks.isEmpty()) return null;
        return updateGrayFrame(
                new GrayFrame(
                        width,
                        height,
                        gray
                ),
                width,
                height
        );
    }

    private PreviewTrackingFrame unavailableAnchor(com.example.alpr_v1.continuity.SourceFrameStamp current,String reason) {
        android.util.Log.d("ALPR_PREVIEW_TRACK","state=UNAVAILABLE reason="+reason
                +" anchor="+(replayOrigin==null?"none":replayOrigin.sourceSequence+"/"+replayOrigin.sourceTimestampNanos
                +"/"+replayOrigin.sceneGeneration+"/"+replayOrigin.visualEpoch+"/"+replayOrigin.cameraTransformGeneration)
                +" current="+(current==null?"none":current.sourceSequence+"/"+current.sourceTimestampNanos
                +"/"+current.sceneGeneration+"/"+current.visualEpoch+"/"+current.cameraTransformGeneration));
        resetImageState();return PreviewTrackingFrame.pending(PreviewTrackingFrame.State.UNAVAILABLE,current);
    }


    private boolean supportedObservation(List<PointF> points,ObservationMask mask) {
        if(points==null||points.size()<4) return false;
        float l=Float.POSITIVE_INFINITY,t=l,r=Float.NEGATIVE_INFINITY,b=r;
        for(PointF p:points){
            if(!mask.supports(p.x,p.y,ANCHOR_SEARCH_RADIUS+PATCH_RADIUS+1)) return false;
            l=Math.min(l,p.x);t=Math.min(t,p.y);r=Math.max(r,p.x);b=Math.max(b,p.y);
        }
        return mask.rectangle((int)Math.floor(l),(int)Math.floor(t),(int)Math.ceil(r),(int)Math.ceil(b));
    }

    private TrackUpdate updateTrack(
            List<PointF> referencePoints,
            byte[] previous,
            byte[] current,
            int width,
            int height,
            int searchRadius
    ) {

        List<Match> matches =
                new ArrayList<>(
                        4
                );


        for (int index = 0;
             index < 4;
             index++) {

            matches.add(
                    matchPoint(
                            previous,
                            current,
                            width,
                            height,
                            referencePoints.get(index),
                            searchRadius
                    )
            );
        }


        List<Integer> dxValues =
                new ArrayList<>();

        List<Integer> dyValues =
                new ArrayList<>();


        for (Match match :
                matches) {

            if (!match.valid) {

                continue;
            }


            dxValues.add(
                    match.dx
            );

            dyValues.add(
                    match.dy
            );
        }


        if (dxValues.size() < 2) {

            return TrackUpdate.invalid();
        }


        int medianDx =
                median(
                        dxValues
                );

        int medianDy =
                median(
                        dyValues
                );


        int support =
                0;

        float errorSum =
                0f;


        for (Match match :
                matches) {

            if (!match.valid) {

                continue;
            }


            if (Math.abs(
                    match.dx - medianDx
            ) <= DISPLACEMENT_CONSISTENCY
                    && Math.abs(
                    match.dy - medianDy
            ) <= DISPLACEMENT_CONSISTENCY) {

                support++;
                errorSum += match.error;
            }
        }


        if (support < 2) {

            return TrackUpdate.invalid();
        }


        return new TrackUpdate(
                true,
                medianDx,
                medianDy,
                support,
                errorSum / Math.max(1, support)
        );
    }


    private AffineTrackUpdate updateAffineTrack(TrackState track,byte[] previous,byte[] anchor,
            byte[] current,int width,int height) {
        if(track.flowAnchorPoints.size()<4||track.currentFlowPoints.size()<4)return AffineTrackUpdate.invalid();
        if(track.foregroundOnly) {
            AffineTrackUpdate initialized=processingCameraMotion==null?AffineTrackUpdate.invalid()
                    :affineStep(track,previous,current,width,height,false,true);
            float initializedAppearance=affineAppearance(track,initialized,current,width,height);
            if(initialized.valid&&initializedAppearance>=.65f)return initialized;
            AffineTrackUpdate incremental=affineStep(track,previous,current,width,height,false);
            float incrementalAppearance=affineAppearance(track,incremental,current,width,height);
            if(incremental.valid&&incrementalAppearance>=.65f)return incremental;
            if(initialized.valid&&initializedAppearance>incrementalAppearance) {
                incremental=initialized;incrementalAppearance=initializedAppearance;
            }
            if(incremental.valid&&incrementalAppearance>=.65f)return incremental;
            AffineTrackUpdate measured=affineStep(track,anchor,current,width,height,true);
            float measuredAppearance=affineAppearance(track,measured,current,width,height);
            if(measured.valid&&measuredAppearance>=.30f&&measuredAppearance>=incrementalAppearance)return measured;
            return incremental.valid&&incrementalAppearance>=.30f?incremental:AffineTrackUpdate.invalid();
        }
        AffineTrackUpdate measured=affineStep(track,anchor,current,width,height,true);
        return measured.valid?measured:affineStep(track,previous,current,width,height,false);
    }

    private AffineTrackUpdate affineStep(TrackState track,byte[] reference,byte[] current,
            int width,int height,boolean anchored) {
        return affineStep(track,reference,current,width,height,anchored,false);
    }
    private AffineTrackUpdate affineStep(TrackState track,byte[] reference,byte[] current,
            int width,int height,boolean anchored,boolean initializeTranslation) {
        List<SparsePyramidalFlow.Point> points=anchored?track.flowAnchorPoints:track.currentFlowPoints;
        if(track.foregroundOnly)points=points.subList(4,points.size());
        boolean validateForwardBackward=track.foregroundOnly||track.ageFrames%3==0;
        SparsePyramidalFlow.Result flow=SparsePyramidalFlow.track(reference,current,width,height,points,
                validateForwardBackward,observationMask,initializeTranslation?processingCameraMotion:null);
        RobustAffineTransform.Result estimate=RobustAffineTransform.estimate(flow.matches);
        RobustAffineTransform.Result transform=anchored?estimate:estimate.valid
                ?estimate.compose(track.affineTransform):RobustAffineTransform.Result.invalid();
        if(!transform.valid||!RobustAffineTransform.reasonableQuad(transform,track.flowAnchorPoints,width,height,
                partialVehicle&&track.foregroundOnly))
            return AffineTrackUpdate.invalid();
        return new AffineTrackUpdate(true,transform,flow.supportRatio,flow.meanError,estimate.inlierCount,anchored);
    }

    private float affineAppearance(TrackState track,AffineTrackUpdate update,byte[] current,int width,int height) {
        if(!update.valid||track.anchorAppearance==null)return -1f;
        float[] candidate=localAppearanceDescriptor(current,width,height,
                firstFourAndroidPoints(transformedPoints(track.flowAnchorPoints,update.transform)),partialVehicle&&track.foregroundOnly);
        return candidate==null?-1f:localAppearanceSimilarity(track.anchorAppearance,candidate);
    }


    private static List<SparsePyramidalFlow.Point> initialFlowPoints(
            List<PointF> quad,boolean foregroundOnly
    ) {
        if (quad == null || quad.size() < 4) return Collections.emptyList();
        List<SparsePyramidalFlow.Point> points = new ArrayList<>(8);
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            PointF point = quad.get(index);
            points.add(new SparsePyramidalFlow.Point(point.x, point.y));
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        float[] xFractions = foregroundOnly?new float[]{.2f,.5f,.8f}:new float[]{.33f,.67f};
        float[] yFractions = foregroundOnly?new float[]{.2f,.5f,.8f}:new float[]{.33f,.67f};
        for (float yFraction : yFractions) {
            for (float xFraction : xFractions) {
                if(foregroundOnly) {
                    PointF a=quad.get(0),b=quad.get(1),c=quad.get(2),d=quad.get(3);
                    points.add(new SparsePyramidalFlow.Point(
                            (1-yFraction)*(a.x+(b.x-a.x)*xFraction)+yFraction*(d.x+(c.x-d.x)*xFraction),
                            (1-yFraction)*(a.y+(b.y-a.y)*xFraction)+yFraction*(d.y+(c.y-d.y)*xFraction)));
                } else points.add(new SparsePyramidalFlow.Point(left+(right-left)*xFraction,top+(bottom-top)*yFraction));
            }
        }
        return Collections.unmodifiableList(points);
    }


    private static List<SparsePyramidalFlow.Point> transformedPoints(
            List<SparsePyramidalFlow.Point> source,
            RobustAffineTransform.Result transform
    ) {
        List<SparsePyramidalFlow.Point> result = new ArrayList<>(source.size());
        for (SparsePyramidalFlow.Point point : source) result.add(transform.apply(point));
        return Collections.unmodifiableList(result);
    }


    private static List<PointF> firstFourAndroidPoints(
            List<SparsePyramidalFlow.Point> source
    ) {
        if (source == null || source.size() < 4) return Collections.emptyList();
        List<PointF> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = source.get(index);
            result.add(new PointF(point.x, point.y));
        }
        return Collections.unmodifiableList(result);
    }


    private OverlayItem buildAffineTrackedPlate(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {
        List<SparsePyramidalFlow.Point> displayPoints = track.displayAnchorPoints.isEmpty()
                ? track.currentFlowPoints : transformedPoints(track.displayAnchorPoints,track.affineTransform);
        if (displayPoints.size() < 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = displayPoints.get(index);
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        if (right - left < 2f || bottom - top < 2f) return null;
        if(track.foregroundOnly) {
            // Ordinary DYN publishes geometry from these actual source pixels.
            // Its presentation controller smooths the owned vehicle/plate pair;
            // a second Kalman filter here would attach old positions to a new stamp.
            List<PointF> normalized=new ArrayList<>(4);
            for(int i=0;i<4;i++) {
                SparsePyramidalFlow.Point point=displayPoints.get(i);
                SparsePyramidalFlow.Point value=trackerToNormalizedPoint(point.x,point.y,previewWidth,previewHeight,
                        partialVehicle&&track.foregroundOnly);
                normalized.add(new PointF(value.x,value.y));
            }
            OverlayItem overlay=overlayFromPoints(track.basePlate,normalized,false);
            if(overlay!=null&&partialVehicle&&track.foregroundOnly&&(overlay.normalizedBounds.left<0
                    ||overlay.normalizedBounds.top<0||overlay.normalizedBounds.right>1||overlay.normalizedBounds.bottom>1)) {
                RectF b=overlay.normalizedBounds;
                // Clip the visible rectangle, preserving the full quad for the attached plate transform.
                overlay=new OverlayItem(overlay.kind,new RectF(clamp(b.left,0,1),clamp(b.top,0,1),
                        clamp(b.right,0,1),clamp(b.bottom,0,1)),normalized,overlay.label,overlay.trackId,false);
            }
            return overlay;
        }
        PlateBoxKalman.Box filtered = track.kalman.update(new PlateBoxKalman.Box(
                (left + right) * 0.5f,
                (top + bottom) * 0.5f,
                right - left,
                bottom - top
        ));
        List<PointF> normalizedPoints = normalizedFilteredQuad(
                displayPoints,
                left,
                top,
                right,
                bottom,
                filtered,
                previewWidth,
                previewHeight
        );
        return overlayFromPoints(track.basePlate, normalizedPoints, false);
    }


    private OverlayItem buildKalmanPrediction(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {
        if (track.lastTrackedOverlay == null) return null;
        PlateBoxKalman.Box predicted = track.kalman.predict();
        if (predicted == null) return null;
        SparsePyramidalFlow.Point topLeft = trackerToNormalizedPoint(
                predicted.centerX - predicted.width * 0.5f,
                predicted.centerY - predicted.height * 0.5f,
                previewWidth,
                previewHeight
        );
        SparsePyramidalFlow.Point bottomRight = trackerToNormalizedPoint(
                predicted.centerX + predicted.width * 0.5f,
                predicted.centerY + predicted.height * 0.5f,
                previewWidth,
                previewHeight
        );
        RectF predictedBounds = new RectF(
                clamp(topLeft.x, 0f, 1f),
                clamp(topLeft.y, 0f, 1f),
                clamp(bottomRight.x, 0f, 1f),
                clamp(bottomRight.y, 0f, 1f)
        );
        RectF previousBounds = track.lastTrackedOverlay.normalizedBounds;
        if (predictedBounds.width() <= 0f || predictedBounds.height() <= 0f
                || previousBounds.width() <= 0f || previousBounds.height() <= 0f) {
            return null;
        }
        List<PointF> points = new ArrayList<>();
        for (PointF point : track.lastTrackedOverlay.normalizedKeypoints) {
            float relativeX = (point.x - previousBounds.left) / previousBounds.width();
            float relativeY = (point.y - previousBounds.top) / previousBounds.height();
            points.add(new PointF(
                    predictedBounds.left + relativeX * predictedBounds.width(),
                    predictedBounds.top + relativeY * predictedBounds.height()
            ));
        }
        OverlayItem prediction = new OverlayItem(
                OverlayItem.Kind.PLATE,
                predictedBounds,
                points,
                track.lastTrackedOverlay.label,
                track.basePlate.trackId,
                true
        );
        track.lastTrackedOverlay = prediction;
        return prediction;
    }


    private List<PointF> normalizedFilteredQuad(
            List<SparsePyramidalFlow.Point> currentPoints,
            float rawLeft,
            float rawTop,
            float rawRight,
            float rawBottom,
            PlateBoxKalman.Box filtered,
            int previewWidth,
            int previewHeight
    ) {
        float filteredLeft = filtered.centerX - filtered.width * 0.5f;
        float filteredTop = filtered.centerY - filtered.height * 0.5f;
        List<PointF> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = currentPoints.get(index);
            float relativeX = (point.x - rawLeft) / Math.max(1f, rawRight - rawLeft);
            float relativeY = (point.y - rawTop) / Math.max(1f, rawBottom - rawTop);
            SparsePyramidalFlow.Point normalized = trackerToNormalizedPoint(
                    filteredLeft + relativeX * filtered.width,
                    filteredTop + relativeY * filtered.height,
                    previewWidth,
                    previewHeight
            );
            result.add(new PointF(
                    clamp(normalized.x, 0f, 1f),
                    clamp(normalized.y, 0f, 1f)
            ));
        }
        return Collections.unmodifiableList(result);
    }


    private SparsePyramidalFlow.Point trackerToNormalizedPoint(
            float x,
            float y,
            int previewWidth,
            int previewHeight
    ) {
        return trackerToNormalizedPoint(x,y,previewWidth,previewHeight,false);
    }

    private SparsePyramidalFlow.Point trackerToNormalizedPoint(float x,float y,int previewWidth,int previewHeight,
            boolean preserveOutside) {
        if(preserveOutside) {
            PointF p=OverlayViewportTransform.mapViewToNormalizedUnclipped(new PointF(x*previewWidth/trackerWidth,
                    y*previewHeight/trackerHeight),sourceWidth,sourceHeight,previewWidth,previewHeight);
            return new SparsePyramidalFlow.Point(p.x,p.y);
        }
        PointF normalized = trackerToNormalized(
                new PointF(x, y),
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight,
                trackerWidth,
                trackerHeight
        );
        return new SparsePyramidalFlow.Point(normalized.x, normalized.y);
    }


    private static OverlayItem overlayFromPoints(
            OverlayItem base,
            List<PointF> points,
            boolean carried
    ) {
        if (points == null || points.size() < 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF point : points) {
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        if (right - left < 0.002f || bottom - top < 0.002f) return null;
        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                new RectF(left, top, right, bottom),
                points,
                base.label,
                base.trackId,
                carried
        );
    }


    private static float affineTrackingQuality(
            AffineTrackUpdate update,
            int framesSinceMtAnchor
    ) {
        float errorScore = 1f - clamp(update.meanError / 32f, 0f, 1f);
        float inlierScore = Math.min(1f, update.inliers / 10f);
        float anchorScore = update.anchored ? 1f : 0.78f;
        float ageScore = Math.max(0.65f, 1f - framesSinceMtAnchor * 0.01f);
        return clamp(
                0.32f * update.supportRatio
                        + 0.27f * inlierScore
                        + 0.23f * errorScore
                        + 0.10f * anchorScore
                        + 0.08f * ageScore,
                0f,
                1f
        );
    }


    private TrackedPlate technicalResult(
            TrackState track,
            OverlayItem overlay,
            byte[] currentGray,
            int width,
            int height,
            long updatedAtNanos
    ) {
        float[] currentAppearance = localAppearanceDescriptor(
                currentGray,
                width,
                height,
                track.trackedPoints,partialVehicle&&track.foregroundOnly
        );
        track.localAppearanceValidated = track.anchorAppearance != null
                && currentAppearance != null;
        track.localAppearanceSimilarity = track.localAppearanceValidated
                ? localAppearanceSimilarity(track.anchorAppearance, currentAppearance)
                : 0f;
        TrackedPlate result=new TrackedPlate(
                overlay,
                track.trackingQuality,
                track.supportRatio,
                track.meanMatchError,
                track.trackerInliers,
                track.consecutiveFailures,
                track.ageFrames,
                track.framesSinceMtAnchor,
                track.localAppearanceSimilarity,
                track.localAppearanceValidated,
                currentAppearance,
                updatedAtNanos,track.identityOwner,track.anchorSourceTimestampNanos,
                normalizedReference(transformedPoints(track.referenceAnchorPoints,track.affineTransform),width,height)
        );
        // Neural corrections use the same local proof admitted by the identity
        // coordinator, rather than a stricter appearance-only OCR-lock threshold.
        track.localPixelValidated=VehicleTrackingCoordinator.validLocal(result);
        track.localPositionAvailable=VehicleTrackingCoordinator.usablePosition(result);
        if(track.localPixelValidated) {
            track.motionHistory.record(processingSourceStamp,track.affineTransform);
            if(processingSourceStamp!=null)track.lastReliableSourceNanos=processingSourceStamp.sourceTimestampNanos;
        } else if(!track.localPositionAvailable)track.motionHistory.clear();
        return result;
    }

    private static com.example.alpr_v1.domain.NormalizedQuad normalizedReference(
            List<SparsePyramidalFlow.Point> points,int width,int height) {
        if(points==null||points.size()<4||width<=0||height<=0)return null;
        float[] p=new float[8];
        for(int i=0;i<4;i++) {
            float x=points.get(i).x/width,y=points.get(i).y/height;
            if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)return null;
            p[2*i]=x;p[2*i+1]=y;
        }
        return new com.example.alpr_v1.domain.NormalizedQuad(p);
    }


    private float[] localAppearanceDescriptor(
            byte[] gray,
            int width,
            int height,
            List<PointF> points, boolean partial
    ) {
        if (gray == null || width <= 0 || height <= 0
                || gray.length < width * height || points == null || points.size() < 4) {
            return null;
        }
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF point : points) {
            if (point == null) continue;
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        int cropLeft = partial?Math.round(left):clamp(Math.round(left), 0, width - 1);
        int cropTop = partial?Math.round(top):clamp(Math.round(top), 0, height - 1);
        int cropRight = partial?Math.round(right):clamp(Math.round(right), cropLeft + 1, width);
        int cropBottom = partial?Math.round(bottom):clamp(Math.round(bottom), cropTop + 1, height);
        if(observationMask!=null&&!observationMask.rectangle(Math.max(0,cropLeft),Math.max(0,cropTop),
                Math.min(width,cropRight)-1,Math.min(height,cropBottom)-1))return null;
        int cropWidth = cropRight - cropLeft;
        int cropHeight = cropBottom - cropTop;
        if (cropWidth < 4 || cropHeight < 2) return null;

        float[] descriptor = new float[APPEARANCE_GRID_X * APPEARANCE_GRID_Y];
        float sum = 0f;
        int visible=0;
        int index = 0;
        for (int gy = 0; gy < APPEARANCE_GRID_Y; gy++) {
            int y = clamp(
                    cropTop + Math.round((gy + 0.5f) * cropHeight / APPEARANCE_GRID_Y),
                    cropTop,
                    cropBottom - 1
            );
            for (int gx = 0; gx < APPEARANCE_GRID_X; gx++) {
                int x = clamp(
                        cropLeft + Math.round((gx + 0.5f) * cropWidth / APPEARANCE_GRID_X),
                        cropLeft,
                        cropRight - 1
                );
                if(x<0||x>=width||y<0||y>=height) { descriptor[index++]=Float.NaN;continue; }
                float value = gray[y * width + x] & 0xff;
                descriptor[index++] = value;
                sum += value;
                visible++;
            }
        }

        if(visible<descriptor.length/2)return null;
        float mean = sum / visible;
        float energy = 0f;
        for (int i = 0; i < descriptor.length; i++) {
            if(!Float.isFinite(descriptor[i]))continue;
            descriptor[i] -= mean;
            energy += descriptor[i] * descriptor[i];
        }
        if (energy < 1e-3f) return null;
        float norm = (float) Math.sqrt(energy);
        for (int i = 0; i < descriptor.length; i++) descriptor[i] /= norm;
        return descriptor;
    }


    static float localAppearanceSimilarity(float[] anchor, float[] current) {
        if (anchor == null || current == null || anchor.length != current.length) return 0f;
        int common=0;float leftMean=0,rightMean=0;
        for(int i=0;i<anchor.length;i++)if(Float.isFinite(anchor[i])&&Float.isFinite(current[i])) {
            common++;leftMean+=anchor[i];rightMean+=current[i];
        }
        if(common<anchor.length/2)return 0f;
        if(common<anchor.length) {
            leftMean/=common;rightMean/=common;
            float dot=0,leftEnergy=0,rightEnergy=0;
            for(int i=0;i<anchor.length;i++)if(Float.isFinite(anchor[i])&&Float.isFinite(current[i])) {
                float a=anchor[i]-leftMean,b=current[i]-rightMean;
                dot+=a*b;leftEnergy+=a*a;rightEnergy+=b*b;
            }
            float energy=(float)Math.sqrt(leftEnergy*rightEnergy);
            return energy>1e-6f?clamp(dot/energy,0,1):0;
        }
        float dot = 0f;
        for (int i = 0; i < anchor.length; i++) dot += anchor[i] * current[i];
        return clamp(dot, 0f, 1f);
    }


    private static float trackingQuality(
            float supportRatio,
            float meanError,
            boolean anchored,
            int framesSinceMtAnchor
    ) {
        float errorScore = 1f - clamp(
                finiteError(meanError) / MAX_MEAN_ERROR,
                0f,
                1f
        );
        float anchorConsistency = anchored ? 1f : 0.70f;
        float ageScore = Math.max(0.65f, 1f - framesSinceMtAnchor * 0.01f);
        return clamp(
                0.40f * clamp(supportRatio, 0f, 1f)
                        + 0.35f * errorScore
                        + 0.15f * anchorConsistency
                        + 0.10f * ageScore,
                0f,
                1f
        );
    }


    private static float finiteError(float error) {
        return Float.isNaN(error) || Float.isInfinite(error)
                ? MAX_MEAN_ERROR
                : Math.max(0f, error);
    }

    private static PreviewTrackerDriftGuard.Motion driftMotion(
            TrackUpdate update
    ) {
        return new PreviewTrackerDriftGuard.Motion(
                update.valid,
                update.dx,
                update.dy,
                update.support
        );
    }


    private List<PointF> initialTrackerPoints(
            OverlayItem plate,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        List<PointF> result =
                new ArrayList<>(
                        4
                );


        for (int index = 0;
             index < 4;
             index++) {

            PointF normalized =
                    plate.normalizedKeypoints.get(
                            index
                    );


            result.add(
                    normalizedToTracker(
                            normalized,
                            sourceWidth,
                            sourceHeight,
                            previewWidth,
                            previewHeight,
                            trackerWidth,
                            trackerHeight
                    )
            );
        }


        return Collections.unmodifiableList(
                result
        );
    }


    private OverlayItem buildTrackedPlate(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {

        if (track.trackedPoints.size()
                < 4) {

            return null;
        }


        OverlayItem basePlate =
                track.basePlate;


        PointF trackedFirst =
                trackerToNormalized(
                        track.trackedPoints.get(0),
                        sourceWidth,
                        sourceHeight,
                        previewWidth,
                        previewHeight,
                        trackerWidth,
                        trackerHeight
                );


        PointF originalFirst =
                basePlate.normalizedKeypoints.get(
                        0
                );


        float dx =
                trackedFirst.x
                        - originalFirst.x;

        float dy =
                trackedFirst.y
                        - originalFirst.y;


        dx =
                clamp(
                        dx,
                        -basePlate.normalizedBounds.left,
                        1f - basePlate.normalizedBounds.right
                );


        dy =
                clamp(
                        dy,
                        -basePlate.normalizedBounds.top,
                        1f - basePlate.normalizedBounds.bottom
                );


        RectF originalBounds =
                basePlate.normalizedBounds;


        RectF movedBounds =
                new RectF(
                        originalBounds.left + dx,
                        originalBounds.top + dy,
                        originalBounds.right + dx,
                        originalBounds.bottom + dy
                );


        List<PointF> movedPoints =
                new ArrayList<>(
                        basePlate
                                .normalizedKeypoints
                                .size()
                );


        for (PointF point :
                basePlate.normalizedKeypoints) {

            movedPoints.add(
                    new PointF(
                            clamp(
                                    point.x + dx,
                                    0f,
                                    1f
                            ),
                            clamp(
                                    point.y + dy,
                                    0f,
                                    1f
                            )
                    )
            );
        }


        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                movedBounds,
                movedPoints,
                basePlate.label,
                basePlate.trackId,
                false
        );
    }


    public int sourceWidth() { return publishedSourceWidth; }
    public int sourceHeight() { return publishedSourceHeight; }

    public void reset() {
        synchronized (controls) {
            pendingControl = new Control(null, 0, 0, ++controlRevision);
            lastQueuedMeasurement = null;
            publishedSourceWidth = 0; publishedSourceHeight = 0;
        }
    }

    private void resetImageState() {
        ownerScopedTracking=false;
        lastProcessedSource=null;
        replayOrigin=null;replayCursor=null;replaySteps=0;

        tracks =
                Collections.emptyList();

        previousGray =
                null;

        anchorGray =
                null;

        trackerWidth =
                0;

        trackerHeight =
                0;

        previousPreviewWidth =
                0;

        previousPreviewHeight =
                0;

        sourceWidth =
                0;

        sourceHeight =
                0;
    }


    private Match matchPoint(
            byte[] previous,
            byte[] current,
            int width,
            int height,
            PointF point,
            int searchRadius
    ) {

        int sourceX =
                Math.round(
                        point.x
                );

        int sourceY =
                Math.round(
                        point.y
                );


        if (!patchInside(
                sourceX,
                sourceY,
                width,
                height
        )) {

            return Match.invalid();
        }


        int minimumX =
                Math.max(
                        PATCH_RADIUS,
                        sourceX - searchRadius
                );

        int maximumX =
                Math.min(
                        width - PATCH_RADIUS - 1,
                        sourceX + searchRadius
                );

        int minimumY =
                Math.max(
                        PATCH_RADIUS,
                        sourceY - searchRadius
                );

        int maximumY =
                Math.min(
                        height - PATCH_RADIUS - 1,
                        sourceY + searchRadius
                );


        float bestError =
                Float.MAX_VALUE;

        int bestX =
                sourceX;

        int bestY =
                sourceY;


        int previousCenter =
                previous[
                        sourceY * width
                                + sourceX
                        ] & 0xff;


        int sampleCount =
                (PATCH_RADIUS * 2 + 1)
                        * (PATCH_RADIUS * 2 + 1);


        /*
         * Wyszukiwanie grubo-dokładne ogranicza liczbę porównań patchy około
         * 5–7 razy, zachowując ten sam maksymalny promień ruchu. To pozwala
         * wykonywać tracker poza cyklem ciężkiej inferencji z częstotliwością
         * zbliżoną do PreviewView.
         */
        int coarseStep = searchRadius >= 8 ? 3 : 2;
        for (int candidateY = minimumY;
             candidateY <= maximumY;
             candidateY += coarseStep) {
            for (int candidateX = minimumX;
                 candidateX <= maximumX;
                 candidateX += coarseStep) {
                float meanError = patchMeanError(
                        previous,
                        current,
                        width,
                        sourceX,
                        sourceY,
                        candidateX,
                        candidateY,
                        previousCenter,
                        sampleCount
                );
                if (meanError < bestError) {
                    bestError = meanError;
                    bestX = candidateX;
                    bestY = candidateY;
                }
            }
        }

        int fineMinimumX = Math.max(minimumX, bestX - coarseStep);
        int fineMaximumX = Math.min(maximumX, bestX + coarseStep);
        int fineMinimumY = Math.max(minimumY, bestY - coarseStep);
        int fineMaximumY = Math.min(maximumY, bestY + coarseStep);
        for (int candidateY = fineMinimumY;
             candidateY <= fineMaximumY;
             candidateY++) {
            for (int candidateX = fineMinimumX;
                 candidateX <= fineMaximumX;
                 candidateX++) {
                float meanError = patchMeanError(
                        previous,
                        current,
                        width,
                        sourceX,
                        sourceY,
                        candidateX,
                        candidateY,
                        previousCenter,
                        sampleCount
                );
                if (meanError < bestError) {
                    bestError = meanError;
                    bestX = candidateX;
                    bestY = candidateY;
                }
            }
        }


        if (bestError
                > MAX_MEAN_ERROR) {

            return Match.invalid();
        }


        return new Match(
                true,
                bestX - sourceX,
                bestY - sourceY,
                bestError
        );
    }


    private float patchMeanError(
            byte[] previous,
            byte[] current,
            int width,
            int sourceX,
            int sourceY,
            int candidateX,
            int candidateY,
            int previousCenter,
            int sampleCount
    ) {
        if(observationMask!=null&&(!observationMask.supports(sourceX,sourceY,PATCH_RADIUS)
                ||!observationMask.supports(candidateX,candidateY,PATCH_RADIUS))) return Float.POSITIVE_INFINITY;
        int currentCenter = current[candidateY * width + candidateX] & 0xff;
        int brightnessShift = currentCenter - previousCenter;
        int error = 0;
        for (int patchY = -PATCH_RADIUS; patchY <= PATCH_RADIUS; patchY++) {
            int previousRow = (sourceY + patchY) * width;
            int currentRow = (candidateY + patchY) * width;
            for (int patchX = -PATCH_RADIUS; patchX <= PATCH_RADIUS; patchX++) {
                int oldValue = previous[previousRow + sourceX + patchX] & 0xff;
                int newValue = current[currentRow + candidateX + patchX] & 0xff;
                error += Math.abs(oldValue - (newValue - brightnessShift));
            }
        }
        return error / (float) sampleCount;
    }


    private static boolean patchInside(
            int x,
            int y,
            int width,
            int height
    ) {

        return x - PATCH_RADIUS >= 0
                && y - PATCH_RADIUS >= 0
                && x + PATCH_RADIUS < width
                && y + PATCH_RADIUS < height;
    }


    private static GrayFrame grayFrame(
            Bitmap source
    ) {

        int sourceWidth =
                source.getWidth();

        int sourceHeight =
                source.getHeight();


        if (sourceWidth <= 0
                || sourceHeight <= 0) {

            return null;
        }


        int width =
                Math.min(
                        MAX_TRACK_WIDTH,
                        sourceWidth
                );


        int height =
                Math.max(
                        1,
                        Math.round(
                                sourceHeight
                                        * (
                                        width
                                                / (float) sourceWidth
                                )
                        )
                );


        Bitmap scaled =
                sourceWidth == width
                        && sourceHeight == height

                        ? source

                        : Bitmap.createScaledBitmap(
                        source,
                        width,
                        height,
                        false
                );


        int[] pixels =
                new int[
                        width * height
                        ];


        scaled.getPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
        );


        byte[] gray =
                new byte[
                        pixels.length
                        ];


        for (int index = 0;
             index < pixels.length;
             index++) {

            int pixel =
                    pixels[index];

            int red =
                    (pixel >> 16)
                            & 0xff;

            int green =
                    (pixel >> 8)
                            & 0xff;

            int blue =
                    pixel
                            & 0xff;


            int luminance =
                    (
                            77 * red
                                    + 150 * green
                                    + 29 * blue
                    ) >> 8;


            gray[index] =
                    (byte) luminance;
        }


        if (scaled != source) {

            scaled.recycle();
        }


        return new GrayFrame(
                width,
                height,
                gray
        );
    }


    private static PointF normalizedToTracker(
            PointF point,
            int sourceWidth,
            int sourceHeight,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        PointF preview = OverlayViewportTransform.mapNormalizedToView(
                point,
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight
        );


        return new PointF(
                preview.x
                        * trackerWidth
                        / previewWidth,

                preview.y
                        * trackerHeight
                        / previewHeight
        );
    }


    private static PointF trackerToNormalized(
            PointF point,
            int sourceWidth,
            int sourceHeight,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        float previewX =
                point.x
                        * previewWidth
                        / trackerWidth;

        float previewY =
                point.y
                        * previewHeight
                        / trackerHeight;


        return OverlayViewportTransform.mapViewToNormalized(
                new PointF(previewX, previewY),
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight
        );
    }


    private static int median(
            List<Integer> values
    ) {

        int[] sorted =
                new int[
                        values.size()
                        ];


        for (int index = 0;
             index < values.size();
             index++) {

            sorted[index] =
                    values.get(index);
        }


        Arrays.sort(
                sorted
        );


        int middle =
                sorted.length / 2;


        if ((sorted.length & 1)
                == 1) {

            return sorted[middle];
        }


        return Math.round(
                (
                        sorted[middle - 1]
                                + sorted[middle]
                ) * 0.5f
        );
    }


    private static float clamp(
            float value,
            float minimum,
            float maximum
    ) {

        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }


    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }


    private static final class GrayFrame {

        final int width;
        final int height;
        final byte[] gray;


        GrayFrame(
                int width,
                int height,
                byte[] gray
        ) {

            this.width =
                    width;

            this.height =
                    height;

            this.gray =
                    gray;
        }
    }


    private static final class Match {

        final boolean valid;
        final int dx;
        final int dy;
        final float error;


        Match(
                boolean valid,
                int dx,
                int dy,
                float error
        ) {

            this.valid =
                    valid;

            this.dx =
                    dx;

            this.dy =
                    dy;

            this.error =
                    error;
        }


        static Match invalid() {

            return new Match(
                    false,
                    0,
                    0,
                    Float.POSITIVE_INFINITY
            );
        }
    }


    private static final class AffineTrackUpdate {
        final boolean valid;
        final RobustAffineTransform.Result transform;
        final float supportRatio;
        final float meanError;
        final int inliers;
        final boolean anchored;

        AffineTrackUpdate(
                boolean valid,
                RobustAffineTransform.Result transform,
                float supportRatio,
                float meanError,
                int inliers,
                boolean anchored
        ) {
            this.valid = valid;
            this.transform = transform;
            this.supportRatio = supportRatio;
            this.meanError = meanError;
            this.inliers = inliers;
            this.anchored = anchored;
        }

        static AffineTrackUpdate invalid() {
            return new AffineTrackUpdate(
                    false,
                    RobustAffineTransform.Result.invalid(),
                    0f,
                    Float.POSITIVE_INFINITY,
                    0,
                    false
            );
        }
    }


    private static final class TrackUpdate {

        final boolean valid;
        final int dx;
        final int dy;
        final int support;
        final float meanError;


        TrackUpdate(
                boolean valid,
                int dx,
                int dy,
                int support,
                float meanError
        ) {

            this.valid =
                    valid;

            this.dx =
                    dx;

            this.dy =
                    dy;

            this.support =
                    support;

            this.meanError =
                    meanError;
        }


        static TrackUpdate invalid() {

            return new TrackUpdate(
                    false,
                    0,
                    0,
                    0,
                    Float.POSITIVE_INFINITY
            );
        }
    }
}
