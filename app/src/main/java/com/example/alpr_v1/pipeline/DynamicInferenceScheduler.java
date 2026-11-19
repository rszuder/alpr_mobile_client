package com.example.alpr_v1.pipeline;

/** Ordinary DYN cadence. Counts real eligible MT cycles, never camera/HUD ticks. */
public final class DynamicInferenceScheduler {
    public static final class Cadence {
        long lastGeometryFrame=Long.MIN_VALUE;
        int cycles;
        void attempted(){cycles=0;}
    }
    private double mp,mt,mz;
    private int width,height;
    public void sourceSize(int w,int h) {
        if(w!=width||h!=height){reset();width=w;height=h;}
    }
    public void reset(){mp=mt=mz=0;}
    public void observe(String stage,long preprocess,long inference,long postprocess,long rectification) {
        if(inference<=0)return;
        double total=Math.max(0,preprocess)+(double)inference+Math.max(0,postprocess)+Math.max(0,rectification);
        if("MP".equals(stage))mp=average(mp,total);
        else if("MT".equals(stage))mt=average(mt,total);
        else if("MZ".equals(stage))mz=average(mz,total);
    }
    private static double average(double previous,double value){return previous<=0?value:previous*.75+value*.25;}
    public int baseCycles() {
        if(mp<=0||mt<=0||mz<=0)return 2;
        return (int)Math.min(Integer.MAX_VALUE/3,Math.max(1,Math.ceil(mz/Math.max(mp,mt))));
    }
    public boolean due(Cadence cadence,long frame,int attempts,boolean stable,int emptyAttempts,boolean betterCrop) {
        if(frame!=cadence.lastGeometryFrame){cadence.lastGeometryFrame=frame;cadence.cycles++;}
        if(attempts==0)return true;
        int factor=(stable||emptyAttempts>=2)&&!betterCrop?3:1;
        return cadence.cycles>=(long)baseCycles()*factor;
    }
    public double stageNanos(String stage){return "MP".equals(stage)?mp:"MT".equals(stage)?mt:mz;}
}
