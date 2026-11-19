package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.alpr_v1.R;
import com.google.android.material.appbar.MaterialToolbar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class MainViewfinderInsetsTest {
    public static class Harness extends AppCompatActivity {
        @Override public void onCreate(Bundle saved){setTheme(R.style.Theme_ALPR_v1);super.onCreate(saved);
            setContentView(R.layout.activity_main);LivePresentationController.configureLayout(findViewById(R.id.main));
            CameraWindowUi.install(getWindow(),findViewById(R.id.main),CameraWindowUi.ContentPolicy.MAIN_FULL_BLEED);}
    }
    static WindowInsetsCompat insets(boolean visible){
        android.view.WindowInsets platform=new android.view.WindowInsets.Builder()
            .setDisplayCutout(new android.view.DisplayCutout(new Rect(0,68,0,0),Collections.singletonList(new Rect(330,0,390,68))))
            .setSystemWindowInsets(android.graphics.Insets.of(0,48,0,90))
            .setStableInsets(android.graphics.Insets.of(0,48,0,90))
            .setMandatorySystemGestureInsets(android.graphics.Insets.of(0,0,0,90)).build();
        return new WindowInsetsCompat.Builder(WindowInsetsCompat.toWindowInsetsCompat(platform))
            .setInsets(WindowInsetsCompat.Type.systemBars(),visible?Insets.of(0,48,0,90):Insets.NONE)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(),Insets.of(0,48,0,90))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(0,68,0,0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.mandatorySystemGestures(),Insets.of(0,0,0,90))
            .setVisible(WindowInsetsCompat.Type.systemBars(),visible).build();
    }
    static void layout(View root){for(int i=0;i<3;i++){
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));
        root.layout(0,0,720,1600);root.getViewTreeObserver().dispatchOnGlobalLayout();}}
    static Rect bounds(View root,View view){Rect r=new Rect();view.getDrawingRect(r);((ViewGroup)root).offsetDescendantRectToMyCoords(view,r);return r;}
    static void image(View root,String name)throws Exception{
        String target=System.getenv("MAIN_VIEWFINDER_UI_ARTIFACTS");if(target==null)return;
        File folder=new File(target);folder.mkdirs();Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    void check(float font)throws Exception{
        RuntimeEnvironment.setFontScale(font);
        ActivityController<Harness> activity=Robolectric.buildActivity(Harness.class);
        // Window coordinates require attachment. API 29's simulated decor otherwise
        // overwrites injected cutout insets with its synthetic empty platform insets.
        if(android.os.Build.VERSION.SDK_INT>=30)activity.setup().visible();else activity.create();
        try{
            Harness a=activity.get();View root=a.findViewById(R.id.main);
            assertEquals("Synthetic cutout",68,insets(false).getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()).top);
            ViewCompat.dispatchApplyWindowInsets(root,insets(false));layout(root);
            assertEquals(0,root.getPaddingTop());assertEquals(0,root.getPaddingBottom());
            assertEquals(0,bounds(root,a.findViewById(R.id.top_app_bar)).top);
            assertEquals(1600,bounds(root,a.findViewById(R.id.control_panel)).bottom);
            MaterialToolbar bar=a.findViewById(R.id.top_app_bar);boolean found=false;
            for(int i=0;i<bar.getChildCount();i++)if(bar.getChildAt(i) instanceof TextView){TextView t=(TextView)bar.getChildAt(i);
                if(t.getText().equals(bar.getTitle())){found=true;assertTrue(bounds(root,t).right<=314);}}
            assertTrue("Toolbar title protected from center cutout",found);
            Rect camera=bounds(root,a.findViewById(R.id.camera_preview));
            ViewCompat.dispatchApplyWindowInsets(root,insets(true));layout(root);assertEquals(camera,bounds(root,a.findViewById(R.id.camera_preview)));
            ViewCompat.dispatchApplyWindowInsets(root,insets(false));layout(root);assertEquals(camera,bounds(root,a.findViewById(R.id.camera_preview)));
            image(root,"main-full-height-font-"+font+".png");
            CameraWindowUi.setMainEditorOpen(root,true);layout(root);assertEquals(68,root.getPaddingTop());assertEquals(90,root.getPaddingBottom());
            CameraWindowUi.setMainEditorOpen(root,false);layout(root);assertEquals(0,root.getPaddingTop());assertEquals(0,root.getPaddingBottom());assertEquals(camera,bounds(root,a.findViewById(R.id.camera_preview)));
        }finally{activity.pause().stop().destroy();RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void mainFillsDisplayAndTransientBarsDoNotMoveThePreview()throws Exception{check(1f);}
    @Test public void largeFontsKeepFullHeightAndCutoutProtection()throws Exception{check(1.5f);}
    @Test @Config(sdk=29) public void androidTenUsesTheSameMainPolicy()throws Exception{check(1f);}
    @Test @Config(sdk=36) public void androidSixteenUsesTheSameMainPolicy()throws Exception{check(1f);}
}
