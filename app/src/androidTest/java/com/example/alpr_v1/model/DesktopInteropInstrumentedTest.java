package com.example.alpr_v1.model;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.vision.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DesktopInteropInstrumentedTest {
    private Context context;
    private File root;
    @Before public void setup() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(target.getCacheDir(),"interop-models-"+UUID.randomUUID()); root.mkdirs();
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return new File(root,"private"); }
            @Override public File getExternalFilesDir(String type) { return new File(root,"external"); }
            @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences(root.getName()+name,mode);
            }
        };
    }
    private File asset(String name) throws Exception {
        File file = new File(root,name);
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("interop/models/"+name)) {
            Files.copy(in,file.toPath());
        }
        return file;
    }
    @Test public void importsActualDesktopArchivesAndRejectsWrongActionsAndCorruption() throws Exception {
        AlprPackageImporter importer = new AlprPackageImporter(context,new ModelRegistry(context));
        for(String name:new String[]{"plate","character","vehicle","mt-mz","mp-mt-mz"}) {
            boolean complete=name.contains("-"); Uri uri=Uri.fromFile(asset(name+".alprmodel"));
            ModelImportResult result=importer.importPackage(uri,complete ? AlprPackageImporter.ExpectedContent.COMPLETE_PACKAGE
                    : AlprPackageImporter.ExpectedContent.SINGLE_MODEL);
            assertEquals(complete,result.isCompletePackage());
            try {
                importer.importPackage(uri,complete ? AlprPackageImporter.ExpectedContent.SINGLE_MODEL
                        : AlprPackageImporter.ExpectedContent.COMPLETE_PACKAGE);
                fail("Wrong action accepted: "+name);
            } catch(ModelPackageException expected) { }
        }
        for(String name:new String[]{"bad-hash","traversal","missing-file","missing-plate","missing-character","wrong-role","duplicate"}) {
            try { importer.importPackage(Uri.fromFile(asset(name+".alprmodel"))); fail(name); }
            catch(ModelPackageException expected) { }
        }
        assertFalse(new File(root,"outside.txt").exists());
    }
    @Test public void desktopOnnxFp32AndQdqExecuteAndDecodeOnAndroid() throws Exception {
        InstalledModel model=new ModelPackageImporter(context,new File(root,"models")).importPackage(asset("plate.alprmodel"));
        Bitmap source=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888); source.eraseColor(Color.RED);
        try {
            int ran=0;
            for(ModelVariant variant:model.manifest().variants()) {
                if(variant.runtime()!=ModelRuntime.ONNX) continue;
                try(InferenceBackend backend=RuntimeBackendFactory.create(model,variant,new ExecutionProfile(ModelRuntime.ONNX,1,false))) {
                    PreparedInput input=BitmapTensorPreprocessor.prepare(source,variant.input(model.manifest().input()),backend.inputInfo());
                    assertEquals(1,input.scale,0); assertEquals(2,input.padY,0);
                    InferenceRunResult run=backend.run(input.buffer);
                    FloatBuffer buffer=run.outputs().get(0).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    float[] values=new float[buffer.remaining()];buffer.get(values);
                    assertEquals(17,values.length); assertEquals(.9,values[4],1e-6);
                    List<Detection> detections=YoloRawDecoder.decode(values,17,1,new YoloOutputSpec(1,4,false,true,false,8,8,.25f,.45f));
                    assertEquals(1,detections.size());assertEquals(4,detections.get(0).keypoints.size());
                    assertEquals(1,detections.get(0).left,1e-6); ran++;
                }
            }
            assertEquals(2,ran);
        } finally { source.recycle(); }
    }
    @Test public void explicitPreprocessingAndQuantizationContractsCannotBeSilentlyIgnored() throws Exception {
        JSONObject base=new JSONObject().put("width",2).put("height",2).put("layout","NHWC")
                .put("data_type","INT8").put("quantization",new JSONObject().put("scale",.01).put("zero_point",-5));
        ModelInputSpec spec=ModelInputSpec.fromJson(base);
        TensorInfo actual=new TensorInfo(0,new int[]{1,2,2,3},"INT8",12,.01f,-5);
        ModelTensorContractValidator.validateInput(spec,actual);
        Bitmap source=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
        try {
            PreparedInput prepared=BitmapTensorPreprocessor.prepare(source,spec,actual);
            assertEquals(95,prepared.buffer.get(0));assertEquals(-5,prepared.buffer.get(1));
        } finally { source.recycle(); }
        try { ModelTensorContractValidator.validateInput(spec,new TensorInfo(0,new int[]{1,2,2,3},"INT8",12,.02f,-5));fail(); }
        catch(IllegalArgumentException expected) { }
        for(Object[] field:new Object[][]{{"batch",2},{"batch",1.5},{"batch","invalid"},{"resize_mode","stretch"},{"padding_value",0},{"interpolation","nearest"},{"color","XYZ"}}) {
            try { ModelInputSpec.fromJson(new JSONObject(base.toString()).put((String)field[0],field[1]));fail(field[0].toString()); }
            catch(JSONException | IllegalArgumentException expected) { }
        }
        JSONObject output=new JSONObject().put("class_count",1).put("decoder","ultralytics_pose_raw_v1")
                .put("keypoint_count",4).put("keypoint_order",new JSONArray()
                        .put("top_left").put("top_right").put("bottom_right").put("bottom_left"));
        assertEquals(4,ModelOutputSpec.fromJson(output).keypointCount());
        output.getJSONArray("keypoint_order").put(0,"bottom_left");
        try { ModelOutputSpec.fromJson(output);fail(); } catch(JSONException expected) { }
    }
    @Test public void normalizerAgreesWithPythonGoldenCases() throws Exception {
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("interop/registration_cases.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONArray cases=new JSONObject(bytes.toString("UTF-8")).getJSONArray("cases");
            for(int i=0;i<cases.length();i++) {
                JSONObject row=cases.getJSONObject(i);String raw=row.isNull("raw") ? null : row.getString("raw");
                assertEquals(String.valueOf(raw),row.getString("key"),com.example.alpr_v1.domain.RegistrationTextNormalizer.registrationKey(raw));
            }
        }
    }
}
