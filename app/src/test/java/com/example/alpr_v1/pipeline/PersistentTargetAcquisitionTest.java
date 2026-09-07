package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.vision.Detection;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersistentTargetAcquisitionTest {
    @Test public void expandedTargetRoiCannotSendNeighborsPlateToMz() {
        VehicleCandidate target = new VehicleCandidate(1L,11L,new NormalizedBounds(.05f,.1f,.45f,.9f),.9f,.9f,0f,false,0,1L,1L);
        VehicleCandidate other = new VehicleCandidate(2L,22L,new NormalizedBounds(.55f,.1f,.95f,.9f),.9f,.9f,0f,false,0,1L,1L);
        List<VehicleCandidate> pool = Arrays.asList(target,other);
        List<Detection> plates = Arrays.asList(
                new Detection(0,.9f,20,60,70,75,Collections.emptyList()),
                new Detection(0,.95f,125,60,175,75,Collections.emptyList()));
        PlateTrackCoordinator tracker = new PlateTrackCoordinator();
        List<PlateTrackCoordinator.Decision> decisions = tracker.update(Arrays.asList(
                new PlateTrackCoordinator.Observation(0,new MotionBoxTracker.Box(.1f,.6f,.35f,.75f),.9f,true),
                new PlateTrackCoordinator.Observation(1,new MotionBoxTracker.Box(.625f,.6f,.875f,.75f),.9f,true)),1L,1L);
        PlateVehicleAssociator associator = new PlateVehicleAssociator();
        Map<Long,PlateVehicleAssociation> owners = new HashMap<>();
        VehicleRoi expanded = new VehicleRoi(target,0,0,200,100);
        for (PlateTrackCoordinator.Decision decision : decisions) owners.put(decision.trackId,
                associator.associateVehicleRoi(plates.get(decision.sourceIndex),expanded,200,100,pool));
        List<PlateTrackCoordinator.Decision> mz = MobileAlprEngine.focusedPlateDecisions(decisions,owners,1L);
        assertEquals(2,decisions.size());
        assertEquals(1,mz.size());
        assertEquals(0,mz.get(0).sourceIndex);
        assertEquals(2,MobileAlprEngine.focusedPlateDecisions(decisions,owners,0L).size());
        assertTrue(MobileAlprEngine.focusedPlateDecisions(decisions,Collections.emptyMap(),1L).isEmpty());
    }
}
