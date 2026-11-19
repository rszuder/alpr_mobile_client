package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.example.alpr_v1.pipeline.PlateObservation;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CropObservationPreviewTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void photosAreReadByExactSourceAndLookupDoesNotRewriteTheSession()throws Exception {
        java.io.File root=temp.newFolder();CropSessionStore store=new CropSessionStore(root);
        Bitmap image=Bitmap.createBitmap(1024,256,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStoreTest fixture=new RecognitionHistoryStoreTest();
        try {
            image.eraseColor(Color.RED);PlateObservation a=fixture.reading(3,1,6,"HV3MP",image,true);
            store.record("session-test",a,null,"test").get(5,TimeUnit.SECONDS);
            image.eraseColor(Color.BLUE);PlateObservation b=fixture.reading(3,1,12,"HVI3MP",image,true);
            store.record("session-test",b,null,"test").get(5,TimeUnit.SECONDS);
            PlateObservation other=fixture.reading(3,2,18,"HV3MP",image,true);
            store.record("session-test",other,null,"test").get(5,TimeUnit.SECONDS);
            Path manifest=root.toPath().resolve("session-test/session.json");byte[] before=Files.readAllBytes(manifest);
            try(CropSessionStore.ObservationPreview first=store.previewForObservation("session-test",new RecognitionHistoryObservation(a,"test",null).key()).get(5,TimeUnit.SECONDS);
                    CropSessionStore.ObservationPreview second=store.previewForObservation("session-test",new RecognitionHistoryObservation(b,"test",null).key()).get(5,TimeUnit.SECONDS)) {
                assertNotNull(first);assertNotNull(second);assertEquals("HV3MP",first.text);assertEquals("HVI3MP",second.text);
                assertTrue(first.bitmap.getWidth()<=512);assertTrue(Color.red(first.bitmap.getPixel(1,1))>200);
                assertTrue(Color.blue(second.bitmap.getPixel(1,1))>200);
            }
            assertNull(store.previewForObservation("session-test",new RecognitionHistoryObservation(other,"test",null).key()).get(5,TimeUnit.SECONDS));
            assertArrayEquals(before,Files.readAllBytes(manifest));
        }finally{store.close();image.recycle();}
    }
}
