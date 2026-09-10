package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.ui.RecognitionObservationDetails;
import com.example.alpr_v1.vision.Detection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DynamicRecognitionHistoryInstrumentedTest {
    public static PlateObservation observation(Bitmap bitmap, long scene, long entity, long track, long frame, String text) {
        return observation(bitmap, scene, entity, track, frame, text, true, text);
    }
    public static PlateObservation observation(Bitmap bitmap, long scene, long entity, long track, long frame,
            String text, boolean attempted, String fresh) {
        CropInferenceTiming timing = new CropInferenceTiming(frame,0,10_000_000,9_000_000,
                1_000_000,20_000_000,1_000_000,1_000_000,1_000_000,30_000_000,1_000_000,64_000_000);
        return new PlateObservation(track,entity > 0 ? PlateVehicleAssociation.direct(entity,entity,"test")
                : PlateVehicleAssociation.unassigned("ambiguous"),MtWorkKind.VEHICLE_ROI,MtReason.SCAN_NEXT_CANDIDATE,
                frame,bitmap,text,.9,.8,true,1,
                Collections.singletonList(new PlateCharacter("W",.8,.1f,.1f,.3f,.9f)),
                frame,frame * 1_000_000, .8f,null,timing,
                PlateGeometry.from(960,1280,new Detection(0,.9f,100,300,400,400,Collections.emptyList()),Collections.emptyList()),
                attempted,!fresh.trim().isEmpty(),fresh,true,1,"single_row",Collections.emptyList(),"",text,
                new ContinuityStamp(scene,1,0,frame));
    }
    public static ObservationTelemetry telemetry() {
        return new ObservationTelemetry(29.5,38.2,67,1,Collections.singletonList(
                new ModelRuntimeSummary("MZ","Characters","onnx_int8","INT8","ONNX Runtime/CPU")));
    }
    @Test public void emptyMzAndCarriedConsensusCannotCreateOrExtendGalleryEntry() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            for (boolean dynamic : new boolean[]{false,true}) {
                store.clear();
                PlateObservation empty = observation(bitmap,1,4,7,10,"WI1234A",true,"");
                PlateObservation carried = observation(bitmap,1,4,7,11,"WI1234A",false,"WI1234A");
                PlateObservation whitespace = observation(bitmap,1,4,7,12,"WI1234A",true,"   ");
                assertFalse(store.upsertObservation(empty,telemetry(),dynamic,"normal"));
                assertFalse(store.upsertObservation(carried,telemetry(),dynamic,"normal"));
                assertFalse(store.upsertObservation(whitespace,telemetry(),dynamic,"normal"));
                assertEquals(0,store.size());
                assertTrue(store.upsertObservation(observation(bitmap,1,4,7,13,"WI1234A"),telemetry(),dynamic,"normal"));
                assertFalse(store.upsertObservation(empty,telemetry(),dynamic,"normal"));
                assertEquals(1,store.newestFirst().get(0).observationRecords().size());
                assertFalse(bitmap.isRecycled());
            }
        } finally { store.clear(); bitmap.recycle(); }
    }

    @Test public void sameNumberAcrossEntitiesAndScenesRetainsOneImageAndAllEvidence() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            store.upsertObservation(observation(bitmap,1,4,7,10,"WI1234A"),telemetry(),true,"normal");
            Bitmap retained = store.newestFirst().get(0).previewBitmap;
            store.upsertObservation(observation(null,2,9,8,11,"WI1234A"),telemetry(),true,"normal");
            assertEquals(1,store.size());
            RecognitionHistoryItem item = store.newestFirst().get(0);
            assertSame(retained,item.previewBitmap);
            assertEquals(2,item.observations);
            assertEquals(4,item.observationRecords().get(0).entityId);
            assertEquals(9,item.observationRecords().get(1).entityId);
            String details = RecognitionObservationDetails.format(item.observationRecords());
            assertTrue(details.contains("Encja: 4"));
            assertTrue(details.contains("Encja: 9"));
            assertTrue(details.contains("29,5"));
            assertTrue(details.contains("38,2"));
            assertTrue(details.contains("67,0"));
            assertTrue(details.contains("onnx_int8"));
            assertTrue(details.contains("960×1280"));
            assertTrue(details.contains("MZ: 30,0 ms"));
            RecognitionHistoryItem snapshot = item.snapshot();
            store.clear();
            assertEquals(2,snapshot.observationRecords().size());
            assertFalse(snapshot.previewBitmap.isRecycled());
            snapshot.close();
        } finally { store.clear(); bitmap.recycle(); }
    }
    @Test public void callbackAndFinalResultAreOneObservationAndLateAssociationUpdatesOwner() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            PlateObservation first = observation(bitmap,1,0,7,10,"WI1234A");
            assertTrue(store.upsertObservation(first,telemetry(),true,"normal"));
            assertFalse(store.upsertObservation(first,telemetry(),true,"normal"));
            assertTrue(store.upsertObservation(observation(null,1,4,7,10,"WI1234A"),telemetry(),true,"normal"));
            assertEquals(1,store.size());
            assertEquals(1,store.newestFirst().get(0).observations);
            assertEquals(4,store.newestFirst().get(0).observationRecords().get(0).entityId);
        } finally { store.clear(); bitmap.recycle(); }
    }
    @Test public void exactReadingsGroupAcrossEntitiesInBothSceneModesIncludingShortOutputs() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            for (boolean dynamic : new boolean[]{false,true}) for (String read : new String[]{"WI", "AAA", "WI1234A"}) {
                store.clear();
                assertTrue(store.upsertObservation(observation(bitmap,1,4,7,10,read),telemetry(),dynamic,"normal"));
                assertTrue(store.upsertObservation(observation(null,2,9,8,11,read),telemetry(),dynamic,"normal"));
                assertEquals(1,store.size());
                assertEquals(2,store.newestFirst().get(0).observationRecords().size());
                assertExactReadings(store);
            }
        } finally { store.clear(); bitmap.recycle(); }
    }

    @Test public void changedReadingCannotFollowEntityAliasOrReplaceOriginalCrop() {
        Bitmap first = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        Bitmap other = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        first.eraseColor(android.graphics.Color.RED); other.eraseColor(android.graphics.Color.BLUE);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            for(boolean dynamic : new boolean[]{false,true}) {
                store.clear();
                store.upsertObservation(observation(first,1,4,7,10,"AAA"),telemetry(),dynamic,"normal");
                RecognitionHistoryItem original = store.newestFirst().get(0);
                Bitmap retained = original.previewBitmap;
                // Reused entity/plate track, different MZ output and a newer (formerly preferred) crop.
                store.upsertObservation(observation(other,1,4,7,11,"AAB"),telemetry(),dynamic,"normal");
                assertEquals(2,store.size());
                assertEquals("AAA",original.text);
                assertSame(retained,original.previewBitmap);
                assertEquals(android.graphics.Color.RED,retained.getPixel(0,0));
                store.upsertObservation(observation(null,2,9,8,12,"AAB"),telemetry(),dynamic,"normal");
                // This entity previously read AAB. AAA must return to the AAA entry, not merge both entries.
                store.upsertObservation(observation(null,2,9,8,13,"AAA"),telemetry(),dynamic,"normal");
                assertEquals(2,store.size());
                assertSame(original,store.newestFirst().get(0));
                assertEquals(2,original.observations);
                assertEquals(4,original.observationRecords().get(0).entityId);
                assertEquals(9,original.observationRecords().get(1).entityId);
                assertSame(retained,original.previewBitmap);
                assertExactReadings(store);
            }
        } finally { store.clear(); first.recycle(); other.recycle(); }
    }

    @Test public void canonicalMatchesKeepEachRawAndOriginalImage() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            String[] readings = {"WI1234A", "WI1234B", "WI1234", "wi1234a", "WI 1234A", "WI-1234A", "WI1234A ", "W11234A"};
            for (int index=0; index<readings.length; index++) {
                store.upsertObservation(observation(bitmap,1,4,7,10+index,readings[index]),telemetry(),true,"normal");
                assertEquals(new int[]{1,2,3,3,3,3,3,4}[index],store.size());
            }
            assertExactReadings(store);
            RecognitionHistoryItem first = store.newestFirst().stream().filter(item -> item.text.equals("WI1234A")).findFirst().get();
            assertEquals(5,first.observationRecords().size());
            assertEquals("wi1234a",first.observationRecords().get(1).rawPrediction);
            assertEquals("WI 1234A",first.observationRecords().get(2).rawPrediction);
        } finally { store.clear(); bitmap.recycle(); }
    }

    @Test public void mismatchingReadWithoutImageCannotContaminateExistingEntry() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        try {
            store.upsertObservation(observation(bitmap,1,4,7,10,"AAA"),telemetry(),true,"normal");
            assertFalse(store.upsertObservation(observation(null,1,4,7,11,"AAB"),telemetry(),true,"normal"));
            assertEquals(1,store.size()); assertEquals(1,store.newestFirst().get(0).observations);
            assertExactReadings(store);
        } finally { store.clear(); bitmap.recycle(); }
    }

    private static void assertExactReadings(RecognitionHistoryStore store) {
        for(RecognitionHistoryItem item : store.newestFirst())
            for(RecognitionHistoryObservation record : item.observationRecords())
                assertEquals(RecognitionHistoryStore.numberKey(item.text),record.registrationKey);
    }

    @Test public void deletionAndCapacityDoNotLeaveNumberAliasesPointingAtRecycledImages() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore store = new RecognitionHistoryStore(1);
        try {
            store.upsertObservation(observation(bitmap,1,4,7,10,"WI1234A"),telemetry(),true,"normal");
            Bitmap previous = store.newestFirst().get(0).previewBitmap;
            store.upsertObservation(observation(bitmap,1,9,8,11,"WI5678B"),telemetry(),true,"normal");
            assertTrue(previous.isRecycled());
            store.remove(store.newestFirst().get(0).historyId);
            store.upsertObservation(observation(bitmap,1,10,9,12,"WI1234A"),telemetry(),true,"normal");
            assertEquals(1,store.size());
            assertEquals(1,store.newestFirst().get(0).observations);
        } finally { store.clear(); bitmap.recycle(); }
    }
}
