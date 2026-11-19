package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.NormalizedQuad;
import java.util.*;

/** One source-bound admission decision for ordinary DYN attachment, rendering and OCR. */
public final class DynamicPlateGeometryGate {
    public enum Reason { ACCEPTED, ACCEPTED_REFERENCE, INVALID_QUAD, OUTSIDE_VEHICLE, BBOX_MISMATCH,
        WAITING_FOR_REFERENCE, PARTIAL_REFERENCE, EXCESSIVE_EXPANSION, STALE_OR_UNOWNED }
    public static final class Decision {
        public final DynamicVehicleIdentity.Stamp owner;
        public final ContinuityStamp source;
        public final NormalizedQuad quad;
        public final NormalizedBounds vehicle;
        public final Reason reason;
        public final float bboxAlignment,referenceCoverage;
        public boolean accepted(){return reason==Reason.ACCEPTED||reason==Reason.ACCEPTED_REFERENCE;}
        private Decision(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source,NormalizedQuad quad,
                Reason reason,float alignment,float coverage,NormalizedBounds vehicle) {
            this.owner=owner;this.source=source;this.quad=quad;this.reason=reason;
            bboxAlignment=alignment;referenceCoverage=coverage;
            this.vehicle=vehicle;
        }
    }
    private static final long MAX_GAP=500_000_000L,MAX_AGE=10_000_000_000L;
    private static final class Sample {
        final ContinuityStamp source;final NormalizedQuad plate;final NormalizedBounds body;
        Sample(ContinuityStamp source,NormalizedQuad plate,NormalizedBounds body){this.source=source;this.plate=plate;this.body=body;}
    }
    private static final class State {
        final DynamicVehicleIdentity.Stamp owner;
        ContinuityStamp context;
        boolean referenceExpected;
        NormalizedQuad neuralReference;
        NormalizedBounds neuralReferenceVehicle;
        final NavigableMap<Long,Sample> samples=new TreeMap<>();
        State(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source){this.owner=owner;context=source;}
    }
    private final Map<Long,State> states=new LinkedHashMap<>();
    public void clear(){states.clear();}
    private static boolean ownerMatches(DynamicVehicleIdentity.Stamp a,DynamicVehicleIdentity.Stamp b) {
        return a!=null&&b!=null&&a.entityId==b.entityId&&a.epoch==b.epoch&&a.vehicleTrackId==b.vehicleTrackId
                &&a.analysisRunId==b.analysisRunId;
    }
    private static boolean contextMatches(ContinuityStamp a,ContinuityStamp b) {
        return a!=null&&b!=null&&a.sceneGeneration==b.sceneGeneration&&a.visualEpoch==b.visualEpoch
                &&a.cameraTransformGeneration==b.cameraTransformGeneration
                &&(a.sourceTimestampDomain==b.sourceTimestampDomain||a.sourceTimestampDomain.freshnessComparableWith(b.sourceTimestampDomain));
    }
    private static boolean olderContext(ContinuityStamp a,ContinuityStamp b) {
        return a.sceneGeneration<b.sceneGeneration||a.sceneGeneration==b.sceneGeneration
                &&(a.visualEpoch<b.visualEpoch||a.cameraTransformGeneration<b.cameraTransformGeneration);
    }
    private State state(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source,boolean observation) {
        if(owner==null||source==null||!source.sourceTimestampDomain.cameraDerived())return null;
        State state=states.get(owner.entityId);
        if(state==null||!ownerMatches(state.owner,owner)) {
            state=new State(owner,source);states.put(owner.entityId,state);
            while(states.size()>64)states.remove(states.keySet().iterator().next());
        } else if(!contextMatches(state.context,source)) {
            if(!observation||olderContext(source,state.context))return null;
            state.context=source;state.samples.clear();state.referenceExpected=false;
            state.neuralReference=null;state.neuralReferenceVehicle=null;
        }
        return state;
    }
    /** Only current validated camera pixels supply reference geometry, never predictions. */
    void observe(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source,NormalizedBounds body,NormalizedQuad fullPlate) {
        State state=state(owner,source,true);if(state==null)return;
        if(!state.samples.isEmpty()&&source.sourceTimestampNanos<state.samples.lastKey())return;
        if(fullPlate!=null&&valid(fullPlate))state.referenceExpected=true;
        state.samples.put(source.sourceTimestampNanos,new Sample(source,fullPlate,body));
        while(state.samples.size()>128||!state.samples.isEmpty()
                &&source.sourceTimestampNanos-state.samples.firstKey()>MAX_AGE)state.samples.pollFirstEntry();
    }
    private Sample[] bracket(State state,ContinuityStamp source,boolean plate) {
        Sample lo=null,hi=null;
        for(Sample sample:state.samples.values()) {
            if(!contextMatches(sample.source,source)||(plate?sample.plate==null:sample.body==null))continue;
            if(sample.source.sourceTimestampNanos<=source.sourceTimestampNanos)lo=sample;
            if(sample.source.sourceTimestampNanos>=source.sourceTimestampNanos){hi=sample;break;}
        }
        if(lo==null||hi==null||hi.source.sourceTimestampNanos-lo.source.sourceTimestampNanos>MAX_GAP)return null;
        return new Sample[]{lo,hi};
    }
    private static float fraction(Sample[] samples,long time) {
        long gap=samples[1].source.sourceTimestampNanos-samples[0].source.sourceTimestampNanos;
        return gap==0?0f:(time-samples[0].source.sourceTimestampNanos)/(float)gap;
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    Decision evaluate(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source,NormalizedBounds detectorBox,
            NormalizedQuad quad,NormalizedBounds measuredVehicle,boolean ownerAccepted) {
        State state=ownerAccepted?state(owner,source,false):null;
        if(state==null)return decision(owner,source,quad,Reason.STALE_OR_UNOWNED,0,0);
        if(!valid(quad)||detectorBox==null||!detectorBox.valid())return decision(owner,source,quad,Reason.INVALID_QUAD,0,0);
        float alignment=quad.bounds().iou(detectorBox);
        if(alignment<.75f)return decision(owner,source,quad,Reason.BBOX_MISMATCH,alignment,0);
        NormalizedBounds vehicle=measuredVehicle;
        if(vehicle==null) {
            Sample[] body=bracket(state,source,false);
            if(body!=null) {
                float t=fraction(body,source.sourceTimestampNanos);NormalizedBounds a=body[0].body,b=body[1].body;
                vehicle=new NormalizedBounds(mix(a.left,b.left,t),mix(a.top,b.top,t),mix(a.right,b.right,t),mix(a.bottom,b.bottom,t));
            }
        }
        if(vehicle==null||!vehicle.valid())return decision(owner,source,quad,Reason.WAITING_FOR_REFERENCE,alignment,0);
        for(int i=0;i<8;i+=2) {
            float[] p=quad.points();
            if(p[i]<vehicle.left-1e-5f||p[i]>vehicle.right+1e-5f||p[i+1]<vehicle.top-1e-5f||p[i+1]>vehicle.bottom+1e-5f)
                return decision(owner,source,quad,Reason.OUTSIDE_VEHICLE,alignment,0);
        }
        if(!state.referenceExpected)return new Decision(owner,source,quad,Reason.ACCEPTED,alignment,1,vehicle);
        NormalizedQuad full=referenceAt(state,source,measuredVehicle);
        if(full==null)return decision(owner,source,quad,Reason.WAITING_FOR_REFERENCE,alignment,0);
        float coverage=coverage(full,quad);
        if(coverage<.95f) {
            // Border/corner jitter may undershoot a known full plate. Reuse the
            // source-aligned full reference only when the current detector box
            // agrees and the full text span remains supported. OCR receives the
            // complete reference, never the shortened raw quadrilateral.
            float fullAlignment=full.bounds().iou(detectorBox);
            if(valid(full)&&inside(full,vehicle)&&fullAlignment>=.75f&&wholeTextSpan(full,quad))
                return new Decision(owner,source,full,Reason.ACCEPTED_REFERENCE,fullAlignment,1f,vehicle);
            return decision(owner,source,quad,Reason.PARTIAL_REFERENCE,alignment,coverage);
        }
        if(area(quad)>4f*area(full)+1e-7f)return decision(owner,source,quad,Reason.EXCESSIVE_EXPANSION,alignment,coverage);
        return new Decision(owner,source,quad,Reason.ACCEPTED,alignment,coverage,vehicle);
    }
    private static boolean inside(NormalizedQuad quad,NormalizedBounds body) {
        float[] p=quad.points();
        for(int i=0;i<8;i+=2)if(p[i]<body.left||p[i]>body.right||p[i+1]<body.top||p[i+1]>body.bottom)return false;
        return true;
    }
    private static float projectedCoverage(float[] reference,float[] candidate,float x,float y) {
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY,a=lo,b=hi;
        for(int i=0;i<8;i+=2) {
            float r=reference[i]*x+reference[i+1]*y,c=candidate[i]*x+candidate[i+1]*y;
            lo=Math.min(lo,r);hi=Math.max(hi,r);a=Math.min(a,c);b=Math.max(b,c);
        }
        return hi>lo?Math.max(0f,Math.min(hi,b)-Math.max(lo,a))/(hi-lo):0f;
    }
    private static boolean wholeTextSpan(NormalizedQuad reference,NormalizedQuad candidate) {
        float[] r=reference.points(),c=candidate.points();
        float ux=r[2]-r[0]+r[4]-r[6],uy=r[3]-r[1]+r[5]-r[7];
        float vx=r[6]-r[0]+r[4]-r[2],vy=r[7]-r[1]+r[5]-r[3];
        float width=(float)Math.hypot(ux,uy),height=(float)Math.hypot(vx,vy);
        if(width<=0||height<=0)return false;
        float horizontal=projectedCoverage(r,c,ux/width,uy/width),vertical=projectedCoverage(r,c,vx/height,vy/height);
        // The shorter span can differ at the border; the complete reference is
        // still used for both rows. A missing row/large crop cannot pass this.
        return width>=height?horizontal>=.95f&&vertical>=.75f:vertical>=.95f&&horizontal>=.75f;
    }
    private NormalizedQuad referenceAt(State state,ContinuityStamp source,NormalizedBounds measuredVehicle) {
        Sample[] reference=bracket(state,source,true);
        if(reference!=null) {
            float[] a=reference[0].plate.points(),b=reference[1].plate.points();float t=fraction(reference,source.sourceTimestampNanos);
            for(int i=0;i<8;i++)a[i]=mix(a[i],b[i],t);
            return new NormalizedQuad(a);
        }
        // A real MP/MT pair can supply source-coordinate geometry while KLT is bootstrapping.
        // This is a reference for crop admission, never a fresh local-presence observation.
        if(measuredVehicle==null||!measuredVehicle.valid()||state.neuralReference==null
                ||state.neuralReferenceVehicle==null||!state.neuralReferenceVehicle.valid())return null;
        float[] p=state.neuralReference.points();NormalizedBounds from=state.neuralReferenceVehicle;
        for(int i=0;i<8;i+=2) {
            p[i]=measuredVehicle.left+(p[i]-from.left)*measuredVehicle.width()/from.width();
            p[i+1]=measuredVehicle.top+(p[i+1]-from.top)*measuredVehicle.height()/from.height();
        }
        return new NormalizedQuad(p);
    }
    boolean commit(Decision decision) {
        if(decision==null||!decision.accepted())return false;
        State state=state(decision.owner,decision.source,false);if(state==null)return false;
        // Neural observations do not become fresh pixel samples or erase a full reference.
        NormalizedQuad previous=referenceAt(state,decision.source,decision.vehicle);
        if(state.neuralReference==null||previous!=null&&coverage(previous,decision.quad)>=.9999f
                &&area(decision.quad)>area(previous)) {
            state.neuralReference=decision.quad;state.neuralReferenceVehicle=decision.vehicle;
        }
        state.referenceExpected=true;
        return true;
    }
    private static Decision decision(DynamicVehicleIdentity.Stamp owner,ContinuityStamp source,NormalizedQuad quad,
            Reason reason,float alignment,float coverage){return new Decision(owner,source,quad,reason,alignment,coverage,null);}
    private static List<float[]> points(NormalizedQuad quad) {
        List<float[]> list=new ArrayList<>();if(quad!=null){float[] p=quad.points();for(int i=0;i<8;i+=2)list.add(new float[]{p[i],p[i+1]});}return list;
    }
    private static float polygonArea(List<float[]> points) {
        double sum=0;for(int i=0;i<points.size();i++){float[] a=points.get(i),b=points.get((i+1)%points.size());sum+=(double)a[0]*b[1]-(double)b[0]*a[1];}
        return (float)Math.abs(sum*.5);
    }
    public static float area(NormalizedQuad quad){return polygonArea(points(quad));}
    public static boolean valid(NormalizedQuad quad) {
        if(quad==null||area(quad)<1e-8f)return false;
        List<float[]> points=points(quad);float sign=0;
        for(int i=0;i<4;i++) {
            float cross=cross(points.get(i),points.get((i+1)%4),points.get((i+2)%4));
            if(!Float.isFinite(cross)||Math.abs(cross)<1e-10f)return false;
            if(sign==0)sign=Math.signum(cross);else if(Math.signum(cross)!=sign)return false;
        }
        return true;
    }
    private static float cross(float[] a,float[] b,float[] p){return (b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0]);}
    /** Convex polygon coverage, invariant to the winding of ordered MT corners. */
    public static float coverage(NormalizedQuad reference,NormalizedQuad candidate) {
        if(!valid(reference)||!valid(candidate))return 0;
        List<float[]> output=points(reference),clip=points(candidate);
        float sign=Math.signum(cross(clip.get(0),clip.get(1),clip.get(2)));
        for(int edge=0;edge<4;edge++) {
            List<float[]> input=output;output=new ArrayList<>();if(input.isEmpty())break;
            float[] a=clip.get(edge),b=clip.get((edge+1)%4),previous=input.get(input.size()-1);
            float previousDistance=sign*cross(a,b,previous);
            for(float[] point:input) {
                float distance=sign*cross(a,b,point);
                if((distance>=-1e-8f)!=(previousDistance>=-1e-8f)) {
                    float fraction=previousDistance/(previousDistance-distance);
                    output.add(new float[]{mix(previous[0],point[0],fraction),mix(previous[1],point[1],fraction)});
                }
                if(distance>=-1e-8f)output.add(point);
                previous=point;previousDistance=distance;
            }
        }
        return Math.min(1f,polygonArea(output)/area(reference));
    }
}
