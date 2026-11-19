package com.example.alpr_v1.pipeline;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.vision.*;
import java.io.File;
import java.nio.*;
import java.nio.file.Files;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MpDiagnosticCaptureTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final ContinuityStamp SOURCE=new ContinuityStamp(2,3,0,9,1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
    private static final TensorInfo INPUT=new TensorInfo(0,new int[]{1,3,4,4},"FLOAT32",192,0,0);
    private static final ModelInputSpec SPEC=new ModelInputSpec(4,4,3,"NCHW","RGB","FLOAT32",1f/255f,0);
    private Bitmap image(){Bitmap b=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);b.setPixel(0,0,Color.RED);return b;}
    private byte[] all(ByteBuffer b){ByteBuffer copy=b.asReadOnlyBuffer();copy.clear();byte[] values=new byte[copy.remaining()];copy.get(values);return values;}

    @Test public void capturesOwnedBytesBeforeBackendAndDecoderWithoutChangingTheirCursors()throws Exception {
        File root=temp.newFolder();MpDiagnosticCapture request=new MpDiagnosticCapture(root,"one-frame");Bitmap source=image();
        MpDiagnosticCapture.Session capture=request.begin(source,7,SOURCE,new JSONObject().put("runtime","SYNTHETIC_NOT_NEURAL"));
        assertNotNull(capture);
        PreparedInput input=BitmapTensorPreprocessor.prepare(source,SPEC,INPUT,capture::letterboxed);
        byte[] expected=all(input.buffer);input.buffer.position(4);input.buffer.limit(188);
        capture.input(input,INPUT);
        assertEquals(4,input.buffer.position());assertEquals(188,input.buffer.limit());
        input.buffer.putFloat(0,1234f); // A backend can mutate its borrowed input after the snapshot.
        ByteBuffer output=ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
        output.putFloat(1.25f).putFloat(Float.NaN);output.position(4);output.limit(4);
        capture.output(new InferenceRunResult(Collections.singletonMap(0,output),Collections.singletonMap(0,
                new TensorInfo(0,new int[]{1,2},"FLOAT32",8,0,0))));
        assertEquals(4,output.position());assertEquals(4,output.limit());output.putFloat(0,99f);
        capture.complete(0,0);capture.close();capture.close();
        File folder=new File(root,"mp-diagnostics/one-frame");
        assertArrayEquals(expected,Files.readAllBytes(new File(folder,"input.bin").toPath()));
        assertEquals(1.25f,ByteBuffer.wrap(Files.readAllBytes(new File(folder,"output-0.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN).getFloat(),0);
        JSONObject metadata=new JSONObject(new String(Files.readAllBytes(new File(folder,"capture.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("COMPLETE",metadata.getString("status"));assertEquals(9,metadata.getLong("source_sequence"));
        JSONObject tensor=metadata.getJSONObject("files").getJSONObject("output-0.bin");
        assertEquals("LITTLE_ENDIAN",tensor.getString("byte_order"));
        assertEquals(1,tensor.getJSONObject("float_statistics").getLong("nan_count"));
        Bitmap decoded=BitmapFactory.decodeFile(new File(folder,"source.png").getAbsolutePath());
        assertEquals(Color.RED,decoded.getPixel(0,0));decoded.recycle();
        assertFalse(source.isRecycled());assertNull(request.begin(source,8,SOURCE,new JSONObject()));source.recycle();
    }
    @Test public void quotaFailureIsNonfatalAndCannotPublishACompleteBundle()throws Exception {
        File root=temp.newFolder();Bitmap source=image();
        MpDiagnosticCapture.Session capture=new MpDiagnosticCapture(root,"small-budget",1).begin(source,1,SOURCE,new JSONObject());
        assertNotNull(capture);
        PreparedInput input=BitmapTensorPreprocessor.prepare(source,SPEC,INPUT,capture::letterboxed);
        capture.input(input,INPUT);capture.complete(0,0);capture.close();
        JSONObject metadata=new JSONObject(new String(Files.readAllBytes(new File(root,"mp-diagnostics/small-budget/capture.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("FAILED",metadata.getString("status"));assertFalse(source.isRecycled());source.recycle();
    }
    @Test public void diagnosticRequestCannotOverwriteAnExistingBundleOrLeaveItsRoot()throws Exception {
        File root=temp.newFolder(),folder=new File(root,"mp-diagnostics/existing");assertTrue(folder.mkdirs());
        File sentinel=new File(folder,"keep.txt");Files.write(sentinel.toPath(),new byte[]{1,2,3});Bitmap source=image();
        assertNull(new MpDiagnosticCapture(root,"existing").begin(source,1,SOURCE,new JSONObject()));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(sentinel.toPath()));assertEquals(1,folder.list().length);
        for(String id:new String[]{"../outside","", "a/b"})try{new MpDiagnosticCapture(root,id);fail(id);}catch(IllegalArgumentException expected){}
        source.recycle();
    }
}
