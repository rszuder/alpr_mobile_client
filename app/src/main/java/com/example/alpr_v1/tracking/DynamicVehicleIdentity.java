package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.*;
import java.util.*;

/** Runtime identity evidence for ordinary dynamic scanning. Owns no detector or OCR engine. */
public final class DynamicVehicleIdentity {
    public static final long COAST_NANOS = 600_000_000L;
    public static final long RECOVERY_NANOS = 5_000_000_000L;
    public static final class Stamp {
        public final long entityId, vehicleTrackId, epoch;
        public final long analysisRunId, displayNumber;
        Stamp(long entityId, long trackId, long epoch) {
            this(entityId,trackId,epoch,0L,0L);
        }
        Stamp(long entityId,long trackId,long epoch,long run,long number) {
            this.entityId=entityId;this.vehicleTrackId=trackId;this.epoch=epoch;
            analysisRunId=run;displayNumber=number;
        }
    }
    public static final class Presentation {
        public final long number, epoch;
        public final boolean visible, verifying;
        public final long recoveryUntilNanos;
        Presentation(long number,long epoch,boolean visible,boolean verifying) {
            this(number,epoch,visible,verifying,0L);
        }
        Presentation(long number,long epoch,boolean visible,boolean verifying,long until) {
            this.number=number;this.epoch=epoch;this.visible=visible;this.verifying=verifying;
            recoveryUntilNanos=until;
        }
    }
    static final class Parent {
        final long id, epoch, until;
        final String key;
        Parent(long id,long epoch,long until,String key) {this.id=id;this.epoch=epoch;this.until=until;this.key=key;}
    }
    static final class ReadDecision {
        final long recoveredEntityId;
        final boolean committedNew;
        ReadDecision(long recoveredEntityId,boolean committedNew) {this.recoveredEntityId=recoveredEntityId;this.committedNew=committedNew;}
    }
    private static final class State {
        long epoch, number, measuredAt, latestProof, brokenAt, lastFrame, lastSequence, lastReadTime, recoveryUntil;
        long continuedMp,ownerStartedAt;
        long coastingAt;
        NormalizedBounds localBounds;
        long controlledUntil,controlledFrom;
        NormalizedBounds controlledBounds;
        long vehicleProof, plateProof;
        boolean plateOnlyGeometry;
        final NavigableMap<Long,NormalizedBounds> geometry = new TreeMap<>();
        boolean lost, verifying;
        long exclusiveParent;
        String lastKey="";
        int agreeingReads;
        final List<Parent> parents=new ArrayList<>();
        State(long epoch) {this.epoch=epoch;}
    }
    private final VehicleEntityRepository repository;
    private final Map<Long,State> states=new LinkedHashMap<>();
    private final Map<Long,Long> previewAliases=new HashMap<>();
    private final Map<Long,Long> canonicalOwners=new LinkedHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_RUN=new java.util.concurrent.atomic.AtomicLong();
    private long analysisRunId=NEXT_RUN.incrementAndGet();
    public synchronized long analysisRunId() { return analysisRunId; }
    public synchronized Map<Long,Long> canonicalOwners() { return new LinkedHashMap<>(canonicalOwners); }
    private long nextEpoch=1, nextNumber=1, latestBreak;
    private long trackWindow, recoveryGrace, entityRetention;
    private boolean enabled;
    private java.util.function.Consumer<String> diagnosticSink;
    public synchronized void setDiagnosticSink(java.util.function.Consumer<String> sink) {diagnosticSink=sink;}
    synchronized boolean hasDiagnostics() {return diagnosticSink!=null;}
    synchronized void diagnostic(String message) {
        if(diagnosticSink!=null)try {diagnosticSink.accept(message);}catch(RuntimeException ignored) { }
    }
    private long exclusiveEntityId;
    synchronized void exclusiveTarget(long id) { exclusiveEntityId=id; }
    synchronized boolean verificationFor(long candidate,long original) {
        State s=states.get(candidate);
        if(s==null||!s.verifying)return false;
        for(Parent parent:s.parents)if(parent.id==original)return true;
        return false;
    }

    DynamicVehicleIdentity(VehicleEntityRepository repository) {this.repository=repository;}
    synchronized void setEnabled(boolean value) {
        if(enabled!=value)clear(false);
        enabled=value;
    }
    public synchronized boolean enabled() {return enabled;}
    public synchronized long breakEpoch() {return latestBreak;}
    public synchronized void beginRunIfEmpty() {if(enabled&&repository.activeEntities().isEmpty())clear(true);}
    synchronized void window(long track,long grace,long retention) {trackWindow=track;recoveryGrace=grace;entityRetention=retention;}
    synchronized NormalizedBounds recoveryBounds(long id) {State s=states.get(id);return s==null?null:s.localBounds;}
    private State state(long id) {return states.computeIfAbsent(id,ignored->new State(nextEpoch++));}
    private String knownKey(long id) {
        VehicleEntity entity=repository.get(id);
        return entity!=null&&RegistrationTextPolicy.displayable(entity.registration().text)
                ? RegistrationTextNormalizer.registrationKey(entity.registration().text):"";
    }
    synchronized boolean known(long id) {
        State s=states.get(id);
        return enabled&&s!=null&&s.number>0&&!s.verifying;
    }
    public synchronized boolean recoverable(long id,long at) {
        State s=states.get(id);
        if(!known(id)||at<s.measuredAt||s.lost&&at<s.brokenAt)return false;
        long until=s.recoveryUntil>0?s.recoveryUntil:Math.max(s.measuredAt,s.latestProof)+RECOVERY_NANOS;
        return at<=until;
    }
    public synchronized boolean verifying(long id) {State s=states.get(id);return enabled&&s!=null&&s.verifying;}
    synchronized boolean pendingCanStart(long id) {State s=states.get(id);return s!=null&&s.verifying&&s.parents.isEmpty()&&s.exclusiveParent==0;}
    synchronized void created(long id,long at,List<Parent> parents) {
        if(!enabled)return;
        State s=state(id);s.measuredAt=at;s.ownerStartedAt=at;
        if(parents!=null&&!parents.isEmpty()){
            s.parents.addAll(parents);s.verifying=true;
            for(Parent parent:parents)if(parent.id==exclusiveEntityId)s.exclusiveParent=exclusiveEntityId;
        }
        else s.number=nextNumber++;
        VehicleEntity entity=repository.get(id);NormalizedBounds bounds=entity==null?null:entity.vehicleBounds();
        diagnostic("create entity="+id+" number="+s.number+" epoch="+s.epoch+" source="+at+" verifying="+s.verifying
                +" bounds="+(bounds==null?"none":bounds.left+","+bounds.top+","+bounds.right+","+bounds.bottom));
    }
    synchronized void measured(long id,long at) {
        if(!enabled)return;
        State s=state(id);
        if(pendingCanStart(id))s.verifying=false;
        if(s.lost) {
            s.lost=false;s.brokenAt=0L;s.ownerStartedAt=at;
            s.recoveryUntil=0L;s.coastingAt=0L;
        }
        if(continuous(id,at))s.continuedMp=at;
        s.measuredAt=Math.max(s.measuredAt,at);
        if(controlled(id,at)) {
            s.latestProof=Math.max(s.latestProof,at);s.vehicleProof=at;
            s.controlledUntil=0;s.controlledBounds=null;
        }
        if(!s.verifying&&s.number==0)s.number=nextNumber++;
    }
    /** Scene/global camera motion is never a substitute for a local object measurement. */
    public synchronized void preview(long sourceTime,Set<Long> locallyValidated,boolean localAvailable) {
        Set<Long> evaluated = localAvailable ? new HashSet<>(states.keySet()) : Collections.emptySet();
        preview(sourceTime, localAvailable?locallyValidated:Collections.emptySet(), Collections.emptySet(), evaluated, Collections.emptySet());
    }

    /** Both channels are evaluated together; an unavailable channel is not a negative observation. */
    public synchronized void preview(long sourceTime, Set<Long> vehicles, Set<Long> plates,
            Set<Long> vehicleEvaluated, Set<Long> plateEvaluated) {
        if(!enabled)return;
        Set<Long> proof=new HashSet<>(vehicles==null?Collections.emptySet():vehicles);
        proof.addAll(plates);
        for(Map.Entry<Long,Long> alias:previewAliases.entrySet())if(proof.contains(alias.getKey()))proof.add(alias.getValue());
        previewAliases.entrySet().removeIf(e->vehicles!=null&&vehicles.contains(e.getValue())||plates.contains(e.getValue()));
        for(Map.Entry<Long,State> entry:states.entrySet()) {
            long id=entry.getKey();State s=entry.getValue();
            if(sourceTime<s.ownerStartedAt)continue;
            if(sourceTime<s.latestProof)continue;
            // A delayed positive sample still carries information. A delayed miss cannot undo a newer MP.
            if(sourceTime<s.measuredAt&&!proof.contains(id))continue;
            boolean controlled=controlled(id,sourceTime);
            if(controlled&&sourceTime<=s.controlledFrom)continue;
            if (!controlled&&s.latestProof > 0 && sourceTime - s.latestProof > trackWindow) {
                lost(id,sourceTime,"pixel_gap_expired");
                continue;
            }
            if(proof.contains(id)&&!s.lost) {
                s.coastingAt=0L;
                s.controlledUntil=0;s.controlledBounds=null;
                s.latestProof=Math.max(s.latestProof,sourceTime);
                repository.observeLocalPresence(id,sourceTime);
                if (vehicles != null && vehicles.contains(id)) s.vehicleProof=sourceTime;
                if (plates.contains(id)) s.plateProof=sourceTime;
            } else if (!controlled&&(s.latestProof > 0 && (vehicleEvaluated.contains(id) && plateEvaluated.contains(id)
                    || vehicleEvaluated.contains(id) && s.vehicleProof >= s.plateProof && s.vehicleProof > 0
                    || plateEvaluated.contains(id) && s.plateProof >= s.vehicleProof && s.plateProof > 0)
                    || sourceTime - Math.max(s.measuredAt,s.latestProof) > trackWindow)) {
                if(s.latestProof>0 && sourceTime-s.latestProof<=COAST_NANOS) {
                    if(s.coastingAt==0)s.coastingAt=sourceTime;
                } else lost(id,sourceTime,"evaluated_pixels_missing");
            }
        }
    }
    synchronized void localBounds(long id,NormalizedBounds bounds,long sourceTime) {
        localBounds(id,bounds,sourceTime,false);
    }
    synchronized void localBounds(long id,NormalizedBounds bounds,long sourceTime,boolean fromPlate) {
        State s=states.get(id);
        if(s!=null&&s.controlledUntil>0&&sourceTime<=s.controlledFrom)return;
        if(enabled&&s!=null&&!s.lost&&bounds!=null&&bounds.valid()
                &&sourceTime>=s.ownerStartedAt&&sourceTime>=s.latestProof) {
            s.localBounds=bounds;s.plateOnlyGeometry=fromPlate;s.geometry.put(sourceTime,bounds);
            while(s.geometry.size()>256)s.geometry.pollFirstEntry();
        }
    }
    synchronized boolean continuous(long id,long at) {
        State s=states.get(id);
        long previewLag=Math.min(trackWindow,s!=null&&s.coastingAt>0?COAST_NANOS:
                com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.maximumFocusedEvidenceAgeNanos);
        return enabled&&s!=null&&!s.lost&&s.latestProof>0
                &&(s.latestProof>s.measuredAt||s.continuedMp==s.measuredAt)
                &&at<=s.latestProof+previewLag;
    }
    synchronized boolean freshLocal(long id,long now) {
        State s=states.get(id);
        return enabled&&s!=null&&!s.lost&&s.latestProof>0&&now-s.latestProof<=trackWindow;
    }
    synchronized NormalizedBounds boundsAt(long id,long at) {
        return boundsAt(id,at,0L);
    }
    synchronized NormalizedBounds boundsAt(long id,long at,long notBefore) {
        State s=states.get(id);
        if(s==null||!freshLocal(id,at))return null;
        Map.Entry<Long,NormalizedBounds> geometry=s.geometry.floorEntry(at);
        return geometry==null||geometry.getKey()<notBefore?null:geometry.getValue();
    }
    public synchronized Map<Long,NormalizedBounds> plateDerivedBounds(long now) {
        Map<Long,NormalizedBounds> result=new LinkedHashMap<>();
        for(Map.Entry<Long,State> e:states.entrySet())if(e.getValue().plateOnlyGeometry&&freshLocal(e.getKey(),now)
                &&e.getValue().localBounds!=null)result.put(e.getKey(),e.getValue().localBounds);
        return Collections.unmodifiableMap(result);
    }
    /** Actual local geometry or at most 600 ms of display coasting, never an invented MP/MT. */
    public synchronized Map<Long,NormalizedBounds> displayBounds(long now) {
        Map<Long,NormalizedBounds> result=new LinkedHashMap<>();
        for(Map.Entry<Long,State> entry:states.entrySet()) {
            State s=entry.getValue();
            if(s.lost||s.latestProof<=0||now<s.latestProof||now-s.latestProof>COAST_NANOS)continue;
            Map.Entry<Long,NormalizedBounds> geometry=s.geometry.floorEntry(now);
            if(geometry!=null)result.put(entry.getKey(),geometry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
    public synchronized long ownerNumber(long id) { State s=states.get(id);return s==null?0L:s.number; }
    synchronized long measuredAt(long id){State s=states.get(id);return s==null?0L:s.measuredAt;}
    synchronized void rejectLocalAppearance(long id,long sourceTime) {
        State s=states.get(id);
        if(s!=null&&sourceTime>=s.ownerStartedAt&&sourceTime>=s.measuredAt&&sourceTime>=s.latestProof)lost(id,sourceTime,"local_appearance_conflict");
    }
    public synchronized void beginControlledTransform(long id,long now) {
        State s=states.get(id);VehicleEntity entity=repository.get(id);
        if(!enabled||s==null||entity==null||!freshLocal(id,now))return;
        s.controlledUntil=now+Math.min(entityRetention,trackWindow+recoveryGrace);
        s.controlledFrom=now;
        s.controlledBounds=s.localBounds==null?entity.vehicleBounds():s.localBounds;
    }
    synchronized boolean controlled(long id,long at) {
        State s=states.get(id);
        return enabled&&s!=null&&!s.lost&&s.controlledUntil>0&&at<=s.controlledUntil;
    }
    synchronized NormalizedBounds controlledBounds(long id,long at) {
        return controlled(id,at)?states.get(id).controlledBounds:null;
    }
    public synchronized void applyControlledZoom(float ratio) {
        if(!Float.isFinite(ratio)||ratio<=0)return;
        for(State s:states.values())if(s.controlledUntil>0&&s.controlledBounds!=null) {
            NormalizedBounds b=s.controlledBounds;
            s.controlledBounds=new NormalizedBounds(.5f+ratio*(b.left-.5f),.5f+ratio*(b.top-.5f),
                    .5f+ratio*(b.right-.5f),.5f+ratio*(b.bottom-.5f));
            s.geometry.clear();
        }
    }
    synchronized void lost(long id,long at) {
        lost(id,at,"unclassified");
    }
    synchronized void lost(long id,long at,String reason) {
        if(!enabled||!states.containsKey(id))return;
        State s=state(id);
        if(!s.lost) {
            diagnostic("loss entity="+id+" number="+s.number+" epoch="+s.epoch+" reason="+reason
                    +" source="+at+" mp="+s.measuredAt+" pixels="+s.latestProof+" vehicle="+s.vehicleProof
                    +" plate="+s.plateProof+" window="+trackWindow+" coast="+s.coastingAt);
            s.recoveryUntil=Math.max(s.measuredAt,s.latestProof)+RECOVERY_NANOS;
            s.lost=true;s.brokenAt=Math.max(at,s.measuredAt);s.epoch=nextEpoch++;
            s.latestProof=0;s.lastKey="";s.agreeingReads=0;s.lastFrame=s.lastSequence=s.lastReadTime=0;
            s.vehicleProof=s.plateProof=0;s.geometry.clear();s.plateOnlyGeometry=false;
            s.continuedMp=0L;
            s.coastingAt=0L;
            s.controlledUntil=0;s.controlledBounds=null;
            latestBreak=Math.max(latestBreak,s.brokenAt);
        }
    }
    synchronized boolean mayAssociate(long id,long at,boolean missed) {
        if(!known(id))return true;
        State s=state(id);
        // The proof must continue after the previous real MP, not just be a new anchor.
        return continuous(id,at)||controlled(id,at)||recoverable(id,at);
    }
    public synchronized boolean sourceCurrent(long sourceTime) {
        State focused=states.get(exclusiveEntityId);
        return !enabled||sourceTime>=(focused==null?latestBreak:focused.brokenAt);
    }
    synchronized Parent parent(long id,long until,long now) {
        if(!known(id))return null;
        State s=state(id);
        long deadline=s.recoveryUntil>0?s.recoveryUntil:until;
        return s.lost&&now>=s.brokenAt&&now<=deadline?new Parent(id,s.epoch,deadline,knownKey(id)):null;
    }
    private boolean parentCurrent(Parent p,long now) {
        State s=states.get(p.id);
        return s!=null&&s.lost&&s.epoch==p.epoch&&repository.get(p.id)!=null&&now<=p.until;
    }
    public synchronized Stamp stamp(long id,long trackId) {
        if(!enabled||!states.containsKey(id))return null;
        State s=state(id);
        return new Stamp(id,trackId,s.epoch,analysisRunId,s.number);
    }
    public synchronized boolean accepts(Stamp stamp) {
        if(!enabled)return true;
        if(stamp==null)return false;
        State s=states.get(stamp.entityId);VehicleEntity entity=repository.get(stamp.entityId);
        return s!=null&&entity!=null&&!s.lost&&s.epoch==stamp.epoch&&entity.vehicleTrackId()==stamp.vehicleTrackId;
    }
    public synchronized boolean accepts(Stamp stamp,long sourceTime) {
        return accepts(stamp)&&sourceTime>=states.get(stamp.entityId).ownerStartedAt;
    }
    public synchronized boolean guarded(long id) {return enabled&&states.containsKey(id);}
    public synchronized String lastLocalEvidence(long id) {
        State s=states.get(id);
        if(!enabled||s==null||s.lost||s.latestProof==0)return "NONE";
        if(s.vehicleProof==s.latestProof&&s.plateProof==s.latestProof)return "BOTH";
        return s.plateProof==s.latestProof?"PLATE":"VEHICLE";
    }
    public synchronized long lostRecoveryDeadline(long id) {
        State s=states.get(id);
        return enabled&&s!=null&&s.lost?s.recoveryUntil:0L;
    }
    public synchronized void endRecovery(long id,long sourceTime) {
        State s=states.get(id);
        if(!enabled||s==null)return;
        s.lost=true;s.epoch=nextEpoch++;s.recoveryUntil=Math.max(1L,sourceTime-1L);
        s.brokenAt=Math.max(s.brokenAt,sourceTime);s.latestProof=0;s.geometry.clear();
        s.controlledUntil=0;s.controlledBounds=null;s.plateOnlyGeometry=false;
        latestBreak=Math.max(latestBreak,sourceTime);
    }
    public synchronized boolean conflicts(Stamp stamp,String raw) {
        String key=RegistrationTextNormalizer.registrationKey(raw);
        return accepts(stamp)&&!verifying(stamp.entityId)&&known(stamp.entityId)
                &&RegistrationTextPolicy.displayable(key)&&!key.equals(knownKey(stamp.entityId));
    }
    synchronized ReadDecision fresh(Stamp stamp,String raw,long frameId,long sequence,long sourceTime) {
        if(!accepts(stamp))return null;
        State s=state(stamp.entityId);
        if(!s.verifying)return new ReadDecision(0,false);
        // OCR is character evidence, never permission to merge or number physical objects.
        return new ReadDecision(0,false);
    }
    private boolean commitNew(State s) {
        if(!s.verifying)return false;
        s.verifying=false;s.parents.clear();s.number=nextNumber++;
        return true;
    }
    synchronized int expire(long now) {
        if(!enabled)return 0;
        int committed=0;
        for(Map.Entry<Long,State> e:states.entrySet())if(e.getValue().verifying) {
            State s=e.getValue();
            s.parents.removeIf(p->!parentCurrent(p,now));
            if(s.parents.isEmpty()) {
                if(s.exclusiveParent>0)repository.retireEntity(e.getKey());
                // A number is allocated only by a subsequent real, unambiguous MP.
            }
        }
        states.keySet().removeIf(id->repository.get(id)==null);
        return committed;
    }
    synchronized void recovered(long candidate,long original,long at) {
        State pending=state(candidate),old=state(original);
        old.lost=false;old.brokenAt=0;old.latestProof=pending.latestProof;old.localBounds=pending.localBounds;old.measuredAt=at;old.epoch=nextEpoch++;
        old.ownerStartedAt=at;old.continuedMp=pending.continuedMp;
        old.geometry.clear();old.geometry.putAll(pending.geometry);
        old.vehicleProof=pending.vehicleProof;old.plateProof=pending.plateProof;old.plateOnlyGeometry=pending.plateOnlyGeometry;
        if(old.number==0)old.number=nextNumber++;
        states.remove(candidate);
        previewAliases.put(candidate,original);
        canonicalOwners.put(candidate,original);
        while(canonicalOwners.size()>256)canonicalOwners.remove(canonicalOwners.keySet().iterator().next());
    }
    public synchronized Map<Long,Presentation> presentation() {
        if(!enabled)return Collections.emptyMap();
        Map<Long,Presentation> result=new LinkedHashMap<>();
        for(Map.Entry<Long,State> e:states.entrySet())if(repository.get(e.getKey())!=null) {
            State s=e.getValue();result.put(e.getKey(),new Presentation(s.number,s.epoch,!s.lost,s.verifying,s.lost?s.recoveryUntil:0L));
        }
        return Collections.unmodifiableMap(result);
    }
    public synchronized long verificationFor(long original) {
        long result=0;
        for(Map.Entry<Long,State> e:states.entrySet())if(e.getValue().verifying)
            for(Parent p:e.getValue().parents)if(p.id==original) {
                if(result>0&&result!=e.getKey())return 0;result=e.getKey();
            }
        return result;
    }
    synchronized void clear(boolean newRun) {
        states.clear();previewAliases.clear();canonicalOwners.clear();latestBreak=0;exclusiveEntityId=0;
        if(newRun){nextNumber=1;analysisRunId=NEXT_RUN.incrementAndGet();}
        // Epochs are never recycled, including a same-generation late callback after STOP.
        nextEpoch++;
    }
}
