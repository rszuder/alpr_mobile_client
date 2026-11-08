package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Process;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.util.Locale;

/** One 2x3 instrument with stable corner anchors; never sizes or moves the camera. */
public final class LiveHudView extends LinearLayout {
    private static final Locale PL=Locale.forLanguageTag("pl-PL");
    private final View module;
    private final Paint separator=new Paint(Paint.ANTI_ALIAS_FLAG);
    private java.util.function.Supplier<HudProtectedArea> protectedArea=HudProtectedArea::new;
    private boolean occluded;
    private int anchor, placementWidth, placementHeight, placementModuleWidth, placementModuleHeight;
    private long lastUpdate;
    private double lastCpuPercent=Double.NaN;
    private final ProcessCpuUsage cpuUsage=new ProcessCpuUsage();

    public LiveHudView(Context context,AttributeSet attributes){
        super(context,attributes);setClickable(false);
        LayoutInflater.from(context).inflate(R.layout.view_live_hud,this,true);
        module=findViewById(R.id.hud1_module);
    }
    public double cpuPercent(){return lastCpuPercent;}
    public int visibleInstrumentCount(){return module.isShown()&&module.getWidth()>0&&module.getHeight()>0?6:0;}
    public boolean isSpaceBlocked(){return occluded;}
    public void resetPlacement(){anchor=0;occluded=false;placementWidth=placementHeight=0;}
    public void setProtectedAreaSupplier(java.util.function.Supplier<HudProtectedArea> supplier){protectedArea=java.util.Objects.requireNonNull(supplier);}

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        float density=getResources().getDisplayMetrics().density;
        float extra=Math.max(0f,getResources().getConfiguration().fontScale-1f);
        int heightDp=80+4*(int)Math.ceil(extra*12);
        module.measure(MeasureSpec.makeMeasureSpec(Math.round(180*density),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(heightDp*density),MeasureSpec.EXACTLY));
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){placeInstruments();}
    /** Keep a safe corner; only search another after a collision. No animation or new timer. */
    public void placeInstruments(){
        if(getWidth()==0||getHeight()==0)return;
        float density=getResources().getDisplayMetrics().density;
        int margin=Math.round(8*density),w=module.getMeasuredWidth(),h=module.getMeasuredHeight();
        if(placementWidth!=getWidth()||placementHeight!=getHeight()||placementModuleWidth!=w||placementModuleHeight!=h){anchor=0;occluded=false;}
        placementWidth=getWidth();placementHeight=getHeight();placementModuleWidth=w;placementModuleHeight=h;
        int[][] positions={{getWidth()-margin-w,margin},{margin,margin},
                {getWidth()-margin-w,getHeight()-margin-h},{margin,getHeight()-margin-h}};
        HudProtectedArea area=protectedArea.get();
        boolean fits=w>0&&h>0&&getWidth()>=w+2*margin&&getHeight()>=h+2*margin;
        int chosen=-1;
        if(fits){
            if(!area.intersects(instrumentRect(positions[anchor],w,h),(occluded?8:4)*density))chosen=anchor;
            else for(int i=0;i<positions.length;i++){
                boolean duplicate=false;
                for(int j=0;j<i;j++)if(java.util.Arrays.equals(positions[i],positions[j]))duplicate=true;
                if(!duplicate&&!area.intersects(instrumentRect(positions[i],w,h),8*density)){chosen=i;break;}
            }
        }
        occluded=chosen<0;
        if(!occluded)anchor=chosen;
        int x=positions[anchor][0],y=positions[anchor][1];
        module.layout(x,y,x+w,y+h);
        module.setVisibility(occluded?INVISIBLE:VISIBLE);
    }
    private static RectF instrumentRect(int[] position,int width,int height){return new RectF(position[0],position[1],position[0]+width,position[1]+height);}
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        if(module.getVisibility()!=VISIBLE)return;
        separator.setStrokeWidth(getResources().getDisplayMetrics().density);
        separator.setColor(0x9932D2E4);
        float left=module.getLeft(),top=module.getTop(),w=module.getWidth(),h=module.getHeight();
        canvas.drawLine(left+w/3,top+1,left+w/3,top+h-1,separator);
        canvas.drawLine(left+2*w/3,top+1,left+2*w/3,top+h-1,separator);
        canvas.drawLine(left+1,top+h/2,left+w-1,top+h/2,separator);
    }
    public void render(MetricsCollector.LiveSnapshot snapshot,String resolution,boolean awaitingFresh,double cameraFps){
        long now=SystemClock.elapsedRealtime();
        if(lastUpdate!=0L&&now-lastUpdate<1_000L)return;
        lastUpdate=now;
        String fps=!Double.isFinite(cameraFps)||cameraFps<0?"—":number(cameraFps,1);
        text(R.id.hud_camera_fps,fps);
        String fullFps=!Double.isFinite(cameraFps)||cameraFps<0?"—":String.format(PL,"%.1f",cameraFps);
        findViewById(R.id.hud_camera_fps).setContentDescription(getContext().getString(R.string.hud_camera_fps)+": "+fullFps);
        findViewById(R.id.hud_camera_fps).setTooltipText(findViewById(R.id.hud_camera_fps).getContentDescription());
        stage(R.id.hud_mp_time,R.id.hud_mp_caption,R.string.hud_vehicle_stage,"MP",awaitingFresh?Double.NaN:snapshot.vehicleInferenceMs);
        stage(R.id.hud_mt_time,R.id.hud_mt_caption,R.string.hud_plate_stage,"MT",awaitingFresh?Double.NaN:snapshot.plateInferenceMs);
        stage(R.id.hud_mz_time,R.id.hud_mz_caption,R.string.hud_character_stage,"MZ",awaitingFresh?Double.NaN:snapshot.characterInferenceMs);
    }
    /** Same existing thermal poll and CPU sampling; only display units moved into captions. */
    public void renderDeviceMetrics(double batteryTemperatureC){
        String temperature=Double.isFinite(batteryTemperatureC)?String.format(PL,"%.1f°C",batteryTemperatureC):"—";
        double cpu=cpuUsage.sample(Process.getElapsedCpuTime(),SystemClock.uptimeMillis(),Runtime.getRuntime().availableProcessors());
        lastCpuPercent=cpu;
        String resources=Double.isFinite(cpu)?String.format(PL,"%.0f%%",cpu):"—";
        text(R.id.hud_temperature,Double.isFinite(batteryTemperatureC)?number(batteryTemperatureC,1):"—");
        text(R.id.hud_resources,Double.isFinite(cpu)?number(cpu,0):"—");
        findViewById(R.id.hud_temperature).setContentDescription(getContext().getString(R.string.hud_temperature_description,temperature));
        findViewById(R.id.hud_resources).setContentDescription(getContext().getString(R.string.hud_resources_description,resources));
    }
    public void resetDeviceSampling(){cpuUsage.reset();lastCpuPercent=Double.NaN;text(R.id.hud_temperature,"—");text(R.id.hud_resources,"—");}
    public void clearMetrics(){
        lastUpdate=0L;text(R.id.hud_camera_fps,"—");findViewById(R.id.hud_camera_fps).setContentDescription(null);
        stage(R.id.hud_mp_time,R.id.hud_mp_caption,R.string.hud_vehicle_stage,"MP",Double.NaN);
        stage(R.id.hud_mt_time,R.id.hud_mt_caption,R.string.hud_plate_stage,"MT",Double.NaN);
        stage(R.id.hud_mz_time,R.id.hud_mz_caption,R.string.hud_character_stage,"MZ",Double.NaN);
        text(R.id.live_hud,"");
    }
    private void stage(int id,int captionId,int description,String name,double millis){
        String unit="ms",value="—";
        if(Double.isFinite(millis)&&millis>0){
            double amount=millis;int decimals=0;
            if(millis<1)value="<1";
            else{
                if(amount>=1_000){amount/=1_000;unit="s";decimals=2;}
                if(amount>=1_000&&unit.equals("s")){amount/=60;unit="min";}
                if(amount>=1_000&&unit.equals("min")){amount/=60;unit="h";}
                value=number(amount,decimals);
            }
        }
        text(captionId,name+" "+unit);text(id,value);
        String full=duration(millis);
        View field=findViewById(id);
        field.setContentDescription(id==R.id.hud_mp_time?getContext().getString(R.string.cockpit_stage_mp,full)
                :getContext().getString(R.string.cockpit_stage_sum,getContext().getString(description),full));
        field.setTooltipText(field.getContentDescription());
    }
    private void text(int id,String value){TextView view=findViewById(id);if(!value.contentEquals(view.getText()))view.setText(value);}
    private static String number(double value,int decimals){
        String result;
        do{result=String.format(PL,"%."+decimals+"f",value);decimals--;}while(result.length()>5&&decimals>=0);
        if(result.length()<=5)return result;
        result=scientific(value,1);return result.length()<=5?result:scientific(value,0);
    }
    private static String scientific(double value,int decimals){
        return String.format(PL,"%."+decimals+"e",value).replace("e+","e").replaceAll("e(-?)0+(\\d)","e$1$2");
    }
    private static String duration(double millis){
        if(!Double.isFinite(millis)||millis<=0)return "—";
        if(millis<1)return "<1 ms";
        return millis<1_000?String.format(PL,"%.0f ms",millis):String.format(PL,"%.2f s",millis/1_000);
    }
}
