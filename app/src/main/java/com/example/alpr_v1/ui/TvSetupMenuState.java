package com.example.alpr_v1.ui;

import android.view.Menu;
import android.view.MenuItem;
import com.example.alpr_v1.R;

/** One menu state calculation; steady readiness ticks must not invalidate the popup adapter. */
public final class TvSetupMenuState {
    private TvSetupMenuState(){}
    public static void render(Menu menu,boolean available,boolean editable,TvSetupController.Mode active,boolean restoring){
        for(int i=0;i<menu.size();i++){
            MenuItem item=menu.getItem(i);int id=item.getItemId();boolean outline=id==R.id.menu_tv_outline;
            boolean tv=id==R.id.menu_tv_calibrate||id==R.id.menu_tv_distance;
            if(outline){boolean visible=available&&active==null;if(item.isVisible()!=visible)item.setVisible(visible);continue;}
            if(active==null&&!tv)continue;
            boolean visible=active==null?available:available&&!restoring&&id==(active==TvSetupController.Mode.DISTANCE?R.id.menu_tv_calibrate:R.id.menu_tv_distance);
            if(item.isVisible()!=visible)item.setVisible(visible);
            if(tv&&item.isEnabled()!=editable)item.setEnabled(editable);
        }
    }
}
