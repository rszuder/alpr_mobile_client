package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.lang.reflect.Constructor;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class DynamicMzPresentationActivityTest {
    private final ContinuityStamp source=new ContinuityStamp(0,0,0,1,1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
    private VehicleTrackingCoordinator coordinator() {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,1_000_000_000L);
        c.updateFromMp(1,source.sourceFrameStamp(),source.sourceTimestampNanos,Collections.singletonList(
                new VehicleTrackManager.Observation(new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,
                        new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),source.sourceTimestampNanos);
        return c;
    }
    private AutoCloseable job(VehicleTrackingCoordinator c,Bitmap crop)throws Exception {
        DynamicVehicleIdentity.Stamp owner=c.identity().stamp(1,1);
        PlateVehicleAssociation association=PlateVehicleAssociation.directValidated(1,1,1,"fixture");
        PlateObservation draft=new PlateObservation(1,association,MtWorkKind.VEHICLE_ROI,MtReason.UNKNOWN,1,crop,"",
                .9,0,false,0,Collections.emptyList(),1,1,.8f,null,null,null,false,false,"",false,0,"single",
                Collections.emptyList(),"","",source).stampIdentityOwner(owner);
        Constructor<?> ctor=Class.forName(MobileAlprEngine.class.getName()+"$DynamicMzJob").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return (AutoCloseable)ctor.newInstance(draft,association,crop,.8f,100,100,1,0L,null,null,c);
    }
    @Test public void cancellingAQueuedJobEndsVerificationWithoutChangingIdentity()throws Exception {
        VehicleTrackingCoordinator c=coordinator();Bitmap crop=Bitmap.createBitmap(16,4,Bitmap.Config.ARGB_8888);
        AutoCloseable job=job(c,crop);
        assertTrue(c.displaySnapshot(source).vehicles.get(1L).mzInProgress);
        job.close();job.close();
        assertFalse(c.displaySnapshot(source).vehicles.get(1L).mzInProgress);
        assertTrue(crop.isRecycled());assertEquals(1,c.identity().ownerNumber(1));
        assertEquals(0,c.repository().get(1).mzAttempts());
    }
    @Test public void transferringTheResultEndsVerificationButPreservesItsGalleryBitmap()throws Exception {
        VehicleTrackingCoordinator c=coordinator();Bitmap crop=Bitmap.createBitmap(16,4,Bitmap.Config.ARGB_8888);
        AutoCloseable job=job(c,crop);ReflectionHelpers.setField(job,"transferred",true);
        job.close();assertFalse(c.displaySnapshot(source).vehicles.get(1L).mzInProgress);
        assertFalse(crop.isRecycled());crop.recycle();
    }
    @Test public void aSceneBoundaryRejectsOldWorkAndItsLateCompletion()throws Exception {
        VehicleTrackingCoordinator c=coordinator();Bitmap crop=Bitmap.createBitmap(16,4,Bitmap.Config.ARGB_8888);
        DynamicVehicleIdentity.Stamp old=c.identity().stamp(1,1);AutoCloseable job=job(c,crop);
        c.resetScene();assertEquals(0,c.beginDynamicMzAttempt(old));job.close();
        assertTrue(c.displaySnapshot(source).vehicles.isEmpty());
    }
}
