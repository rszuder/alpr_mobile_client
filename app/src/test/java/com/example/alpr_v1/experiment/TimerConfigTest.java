package com.example.alpr_v1.experiment;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimerConfigTest {
    @Test public void customMinutesAcceptCommaDotAndDurationsAboveAnHour() {
        assertEquals(150, TimerConfig.fromMinutes("2,5").durationSeconds());
        assertEquals(75, TimerConfig.fromMinutes("1.25").durationSeconds());
        assertEquals(7200, TimerConfig.fromMinutes("120").durationSeconds());
        assertEquals(7_200_000L, TimerConfig.fromMinutes("120").durationMillis());
    }
    @Test public void invalidAndOverflowingValuesNeverSilentlyChangeDuration() {
        for (String input : new String[]{"", "0", "-1", "NaN", "Infinity", "0.01", "999999999999", "1,2,3"}) {
            try { TimerConfig.fromMinutes(input); fail(input); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void disabledTimerAndExistingPresetsKeepTheirSemantics() {
        assertFalse(TimerConfig.disabled().enabled());
        assertEquals(180_000L, TimerConfig.of(true, 180).durationMillis());
        assertEquals(15, TimerConfig.of(true, 15).durationSeconds());
    }
}
