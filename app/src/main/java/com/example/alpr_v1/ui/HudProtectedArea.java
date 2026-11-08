package com.example.alpr_v1.ui;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/** A presentation snapshot: plate/label areas and actual outline segments, never ROI policy. */
public final class HudProtectedArea {
    private final List<RectF> rectangles = new ArrayList<>();
    private final List<float[]> edges = new ArrayList<>();

    public void add(RectF area) {
        if (area != null && !area.isEmpty() && Float.isFinite(area.left) && Float.isFinite(area.top)
                && Float.isFinite(area.right) && Float.isFinite(area.bottom)) rectangles.add(new RectF(area));
    }
    public void edge(float x1, float y1, float x2, float y2) {
        if (Float.isFinite(x1) && Float.isFinite(y1) && Float.isFinite(x2) && Float.isFinite(y2))
            edges.add(new float[]{x1,y1,x2,y2});
    }
    public void outline(RectF r) {
        edge(r.left,r.top,r.right,r.top); edge(r.right,r.top,r.right,r.bottom);
        edge(r.right,r.bottom,r.left,r.bottom); edge(r.left,r.bottom,r.left,r.top);
    }
    public void include(HudProtectedArea other) {
        for (RectF r : other.rectangles) add(r);
        for (float[] e : other.edges) edge(e[0],e[1],e[2],e[3]);
    }
    public boolean intersects(RectF candidate, float clearance) {
        float l=candidate.left-clearance,t=candidate.top-clearance,r=candidate.right+clearance,b=candidate.bottom+clearance;
        for (RectF area : rectangles) if (l<area.right&&area.left<r&&t<area.bottom&&area.top<b) return true;
        for (float[] e : edges) if (segmentIntersects(l,t,r,b,e)) return true;
        return false;
    }
    private static int outcode(float x,float y,float l,float t,float r,float b){
        return (x<l?1:x>r?2:0)|(y<t?4:y>b?8:0);
    }
    private static boolean segmentIntersects(float l,float t,float r,float b,float[] edge) {
        float x1=edge[0],y1=edge[1],x2=edge[2],y2=edge[3];
        int first=outcode(x1,y1,l,t,r,b),second=outcode(x2,y2,l,t,r,b);
        while(true){
            if((first|second)==0)return true;
            if((first&second)!=0)return false;
            int outside=first!=0?first:second;float x,y;
            if((outside&8)!=0){y=b;x=x1+(x2-x1)*(b-y1)/(y2-y1);}
            else if((outside&4)!=0){y=t;x=x1+(x2-x1)*(t-y1)/(y2-y1);}
            else if((outside&2)!=0){x=r;y=y1+(y2-y1)*(r-x1)/(x2-x1);}
            else{x=l;y=y1+(y2-y1)*(l-x1)/(x2-x1);}
            if(outside==first){x1=x;y1=y;first=outcode(x,y,l,t,r,b);}
            else{x2=x;y2=y;second=outcode(x,y,l,t,r,b);}
        }
    }
}
