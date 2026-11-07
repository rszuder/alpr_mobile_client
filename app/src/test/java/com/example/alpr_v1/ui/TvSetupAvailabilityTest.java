package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.view.ContextThemeWrapper;
import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.google.android.material.appbar.MaterialToolbar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32)
public class TvSetupAvailabilityTest {
    private Context context(){return new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);}
    @Test public void normalModeAndF5aAreAvailableWhileOtherExperimentsKeepExistingGate(){
        for(String type:new String[]{"roi_budget","runtime","consensus","continuity","end_to_end"})assertTrue(TvSetupAvailability.available(false,type));
        assertTrue(TvSetupAvailability.available(true,"roi_budget"));assertFalse(TvSetupAvailability.available(true,"runtime"));assertFalse(TvSetupAvailability.available(true,"consensus"));
    }
    @Test public void toolbarExposesToolsWithoutExperimentButHonorsBusyGuard(){
        MaterialToolbar bar=new MaterialToolbar(context());bar.inflateMenu(R.menu.main_menu);
        TvSetupMenuState.render(bar.getMenu(),TvSetupAvailability.available(false,"runtime"),true,null,false);
        for(int id:new int[]{R.id.menu_tv_calibrate,R.id.menu_tv_distance,R.id.menu_tv_outline})assertTrue(bar.getMenu().findItem(id).isVisible());
        TvSetupMenuState.render(bar.getMenu(),true,false,null,false);assertFalse(bar.getMenu().findItem(R.id.menu_tv_calibrate).isEnabled());
        TvSetupMenuState.render(bar.getMenu(),TvSetupAvailability.available(true,"runtime"),true,null,false);assertFalse(bar.getMenu().findItem(R.id.menu_tv_calibrate).isVisible());
    }
    @Test public void existingSettingsActionsLiveOutsideHiddenExperimentOptions(){
        View root=LayoutInflater.from(context()).inflate(R.layout.activity_settings,null,false);
        View options=root.findViewById(R.id.settings_roi_experiment_options);options.setVisibility(View.GONE);
        View card=root.findViewById(R.id.settings_tv_tools);assertNotNull(card);assertEquals(View.VISIBLE,card.getVisibility());
        for(int id:new int[]{R.id.settings_tv_calibrate,R.id.settings_tv_distance}){
            View button=root.findViewById(id);assertNotNull(button);View parent=button;boolean inCard=false;
            while(parent!=null){assertNotSame(options,parent);if(parent==card)inCard=true;parent=parent.getParent() instanceof View?(View)parent.getParent():null;}
            assertTrue(inCard);
        }
    }
    @Test public void navigationDoesNotEnableExperimentOrTouchConfirmedCalibration(){
        SharedPreferences prefs=context().getSharedPreferences("tv-navigation-test",0);prefs.edit().clear().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).putString("tv_setup_calibration_v1","preserve").commit();
        TvSetupNavigation.request(prefs,TvSetupController.Mode.CALIBRATION);assertEquals(TvSetupController.Mode.CALIBRATION,TvSetupNavigation.requestedMode(prefs.getString(TvSetupController.ACTION,"")));
        assertFalse(prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true));assertEquals("preserve",prefs.getString("tv_setup_calibration_v1",""));
    }
}
