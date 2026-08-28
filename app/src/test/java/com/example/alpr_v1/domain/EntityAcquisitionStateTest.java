package com.example.alpr_v1.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EntityAcquisitionStateTest {
    @Test
    public void ordinaryProgressNeverRegresses() {
        assertEquals(
                EntityAcquisitionState.READY_TO_FINALIZE,
                EntityAcquisitionState.advance(
                        EntityAcquisitionState.READY_TO_FINALIZE,
                        EntityAcquisitionState.PLATE_LOCALIZED
                )
        );
        assertEquals(
                EntityAcquisitionState.ACQUIRED,
                EntityAcquisitionState.advance(
                        EntityAcquisitionState.ACQUIRED,
                        EntityAcquisitionState.ACQUIRING
                )
        );
        assertEquals(
                EntityAcquisitionState.EXPIRED,
                EntityAcquisitionState.advance(
                        EntityAcquisitionState.READY_TO_FINALIZE,
                        EntityAcquisitionState.EXPIRED
                )
        );
    }
}
