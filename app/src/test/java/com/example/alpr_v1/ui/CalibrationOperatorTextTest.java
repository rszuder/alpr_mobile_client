package com.example.alpr_v1.ui;

import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.Final180PhotoRoi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32)
public class CalibrationOperatorTextTest {
    @Test public void f6DiagnosticsGetOperatorCopyWithoutChangingTheirCodes(){
        android.content.Context c=RuntimeEnvironment.getApplication();
        for(String raw:new String[]{"F6_PHOTO_ROI_REQUIRED_OR_STALE","F6_PHOTO_ROI_REQUIRED: previous diagnostic","F6_PHOTO_ROI_INVALID"}){
            assertEquals(c.getString(R.string.tv_precision_f6_required),CalibrationOperatorText.preparationError(c,raw));
            assertTrue(raw.startsWith("F6_PHOTO_ROI_"));
        }
        assertEquals("BACKEND_FAILURE",CalibrationOperatorText.preparationError(c,"BACKEND_FAILURE"));
        assertEquals("BACKEND_FAILURE",CalibrationOperatorText.preparationMessage(c,"BACKEND_FAILURE"));
        assertEquals("",CalibrationOperatorText.preparationDetail(c,"BACKEND_FAILURE"));
    }
    @Test public void detailUsesActualPresenceOfSavedDataAndDoesNotGuessAChangedGeometry(){
        android.content.Context c=RuntimeEnvironment.getApplication();
        c.getSharedPreferences("alpr_ui",0).edit().remove(Final180PhotoRoi.PREFERENCE).commit();
        assertEquals(c.getString(R.string.tv_precision_f6_missing_detail),CalibrationOperatorText.preparationDetail(c,"F6_PHOTO_ROI_REQUIRED_OR_STALE"));
        c.getSharedPreferences("alpr_ui",0).edit().putString(Final180PhotoRoi.PREFERENCE,"invalid record").commit();
        assertEquals(c.getString(R.string.tv_precision_f6_check_detail),CalibrationOperatorText.preparationDetail(c,"F6_PHOTO_ROI_REQUIRED_OR_STALE"));
    }
}
