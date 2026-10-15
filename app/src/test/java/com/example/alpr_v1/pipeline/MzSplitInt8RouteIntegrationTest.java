package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.inference.InferenceRunResult;
import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.vision.Detection;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Focused synthetic routing checks. NOT a LiteRT/Android runtime validation. */
public final class MzSplitInt8RouteIntegrationTest {
    private static final float GS = 0.006233580876141787f;
    private static final float SS = 0.00390625f;

    private static ModelOutputSpec spec(String format, String decoder) throws Exception {
        Constructor<ModelOutputSpec> ctor = ModelOutputSpec.class.getDeclaredConstructor(
                String.class, String.class, String.class,
                int.class, int.class, int.class,
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                float.class, float.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(decoder,format,"ltrb",36,0,0,false,true,true,false,false,.25f,.45f,4,5);
    }
    private static ModelOutputSpec split() throws Exception {
        return spec("raw_split_ltrb_scores_v1","ultralytics_detect_split_ltrb_scores_int8_v1");
    }
    private static ModelInputSpec input() {
        return new ModelInputSpec(320,320,3,"NHWC","RGB","INT8",1f/255f,0f);
    }
    private static InferenceRunResult run(boolean includeScores, boolean badShape) {
        ByteBuffer boxes=ByteBuffer.allocateDirect(8400);
        ByteBuffer scores=ByteBuffer.allocateDirect(75600);
        for(int i=0;i<8400;i++) boxes.put((byte)-110);
        for(int i=0;i<75600;i++) scores.put((byte)-128);
        // Confidence (q - zp)*scale = 38/256 = 0.1484375: the .10 recall floor must survive.
        scores.put(0,(byte)-90);
        boxes.rewind();scores.rewind();
        Map<Integer,ByteBuffer> buffers=new LinkedHashMap<>();
        Map<Integer,TensorInfo> info=new LinkedHashMap<>();
        buffers.put(0,boxes);
        info.put(0,new TensorInfo(0,badShape?new int[]{1,2100,4}:new int[]{1,4,2100},"INT8",8400,GS,-126));
        if(includeScores){buffers.put(1,scores);info.put(1,new TensorInfo(1,new int[]{1,36,2100},"INT8",75600,SS,-128));}
        return new InferenceRunResult(buffers,info);
    }
    @SuppressWarnings("unchecked")
    private static List<Detection> dispatch(InferenceRunResult run,ModelOutputSpec spec,float floor) throws Exception {
        Method m=MobileAlprEngine.class.getDeclaredMethod("decodeSplitMZ",InferenceRunResult.class,ModelInputSpec.class,ModelOutputSpec.class,float.class);
        m.setAccessible(true);
        try {return (List<Detection>)m.invoke(null,run,input(),spec,floor);}
        catch(InvocationTargetException ex){throw (ex.getCause() instanceof RuntimeException)?(RuntimeException)ex.getCause():ex;}
    }
    @Test public void routesTwoPortsAndRetainsPointTenRecoveryCandidate() throws Exception {
        List<Detection> found=dispatch(run(true,false),split(),.10f);
        assertEquals(1,found.size());
        assertEquals(0,found.get(0).classId);
        assertEquals(38f/256f,found.get(0).confidence,1e-5f);
    }
    @Test public void normalPointTwentyFiveThresholdExcludesRecoveryOnlyCandidate() throws Exception {
        assertEquals(0,dispatch(run(true,false),split(),.25f).size());
    }
    @Test public void missingSecondPortFailsClosed() throws Exception {
        try {dispatch(run(false,false),split(),.10f);fail("Missing port 1 accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("TWO_PORTS_REQUIRED"));}
    }
    @Test public void transposedGeometryFailsClosed() throws Exception {
        try {dispatch(run(true,true),split(),.10f);fail("Transposed geometry accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("LTRB"));}
    }
    @Test public void legacyFormatCannotEnterSplitDispatcher() throws Exception {
        try {dispatch(run(true,false),spec("raw_yolo","ultralytics_detect_raw_v1"),.10f);fail("Legacy accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("DISPATCH_CONTRACT_MISMATCH"));}
    }
    @Test public void characterRoleAllowsSplitButRejectsItAsPlate() throws Exception {
        Method character=MobileAlprEngine.class.getDeclaredMethod("validateCharacterDecoder",ModelOutputSpec.class);
        Method plate=MobileAlprEngine.class.getDeclaredMethod("validateDecoder",ModelOutputSpec.class);
        character.setAccessible(true);plate.setAccessible(true);
        character.invoke(null,split());
        try {plate.invoke(null,split());fail("Split decoder allowed in plate role");}
        catch(InvocationTargetException ex){assertTrue(ex.getCause() instanceof IllegalArgumentException);}
    }
}
