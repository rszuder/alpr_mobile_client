package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AcquisitionQueueTest {
    @Test
    public void waitingAgePreventsPermanentStarvation() {
        AcquisitionQueue queue = new AcquisitionQueue(8, 10_000_000_000L);
        queue.offer(offer(1L, 0.82f), 0L);
        queue.offer(offer(2L, 1.00f), 3_000_000_000L);

        assertEquals(1L, queue.peek(4_000_000_000L).entityId);
    }

    @Test
    public void ttlRemovesStaleCandidateAndCapacityKeepsBest() {
        AcquisitionQueue queue = new AcquisitionQueue(2, 1_000L);
        queue.offer(offer(1L, 0.2f), 100L);
        queue.offer(offer(2L, 0.9f), 100L);
        queue.offer(offer(3L, 0.7f), 100L);

        assertEquals(2, queue.size());
        assertEquals(2L, queue.poll(100L).entityId);
        assertEquals(3L, queue.poll(100L).entityId);
        assertNull(queue.poll(100L));

        queue.offer(offer(4L, 1f), 200L);
        assertEquals(1, queue.expire(1_201L));
        assertTrue(queue.snapshot(1_201L).isEmpty());
    }

    private static AcquisitionQueue.Offer offer(long entityId, float readability) {
        return new AcquisitionQueue.Offer(
                entityId, 0f, readability, 0f, 0f, 0f, 0f, 0f
        );
    }
}
