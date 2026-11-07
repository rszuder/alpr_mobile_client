package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.List;

/** TV vertices lie on a closed image boundary; camera pixel hit-testing stays half-open. */
public final class TvCornerEditGeometry {
    private static final double VIEW_EPS=1e-4;
    private final CameraPreviewGeometry geometry;
    private List<double[]> domain=new ArrayList<>();

    public static final class Position {
        public final double x,y;
        public Position(double x,double y){this.x=x;this.y=y;}
        public Point2 toPoint(){return new Point2((float)x,(float)y);}
    }
    public static final class Move {
        public final Position point;
        public final double viewX,viewY;
        public final boolean limited,onBoundary;
        private Move(Position point,double x,double y,boolean limited,boolean edge){
            this.point=point;viewX=x;viewY=y;this.limited=limited;onBoundary=edge;
        }
    }
    public TvCornerEditGeometry(CameraPreviewGeometry geometry){
        this.geometry=geometry;
        for(double[] p:new double[][]{{0,0},{1,0},{1,1},{0,1}})domain.add(geometry.normalizedToView(p[0],p[1]));
        double[] u=geometry.usableViewRect();
        domain=clip(domain,0,u[0],true);domain=clip(domain,0,u[2],false);
        domain=clip(domain,1,u[1],true);domain=clip(domain,1,u[3],false);
        if(domain.size()<3||Math.abs(area(domain))<1e-8)throw new IllegalArgumentException("No visible TV editing area");
    }
    /** Existing FP32 snapshot values need at most 0.0001 view px of rounding tolerance. */
    public static boolean contains(CameraPreviewGeometry g,Point2 point){
        return point!=null&&contains(g,point.x,point.y);
    }
    public static boolean contains(CameraPreviewGeometry g,double x,double y){
        if(g==null||!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)return false;
        double[] p=g.normalizedToView(x,y),u=g.usableViewRect();
        return p[0]>=u[0]-VIEW_EPS&&p[0]<=u[2]+VIEW_EPS&&p[1]>=u[1]-VIEW_EPS&&p[1]<=u[3]+VIEW_EPS;
    }
    public boolean containsView(double x,double y){
        if(!Double.isFinite(x)||!Double.isFinite(y))return false;
        double[] n=geometry.viewToNormalized(x,y);return contains(geometry,n[0],n[1]);
    }
    public Move constrain(double x,double y){
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid TV cursor");
        double px=x,py=y,best=Double.POSITIVE_INFINITY;
        double[] nearest=null;
        for(int i=0;i<domain.size();i++){
            double[] q=nearest(domain.get(i),domain.get((i+1)%domain.size()),x,y);
            double distance=Math.hypot(q[0]-x,q[1]-y);
            if(distance<best){best=distance;nearest=q;}
        }
        if(!inside(x,y)||best<=VIEW_EPS){px=nearest[0];py=nearest[1];}
        for(double[] vertex:domain)if(Math.hypot(vertex[0]-x,vertex[1]-y)<=VIEW_EPS){px=vertex[0];py=vertex[1];break;}
        double[] n=geometry.viewToNormalized(px,py);
        Position point=new Position(unit(n[0]),unit(n[1]));
        double[] v=geometry.normalizedToView(point.x,point.y);
        return new Move(point,v[0],v[1],Math.hypot(x-v[0],y-v[1])>VIEW_EPS,best<=VIEW_EPS||!inside(x,y));
    }
    /** Directions are visual; distance is exactly one source pixel before boundary clipping. */
    public Move nudge(Position point,int dx,int dy){
        if(Math.abs(dx)+Math.abs(dy)!=1)throw new IllegalArgumentException("Expected one visual axis");
        double[] v=geometry.normalizedToView(point.x,point.y),n=geometry.viewToNormalized(v[0]+dx,v[1]+dy);
        double sx=(n[0]-point.x)*geometry.sourceWidth,sy=(n[1]-point.y)*geometry.sourceHeight,length=Math.hypot(sx,sy);
        if(!(length>0))throw new IllegalArgumentException("Invalid TV nudge transform");
        double[] target=geometry.normalizedToView(point.x+sx/length/geometry.sourceWidth,point.y+sy/length/geometry.sourceHeight);
        return constrain(target[0],target[1]);
    }
    public boolean onBoundary(Position p){
        if(p==null||!contains(geometry,p.x,p.y))return false;
        double[] v=geometry.normalizedToView(p.x,p.y);
        for(int i=0;i<domain.size();i++){
            double[] q=nearest(domain.get(i),domain.get((i+1)%domain.size()),v[0],v[1]);
            if(Math.hypot(q[0]-v[0],q[1]-v[1])<=VIEW_EPS)return true;
        }
        return false;
    }
    private boolean inside(double x,double y){
        double sign=Math.signum(area(domain));
        for(int i=0;i<domain.size();i++){
            double[] a=domain.get(i),b=domain.get((i+1)%domain.size());
            if(sign*((b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]))<0)return false;
        }
        return true;
    }
    private static double unit(double n){return Math.abs(n)<1e-12?0:Math.abs(n-1)<1e-12?1:Math.max(0,Math.min(1,n));}
    private static double area(List<double[]> points){
        double value=0;for(int i=0;i<points.size();i++){double[] a=points.get(i),b=points.get((i+1)%points.size());value+=a[0]*b[1]-b[0]*a[1];}return value/2;
    }
    private static double[] nearest(double[] a,double[] b,double x,double y){
        double dx=b[0]-a[0],dy=b[1]-a[1],length=dx*dx+dy*dy;
        double t=length==0?0:Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/length));
        return new double[]{a[0]+t*dx,a[1]+t*dy};
    }
    private static List<double[]> clip(List<double[]> input,int axis,double edge,boolean minimum){
        List<double[]> output=new ArrayList<>();if(input.isEmpty())return output;
        double[] previous=input.get(input.size()-1);boolean previousInside=minimum?previous[axis]>=edge:previous[axis]<=edge;
        for(double[] current:input){
            boolean currentInside=minimum?current[axis]>=edge:current[axis]<=edge;
            if(currentInside!=previousInside){
                double t=(edge-previous[axis])/(current[axis]-previous[axis]);
                output.add(new double[]{previous[0]+t*(current[0]-previous[0]),previous[1]+t*(current[1]-previous[1])});
            }
            if(currentInside)output.add(current);
            previous=current;previousInside=currentInside;
        }
        return output;
    }
}
