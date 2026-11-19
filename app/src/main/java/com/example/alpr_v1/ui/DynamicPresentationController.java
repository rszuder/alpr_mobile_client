package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot;
import com.example.alpr_v1.tracking.DynamicVehicleIdentity;
import java.util.*;

/** Presentation of one atomic DYN snapshot; owns no detector, pixel tracker or OCR state. */
public final class DynamicPresentationController {
    private static final double CORRECTION_NANOS=120_000_000.0;
    private static final double LOCAL_CORRECTION_NANOS=60_000_000.0;
    private static final long MAX_PREDICTION=300_000_000L;
    private static final class MotionPose {
        long epoch,time,measurement;RectF previous;float vx,vy,vw,vh;
        RectF predict(RectF target,long ownerEpoch,long measured,long geometryAt,long now) {
            if(previous==null||epoch!=ownerEpoch){previous=new RectF(target);time=geometryAt;measurement=measured;vx=vy=vw=vh=0;}
            else if(geometryAt>time) {
                double seconds=(geometryAt-time)/1_000_000_000.0;
                if(measured==measurement&&seconds<=.6) {
                    vx=.7f*limit((target.centerX()-previous.centerX())/seconds,1)+.3f*vx;
                    vy=.7f*limit((target.centerY()-previous.centerY())/seconds,1)+.3f*vy;
                    vw=.7f*limit(Math.log(target.width()/previous.width())/seconds,.8)+.3f*vw;
                    vh=.7f*limit(Math.log(target.height()/previous.height())/seconds,.8)+.3f*vh;
                } else {vx*=.5f;vy*=.5f;vw=vh=0;}
                previous=new RectF(target);time=geometryAt;measurement=measured;
            }
            epoch=ownerEpoch;
            double ahead=Math.max(0L,Math.min(MAX_PREDICTION,now-geometryAt))/1_000_000_000.0;
            float x=target.centerX()+limit(vx*ahead,target.width()*.25),y=target.centerY()+limit(vy*ahead,target.height()*.25);
            float w=target.width()*Math.max(.85f,Math.min(1.15f,(float)Math.exp(vw*ahead)));
            float h=target.height()*Math.max(.85f,Math.min(1.15f,(float)Math.exp(vh*ahead)));
            return new RectF(x-w/2,y-h/2,x+w/2,y+h/2);
        }
        private static float limit(double value,double maximum){return (float)Math.max(-maximum,Math.min(maximum,value));}
    }
    private static final class Pose {
        long epoch,measurement,geometry,time;
        double responseNanos=CORRECTION_NANOS;
        RectF displayed;
        float dx,dy,logWidth,logHeight;
        float angleOffset,displayedAngle;
        long angleMeasurement,angleTime,angleEpoch;
        float angle(float target,long epoch,long measurement,long now) {
            if(angleTime==0||angleEpoch!=epoch)angleOffset=0;
            else if(measurement>angleMeasurement) {
                angleOffset=displayedAngle-target;
                while(angleOffset>Math.PI)angleOffset-=2*Math.PI;
                while(angleOffset<-Math.PI)angleOffset+=2*Math.PI;
            }
            angleOffset*=(float)Math.exp(-Math.max(0,now-angleTime)/CORRECTION_NANOS);
            displayedAngle=target+angleOffset;angleMeasurement=measurement;angleTime=now;angleEpoch=epoch;
            return angleOffset;
        }
        RectF update(RectF target,long nextEpoch,long nextMeasurement,long now) {
            return update(target,nextEpoch,nextMeasurement,nextMeasurement,now);
        }
        RectF update(RectF target,long nextEpoch,long nextMeasurement,long nextGeometry,long now) {
            if(displayed==null||epoch!=nextEpoch){displayed=new RectF(target);dx=dy=logWidth=logHeight=0;}
            else if(nextMeasurement>measurement||nextGeometry>geometry) {
                responseNanos=nextMeasurement>measurement?CORRECTION_NANOS:LOCAL_CORRECTION_NANOS;
                dx=displayed.centerX()-target.centerX();dy=displayed.centerY()-target.centerY();
                logWidth=(float)Math.log(displayed.width()/target.width());
                logHeight=(float)Math.log(displayed.height()/target.height());
            }
            float decay=(float)Math.exp(-Math.max(0,now-time)/responseNanos);
            dx*=decay;dy*=decay;logWidth*=decay;logHeight*=decay;
            float w=target.width()*(float)Math.exp(logWidth),h=target.height()*(float)Math.exp(logHeight);
            float x=target.centerX()+dx,y=target.centerY()+dy;
            displayed=new RectF(x-w/2,y-h/2,x+w/2,y+h/2);
            epoch=nextEpoch;measurement=nextMeasurement;geometry=nextGeometry;time=now;
            return new RectF(displayed);
        }
    }
    private static final class PlateAttachment {
        final long epoch,track,time,measurement;
        final float[] relative;
        PlateAttachment(long epoch,long track,long time,long measurement,float[] points,RectF body) {
            this.epoch=epoch;this.track=track;this.time=time;this.measurement=measurement;
            relative=new float[8];
            for(int i=0;i<4;i++){relative[2*i]=(points[2*i]-body.left)/body.width();relative[2*i+1]=(points[2*i+1]-body.top)/body.height();}
        }
        boolean inside(){for(float value:relative)if(!Float.isFinite(value)||value<0||value>1)return false;return true;}
        float[] points(RectF body) {
            float[] result=new float[8];
            for(int i=0;i<4;i++){result[2*i]=body.left+relative[2*i]*body.width();result[2*i+1]=body.top+relative[2*i+1]*body.height();}
            return result;
        }
    }
    private final Map<Long,Pose> bodyPoses=new HashMap<>(),platePoses=new HashMap<>();
    private final Map<Long,MotionPose> motionPoses=new HashMap<>();
    private final Map<Long,PlateAttachment> plateAttachments=new HashMap<>();
    private final Map<Long,Long> rejectedPlateMeasurements=new HashMap<>();
    private final Set<Long> refresh=new LinkedHashSet<>();
    private long run=-1,scene=-1,selected;
    public void reset(){run=scene=-1;selected=0;bodyPoses.clear();platePoses.clear();motionPoses.clear();plateAttachments.clear();rejectedPlateMeasurements.clear();refresh.clear();}
    private void context(DynamicVehicleDisplaySnapshot snapshot) {
        if(run!=snapshot.analysisRunId||scene!=snapshot.sceneGeneration){reset();run=snapshot.analysisRunId;scene=snapshot.sceneGeneration;}
    }
    public long selected(DynamicVehicleDisplaySnapshot snapshot,long explicit) {
        if(snapshot==null)return 0;context(snapshot);
        if(explicit>0) {
            selected=explicit;
            DynamicVehicleIdentity.Presentation focus=snapshot.identities.get(explicit);
            return focus!=null&&!focus.verifying&&(focus.visible||focus.recoveryUntilNanos>=snapshot.sourceTimeNanos)?explicit:0L;
        }
        DynamicVehicleIdentity.Presentation current=snapshot.identities.get(selected);
        if(current!=null&&!current.verifying&&(current.visible||current.recoveryUntilNanos>=snapshot.sourceTimeNanos))return selected;
        selected=0;long first=Long.MAX_VALUE;
        for(Map.Entry<Long,DynamicVehicleIdentity.Presentation> entry:snapshot.identities.entrySet()) {
            DynamicVehicleIdentity.Presentation value=entry.getValue();
            if(value.visible&&!value.verifying&&value.number>0&&value.number<first){first=value.number;selected=entry.getKey();}
        }
        return selected;
    }
    public List<OverlayItem> geometry(List<OverlayItem> incoming,DynamicVehicleDisplaySnapshot snapshot,long now) {
        if(snapshot==null){reset();return Collections.emptyList();}context(snapshot);
        Map<Long,OverlayItem> rawVehicles=new HashMap<>();
        if(incoming!=null)for(OverlayItem item:incoming)if(item.kind==OverlayItem.Kind.VEHICLE)rawVehicles.put(item.trackId,item);
        List<OverlayItem> result=new ArrayList<>();Set<Long> visible=new HashSet<>();
        for(Map.Entry<Long,DynamicVehicleIdentity.Presentation> entry:snapshot.identities.entrySet()) {
            long id=entry.getKey();DynamicVehicleIdentity.Presentation identity=entry.getValue();
            if(!identity.visible)continue;
            NormalizedBounds local=snapshot.localGeometry.get(id);OverlayItem raw=rawVehicles.get(id);
            NormalizedBounds tentative=snapshot.tentativeGeometry.get(id);
            if(tentative!=null)local=null;
            DynamicVehicleDisplaySnapshot.Vehicle vehicle=snapshot.vehicles.get(id);
            long measured=vehicle==null?0:vehicle.measuredAtNanos;
            long geometryAt=vehicle==null?measured:vehicle.geometryAtNanos;
            MotionPose cached=motionPoses.get(id);
            boolean cachedPixels=local==null&&tentative==null&&cached!=null&&cached.previous!=null
                    &&cached.epoch==identity.epoch&&cached.time==geometryAt&&geometryAt>0;
            RectF target=local==null?(tentative==null?(raw==null?null:new RectF(raw.normalizedBounds)):rect(tentative)):
                    new RectF(local.left,local.top,local.right,local.bottom);
            if(cachedPixels)target=new RectF(cached.previous);
            if(target==null||target.width()<=0||target.height()<=0)continue;
            long allowance=presentationAllowance(snapshot,geometryAt);
            if(geometryAt>0&&(now<geometryAt||now-geometryAt>allowance))continue;
            if(local==null&&tentative==null&&!cachedPixels&&vehicle!=null&&(measured<=0||snapshot.sourceTimeNanos<measured
                    ||snapshot.sourceTimeNanos-measured>DynamicVehicleIdentity.COAST_NANOS))continue;
            RectF predicted=motionPoses.computeIfAbsent(id,ignored->new MotionPose()).predict(target,identity.epoch,measured,geometryAt,now);
            RectF displayed=bodyPoses.computeIfAbsent(id,ignored->new Pose()).update(predicted,identity.epoch,measured,geometryAt,now);
            result.add(new OverlayItem(OverlayItem.Kind.VEHICLE,displayed,corners(displayed),"",id,local==null));visible.add(id);
            if(identity.verifying||vehicle==null)continue;
            long plateMeasured=vehicle.plateMeasuredAtNanos;
            float[] points;
            boolean attached=vehicle.plate==null;
            if(attached) {
                PlateAttachment previous=plateAttachments.get(id);
                if(previous==null||previous.epoch!=identity.epoch||previous.track!=vehicle.plateTrackId
                        ||now<previous.time||now-previous.time>presentationAllowance(snapshot,previous.time))continue;
                // Bridge an unavailable publication using the last actual attachment,
                // in the same bounded display window as its still-visible body.
                // Never refresh the cache timestamp from this inferred geometry.
                points=previous.points(target);plateMeasured=previous.measurement;
            } else points=vehicle.plate.points();
            RectF plate=new RectF(points[0],points[1],points[0],points[1]);
            for(int i=1;i<4;i++){plate.left=Math.min(plate.left,points[2*i]);plate.top=Math.min(plate.top,points[2*i+1]);plate.right=Math.max(plate.right,points[2*i]);plate.bottom=Math.max(plate.bottom,points[2*i+1]);}
            if(plate.width()<=0||plate.height()<=0)continue;
            Pose pose=platePoses.computeIfAbsent(id,ignored->new Pose());
            RectF smoothed=pose.update(plate,identity.epoch,plateMeasured,now);
            float rotation=pose.angle((float)Math.atan2(points[3]-points[1],points[2]-points[0]),identity.epoch,plateMeasured,now);
            float cos=(float)Math.cos(rotation),sin=(float)Math.sin(rotation);
            List<PointF> quad=new ArrayList<>();boolean inside=true;
            for(int i=0;i<4;i++) {
                float x=smoothed.centerX()+(points[2*i]-plate.centerX())*smoothed.width()/plate.width();
                float y=smoothed.centerY()+(points[2*i+1]-plate.centerY())*smoothed.height()/plate.height();
                float rx=x-smoothed.centerX(),ry=y-smoothed.centerY();
                x=smoothed.centerX()+cos*rx-sin*ry;y=smoothed.centerY()+sin*rx+cos*ry;
                x=displayed.left+(x-target.left)*displayed.width()/target.width();
                y=displayed.top+(y-target.top)*displayed.height()/target.height();
                quad.add(new PointF(x,y));
                inside&=x>=displayed.left&&x<=displayed.right&&y>=displayed.top&&y<=displayed.bottom;
            }
            if(!inside) {
                Long previous=rejectedPlateMeasurements.put(id,vehicle.plateMeasuredAtNanos);
                if(previous==null||previous!=vehicle.plateMeasuredAtNanos)refresh.add(id);
                continue;
            }
            RectF bounds=new RectF(quad.get(0).x,quad.get(0).y,quad.get(0).x,quad.get(0).y);
            for(PointF p:quad){bounds.left=Math.min(bounds.left,p.x);bounds.top=Math.min(bounds.top,p.y);bounds.right=Math.max(bounds.right,p.x);bounds.bottom=Math.max(bounds.bottom,p.y);}
            if(!attached&&geometryAt>0) {
                PlateAttachment value=new PlateAttachment(identity.epoch,vehicle.plateTrackId,geometryAt,plateMeasured,points,target);
                if(value.inside())plateAttachments.put(id,value);
            }
            result.add(new OverlayItem(OverlayItem.Kind.PLATE,bounds,quad,"",vehicle.plateTrackId,false));
        }
        bodyPoses.keySet().retainAll(visible);platePoses.keySet().retainAll(visible);motionPoses.keySet().retainAll(visible);plateAttachments.keySet().retainAll(visible);
        return Collections.unmodifiableList(result);
    }
    public boolean canAnimate(DynamicVehicleDisplaySnapshot snapshot,long now) {
        if(snapshot==null)return false;
        for(DynamicVehicleDisplaySnapshot.Vehicle v:snapshot.vehicles.values()) {
            DynamicVehicleIdentity.Presentation owner=snapshot.identities.get(v.entityId);
            if(owner!=null&&owner.visible&&v.geometryAtNanos>0&&now>=v.geometryAtNanos
                    &&now-v.geometryAtNanos<=presentationAllowance(snapshot,v.geometryAtNanos))return true;
        }
        return false;
    }
    private static long presentationAllowance(DynamicVehicleDisplaySnapshot snapshot,long geometryAt) {
        // An active camera can advance while the pixel worker/UI publication is
        // briefly delayed. Bridge that measured lag only for drawing. A frozen
        // camera still expires after 600 ms; no case may exceed one second.
        long lag=Math.max(0L,snapshot.sourceTimeNanos-geometryAt);
        return DynamicVehicleIdentity.COAST_NANOS+Math.min(400_000_000L,lag);
    }
    public Set<Long> consumePlateRefresh(){Set<Long> result=new LinkedHashSet<>(refresh);refresh.clear();return result;}
    private static RectF rect(NormalizedBounds b){return new RectF(b.left,b.top,b.right,b.bottom);}
    private static List<PointF> corners(RectF b){return Arrays.asList(new PointF(b.left,b.top),new PointF(b.right,b.top),new PointF(b.right,b.bottom),new PointF(b.left,b.bottom));}
}
