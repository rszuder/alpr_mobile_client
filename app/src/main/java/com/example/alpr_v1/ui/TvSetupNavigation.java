package com.example.alpr_v1.ui;

import android.content.SharedPreferences;
import com.example.alpr_v1.R;

/** Shared routing for the camera toolbar and the existing settings screen. */
public final class TvSetupNavigation {
    private TvSetupNavigation() {}

    public static boolean select(int itemId,TvSetupController setup) {
        if(itemId==R.id.menu_tv_outline){setup.toggleOutline();return true;}
        if(itemId==R.id.menu_tv_calibrate){setup.openAfterMenu(TvSetupController.Mode.CALIBRATION);return true;}
        if(itemId==R.id.menu_tv_distance){setup.openAfterMenu(TvSetupController.Mode.DISTANCE);return true;}
        return false;
    }

    public static void request(SharedPreferences preferences,TvSetupController.Mode mode) {
        preferences.edit().putString(TvSetupController.ACTION,mode==TvSetupController.Mode.DISTANCE?"distance":"calibrate").apply();
    }

    static TvSetupController.Mode requestedMode(String action) {
        return "distance".equals(action)?TvSetupController.Mode.DISTANCE:
                "calibrate".equals(action)?TvSetupController.Mode.CALIBRATION:null;
    }
}
