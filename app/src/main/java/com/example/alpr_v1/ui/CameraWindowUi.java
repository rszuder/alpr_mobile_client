package com.example.alpr_v1.ui;

import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.alpr_v1.R;

/** Shared fullscreen policy. Transient system bars overlay the content without resizing it. */
public final class CameraWindowUi {
    private CameraWindowUi() {}

    public enum ContentPolicy { SAFE_CONTENT, MAIN_FULL_BLEED }

    public static void install(Window window, View root) {
        install(window,root,ContentPolicy.SAFE_CONTENT);
    }
    public static void install(Window window, View root,ContentPolicy policy) {
        keepImmersive(window);
        WindowCompat.setDecorFitsSystemWindows(window,false);
        window.getAttributes().layoutInDisplayCutoutMode=android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(window.getAttributes());
        int[] original=(int[])root.getTag(R.id.window_content_padding);
        if(original==null){original=new int[]{root.getPaddingLeft(),root.getPaddingTop(),root.getPaddingRight(),root.getPaddingBottom()};root.setTag(R.id.window_content_padding,original);}
        Binding binding=(Binding)root.getTag(R.id.window_insets_binding);
        if(binding==null){
            binding=new Binding(root,original,policy);root.setTag(R.id.window_insets_binding,binding);
            Binding owner=binding;root.getViewTreeObserver().addOnGlobalLayoutListener(owner::protectToolbarTitle);
        }
        binding.policy=policy;binding.main=policy==ContentPolicy.MAIN_FULL_BLEED;
        if(binding.main){
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
        }
        final Binding owner=binding;
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            owner.insets=insets;owner.apply();
            boolean visible=insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom=insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            ImeState state=new ImeState(visible,imeBottom);view.setTag(R.id.tv_ime_state,state);
            Object observer=view.getTag(R.id.tv_ime_observer);
            if(observer instanceof ImeListener)((ImeListener)observer).changed(state);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Main alone fills the display. Editors keep their existing keyboard/cutout safety. */
    public static void setMainEditorOpen(View root,boolean editing){
        Binding owner=(Binding)root.getTag(R.id.window_insets_binding);
        if(owner==null||!owner.main)return;
        ContentPolicy next=editing?ContentPolicy.SAFE_CONTENT:ContentPolicy.MAIN_FULL_BLEED;
        if(owner.policy==next)return;owner.policy=next;owner.apply();
    }
    private static final class Binding {
        final View root;final int[] base;
        ContentPolicy policy;boolean main;
        WindowInsetsCompat insets;
        Binding(View root,int[] base,ContentPolicy policy){this.root=root;this.base=base;this.policy=policy;}
        void apply(){
            if(insets==null)return;
            Insets safe=policy==ContentPolicy.SAFE_CONTENT?insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.displayCutout()|WindowInsetsCompat.Type.mandatorySystemGestures()):Insets.NONE;
            padding(root,base[0]+safe.left,base[1]+safe.top,base[2]+safe.right,base[3]+safe.bottom);
            if(main){
                // Only real hardware cutouts constrain controls. Hidden navigation bars reserve no strip.
                Insets cut=policy==ContentPolicy.MAIN_FULL_BLEED?insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()):Insets.NONE;
                local(root.findViewById(R.id.top_app_bar),cut.left,cut.right,0);
                local(root.findViewById(R.id.normal_camera_hud),cut.left,cut.right,0);
                local(root.findViewById(R.id.control_panel),cut.left,cut.right,cut.bottom);
                protectToolbarTitle();
            }
        }
        void protectToolbarTitle(){
            if(!main)return;
            com.google.android.material.appbar.MaterialToolbar toolbar=root.findViewById(R.id.top_app_bar);
            if(toolbar==null)return;
            for(int i=0;i<toolbar.getChildCount();i++){
                View child=toolbar.getChildAt(i);
                if(!(child instanceof android.widget.TextView)||!java.util.Objects.equals(((android.widget.TextView)child).getText(),toolbar.getTitle()))continue;
                android.widget.TextView title=(android.widget.TextView)child;
                Integer original=(Integer)title.getTag(R.id.window_toolbar_title_width);
                if(original==null){original=title.getMaxWidth();title.setTag(R.id.window_toolbar_title_width,original);}
                int width=original;
                if(policy==ContentPolicy.MAIN_FULL_BLEED&&insets!=null&&insets.getDisplayCutout()!=null&&title.getWidth()>0){
                    int[] at=new int[2];title.getLocationInWindow(at);
                    int margin=Math.round(8*root.getResources().getDisplayMetrics().density);
                    for(android.graphics.Rect cut:insets.getDisplayCutout().getBoundingRects())
                        if(cut.top<at[1]+title.getHeight()&&cut.bottom>at[1]&&cut.left>at[0])width=Math.min(width,Math.max(0,cut.left-at[0]-margin));
                }
                if(title.getMaxWidth()!=width)title.setMaxWidth(width);
            }
        }
        static void local(View view,int left,int right,int bottom){
            if(view==null)return;
            int[] p=(int[])view.getTag(R.id.window_child_padding);
            if(p==null){p=new int[]{view.getPaddingLeft(),view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom(),view.getPaddingBottom()};view.setTag(R.id.window_child_padding,p);}
            int b=view.getPaddingBottom();
            if(view.getId()==R.id.control_panel){if(b!=p[4])p[3]=b;b=p[3]+bottom;p[4]=b;}
            padding(view,p[0]+left,view.getPaddingTop(),p[2]+right,b);
        }
        static void padding(View v,int l,int t,int r,int b){
            if(v.getPaddingLeft()!=l||v.getPaddingTop()!=t||v.getPaddingRight()!=r||v.getPaddingBottom()!=b)v.setPadding(l,t,r,b);
        }
    }

    public static final class ImeState {
        public final boolean visible;
        public final int bottom;
        ImeState(boolean visible,int bottom){this.visible=visible;this.bottom=Math.max(0,bottom);}
    }
    public interface ImeListener {void changed(ImeState state);}
    /** Shares the installed listener: calculator code never replaces the system-bar owner. */
    public static void observeIme(View root,ImeListener listener){
        root.setTag(R.id.tv_ime_observer,listener);
        Object state=root.getTag(R.id.tv_ime_state);
        if(state instanceof ImeState)listener.changed((ImeState)state);
        ViewCompat.requestApplyInsets(root);
    }

    /** One hook per decor; no polling and no fight with the user's transient swipe gesture. */
    public static void keepImmersive(Window window) {
        if(window==null)return;
        View decor=window.getDecorView();
        if(!Boolean.TRUE.equals(decor.getTag(R.id.window_immersive_bound))){
            decor.setTag(R.id.window_immersive_bound,true);
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(focus->{if(focus)hideBars(window);});
            decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                public void onViewAttachedToWindow(View view){hideBars(window);}
                public void onViewDetachedFromWindow(View view){}
            });
        }
        hideBars(window);
    }
    private static void hideBars(Window window){
        WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(window,window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
