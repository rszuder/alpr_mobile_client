package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import java.util.*;

/** Bounded owned source pixels for catching a delayed neural anchor up to live preview. */
public final class PreviewLumaHistory {
    static final class Frame {
        final SourceFrameStamp stamp;
        final byte[] gray;
        final int width,height;
        Frame(SourceFrameStamp stamp,byte[] gray,int width,int height) {
            this.stamp=stamp;this.gray=gray;this.width=width;this.height=height;
        }
    }
    private final Deque<Frame> frames=new ArrayDeque<>();
    private final long maximumBytes,maximumAge,maximumGap;
    private final long minimumSampleInterval;
    private final int maximumFrames,maximumReplay;
    private long bytes;
    // Cold camera/model startup can leave a 600-800 ms interval between real
    // samples. KLT must still validate those pixels; this is no presence proof.
    public PreviewLumaHistory() {this(4L*1024*1024,64,10_000_000_000L,1_000_000_000L,16,200_000_000L);}
    PreviewLumaHistory(long bytes,int frames,long age,long gap,int replay) {
        this(bytes,frames,age,gap,replay,0L);
    }
    private PreviewLumaHistory(long bytes,int frames,long age,long gap,int replay,long interval) {
        maximumBytes=Math.max(0,bytes);maximumFrames=Math.max(1,frames);
        maximumAge=Math.max(0,age);maximumGap=Math.max(0,gap);maximumReplay=Math.max(1,replay);
        minimumSampleInterval=interval;
    }
    public synchronized void clear() {frames.clear();bytes=0;}
    public synchronized long retainedBytes() {return bytes;}
    public synchronized int retainedFrames() {return frames.size();}
    int maximumReplaySteps() {return maximumReplay;}
    synchronized boolean canFinish(SourceFrameStamp origin,SourceFrameStamp cursor,SourceFrameStamp current) {
        return sameContext(origin,current)&&sameContext(cursor,current)&&current.domain.cameraDerived()
                &&current.sourceTimestampNanos>=cursor.sourceTimestampNanos
                &&current.sourceTimestampNanos-origin.sourceTimestampNanos<=maximumAge
                &&current.sourceTimestampNanos-cursor.sourceTimestampNanos<=maximumGap;
    }
    /** One shared historical image; the caller never pins a second image queue. */
    synchronized Frame nextReplayFrame(SourceFrameStamp origin,SourceFrameStamp cursor,SourceFrameStamp current) {
        if(!sameContext(origin,current)||!sameContext(cursor,current)||!current.domain.cameraDerived()
                ||current.sourceTimestampNanos<cursor.sourceTimestampNanos
                ||current.sourceTimestampNanos-origin.sourceTimestampNanos>maximumAge)return null;
        Frame next=null;
        for(Frame frame:frames)if(sameContext(frame.stamp,current)
                &&frame.stamp.sourceTimestampNanos>cursor.sourceTimestampNanos
                &&frame.stamp.sourceTimestampNanos<current.sourceTimestampNanos
                &&frame.stamp.sourceTimestampNanos-cursor.sourceTimestampNanos<=maximumGap)next=frame;
        return next;
    }
    private static boolean sameContext(SourceFrameStamp a,SourceFrameStamp b) {
        return a!=null&&b!=null&&a.sceneGeneration==b.sceneGeneration&&a.visualEpoch==b.visualEpoch
                &&a.cameraTransformGeneration==b.cameraTransformGeneration&&a.domain==b.domain;
    }
    public synchronized void offer(SourceFrameStamp stamp,byte[] gray,int width,int height) {
        long size=(long)width*height;
        if(stamp==null||!stamp.domain.cameraDerived()||stamp.sourceTimestampNanos<=0||gray==null
                ||width<=0||height<=0||size>262144||size>maximumBytes||gray.length<size)return;
        Frame last=frames.peekLast();
        if(last!=null) {
            if(stamp.sceneGeneration<last.stamp.sceneGeneration||stamp.visualEpoch<last.stamp.visualEpoch
                    ||stamp.cameraTransformGeneration<last.stamp.cameraTransformGeneration)return;
            if(sameContext(stamp,last.stamp)&&stamp.sourceTimestampNanos<=last.stamp.sourceTimestampNanos)return;
            if(!sameContext(stamp,last.stamp)||width!=last.width||height!=last.height)clear();
            else if(stamp.sourceTimestampNanos-last.stamp.sourceTimestampNanos<minimumSampleInterval)return;
        }
        frames.addLast(new Frame(stamp,Arrays.copyOf(gray,(int)size),width,height));bytes+=size;
        while(!frames.isEmpty()&&(frames.size()>maximumFrames||bytes>maximumBytes
                ||stamp.sourceTimestampNanos-frames.peekFirst().stamp.sourceTimestampNanos>maximumAge)) {
            bytes-=frames.removeFirst().gray.length;
        }
    }
    /** Null means the real pixel path is unavailable; never bridge a hole by prediction. */
    synchronized List<Frame> replayAfter(SourceFrameStamp measured,SourceFrameStamp current) {
        if(!sameContext(measured,current)||!current.domain.cameraDerived()
                ||current.sourceTimestampNanos<measured.sourceTimestampNanos
                ||current.sourceTimestampNanos-measured.sourceTimestampNanos>maximumAge)return null;
        List<Frame> available=new ArrayList<>();
        for(Frame frame:frames)if(sameContext(frame.stamp,current)
                &&frame.stamp.sourceTimestampNanos>measured.sourceTimestampNanos
                &&frame.stamp.sourceTimestampNanos<current.sourceTimestampNanos)available.add(frame);
        List<Frame> replay=new ArrayList<>();
        if(available.size()<=maximumReplay)replay.addAll(available);
        else for(int i=0;i<maximumReplay;i++)replay.add(available.get((i+1)*available.size()/(maximumReplay+1)));
        long previous=measured.sourceTimestampNanos;
        for(Frame frame:replay) {
            if(frame.stamp.sourceTimestampNanos-previous>maximumGap)return null;
            previous=frame.stamp.sourceTimestampNanos;
        }
        return current.sourceTimestampNanos-previous>maximumGap?null:Collections.unmodifiableList(replay);
    }
}
