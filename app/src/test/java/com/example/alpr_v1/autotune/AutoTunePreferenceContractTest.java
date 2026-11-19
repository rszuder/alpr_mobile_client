package com.example.alpr_v1.autotune;

import android.app.Application;
import com.example.alpr_v1.inference.RuntimeEvidenceFixture;
import com.example.alpr_v1.model.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.File;
import static org.junit.Assert.*;

/** Regression for the existing ordinary-mode selection order after exposing immutable read-only inputs. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class AutoTunePreferenceContractTest {
    InstalledModel model;
    @Before public void model()throws Exception{
        InstalledModel original=RuntimeEvidenceFixture.model(new File(RuntimeEnvironment.getApplication().getFilesDir(),"TEST_ONLY_VARIANTS"),"fp32","FLOAT32",null);
        JSONObject json=new JSONObject(original.manifest().rawJson());JSONObject first=json.getJSONArray("variants").getJSONObject(0);
        JSONObject second=new JSONObject(first.toString()).put("id","measured").put("runtime","onnx");
        json.getJSONArray("variants").put(second);model=new InstalledModel(ModelManifest.parse(json.toString()),original.directory(),original.fingerprint());
    }
    @Test public void manualPinPrecedesTheMeasuredWinner(){
        assertEquals("selected",new AutoTuneManager.VariantSelection("selected","measured").choose(model,r->true).id());
    }
    @Test public void staleOrUnavailableOrdinaryPinFallsBackToMeasuredWinner(){
        assertEquals("measured",new AutoTuneManager.VariantSelection("removed","measured").choose(model,r->true).id());
        assertEquals("measured",new AutoTuneManager.VariantSelection("selected","measured").choose(model,r->r==ModelRuntime.ONNX).id());
    }
    @Test public void unavailableWinnerUsesTheOriginalDeterministicFallback(){
        assertEquals("selected",new AutoTuneManager.VariantSelection("","measured").choose(model,r->r==ModelRuntime.TFLITE).id());
        assertEquals("selected",new AutoTuneManager.VariantSelection("","removed").choose(model,r->true).id());
        assertThrows(IllegalStateException.class,()->new AutoTuneManager.VariantSelection("","measured").choose(model,r->false));
    }
}
