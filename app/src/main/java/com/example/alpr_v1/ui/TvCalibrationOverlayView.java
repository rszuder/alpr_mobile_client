package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.setup.TvCornerEditGeometry;
import com.example.alpr_v1.setup.TvCornerEditGeometry.Position;
import com.example.alpr_v1.setup.TvCornerEditGeometry.Move;
import com.example.alpr_v1.setup.TvSetupCalibration;
import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** TV-only draft. Double editing coordinates; the existing FP32 snapshot contract stays intact. */
public final class TvCalibrationOverlayView extends View {
    private CameraPreviewGeometry geometry;
    private TvCornerEditGeometry editGeometry;
    private final List<Position> points=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean editing,limited,hapticSent,nudgeHeld,staleOutline;
    private int selected,dragging=-1,pointer=-1,nudgeX,nudgeY,nudgeIndex=-1;
    private double grabX,grabY;
    private Position dragStart,nudgeStart;
    private Runnable changed=()->{};
    private BooleanSupplier allowed=()->true;
    private PreviewView preview;
    private TvCalibrationMagnifier magnifier;
    private final Runnable repeatNudge=new Runnable(){@Override public void run(){
        if(!nudgeHeld)return;
        if(!editing||editGeometry==null||!allowed.getAsBoolean()){stopNudge(true);return;}
        applyNudge();if(nudgeHeld)postDelayed(this,100);
    }};
    public TvCalibrationOverlayView(Context context,AttributeSet attrs){
        super(context,attrs);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(context.getString(R.string.tv_editor_instruction));
    }
    public void configure(PreviewView preview,Runnable changed){this.preview=preview;this.changed=changed;}
    public void configure(PreviewView preview,Runnable changed,BooleanSupplier allowed){configure(preview,changed);this.allowed=allowed;}
    public void setGeometry(CameraPreviewGeometry value){
        boolean replace=geometry==null||value==null||!geometry.sameGeometry(value);
        if(replace)dismissMagnifier();
        if(geometry!=null&&(value==null||geometry.presentationDifferencePixels(value)>.25))cancelInteraction();
        geometry=value;
        if(replace){try{editGeometry=value==null?null:new TvCornerEditGeometry(value);}catch(IllegalArgumentException unavailable){editGeometry=null;}}
        invalidate();
    }
    public void setEditing(boolean value){if(!value)cancelInteraction();editing=value;invalidate();}
    public boolean editing(){return editing;}
    public boolean isDragging(){return dragging>=0;}
    public boolean isManipulating(){return dragging>=0||nudgeHeld;}
    public boolean boundaryLimited(){return limited;}
    public boolean selectedOnBoundary(){return editGeometry!=null&&editGeometry.onBoundary(selectedPosition());}
    public Position selectedPosition(){int index=sourceIndex(selected);return points.size()>index?points.get(index):null;}
    public void setCorners(List<Point2> source){cancelInteraction();points.clear();for(Point2 p:source)points.add(p==null?null:new Position(p.x,p.y));invalidate();}
    public List<Point2> corners(){List<Point2> copy=new ArrayList<>();for(Position p:points)copy.add(p==null?null:p.toPoint());return copy;}
    public List<Position> draftPositions(){return new ArrayList<>(points);}
    public void restoreDraft(List<Position> source,int selected){cancelInteraction();points.clear();points.addAll(source);this.selected=selected;invalidate();}
    public void setStaleOutline(boolean stale){if(staleOutline!=stale){staleOutline=stale;invalidate();}}
    public int placedCount(){int count=0;for(Position p:points)if(p!=null)count++;return count;}
    public int selectedCorner(){return selected;}
    public void selectCorner(int visual){cancelInteraction();selected=Math.max(0,Math.min(3,visual));invalidate();changed.run();}
    private int sourceIndex(int visual){return geometry!=null&&geometry.mirrored?new int[]{1,0,3,2}[visual]:visual;}
    private int visualIndex(int source){return geometry!=null&&geometry.mirrored?new int[]{1,0,3,2}[source]:source;}
    public boolean cornerPlaced(int visual){int i=sourceIndex(visual);return points.size()>i&&points.get(i)!=null;}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private PointF map(Position p){double[] v=geometry.normalizedToView(p.x,p.y);return new PointF((float)v[0],(float)v[1]);}
    public HudProtectedArea hudProtectedArea(){
        HudProtectedArea result=new HudProtectedArea();
        if(geometry==null||points.isEmpty())return result;
        PointF first=null,previous=null;
        for(Position p:points){
            if(p==null)continue;
            PointF point=map(p);
            result.add(new RectF(point.x-dp(5),point.y-dp(5),point.x+dp(5),point.y+dp(5)));
            if(previous!=null)result.edge(previous.x,previous.y,point.x,point.y);
            if(first==null)first=point;previous=point;
        }
        if(points.size()==4&&first!=null&&previous!=null)result.edge(previous.x,previous.y,first.x,first.y);
        return result;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(geometry==null||points.isEmpty())return;
        boolean valid=TvSetupCalibration.validation(corners(),geometry,getResources().getDisplayMetrics().density)==null;
        int warning=0xffffbf5b,color=!editing&&staleOutline?warning:0xff32d2e4;
        int save=canvas.save();double[] usable=geometry.usableViewRect();
        canvas.clipRect((float)usable[0],(float)usable[1],(float)usable[2],(float)usable[3]);
        Path path=new Path();boolean first=true;int count=0;
        for(Position p:points){if(p==null)continue;PointF q=map(p);if(first){path.moveTo(q.x,q.y);first=false;}else path.lineTo(q.x,q.y);count++;}
        if(count==4)path.close();
        if(valid&&editing){paint.setColor(color);paint.setAlpha(14);paint.setStyle(Paint.Style.FILL);canvas.drawPath(path,paint);}
        paint.setColor(color);paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(editing?2:1.5f));
        paint.setPathEffect(!editing&&staleOutline?new DashPathEffect(new float[]{dp(6),dp(4)},0):null);canvas.drawPath(path,paint);paint.setPathEffect(null);
        canvas.restoreToCount(save); // Handles may extend into letterbox, never their source coordinates.
        String[] labels={"LG","PG","PD","LD"};
        for(int i=0;i<points.size();i++){
            Position p=points.get(i);if(p==null)continue;PointF q=map(p);boolean active=editing&&visualIndex(i)==selected;
            if(!editing){paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawCircle(q.x,q.y,dp(3),paint);continue;}
            int pointColor=active&&limited?warning:color;
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xdd080d15);canvas.drawCircle(q.x,q.y,dp(active?14:10),paint);
            paint.setColor(pointColor);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(active?3:2));canvas.drawCircle(q.x,q.y,dp(active?12:8),paint);
            paint.setStrokeWidth(dp(1));if(active){canvas.drawLine(q.x-dp(6),q.y,q.x+dp(6),q.y,paint);canvas.drawLine(q.x,q.y-dp(6),q.x,q.y+dp(6),paint);}
            paint.setStyle(Paint.Style.FILL);canvas.drawCircle(q.x,q.y,dp(2),paint);
            paint.setTextSize(dp(14));paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            float labelW=paint.measureText(labels[visualIndex(i)])+dp(16),labelH=dp(28);
            int visual=visualIndex(i);boolean leftSide=visual==0||visual==3;
            float desiredLeft=leftSide?q.x-labelW-dp(4):q.x+dp(4);
            float left=Math.max(0,Math.min(getWidth()-labelW,desiredLeft)),top=Math.max(0,Math.min(getHeight()-labelH,q.y-dp(34)));
            paint.setColor(0xee101924);canvas.drawRoundRect(new RectF(left,top,left+labelW,top+labelH),dp(6),dp(6),paint);
            paint.setColor(Color.WHITE);canvas.drawText(labels[visualIndex(i)],left+dp(8),top+dp(19),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!editing||editGeometry==null||!allowed.getAsBoolean()){if(isManipulating()){cancelInteraction();changed.run();}return false;}
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancelInteraction();changed.run();return true;}
        if(action==MotionEvent.ACTION_DOWN){
            cancelInteraction();float best=dp(24);int nearest=-1;
            for(int i=0;i<points.size();i++){Position p=points.get(i);if(p==null)continue;PointF q=map(p);
                float distance=(float)Math.hypot(q.x-event.getX(),q.y-event.getY());if(distance<best||distance==best&&visualIndex(i)==selected){best=distance;nearest=i;}}
            if(nearest<0&&!editGeometry.containsView(event.getX(),event.getY()))return false;
            while(points.size()<4)points.add(null);
            dragging=nearest>=0?nearest:sourceIndex(selected);selected=visualIndex(dragging);
            dragStart=points.get(dragging);pointer=event.getPointerId(0);hapticSent=false;
            if(nearest>=0){double[] q=geometry.normalizedToView(dragStart.x,dragStart.y);grabX=q[0]-event.getX();grabY=q[1]-event.getY();}else{grabX=grabY=0;}
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
        }
        if(dragging<0)return false;
        int index=event.findPointerIndex(pointer);
        if(index<0){cancelInteraction();changed.run();return true;}
        try{applyMove(dragging,editGeometry.constrain(event.getX(index)+grabX,event.getY(index)+grabY));}
        catch(IllegalArgumentException invalid){cancelInteraction();changed.run();return true;}
        if(action==MotionEvent.ACTION_UP){
            dragging=-1;pointer=-1;dragStart=null;limited=false;dismissMagnifier();releaseTouch();
            for(int i=0;i<4;i++)if(!cornerPlaced(i)){selected=i;break;}
            performClick();
        }else{PointF p=map(points.get(dragging));showMagnifier(p.x,p.y);}
        changed.run();return true;
    }
    public boolean startNudge(int dx,int dy){
        if(!editing||editGeometry==null||!allowed.getAsBoolean()||isManipulating()||selectedPosition()==null)return false;
        nudgeHeld=true;nudgeX=dx;nudgeY=dy;nudgeIndex=sourceIndex(selected);nudgeStart=points.get(nudgeIndex);hapticSent=false;
        applyNudge();if(nudgeHeld)postDelayed(repeatNudge,400);return nudgeHeld;
    }
    public void stopNudge(boolean cancel){
        if(!nudgeHeld)return;removeCallbacks(repeatNudge);
        if(cancel&&nudgeStart!=null)points.set(nudgeIndex,nudgeStart);
        nudgeHeld=false;nudgeStart=null;nudgeIndex=-1;limited=false;dismissMagnifier();releaseTouch();invalidate();changed.run();
    }
    public void nudgeSelected(int dx,int dy){if(startNudge(dx,dy))stopNudge(false);}
    private void applyNudge(){
        try{applyMove(nudgeIndex,editGeometry.nudge(points.get(nudgeIndex),nudgeX,nudgeY));}
        catch(IllegalArgumentException invalid){stopNudge(true);return;}
        PointF p=map(points.get(nudgeIndex));showMagnifier(p.x,p.y);changed.run();
    }
    private void applyMove(int index,Move move){
        points.set(index,move.point);limited=move.limited;
        if(limited&&!hapticSent){hapticSent=true;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
        invalidate();
    }
    private void cancelInteraction(){
        if(dragging>=0&&points.size()>dragging)points.set(dragging,dragStart);
        if(nudgeHeld&&nudgeStart!=null&&points.size()>nudgeIndex)points.set(nudgeIndex,nudgeStart);
        removeCallbacks(repeatNudge);dragging=-1;pointer=-1;nudgeHeld=false;nudgeIndex=-1;dragStart=nudgeStart=null;limited=false;
        dismissMagnifier();releaseTouch();invalidate();
    }
    private void releaseTouch(){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
    private void showMagnifier(float x,float y){
        try{if(preview==null||geometry==null)return;
            if(magnifier==null)magnifier=new TvCalibrationMagnifier(this,preview);
            magnifier.show(geometry,x,y);
        }catch(RuntimeException unavailable){dismissMagnifier();}
    }
    private void dismissMagnifier(){if(magnifier!=null)magnifier.dismiss();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)cancelInteraction();}
    @Override protected void onDetachedFromWindow(){cancelInteraction();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
}
