package com.example.alpr_v1.ui;

import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.alpr_v1.R;

/** Stable camera content bounds, including while an overflow popup owns window focus. */
public final class CameraWindowUi {
    private CameraWindowUi() {}

    public static void install(Window window, View root) {
        final int left=root.getPaddingLeft(),top=root.getPaddingTop();
        final int right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        showSystemBars(window);
        WindowCompat.setDecorFitsSystemWindows(window,false);
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            Insets bars=insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            int l=left+bars.left,t=top+bars.top,r=right+bars.right,b=bottom+bars.bottom;
            if(view.getPaddingLeft()!=l||view.getPaddingTop()!=t||view.getPaddingRight()!=r||view.getPaddingBottom()!=b)
                view.setPadding(l,t,r,b);
            boolean visible=insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom=insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            ImeState state=new ImeState(visible,imeBottom);view.setTag(R.id.tv_ime_state,state);
            Object observer=view.getTag(R.id.tv_ime_observer);
            if(observer instanceof ImeListener)((ImeListener)observer).changed(state);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
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

    public static void showSystemBars(Window window) {
        WindowCompat.getInsetsController(window,window.getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
    }
}
